package org.konaframework.cache

/**
 * High-level cache repository (Laravel Cache store proxy / Repository).
 */
class Repository(
    private val store: CacheStore,
) {
    fun store(): CacheStore = store

    fun get(key: String): Any? = store.get(key)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T): T {
        val value = store.get(key)
        return if (value == null) default else value as T
    }

    fun put(key: String, value: Any?, seconds: Long): Boolean {
        store.put(key, value, seconds)
        return true
    }

    fun forever(key: String, value: Any?): Boolean {
        store.forever(key, value)
        return true
    }

    fun forget(key: String): Boolean = store.forget(key)

    fun flush(): Boolean {
        store.flush()
        return true
    }

    fun has(key: String): Boolean = store.has(key)

    fun missing(key: String): Boolean = !has(key)

    fun add(key: String, value: Any?, seconds: Long): Boolean {
        if (has(key)) {
            return false
        }
        put(key, value, seconds)
        return true
    }

    fun remember(key: String, seconds: Long, callback: CacheCallback): Any? {
        val existing = get(key)
        if (existing != null) {
            return existing
        }
        val value = callback.call()
        put(key, value, seconds)
        return value
    }

    fun rememberForever(key: String, callback: CacheCallback): Any? {
        val existing = get(key)
        if (existing != null) {
            return existing
        }
        val value = callback.call()
        forever(key, value)
        return value
    }

    fun pull(key: String): Any? {
        val value = get(key)
        forget(key)
        return value
    }

    /** Increment a numeric cache value (Laravel `Cache::increment`). */
    @JvmOverloads
    fun increment(key: String, amount: Long = 1): Long {
        val current = when (val value = get(key)) {
            null -> 0L
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull() ?: 0L
        }
        val next = current + amount
        forever(key, next)
        return next
    }

    /** Decrement a numeric cache value (Laravel `Cache::decrement`). */
    @JvmOverloads
    fun decrement(key: String, amount: Long = 1): Long = increment(key, -amount)
}

fun interface CacheCallback {
    fun call(): Any?
}
