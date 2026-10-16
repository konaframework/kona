package io.konaframework.redis

/**
 * Minimal Redis command surface used by cache/session (Lettuce or in-memory).
 */
interface RedisConnection : AutoCloseable {
    fun get(key: String): String?

    fun set(key: String, value: String)

    fun setex(key: String, seconds: Long, value: String)

    fun del(vararg keys: String): Long

    fun exists(key: String): Boolean

    fun expire(key: String, seconds: Long): Boolean

    fun ttl(key: String): Long

    fun keys(pattern: String): List<String>

    fun flushDb()

    fun ping(): String
}
