package io.konaframework.redis

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local Redis stand-in for tests and default boot without a server.
 */
class InMemoryRedisConnection @JvmOverloads constructor(
    private val clock: Clock = Clock.systemUTC(),
) : RedisConnection {
    private data class Entry(val value: String, val expiresAt: Instant?)

    private val store = ConcurrentHashMap<String, Entry>()

    override fun get(key: String): String? {
        val entry = store[key] ?: return null
        if (isExpired(entry)) {
            store.remove(key)
            return null
        }
        return entry.value
    }

    override fun set(key: String, value: String) {
        store[key] = Entry(value, null)
    }

    override fun setex(key: String, seconds: Long, value: String) {
        val expires = Instant.now(clock).plusSeconds(seconds.coerceAtLeast(0))
        store[key] = Entry(value, expires)
    }

    override fun del(vararg keys: String): Long {
        var removed = 0L
        for (key in keys) {
            if (store.remove(key) != null) {
                removed++
            }
        }
        return removed
    }

    override fun exists(key: String): Boolean = get(key) != null

    override fun expire(key: String, seconds: Long): Boolean {
        val entry = store[key] ?: return false
        if (isExpired(entry)) {
            store.remove(key)
            return false
        }
        store[key] = Entry(entry.value, Instant.now(clock).plusSeconds(seconds.coerceAtLeast(0)))
        return true
    }

    override fun ttl(key: String): Long {
        val entry = store[key] ?: return -2
        val expires = entry.expiresAt ?: return -1
        val seconds = expires.epochSecond - Instant.now(clock).epochSecond
        if (seconds <= 0) {
            store.remove(key)
            return -2
        }
        return seconds
    }

    override fun keys(pattern: String): List<String> {
        val regex = globToRegex(pattern)
        return store.mapNotNull { (key, entry) ->
            if (!isExpired(entry) && regex.matches(key)) key else null
        }.sorted()
    }

    override fun flushDb() {
        store.clear()
    }

    override fun ping(): String = "PONG"

    override fun close() {
        store.clear()
    }

    private fun isExpired(entry: Entry): Boolean {
        val expires = entry.expiresAt ?: return false
        return !expires.isAfter(Instant.now(clock))
    }

    private fun globToRegex(pattern: String): Regex {
        val escaped = buildString {
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
        return Regex("^$escaped$")
    }
}
