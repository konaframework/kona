package dev.kona.auth

import dev.kona.auth.session.SessionAttributes
import dev.kona.auth.session.SessionBag
import dev.kona.http.Request

/**
 * Session-backed guard. Reads/writes the user id under the request session attribute
 * (`"session"` → [SessionBag] or `MutableMap`).
 */
class SessionGuard(
    private val name: String,
    private val provider: UserProvider,
) : Guard {
    private var request: Request? = null
    private var user: Authenticatable? = null
    private var resolved: Boolean = false

    fun getName(): String = name

    fun getProvider(): UserProvider = provider

    override fun setRequest(request: Request): Guard {
        this.request = request
        this.user = null
        this.resolved = false
        return this
    }

    override fun attempt(credentials: Map<String, Any?>, remember: Boolean): Boolean {
        val candidate = provider.retrieveByCredentials(credentials) ?: return false
        if (!provider.validateCredentials(candidate, credentials)) {
            return false
        }
        login(candidate, remember)
        return true
    }

    override fun login(user: Authenticatable, remember: Boolean) {
        val req = requireRequest()
        val session = SessionAttributes.ensure(req)
        session.put(sessionKey(), user.getAuthIdentifier())
        if (remember) {
            session.put(rememberKey(), true)
        } else {
            session.forget(rememberKey())
        }
        this.user = user
        this.resolved = true
        req.setAttribute(USER_ATTRIBUTE, user)
    }

    override fun logout() {
        val req = request
        if (req != null) {
            SessionAttributes.resolve(req)?.let { session ->
                session.forget(sessionKey())
                session.forget(rememberKey())
            }
            req.setAttribute(USER_ATTRIBUTE, null)
        }
        user = null
        resolved = true
    }

    override fun check(): Boolean = user() != null

    override fun id(): Any? {
        val current = user() ?: return null
        return current.getAuthIdentifier()
    }

    override fun user(): Authenticatable? {
        if (resolved) {
            return user
        }
        resolved = true
        val req = request ?: return null
        val cached = req.getAttribute(USER_ATTRIBUTE) as? Authenticatable
        if (cached != null) {
            user = cached
            return cached
        }
        val session = SessionAttributes.resolve(req)
        if (session == null) {
            return null
        }
        val id = session.get(sessionKey())
        if (id == null) {
            return null
        }
        val found = provider.retrieveById(id)
        user = found
        if (found != null) {
            req.setAttribute(USER_ATTRIBUTE, found)
        }
        return found
    }

    private fun sessionKey(): String = "login_${name}_id"

    private fun rememberKey(): String = "login_${name}_remember"

    private fun requireRequest(): Request =
        request ?: throw IllegalStateException("SessionGuard has no request; call setRequest first")

    companion object {
        const val USER_ATTRIBUTE: String = "user"
    }
}
