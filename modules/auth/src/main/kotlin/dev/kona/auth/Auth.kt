package dev.kona.auth

import dev.kona.http.Request

/**
 * Global Auth facade (`Auth::attempt` / `check` / `user` shape).
 *
 * Bind via [setManager] (done by [AuthServiceProvider]) or resolve from the container.
 */
object Auth {
    @Volatile
    private var manager: AuthManager? = null

    @JvmStatic
    fun setManager(value: AuthManager) {
        manager = value
    }

    @JvmStatic
    fun clearManager() {
        manager = null
    }

    @JvmStatic
    fun manager(): AuthManager =
        manager
            ?: throw IllegalStateException("Auth manager is not set; register AuthServiceProvider or call Auth.setManager")

    @JvmStatic
    fun setRequest(request: Request?): AuthManager = manager().setRequest(request)

    @JvmStatic
    fun guard(): Guard = manager().guard()

    @JvmStatic
    fun guard(name: String): Guard = manager().guard(name)

    @JvmStatic
    @JvmOverloads
    fun attempt(credentials: Map<String, Any?>, remember: Boolean = false): Boolean =
        manager().attempt(credentials, remember)

    @JvmStatic
    @JvmOverloads
    fun login(user: Authenticatable, remember: Boolean = false) {
        manager().login(user, remember)
    }

    @JvmStatic
    fun logout() {
        manager().logout()
    }

    @JvmStatic
    fun check(): Boolean = manager().check()

    @JvmStatic
    fun guest(): Boolean = manager().guest()

    @JvmStatic
    fun id(): Any? = manager().id()

    @JvmStatic
    fun user(): Authenticatable? = manager().user()

    @JvmStatic
    fun validate(credentials: Map<String, Any?>): Boolean = manager().validate(credentials)

    @JvmStatic
    @JvmOverloads
    fun loginUsingId(id: Any, remember: Boolean = false): Authenticatable? =
        manager().loginUsingId(id, remember)

    @JvmStatic
    fun once(credentials: Map<String, Any?>): Boolean = manager().once(credentials)

    @JvmStatic
    fun onceUsingId(id: Any): Boolean = manager().onceUsingId(id)

    @JvmStatic
    fun viaRemember(): Boolean = manager().viaRemember()
}
