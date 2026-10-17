package org.konaframework.http

/**
 * Minimal session surface for redirects, old input, and validation flash
 * (implemented by `kona-session` [org.konaframework.session.Session]).
 */
interface RequestSession {
    fun get(key: String): Any?

    fun put(key: String, value: Any?): RequestSession

    fun flash(key: String, value: Any?): RequestSession

    /** Flash request input for the next request (`_old_input`). */
    fun flashInput(input: Map<String, String>): RequestSession

    /** Read a previously flashed input value. */
    fun old(key: String, default: String?): String?

    /** Read a previously flashed input value, or null when absent. */
    fun old(key: String): String? = old(key, null)

    /** All flashed old input (empty when none). */
    fun oldInput(): Map<String, String>

    fun previousUrl(): String?

    fun setPreviousUrl(url: String): RequestSession
}
