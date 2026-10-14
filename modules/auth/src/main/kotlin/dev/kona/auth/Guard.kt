package dev.kona.auth

import dev.kona.http.Request

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
}
