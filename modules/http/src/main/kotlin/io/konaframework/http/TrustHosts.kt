package io.konaframework.http

/**
 * Rejects requests whose `Host` header is not in [at] (exact or `*.example.com` suffix match).
 */
class TrustHosts(
    private val at: List<String>,
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val hostHeader = request.header("Host")
        val host = if (hostHeader == null) {
            ""
        } else {
            hostHeader.substringBefore(':').trim()
        }
        if (host.isEmpty() || !allowed(host)) {
            return Response.text("Invalid Host header.", 400)
        }
        return next.handle(request)
    }

    private fun allowed(host: String): Boolean =
        at.any { pattern -> hostMatches(host, pattern.trim()) }

    companion object {
        internal fun hostMatches(host: String, pattern: String): Boolean {
            if (pattern == "*") {
                return true
            }
            if (pattern.startsWith("*.")) {
                val suffix = pattern.removePrefix("*")
                return host.endsWith(suffix) && host != suffix.removePrefix(".")
            }
            return host.equals(pattern, ignoreCase = true)
        }
    }
}
