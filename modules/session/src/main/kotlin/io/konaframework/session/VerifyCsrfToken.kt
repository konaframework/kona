package io.konaframework.session

import io.konaframework.http.Handler
import io.konaframework.http.HttpException
import io.konaframework.http.Middleware
import io.konaframework.http.Request
import io.konaframework.http.Response

/**
 * Verifies CSRF tokens on state-changing requests (Laravel `VerifyCsrfToken`).
 *
 * Accepts `_token` input, `X-CSRF-TOKEN`, or `X-XSRF-TOKEN` headers.
 * Skips GET / HEAD / OPTIONS (and TRACE). Failure → HTTP 419.
 */
class VerifyCsrfToken @JvmOverloads constructor(
    private val except: List<String> = emptyList(),
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        if (inExcept(request) || isReading(request)) {
            return next.handle(request)
        }
        val session = request.getAttribute(StartSession.ATTRIBUTE) as? Session
            ?: throw TokenMismatchException()
        val expected = session.get(Csrf.TOKEN_KEY) as? String
        val provided = request.input("_token")
            ?: request.header("X-CSRF-TOKEN")
            ?: request.header("X-XSRF-TOKEN")
        if (!Csrf.tokensMatch(expected, provided)) {
            throw TokenMismatchException()
        }
        return next.handle(request)
    }

    private fun isReading(request: Request): Boolean {
        val method = request.method.uppercase()
        return method == "GET" || method == "HEAD" || method == "OPTIONS" || method == "TRACE"
    }

    private fun inExcept(request: Request): Boolean {
        if (except.isEmpty()) {
            return false
        }
        val path = request.path
        return except.any { pattern ->
            when {
                pattern.endsWith("*") -> path.startsWith(pattern.dropLast(1))
                else -> path == pattern
            }
        }
    }
}

/** HTTP 419 Page Expired (Laravel CSRF failure). */
class TokenMismatchException @JvmOverloads constructor(
    message: String = "CSRF token mismatch.",
) : HttpException(419, message)
