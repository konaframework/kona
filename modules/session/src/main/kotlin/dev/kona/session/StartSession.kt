package dev.kona.session

import dev.kona.http.Handler
import dev.kona.http.Middleware
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * Loads the session, attaches it as request attribute [ATTRIBUTE], saves and sets cookie on the way out.
 *
 * Also records the previous URL for GET responses and applies redirect flash / old input / errors
 * pending on the [Response] (`with` / `withInput` / `withErrors`).
 */
class StartSession(
    private val manager: SessionManager,
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val session = manager.start(request)
        request.setAttribute(ATTRIBUTE, session)
        val response = next.handle(request)
        applyPending(session, response)
        if (shouldRememberUrl(request, response)) {
            session.setPreviousUrl(requestUrl(request))
        }
        manager.save(session, response)
        return response
    }

    private fun applyPending(session: Session, response: Response) {
        response.sessionFlash.forEach { (key, value) -> session.flash(key, value) }
        response.sessionOldInput?.let { session.flashInput(it) }
        response.sessionErrors?.let { session.flash(StoreSession.ERRORS, it) }
    }

    private fun shouldRememberUrl(request: Request, response: Response): Boolean {
        if (!request.method.equals("GET", ignoreCase = true)) {
            return false
        }
        if (response.status !in 200..299) {
            return false
        }
        val location = response.headers["Location"]
        return location.isNullOrBlank()
    }

    private fun requestUrl(request: Request): String {
        if (request.query.isEmpty()) {
            return request.path
        }
        val query = request.query.entries.joinToString("&") { (k, v) -> "$k=$v" }
        return "${request.path}?$query"
    }

    companion object {
        const val ATTRIBUTE: String = Request.SESSION_ATTRIBUTE
    }
}
