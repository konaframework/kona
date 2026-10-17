package org.konaframework.auth

import org.konaframework.http.Request

/**
 * Authentication guard (Laravel Guard contract).
 */
interface Guard {
    fun attempt(credentials: Map<String, Any?>, remember: Boolean = false): Boolean

    fun login(user: Authenticatable, remember: Boolean = false)

    fun logout()

    fun check(): Boolean

    fun guest(): Boolean = !check()

    fun id(): Any?

    fun user(): Authenticatable?

    /** Bind this guard to the current HTTP request (session / bearer). */
    fun setRequest(request: Request): Guard

    /** Validate credentials without logging the user in. */
    fun validate(credentials: Map<String, Any?>): Boolean

    /** Log in the user with the given identifier. Returns null when missing. */
    fun loginUsingId(id: Any, remember: Boolean = false): Authenticatable?

    /** Authenticate for this request only (no session / remember persistence). */
    fun once(credentials: Map<String, Any?>): Boolean

    /** Authenticate by id for this request only. */
    fun onceUsingId(id: Any): Boolean

    /** True when the current user was restored from a remember cookie. */
    fun viaRemember(): Boolean = false
}
