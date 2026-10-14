package dev.kona.core

/**
 * Laravel-style service container: bind, singleton, instance, resolve.
 */
class Container {
    private val bindings = linkedMapOf<String, Binding>()
    private val instances = linkedMapOf<String, Any>()
    private val aliases = linkedMapOf<String, String>()

    fun bind(abstract: String, concrete: (Container) -> Any, shared: Boolean = false) {
        bindings[abstract] = Binding(concrete, shared)
        instances.remove(abstract)
    }

    fun <T : Any> bind(type: Class<T>, concrete: (Container) -> T, shared: Boolean = false) {
        bind(type.name, { c -> concrete(c) }, shared)
    }

    fun singleton(abstract: String, concrete: (Container) -> Any) {
        bind(abstract, concrete, shared = true)
    }

    fun <T : Any> singleton(type: Class<T>, concrete: (Container) -> T) {
        singleton(type.name) { c -> concrete(c) }
    }

    fun instance(abstract: String, instance: Any) {
        instances[abstract] = instance
        bindings[abstract] = Binding({ instance }, shared = true)
    }

    fun <T : Any> instance(type: Class<T>, instance: T) {
        instance(type.name, instance)
    }

    fun alias(alias: String, abstract: String) {
        aliases[alias] = abstract
    }

    fun bound(abstract: String): Boolean {
        val key = aliases[abstract] ?: abstract
        return bindings.containsKey(key) || instances.containsKey(key)
    }

    fun <T : Any> bound(type: Class<T>): Boolean = bound(type.name)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(abstract: String): T {
        val key = aliases[abstract] ?: abstract
        instances[key]?.let { return it as T }
        val binding = bindings[key]
            ?: throw BindingException("No binding for [$abstract]")
        val value = binding.factory(this)
        if (binding.shared) {
            instances[key] = value
        }
        return value as T
    }

    fun <T : Any> resolve(type: Class<T>): T = resolve(type.name)

    fun <T : Any> make(type: Class<T>): T {
        if (bound(type)) {
            return resolve(type)
        }
        return type.getDeclaredConstructor().newInstance()
    }

    fun flush() {
        bindings.clear()
        instances.clear()
        aliases.clear()
    }

    private data class Binding(
        val factory: (Container) -> Any,
        val shared: Boolean,
    )
}

class BindingException(message: String) : RuntimeException(message)
