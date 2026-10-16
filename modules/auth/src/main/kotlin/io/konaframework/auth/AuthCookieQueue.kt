package io.konaframework.auth

import io.konaframework.http.Cookie
import io.konaframework.http.Request
import io.konaframework.http.Response

/**
 * Queues auth-related cookies (remember-me) on the request and flushes them onto responses.
 */
object AuthCookieQueue {
    const val ATTRIBUTE: String = "auth.queued_cookies"

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun queued(request: Request): MutableList<Cookie> {
        val existing = request.getAttribute(ATTRIBUTE) as? MutableList<Cookie>
        if (existing != null) {
            return existing
        }
        val fresh = mutableListOf<Cookie>()
        request.setAttribute(ATTRIBUTE, fresh)
        return fresh
    }

    @JvmStatic
    fun queue(request: Request, cookie: Cookie) {
        val list = queued(request)
        list.removeAll { it.name == cookie.name }
        list += cookie
    }

    @JvmStatic
    fun flush(request: Request, response: Response): Response {
        val cookies = request.getAttribute(ATTRIBUTE) as? List<Cookie>
        if (cookies == null) {
            return response
        }
        for (cookie in cookies) {
            response.cookie(cookie)
        }
        request.setAttribute(ATTRIBUTE, null)
        return response
    }
}
