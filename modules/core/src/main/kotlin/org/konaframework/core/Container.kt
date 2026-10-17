package org.konaframework.core

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Laravel-style service container: bind, singleton, instance, alias, resolve,
 * plus constructor autowiring for unbound concrete classes. Thread-safe.
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

    @JvmOverloads
    fun bind(abstract: String, concrete: (Container) -> Any, shared: Boolean = false) {
        bindings[abstract] = Binding(concrete, shared)
        instances.remove(abstract)
    }

    @JvmOverloads
    fun <T : Any> bind(type: Class<T>, concrete: (Container) -> T, shared: Boolean = false) {
        bind(type.name, { c -> concrete(c) }, shared)
    }

    /** Binds an abstraction (usually an interface) to an autowired implementation class. */
    @JvmOverloads
    fun <T : Any> bind(type: Class<T>, implementation: Class<out T>, shared: Boolean = false) {
        bind(type.name, { c -> c.make(implementation) }, shared)
    }

    fun singleton(abstract: String, concrete: (Container) -> Any) {
        bind(abstract, concrete, shared = true)
    }

    fun <T : Any> singleton(type: Class<T>, concrete: (Container) -> T) {
        singleton(type.name) { c -> concrete(c) }
    }

    fun <T : Any> singleton(type: Class<T>, implementation: Class<out T>) {
        bind(type, implementation, shared = true)
    }

    fun instance(abstract: String, instance: Any) {
        bindings[abstract] = Binding({ instance }, shared = true)
        instances[abstract] = instance
    }

    fun <T : Any> instance(type: Class<T>, instance: T) {
        instance(type.name, instance)
    }

    fun alias(alias: String, abstract: String) {
        if (getAlias(abstract) == alias) {
            throw BindingException("[$alias] is aliased to itself.")
        }
        aliases[alias] = abstract
    }

    fun getAlias(abstract: String): String {
        var key = abstract
        while (true) {
            key = aliases[key] ?: return key
        }
    }

    fun bound(abstract: String): Boolean = bindings.containsKey(getAlias(abstract))

    fun <T : Any> bound(type: Class<T>): Boolean = bound(type.name)

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

    fun <T : Any> make(abstract: String): T = resolve(abstract)

    fun <T : Any> make(type: Class<T>): T = resolve(type)

    fun flush() {
        bindings.clear()
        instances.clear()
        aliases.clear()
        registerSelf()
    }

    private fun registerSelf() {
        instance(Container::class.java, this)
    }

    private fun build(key: String, binding: Binding): Any {
        if (!binding.shared) {
            return track(key) { binding.factory(this) }
        }
        return sharedLock.withLock {
            instances[key] ?: track(key) { binding.factory(this) }.also { instances[key] = it }
        }
    }

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

    private fun resolveParameter(owner: Class<*>, parameter: Class<*>): Any {
        if (!bound(parameter) && isPlatformType(parameter)) {
            throw BindingException(
                "Unresolvable dependency [${parameter.name}] in class [${owner.name}]",
            )
        }
        return resolve(parameter)
    }

    private fun isPlatformType(type: Class<*>): Boolean =
        type.isPrimitive || PLATFORM_PREFIXES.any { type.name.startsWith(it) }

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

open class BindingException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class CircularDependencyException(message: String) : BindingException(message)
