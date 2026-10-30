package org.konaframework.firefly

import org.konaframework.auth.Guard
import org.konaframework.auth.HasCredentials
import org.konaframework.auth.HasEmail
import org.konaframework.auth.HasName

/**
 * The guard a panel checks. It is separate from the application's guard unless the panel is
 * configured to reuse that guard.
 */
interface PanelGuard {
    fun attempt(email: String, password: String): Boolean

    fun check(): Boolean

    fun id(): String?

    /** Human display name for chrome, when the backing user exposes one. */
    fun name(): String?

    /** Email for chrome, when the backing user exposes one. */
    fun email(): String?

    fun logout()

    fun register(name: String, email: String, password: String): String?

    fun requestReset(email: String): String?

    fun verify(email: String): String?
}

/** In-memory accounts for a panel that does not reuse the application guard. */
class MemoryPanelGuard : PanelGuard {
    private class Account(val name: String, val email: String, val password: String, var verified: Boolean)

    private val users = LinkedHashMap<String, Account>()
    private var current: String? = null

    override fun register(name: String, email: String, password: String): String? {
        if (name.isBlank()) return "Name is blank"
        if (email.isBlank()) return "Email is blank"
        if (password.isBlank()) return "Password is blank"
        if (users.containsKey(email)) return "Email is already registered"
        users[email] = Account(name, email, password, false)
        return null
    }

    override fun attempt(email: String, password: String): Boolean {
        val account = users[email]
        if (account == null) return false
        if (account.password != password) return false
        current = email
        return true
    }

    override fun check(): Boolean = current != null

    override fun id(): String? = current

    override fun name(): String? = current?.let { users.getValue(it).name }

    override fun email(): String? = current?.let { users.getValue(it).email }

    override fun logout() {
        current = null
    }

    override fun requestReset(email: String): String? {
        if (email.isBlank()) return "Email is blank"
        if (!users.containsKey(email)) return "Email is not registered"
        return null
    }

    override fun verify(email: String): String? {
        if (email.isBlank()) return "Email is blank"
        val account = users[email]
        if (account == null) return "Email is not registered"
        account.verified = true
        return null
    }

    fun verified(email: String): Boolean {
        val account = users[email]
        if (account == null) return false
        return account.verified
    }

    /**
     * Update the signed-in account. Optional [newPassword] replaces the password when non-null.
     * Changing email re-keys the account map and keeps the session on the new email.
     */
    fun updateProfile(name: String, email: String, newPassword: String?): String? {
        val id = current
        if (id == null) return "Not signed in"
        val account = users[id]
        if (account == null) {
            current = null
            return "Not signed in"
        }
        if (name.isBlank()) return "Name is blank"
        if (email.isBlank()) return "Email is blank"
        if (email != id && users.containsKey(email)) return "Email is already registered"
        val password =
            if (newPassword != null && newPassword.isNotBlank()) {
                newPassword
            } else {
                account.password
            }
        users.remove(id)
        users[email] = Account(name, email, password, account.verified)
        current = email
        return null
    }
}

/**
 * Adapts an application [Guard] when a panel is configured to reuse it.
 * Prefer [Panel.reuseAppGuard] so the live Auth manager guard is resolved per request.
 */
class AuthPanelGuard(private val resolve: () -> Guard) : PanelGuard {
    constructor(guard: Guard) : this({ guard })

    override fun attempt(email: String, password: String): Boolean =
        resolve().attempt(mapOf("email" to email, "password" to password))

    override fun check(): Boolean = resolve().check()

    override fun id(): String? {
        val value = resolve().id()
        if (value == null) return null
        return value.toString()
    }

    override fun name(): String? {
        val user = resolve().user() ?: return null
        if (user is HasName) {
            val name = user.getName()
            if (name != null) return name
        }
        if (user is HasCredentials) {
            val name = user.getCredential("name")
            if (name != null) return name.toString()
        }
        return null
    }

    override fun email(): String? {
        val user = resolve().user() ?: return null
        if (user is HasEmail) {
            val email = user.getEmail()
            if (email != null) return email
        }
        if (user is HasCredentials) {
            val email = user.getCredential("email")
            if (email != null) return email.toString()
        }
        return null
    }

    override fun logout() {
        resolve().logout()
    }

    override fun register(name: String, email: String, password: String): String? =
        "Application guard does not register panel users"

    override fun requestReset(email: String): String? =
        "Application guard does not reset panel passwords"

    override fun verify(email: String): String? =
        "Application guard does not verify panel email"
}
