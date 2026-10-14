package dev.kona.auth

/**
 * Retrieves authenticatable users (Laravel UserProvider).
 */
interface UserProvider {
    fun retrieveById(identifier: Any): Authenticatable?

    fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable?

    fun validateCredentials(user: Authenticatable, credentials: Map<String, Any?>): Boolean
}
