package dev.kona.session

import dev.kona.http.Handler
import dev.kona.http.Middleware
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * Loads the session, attaches it as request attribute [ATTRIBUTE], saves and sets cookie on the way out.
 */
class StartSession(
    private val manager: SessionManager,
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val session = manager.start(request)
        request.setAttribute(ATTRIBUTE, session)
        val response = next.handle(request)
        manager.save(session, response)
        return response
    }

    companion object {
        const val ATTRIBUTE: String = "session"
    }
}
