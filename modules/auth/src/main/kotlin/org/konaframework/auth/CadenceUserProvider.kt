package org.konaframework.auth

import org.konaframework.auth.hashing.Hasher
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion

/**
 * [UserProvider] backed by a Cadence model that implements [Authenticatable].
 *
 * Credential columns (except the password key) become `WHERE` equality clauses.
 * Remember tokens are written to `remember_token` and persisted with [Cadence.save].
 */
class CadenceUserProvider<T> @JvmOverloads constructor(
    private val modelClass: Class<T>,
    private val hasher: Hasher,
    private val passwordKey: String = "password",
    private val rememberTokenKey: String = "remember_token",
) : UserProvider where T : Cadence<T>, T : Authenticatable {
    private val companion: CadenceCompanion<T> = Cadence.of(modelClass)

    fun modelClass(): Class<T> = modelClass

    override fun retrieveById(identifier: Any): Authenticatable? = companion.find(identifier)

    override fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable? {
        val loginKeys = credentials.keys.filter { it != passwordKey }
        if (loginKeys.isEmpty()) {
            return null
        }
        var query = companion.query()
        for (key in loginKeys) {
            val expected = credentials[key] ?: return null
            query = query.where(key, expected)
        }
        return query.first()
    }

    override fun validateCredentials(user: Authenticatable, credentials: Map<String, Any?>): Boolean {
        val plainValue = credentials[passwordKey] ?: return false
        return hasher.check(plainValue.toString(), user.getAuthPassword())
    }

    override fun retrieveByToken(identifier: Any, token: String): Authenticatable? {
        val user = retrieveById(identifier) ?: return null
        val remember = user.getRememberToken()
        if (remember.isNullOrBlank()) {
            return null
        }
        if (remember != token) {
            return null
        }
        return user
    }

    override fun updateRememberToken(user: Authenticatable, token: String?) {
        user.setRememberToken(token)
        @Suppress("UNCHECKED_CAST")
        val model = user as? T ?: return
        model.setAttribute(rememberTokenKey, token)
        model.save()
    }
}
