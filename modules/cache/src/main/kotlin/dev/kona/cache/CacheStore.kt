package dev.kona.cache

/**
 * Cache store contract (Laravel cache store shape).
 */
interface CacheStore {
    fun get(key: String): Any?

    fun put(key: String, value: Any?, seconds: Long)

    fun forever(key: String, value: Any?)

    fun forget(key: String): Boolean

    fun flush()

    fun has(key: String): Boolean = get(key) != null
}
