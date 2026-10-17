package org.konaframework.auth.middleware

import org.konaframework.auth.AuthCookieQueue
import org.konaframework.auth.AuthManager
import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * Redirects already-authenticated users away from guest-only routes
 * (Laravel `RedirectIfAuthenticated` / `guest` middleware).
 */
class RedirectIfAuthenticated(
    private val auth: AuthManager,
    private val redirectTo: String,
    private val guards: List<String>,
) : Middleware {
    constructor(auth: AuthManager) : this(auth, "/", emptyList())

    constructor(auth: AuthManager, redirectTo: String) : this(auth, redirectTo, emptyList())

    override fun handle(request: Request, next: Handler): Response {
        auth.setRequest(request)
        val authenticated =
            if (guards.isEmpty()) {
                auth.check()
            } else {
                auth.checkAny(*guards.toTypedArray())
            }
        if (authenticated) {
            return AuthCookieQueue.flush(request, Response.redirect(redirectTo, 302))
        }
        return AuthCookieQueue.flush(request, next.handle(request))
    }
}
