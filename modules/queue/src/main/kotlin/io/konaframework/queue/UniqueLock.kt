package io.konaframework.queue

import java.util.concurrent.ConcurrentHashMap

/** Process-local unique job locks. */
object UniqueLock {
    private val locks = ConcurrentHashMap<String, Long>()

    fun acquire(id: String, forSeconds: Long): Boolean {
        val now = System.currentTimeMillis()
        locks.entries.removeIf { it.value <= now }
        val expires = now + forSeconds.coerceAtLeast(1) * 1000
        return locks.putIfAbsent(id, expires) == null
    }

    fun release(id: String) {
        locks.remove(id)
    }

    fun clear() {
        locks.clear()
    }

    fun isLocked(id: String): Boolean {
        val expires = locks[id] ?: return false
        if (expires <= System.currentTimeMillis()) {
            locks.remove(id)
            return false
        }
        return true
    }
}
