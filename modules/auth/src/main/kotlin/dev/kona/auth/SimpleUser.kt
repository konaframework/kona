package dev.kona.auth

/**
 * Simple authenticatable user for samples and tests (Java-friendly).
 */
class SimpleUser @JvmOverloads constructor(
    private val id: Any,
    private val passwordHash: String,
    private val email: String? = null,
    private val attributes: MutableMap<String, Any?> = linkedMapOf(),
) : Authenticatable, HasCredentials, HasEmail, dev.kona.auth.passwords.HasPasswordEmail {
    private var rememberToken: String? = null

    init {
        email?.let { attributes["email"] = it }
        attributes["id"] = id
    }

    override fun getAuthIdentifier(): Any = id

    override fun getAuthPassword(): String = passwordHash

    override fun getRememberToken(): String? = rememberToken

    override fun setRememberToken(token: String?) {
        rememberToken = token
    }

    override fun getCredential(key: String): Any? = when (key) {
        "id", "identifier" -> id
        "email" -> email ?: attributes["email"]
        else -> attributes[key]
    }

    override fun getEmail(): String? = email ?: attributes["email"]?.toString()

    fun setAttribute(key: String, value: Any?): SimpleUser {
        attributes[key] = value
        return this
    }

    fun withPasswordHash(hash: String): SimpleUser =
        SimpleUser(id, hash, email, attributes.toMutableMap()).also { it.rememberToken = rememberToken }
}
