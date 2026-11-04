package org.konaframework.cadence

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * Named cast factories for string declarations such as `"hashed"`. Auth registers
 * `hashed` → [org.konaframework.auth.HashedCast] when the auth module is on the classpath.
 */
object CastRegistry {
    private val factories = ConcurrentHashMap<String, Supplier<CastsAttributes>>()

    @JvmStatic
    fun register(name: String, factory: Supplier<CastsAttributes>) {
        factories[name] = factory
    }

    @JvmStatic
    fun unregister(name: String) {
        factories.remove(name)
    }

    @JvmStatic
    fun clear() {
        factories.clear()
    }

    @JvmStatic
    fun create(name: String): CastsAttributes? = factories[name]?.get()

    @JvmStatic
    fun isRegistered(name: String): Boolean = factories.containsKey(name)
}
