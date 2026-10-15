package dev.kona.auth.middleware

import dev.kona.auth.AuthCookieQueue
import dev.kona.auth.AuthManager
import dev.kona.auth.AuthenticationException
import dev.kona.auth.session.SessionAttributes
import dev.kona.http.Handler
import dev.kona.http.Middleware
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * Requires an authenticated user (Laravel `Authenticate` middleware).
 *
 * When [redirectTo] is non-null, unauthenticated browser requests are redirected
 * (and the current URL is stored as the intended URL). When null, throws
 * [AuthenticationException] (HTTP 401) — typical for APIs.
 */
class Authenticate(
    private val auth: AuthManager,
    private val redirectTo: String?,
    private val guards: List<String>,
) : Middleware {
    constructor(auth: AuthManager) : this(auth, "/login", emptyList())

    constructor(auth: AuthManager, redirectTo: String?) : this(auth, redirectTo, emptyList())

    override fun handle(request: Request, next: Handler): Response {
        auth.setRequest(request)
        if (authenticated()) {
            return AuthCookieQueue.flush(request, next.handle(request))
        }
        SessionAttributes.ensure(request).put(INTENDED_KEY, request.path)
        val target = redirectTo
        if (target != null) {
            return AuthCookieQueue.flush(request, Response.redirect(target, 302))
        }
        throw AuthenticationException("Unauthenticated.")
    }

    private fun authenticated(): Boolean {
        if (guards.isEmpty()) {
            return auth.check()
        }
        return auth.checkAny(*guards.toTypedArray())
    }

    companion object {
        const val INTENDED_KEY: String = "url.intended"

        @JvmStatic
        fun pullIntended(request: Request): String = pullIntended(request, "/")

        @JvmStatic
        fun pullIntended(request: Request, default: String): String {
            val session = SessionAttributes.resolve(request)
            if (session == null) {
                return default
            }
            val raw = session.get(INTENDED_KEY)
            session.forget(INTENDED_KEY)
            if (raw == null) {
                return default
            }
            val value = raw.toString()
            if (value.isBlank()) {
                return default
            }
            return value
        }
    }
}
