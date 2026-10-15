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

    fun validate(credentials: Map<String, Any?>): Boolean = guard().validate(credentials)

    @JvmOverloads
    fun loginUsingId(id: Any, remember: Boolean = false): Authenticatable? =
        guard().loginUsingId(id, remember)

    fun once(credentials: Map<String, Any?>): Boolean = guard().once(credentials)

    fun onceUsingId(id: Any): Boolean = guard().onceUsingId(id)

    fun viaRemember(): Boolean = guard().viaRemember()

    /** True when any of [names] (or the default guard when empty) is authenticated. */
    fun checkAny(vararg names: String): Boolean {
        if (names.isEmpty()) {
            return check()
        }
        return names.any { name ->
            try {
                guard(name).check()
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }

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
