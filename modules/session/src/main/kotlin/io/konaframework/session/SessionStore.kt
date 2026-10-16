package io.konaframework.session

/**
 * Persistent session backend (Laravel session driver shape).
 */
interface SessionStore {
    fun read(id: String): Map<String, Any?>

    fun write(id: String, data: Map<String, Any?>, lifetimeMinutes: Int)

    fun destroy(id: String)
}
