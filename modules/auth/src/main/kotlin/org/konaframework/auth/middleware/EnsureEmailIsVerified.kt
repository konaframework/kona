package org.konaframework.auth.middleware

import org.konaframework.auth.AuthManager
import org.konaframework.auth.MustVerifyEmail
import org.konaframework.http.Handler
import org.konaframework.http.HttpException
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * Redirects (or 403s) users that implement [MustVerifyEmail] but have not verified
 * (Laravel `EnsureEmailIsVerified` / `verified` middleware).
 */
class EnsureEmailIsVerified(
    private val auth: AuthManager,
    private val redirectTo: String = "/email/verify",
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        auth.setRequest(request)
        val user = auth.user()
        if (user is MustVerifyEmail && !user.hasVerifiedEmail()) {
            if (wantsJson(request)) {
                throw HttpException(403, "Your email address is not verified.")
            }
            return Response.redirect(redirectTo, 302)
        }
        return next.handle(request)
    }

    private fun wantsJson(request: Request): Boolean {
        val accept = request.header("Accept").orEmpty()
        return accept.contains("application/json", ignoreCase = true) ||
            request.header("X-Inertia") != null
    }
}
