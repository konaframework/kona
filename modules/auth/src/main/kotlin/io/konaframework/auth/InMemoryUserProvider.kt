package io.konaframework.auth

import io.konaframework.auth.hashing.Hasher

/**
 * In-memory user provider for tests and M3 bootstrap.
 */
class InMemoryUserProvider @JvmOverloads constructor(
    private val hasher: Hasher,
    private val passwordKey: String = "password",
) : UserProvider {
    private val byId = linkedMapOf<Any, Authenticatable>()

    fun add(user: Authenticatable): InMemoryUserProvider {
        byId[user.getAuthIdentifier()] = user
        return this
    }

    fun clear(): InMemoryUserProvider {
        byId.clear()
        return this
    }

    fun all(): List<Authenticatable> = byId.values.toList()

    override fun retrieveById(identifier: Any): Authenticatable? =
        byId[identifier] ?: byId.entries.firstOrNull { it.key.toString() == identifier.toString() }?.value

    override fun retrieveByCredentials(credentials: Map<String, Any?>): Authenticatable? {
        val loginKeys = credentials.keys.filter { it != passwordKey }
        if (loginKeys.isEmpty()) {
            return null
        }
        return byId.values.firstOrNull { user ->
            loginKeys.all { key ->
                val expected = credentials[key] ?: return@all false
                credentialValue(user, key)?.toString() == expected.toString()
            }
        }
    }

    override fun validateCredentials(user: Authenticatable, credentials: Map<String, Any?>): Boolean {
        val plainValue = credentials[passwordKey]
        if (plainValue == null) {
            return false
        }
        return hasher.check(plainValue.toString(), user.getAuthPassword())
    }

    override fun retrieveByToken(identifier: Any, token: String): Authenticatable? {
        val user = retrieveById(identifier) ?: return null
        val remember = user.getRememberToken()
        if (remember.isNullOrBlank() || remember != token) {
            return null
        }
        return user
    }

    override fun updateRememberToken(user: Authenticatable, token: String?) {
        user.setRememberToken(token)
        byId[user.getAuthIdentifier()] = user
    }

    private fun credentialValue(user: Authenticatable, key: String): Any? {
        if (user is HasCredentials) {
            return user.getCredential(key)
        }
        if (key == "id" || key == "identifier") {
            return user.getAuthIdentifier()
        }
        if (key == "email" && user is HasEmail) {
            return user.getEmail()
        }
        return null
    }
}

/** Optional credential bag for in-memory matching (email, username, …). */
interface HasCredentials {
    fun getCredential(key: String): Any?
}

interface HasEmail {
    fun getEmail(): String?
}
