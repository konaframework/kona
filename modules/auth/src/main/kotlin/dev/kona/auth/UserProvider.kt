package dev.kona.auth

/**
 * Retrieves authenticatable users (Laravel UserProvider).
 */
interface UserProvider {
    fun retrieveById(identifier: Any): Authenticatable?

    fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable?

    fun validateCredentials(user: Authenticatable, credentials: Map<String, Any?>): Boolean

    /** Remember-cookie lookup (`id` + remember token). Default: unsupported. */
    fun retrieveByToken(identifier: Any, token: String): Authenticatable? = null

    /** Persist a new remember token after login/logout. Default: no-op. */
    fun updateRememberToken(user: Authenticatable, token: String?) {
        user.setRememberToken(token)
    }
}
