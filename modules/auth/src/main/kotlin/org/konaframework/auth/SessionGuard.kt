package org.konaframework.auth

import org.konaframework.auth.session.SessionAttributes
import org.konaframework.auth.session.SessionBag
import org.konaframework.http.Cookie
import org.konaframework.http.Request
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * Session-backed guard. Reads/writes the user id under the request session attribute
 * (`"session"` → [SessionBag] or `MutableMap`).
 *
 * When [login] is called with `remember = true`, a long-lived remember cookie is queued
 * on the request ([QUEUED_COOKIES_ATTRIBUTE]) and a remember token is stored on the user.
 */
class SessionGuard(
    private val name: String,
    private val provider: UserProvider,
) : Guard {
    private var request: Request? = null
    private var user: Authenticatable? = null
    private var resolved: Boolean = false
    private var viaRemember: Boolean = false
    private val random = SecureRandom()

    fun getName(): String = name

    fun getProvider(): UserProvider = provider

    override fun setRequest(request: Request): Guard {
        this.request = request
        this.user = null
        this.resolved = false
        this.viaRemember = false
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

    override fun validate(credentials: Map<String, Any?>): Boolean {
        val candidate = provider.retrieveByCredentials(credentials) ?: return false
        return provider.validateCredentials(candidate, credentials)
    }

    override fun loginUsingId(id: Any, remember: Boolean): Authenticatable? {
        val found = provider.retrieveById(id) ?: return null
        login(found, remember)
        return found
    }

    override fun once(credentials: Map<String, Any?>): Boolean {
        val candidate = provider.retrieveByCredentials(credentials) ?: return false
        if (!provider.validateCredentials(candidate, credentials)) {
            return false
        }
        setUser(candidate)
        return true
    }

    override fun onceUsingId(id: Any): Boolean {
        val found = provider.retrieveById(id) ?: return false
        setUser(found)
        return true
    }

    override fun viaRemember(): Boolean {
        user()
        return viaRemember
    }

    override fun login(user: Authenticatable, remember: Boolean) {
        val req = requireRequest()
        val session = SessionAttributes.ensure(req)
        session.put(sessionKey(), user.getAuthIdentifier())
        session.put(passwordHashKey(), user.getAuthPassword())
        if (remember) {
            cycleRememberToken(user)
            session.put(rememberKey(), true)
            queueRememberCookie(req, user)
        } else {
            session.forget(rememberKey())
        }
        viaRemember = false
        setUser(user)
    }

    override fun logout() {
        val req = request
        if (req != null) {
            SessionAttributes.resolve(req)?.let { session ->
                session.forget(sessionKey())
                session.forget(rememberKey())
                session.forget(passwordHashKey())
            }
            val current = user
            if (current != null) {
                provider.updateRememberToken(current, null)
            }
            queueForgetRememberCookie(req)
            req.setAttribute(USER_ATTRIBUTE, null)
        }
        user = null
        resolved = true
        viaRemember = false
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
        if (session != null) {
            val id = session.get(sessionKey())
            if (id != null) {
                val found = provider.retrieveById(id)
                user = found
                if (found != null) {
                    req.setAttribute(USER_ATTRIBUTE, found)
                }
                return found
            }
        }
        return recallViaRememberCookie(req)
    }

    /** Password hash stored at last login (for AuthenticateSession). */
    fun sessionPasswordHash(): String? {
        val req = request ?: return null
        val session = SessionAttributes.resolve(req) ?: return null
        return session.get(passwordHashKey())?.toString()
    }

    fun updateSessionPasswordHash(hash: String) {
        val req = request ?: return
        SessionAttributes.ensure(req).put(passwordHashKey(), hash)
    }

    private fun recallViaRememberCookie(req: Request): Authenticatable? {
        val raw = req.cookie(rememberCookieName()) ?: return null
        val parts = raw.split('|', limit = 2)
        if (parts.size != 2) {
            return null
        }
        val id = parts[0]
        val token = parts[1]
        if (id.isEmpty()) {
            return null
        }
        if (token.isEmpty()) {
            return null
        }
        val found = provider.retrieveByToken(id, token) ?: return null
        val session = SessionAttributes.ensure(req)
        session.put(sessionKey(), found.getAuthIdentifier())
        session.put(passwordHashKey(), found.getAuthPassword())
        session.put(rememberKey(), true)
        viaRemember = true
        user = found
        req.setAttribute(USER_ATTRIBUTE, found)
        return found
    }

    private fun setUser(user: Authenticatable) {
        this.user = user
        this.resolved = true
        request?.setAttribute(USER_ATTRIBUTE, user)
    }

    private fun cycleRememberToken(user: Authenticatable) {
        val token = generateRememberToken()
        provider.updateRememberToken(user, token)
    }

    private fun queueRememberCookie(req: Request, user: Authenticatable) {
        val token = user.getRememberToken() ?: return
        val value = "${user.getAuthIdentifier()}|$token"
        AuthCookieQueue.queue(
            req,
            Cookie(
                name = rememberCookieName(),
                value = value,
                maxAge = REMEMBER_DURATION,
                httpOnly = true,
            ),
        )
    }

    private fun queueForgetRememberCookie(req: Request) {
        AuthCookieQueue.queue(
            req,
            Cookie(
                name = rememberCookieName(),
                value = "",
                maxAge = Duration.ZERO,
                httpOnly = true,
            ),
        )
    }

    private fun generateRememberToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sessionKey(): String = "login_${name}_id"

    private fun rememberKey(): String = "login_${name}_remember"

    private fun passwordHashKey(): String = "login_${name}_password_hash"

    fun rememberCookieName(): String = "remember_$name"

    private fun requireRequest(): Request =
        request ?: throw IllegalStateException("SessionGuard has no request; call setRequest first")

    companion object {
        const val USER_ATTRIBUTE: String = "user"
        const val QUEUED_COOKIES_ATTRIBUTE: String = AuthCookieQueue.ATTRIBUTE
        val REMEMBER_DURATION: Duration = Duration.ofDays(365 * 5)
    }
}
