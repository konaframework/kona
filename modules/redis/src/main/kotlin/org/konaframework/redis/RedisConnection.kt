package org.konaframework.redis

/**
 * Minimal Redis command surface used by cache/session/broadcasting (Lettuce or in-memory).
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

    /** PUBLISH [channel] [message]. Returns receiver count (best-effort for in-memory). */
    fun publish(channel: String, message: String): Long

    /**
     * Subscribe to [channels]. The returned handle unsubscribes.
     * In-memory delivers on the publishing thread; Lettuce uses a dedicated pub/sub connection.
     */
    fun subscribe(vararg channels: String, onMessage: (channel: String, message: String) -> Unit): AutoCloseable
}
