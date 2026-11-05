package org.konaframework.auth.middleware

import org.konaframework.auth.AuthManager
import org.konaframework.auth.session.SessionAttributes
import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response
import java.time.Clock
import java.time.Instant

/**
 * Requires a recent password confirmation (Laravel `password.confirm` /
 * `RequirePassword`). Stores confirmation time under [CONFIRMED_AT_KEY].
 */
class RequirePassword @JvmOverloads constructor(
    private val auth: AuthManager,
    private val redirectTo: String = "/user/confirm-password",
    private val timeoutSeconds: Long = 10_800,
    private val clock: Clock = Clock.systemUTC(),
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        auth.setRequest(request)
        if (auth.guest()) {
            return Response.redirect("/login", 302)
        }
        if (confirmedRecently(request)) {
            return next.handle(request)
        }
        SessionAttributes.ensure(request).put(Authenticate.INTENDED_KEY, request.path)
        return Response.redirect(redirectTo, 302)
    }

    private fun confirmedRecently(request: Request): Boolean {
        val session = SessionAttributes.resolve(request) ?: return false
        val raw = session.get(CONFIRMED_AT_KEY) ?: return false
        val confirmedAt = when (raw) {
            is Instant -> raw
            is Number -> Instant.ofEpochSecond(raw.toLong())
            else -> Instant.ofEpochSecond(raw.toString().toLongOrNull() ?: return false)
        }
        return Instant.now(clock).isBefore(confirmedAt.plusSeconds(timeoutSeconds))
    }

    companion object {
        const val CONFIRMED_AT_KEY: String = "auth.password_confirmed_at"

        /** Mark the password as confirmed for this session. */
        @JvmStatic
        @JvmOverloads
        fun confirm(request: Request, clock: Clock = Clock.systemUTC()) {
            SessionAttributes.ensure(request).put(CONFIRMED_AT_KEY, Instant.now(clock).epochSecond)
        }
    }
}
