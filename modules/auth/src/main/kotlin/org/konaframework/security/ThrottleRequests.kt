package org.konaframework.security

import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.TrustProxies

/**
 * HTTP throttle middleware using a named [RateLimiter] definition.
 *
 * On limit exceeded: HTTP 429 with `Retry-After`.
 */
class ThrottleRequests @JvmOverloads constructor(
    private val rateLimiter: RateLimiter,
    private val limiterName: String,
    private val keyResolver: (Request) -> String = { req ->
        val trusted = req.getAttribute(TrustProxies.CLIENT_IP)
        when {
            trusted != null -> trusted.toString()
            else ->
                req.header("X-Forwarded-For")
                    ?: req.header("X-Real-IP")
                    ?: req.getAttribute("ip")?.toString()
                    ?: "global"
        }
    },
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val limit = rateLimiter.resolve(limiterName, request)
        val key = "$limiterName:${keyResolver(request)}"
        if (rateLimiter.tooManyAttempts(key, limit.maxAttempts)) {
            throw RateLimitExceededException(rateLimiter.availableIn(key))
        }
        rateLimiter.hit(key, limit.decaySeconds)
        val response = next.handle(request)
        val remaining = rateLimiter.retriesLeft(key, limit.maxAttempts)
        response.header("X-RateLimit-Limit", limit.maxAttempts.toString())
        response.header("X-RateLimit-Remaining", remaining.toString())
        return response
    }
}
