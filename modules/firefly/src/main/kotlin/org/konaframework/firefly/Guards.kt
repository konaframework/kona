package org.konaframework.firefly

import org.konaframework.auth.Guard

/**
 * The guard a panel checks. It is separate from the application's guard unless the panel is
 * configured to reuse that guard.
 */
interface PanelGuard {
    fun attempt(email: String, password: String): Boolean

    fun check(): Boolean

    fun id(): String?

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
}

/** Adapts an application [Guard] when a panel is configured to reuse it. */
class AuthPanelGuard(private val guard: Guard) : PanelGuard {
    override fun attempt(email: String, password: String): Boolean =
        guard.attempt(mapOf("email" to email, "password" to password))

    override fun check(): Boolean = guard.check()

    override fun id(): String? {
        val value = guard.id()
        if (value == null) return null
        return value.toString()
    }

    override fun logout() {
        guard.logout()
    }

    override fun register(name: String, email: String, password: String): String? =
        "Application guard does not register panel users"

    override fun requestReset(email: String): String? =
        "Application guard does not reset panel passwords"

    override fun verify(email: String): String? =
        "Application guard does not verify panel email"
}
