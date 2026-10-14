package dev.kona.cache

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ArrayCacheStore @JvmOverloads constructor(
    private val clock: Clock = Clock.systemUTC(),
) : CacheStore {
    private data class Entry(val value: Any?, val expiresAt: Instant?)

    private val items = ConcurrentHashMap<String, Entry>()

    override fun get(key: String): Any? {
        val entry = items[key] ?: return null
        if (expired(entry)) {
            items.remove(key)
            return null
        }
        return entry.value
    }

    override fun put(key: String, value: Any?, seconds: Long) {
        val expires = if (seconds <= 0) {
            Instant.now(clock)
        } else {
            Instant.now(clock).plusSeconds(seconds)
        }
        items[key] = Entry(value, expires)
    }

    override fun forever(key: String, value: Any?) {
        items[key] = Entry(value, null)
    }

    override fun forget(key: String): Boolean = items.remove(key) != null

    override fun flush() {
        items.clear()
    }

    private fun expired(entry: Entry): Boolean {
        val expires = entry.expiresAt ?: return false
        return !expires.isAfter(Instant.now(clock))
    }
}
