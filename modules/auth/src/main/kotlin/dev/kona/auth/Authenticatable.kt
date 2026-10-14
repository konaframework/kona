package dev.kona.auth

/**
 * User that can be authenticated (Laravel Authenticatable).
 */
interface Authenticatable {
    /** Primary key / identifier stored in session or tokens. */
    fun getAuthIdentifier(): Any

    /** Password hash used by [dev.kona.auth.SessionGuard.attempt]. */
    fun getAuthPassword(): String

    /** Optional remember-token column; unused in M3 session-only flow. */
    fun getRememberToken(): String? = null

    fun setRememberToken(token: String?) {
        // default no-op for Java implementors that omit remember tokens
    }
}
