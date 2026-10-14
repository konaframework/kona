package dev.kona.auth

import dev.kona.http.Request

/**
 * Auth manager resolving named guards (Laravel AuthManager).
 */
class AuthManager {
    private val guards = linkedMapOf<String, Guard>()
    private val providers = linkedMapOf<String, UserProvider>()
    private var defaultGuard: String = "web"
    private var request: Request? = null

    fun setRequest(request: Request?): AuthManager {
        this.request = request
        propagateRequest()
        return this
    }

    fun extend(name: String, guard: Guard): AuthManager {
        guards[name] = guard
        val current = request
        if (current != null) {
            guard.setRequest(current)
        }
        return this
    }

    fun provider(name: String, provider: UserProvider): AuthManager {
        providers[name] = provider
        return this
    }

    fun getProvider(name: String): UserProvider =
        providers[name] ?: throw IllegalArgumentException("Auth user provider [$name] is not defined")

    fun setDefaultDriver(name: String): AuthManager {
        defaultGuard = name
        return this
    }

    fun getDefaultDriver(): String = defaultGuard

    fun guard(): Guard = guard(defaultGuard)

    fun guard(name: String): Guard {
        val resolved = guards[name] ?: throw IllegalArgumentException("Auth guard [$name] is not defined")
        val current = request
        if (current != null) {
            resolved.setRequest(current)
        }
        return resolved
    }

    @JvmOverloads
    fun attempt(credentials: Map<String, Any?>, remember: Boolean = false): Boolean =
        guard().attempt(credentials, remember)

    @JvmOverloads
    fun login(user: Authenticatable, remember: Boolean = false) {
        guard().login(user, remember)
    }

    fun logout() {
        guard().logout()
    }

    fun check(): Boolean = guard().check()

    fun guest(): Boolean = guard().guest()

    fun id(): Any? = guard().id()

    fun user(): Authenticatable? = guard().user()

    private fun propagateRequest() {
        val current = request
        if (current == null) {
            return
        }
        for (guard in guards.values) {
            guard.setRequest(current)
        }
    }
}
