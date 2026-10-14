package dev.kona.session

/**
 * HTTP session (Laravel Session shape).
 *
 * Placed on the request under [StartSession.ATTRIBUTE] (`"session"`), matching
 * auth’s session attribute key so guards can resolve the same request attribute.
 */
interface Session {
    val id: String

    fun get(key: String): Any?

    fun put(key: String, value: Any?): Session

    fun forget(key: String): Session

    /** Flash [value] for the next request only. */
    fun flash(key: String, value: Any?): Session

    /** Returns the value for [key] (or [default]) and removes it from the session. */
    fun pull(key: String, default: Any?): Any?

    /** Pull without a default. */
    fun pull(key: String): Any? = pull(key, null)

    /** Issues a new session id; when [destroy] is true, removes the previous store entry. */
    fun regenerate(destroy: Boolean): Session

    /** Regenerate without destroying the previous store entry. */
    fun regenerate(): Session = regenerate(false)

    /** Clears all attributes and regenerates the id (destroying the old store entry). */
    fun invalidate(): Session

    fun all(): Map<String, Any?>

    /** Ages flash data (drop previous flash; promote new → old). Called when a session starts. */
    fun ageFlashData(): Session

    fun has(key: String): Boolean = get(key) != null
}
