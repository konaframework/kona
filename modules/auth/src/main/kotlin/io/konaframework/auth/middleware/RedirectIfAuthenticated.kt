package io.konaframework.auth.middleware

import io.konaframework.auth.AuthCookieQueue
import io.konaframework.auth.AuthManager
import io.konaframework.http.Handler
import io.konaframework.http.Middleware
import io.konaframework.http.Request
import io.konaframework.http.Response

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
