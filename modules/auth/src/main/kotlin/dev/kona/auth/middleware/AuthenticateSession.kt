package dev.kona.auth.middleware

import dev.kona.auth.AuthCookieQueue
import dev.kona.auth.AuthManager
import dev.kona.auth.AuthenticationException
import dev.kona.auth.SessionGuard
import dev.kona.http.Handler
import dev.kona.http.Middleware
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * Invalidates the session when the user's password hash no longer matches the
 * hash captured at login (Laravel `AuthenticateSession`).
 */
class AuthenticateSession(
    private val auth: AuthManager,
    private val guardName: String?,
    private val redirectTo: String?,
) : Middleware {
    constructor(auth: AuthManager) : this(auth, null, "/login")

    constructor(auth: AuthManager, guardName: String?) : this(auth, guardName, "/login")

    override fun handle(request: Request, next: Handler): Response {
        auth.setRequest(request)
        val guard = if (guardName != null) auth.guard(guardName) else auth.guard()
        if (guard is SessionGuard) {
            val user = guard.user()
            if (user != null) {
                val stored = guard.sessionPasswordHash()
                if (stored != null && stored != user.getAuthPassword()) {
                    guard.logout()
                    val target = redirectTo
                    if (target != null) {
                        return AuthCookieQueue.flush(request, Response.redirect(target, 302))
                    }
                    throw AuthenticationException("Password changed; please log in again.")
                }
            }
        }
        return AuthCookieQueue.flush(request, next.handle(request))
    }
}
