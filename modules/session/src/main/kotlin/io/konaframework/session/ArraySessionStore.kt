package io.konaframework.session

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session driver (`array` / `memory`). Process-local; lost on restart.
 */
class ArraySessionStore : SessionStore {
    private val sessions = ConcurrentHashMap<String, Map<String, Any?>>()

    override fun read(id: String): Map<String, Any?> =
        sessions[id]?.let { LinkedHashMap(it) } ?: emptyMap()

    override fun write(id: String, data: Map<String, Any?>, lifetimeMinutes: Int) {
        sessions[id] = LinkedHashMap(data)
    }

    override fun destroy(id: String) {
        sessions.remove(id)
    }

    fun clear() {
        sessions.clear()
    }

    fun size(): Int = sessions.size
}
