package org.konaframework.core

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/***********************************************************************************************************************
 * Dependency injection container.
 * ---------------------------------------------------------------------------------------------------------------------
 * This is the core of the service container, providing a way to manage dependencies and their lifecycles.
 * It allows you to bind abstractions (usually interfaces) to concrete implementations, manage singleton instances,
 * and resolve dependencies automatically through constructor injection.
 *
 * Inspired by Laravel's service container, this implementation supports:
 * - Binding abstractions to concrete implementations.
 * - Singleton management for shared instances.
 * - Aliasing of abstractions for easier resolution.
 * - Circular dependency detection to prevent infinite loops during resolution.
 * - Autowiring of unbound concrete classes through constructor injection.
 *
 * Usage example:
 * ```kotlin
 * val container = Container()
 * container.bind(MyInterface::class.java) { MyImplementation() }
 * val instance = container.resolve(MyInterface::class.java)
 * ```
 * *********************************************************************************************************************
 */
class Container {
    private val bindings = ConcurrentHashMap<String, Binding>()
    private val instances = ConcurrentHashMap<String, Any>()
    private val aliases = ConcurrentHashMap<String, String>()
    private val sharedLock = ReentrantLock()
    private val building = ThreadLocal.withInitial { LinkedHashSet<String>() }

    init {
        registerSelf()
    }

    /** Binds an abstraction (usually an interface) to a concrete implementation factory. */
    @JvmOverloads
    fun bind(abstract: String, concrete: (Container) -> Any, shared: Boolean = false) {
        bindings[abstract] = Binding(concrete, shared)
        instances.remove(abstract)
    }

    /** Binds an abstraction (usually an interface) to a concrete implementation factory. */
    @JvmOverloads
    fun <T : Any> bind(type: Class<T>, concrete: (Container) -> T, shared: Boolean = false) {
        bind(type.name, { c -> concrete(c) }, shared)
    }

    /** Binds an abstraction (usually an interface) to an autowired implementation class. */
    @JvmOverloads
    fun <T : Any> bind(type: Class<T>, implementation: Class<out T>, shared: Boolean = false) {
        bind(type.name, { c -> c.make(implementation) }, shared)
    }

    /** Binds an abstraction (usually an interface) to a singleton implementation factory. */
    fun singleton(abstract: String, concrete: (Container) -> Any) {
        bind(abstract, concrete, shared = true)
    }

    /** Binds an abstraction (usually an interface) to a singleton implementation factory. */
    fun <T : Any> singleton(type: Class<T>, concrete: (Container) -> T) {
        singleton(type.name) { c -> concrete(c) }
    }

    /** Binds an abstraction (usually an interface) to a singleton autowired implementation class. */
    fun <T : Any> singleton(type: Class<T>, implementation: Class<out T>) {
        bind(type, implementation, shared = true)
    }

    /** Binds an abstraction (usually an interface) to a specific instance. */
    fun instance(abstract: String, instance: Any) {
        bindings[abstract] = Binding({ instance }, shared = true)
        instances[abstract] = instance
    }

    /** Binds an abstraction (usually an interface) to a specific instance. */
    fun <T : Any> instance(type: Class<T>, instance: T) {
        instance(type.name, instance)
    }

    /** Creates an alias for an abstraction. */
    fun alias(alias: String, abstract: String) {
        if (getAlias(abstract) == alias) {
            throw BindingException("[$alias] is aliased to itself.")
        }
        aliases[alias] = abstract
    }

    /** Resolves the final abstraction name, following any aliases. */
    fun getAlias(abstract: String): String {
        var key = abstract
        while (true) {
            key = aliases[key] ?: return key
        }
    }

    /** Checks if an abstraction is bound in the container. */
    fun bound(abstract: String): Boolean = bindings.containsKey(getAlias(abstract))

    /** Checks if a type is bound in the container. */
    fun <T : Any> bound(type: Class<T>): Boolean = bound(type.name)

    /** Resolves a bound type, throwing an exception if not found. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(abstract: String): T {
        val key = getAlias(abstract)
        instances[key]?.let { return it as T }
        val binding = bindings[key]
            ?: throw BindingException("No binding for [$abstract]")
        return build(key, binding) as T
    }

    /** Resolves a bound type, or autowires it through its constructor when unbound. */
    fun <T : Any> resolve(type: Class<T>): T {
        if (bound(type)) {
            return resolve(type.name)
        }
        return track(type.name) { autowire(type) }
    }

    /** Resolves a bound type, or autowires it through its constructor when unbound. */
    fun <T : Any> make(abstract: String): T = resolve(abstract)

    /** Resolves a bound type, or autowires it through its constructor when unbound. */
    fun <T : Any> make(type: Class<T>): T = resolve(type)

    /** Clears all bindings, instances, and aliases from the container. */
    fun flush() {
        bindings.clear()
        instances.clear()
        aliases.clear()
        registerSelf()
    }

    /** Clears all singleton instances from the container, but keeps bindings and aliases. */
    private fun registerSelf() {
        instance(Container::class.java, this)
    }

    /** Builds an instance for a given key and binding, handling shared instances and circular dependencies. */
    private fun build(key: String, binding: Binding): Any {
        if (!binding.shared) {
            return track(key) { binding.factory(this) }
        }
        return sharedLock.withLock {
            instances[key] ?: track(key) { binding.factory(this) }.also { instances[key] = it }
        }
    }

    /** Tracks the building process for a given key, detecting circular dependencies. */
    private fun <R> track(key: String, block: () -> R): R {
        val stack = building.get()
        if (!stack.add(key)) {
            val chain = stack.dropWhile { it != key } + key
            throw CircularDependencyException("Circular dependency detected: ${chain.joinToString(" -> ")}")
        }
        try {
            return block()
        } finally {
            stack.remove(key)
        }
    }

    /** Autowires a concrete class by selecting its constructor and resolving its parameters. */
    private fun <T : Any> autowire(type: Class<T>): T {
        val constructor = selectConstructor(type)
        val args = constructor.parameterTypes.map { resolveParameter(type, it) }
        try {
            return constructor.newInstance(*args.toTypedArray())
        } catch (e: InvocationTargetException) {
            throw BindingException("Failed to build [${type.name}]: ${e.targetException.message}", e.targetException)
        } catch (e: IllegalAccessException) {
            throw BindingException("Target [${type.name}] is not accessible.", e)
        }
    }

    /** Selects the appropriate constructor for autowiring, preferring @Inject annotated constructors. */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> selectConstructor(type: Class<T>): Constructor<T> {
        val constructors = (type.constructors as Array<Constructor<T>>).filter { !it.isSynthetic }
        if (Modifier.isAbstract(type.modifiers) || constructors.isEmpty()) {
            throw BindingException("Target [${type.name}] is not instantiable.")
        }
        val injectable = constructors.filter { ctor ->
            ctor.annotations.any { it.annotationClass.java.simpleName == "Inject" }
        }
        return when (injectable.size) {
            0 -> constructors.maxBy { it.parameterCount }
            1 -> injectable.single()
            else -> throw BindingException("Target [${type.name}] has multiple @Inject constructors.")
        }
    }

    /** Resolves a constructor parameter, throwing an exception for unresolvable platform types. */
    private fun resolveParameter(owner: Class<*>, parameter: Class<*>): Any {
        if (!bound(parameter) && isPlatformType(parameter)) {
            throw BindingException(
                "Unresolvable dependency [${parameter.name}] in class [${owner.name}]",
            )
        }
        return resolve(parameter)
    }

    /** Determines if a type is a platform type (primitive or standard library). */
    private fun isPlatformType(type: Class<*>): Boolean =
        type.isPrimitive || PLATFORM_PREFIXES.any { type.name.startsWith(it) }

    /** Represents a binding in the container, including its factory and whether it's shared. */
    private data class Binding(
        val factory: (Container) -> Any,
        val shared: Boolean,
    )
}

private val PLATFORM_PREFIXES = listOf("java.", "javax.", "kotlin.")

/** Marks the constructor the container should use when autowiring. */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject

/** Exception thrown when a binding cannot be resolved or a circular dependency is detected. */
open class BindingException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Exception thrown when a circular dependency is detected during resolution. */
class CircularDependencyException(message: String) : BindingException(message)
