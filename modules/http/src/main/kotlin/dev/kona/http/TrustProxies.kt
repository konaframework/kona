package dev.kona.http

/**
 * When the immediate peer is trusted, copy forwarded client metadata onto the request.
 *
 * Attributes set (when headers present):
 * - `kona.clientIp` — first hop in `X-Forwarded-For`, else `X-Real-IP`
 * - `kona.scheme` — `X-Forwarded-Proto`
 * - `kona.host` — `X-Forwarded-Host`
 * - `kona.port` — `X-Forwarded-Port`
 *
 * Peer address is read from attribute `kona.remoteAddr` (set by the server or tests).
 * When absent, proxies are not applied unless [trustAnyPeer] is true (`at` contains `*`).
 */
class TrustProxies @JvmOverloads constructor(
    private val at: List<String> = listOf("*"),
    private val headers: Set<String> = DEFAULT_HEADERS,
) : Middleware {
    private val trustAnyPeer: Boolean = at.any { it.trim() == "*" }

    override fun handle(request: Request, next: Handler): Response {
        if (shouldTrust(request)) {
            applyForwarded(request)
        }
        return next.handle(request)
    }

    private fun shouldTrust(request: Request): Boolean {
        if (trustAnyPeer) {
            return true
        }
        val peerAttr = request.getAttribute(REMOTE_ADDR) ?: return false
        val peer = peerAttr.toString()
        return at.any { pattern -> peerMatches(peer, pattern.trim()) }
    }

    private fun applyForwarded(request: Request) {
        if (HEADER_FORWARDED_FOR in headers || HEADER_REAL_IP in headers) {
            val forwarded = firstForwardedFor(request)
            val realIp = nonBlankHeader(request, "X-Real-IP")
            val clientIp = forwarded ?: realIp
            if (clientIp != null) {
                request.setAttribute(CLIENT_IP, clientIp)
            }
        }
        if (HEADER_FORWARDED_PROTO in headers) {
            nonBlankHeader(request, "X-Forwarded-Proto")?.let { request.setAttribute(SCHEME, it) }
        }
        if (HEADER_FORWARDED_HOST in headers) {
            nonBlankHeader(request, "X-Forwarded-Host")?.let { request.setAttribute(HOST, it) }
        }
        if (HEADER_FORWARDED_PORT in headers) {
            nonBlankHeader(request, "X-Forwarded-Port")?.let { request.setAttribute(PORT, it) }
        }
    }

    companion object {
        const val REMOTE_ADDR = "kona.remoteAddr"
        const val CLIENT_IP = "kona.clientIp"
        const val SCHEME = "kona.scheme"
        const val HOST = "kona.host"
        const val PORT = "kona.port"

        const val HEADER_FORWARDED_FOR = "forwarded_for"
        const val HEADER_REAL_IP = "real_ip"
        const val HEADER_FORWARDED_PROTO = "forwarded_proto"
        const val HEADER_FORWARDED_HOST = "forwarded_host"
        const val HEADER_FORWARDED_PORT = "forwarded_port"

        val DEFAULT_HEADERS: Set<String> = setOf(
            HEADER_FORWARDED_FOR,
            HEADER_REAL_IP,
            HEADER_FORWARDED_PROTO,
            HEADER_FORWARDED_HOST,
            HEADER_FORWARDED_PORT,
        )

        private fun firstForwardedFor(request: Request): String? {
            val header = request.header("X-Forwarded-For") ?: return null
            return header.split(',')
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
        }

        private fun nonBlankHeader(request: Request, name: String): String? {
            val header = request.header(name) ?: return null
            val trimmed = header.trim()
            return if (trimmed.isNotEmpty()) trimmed else null
        }

        internal fun peerMatches(peer: String, pattern: String): Boolean {
            if (pattern == "*") {
                return true
            }
            if (pattern.endsWith(".*") || pattern.endsWith(".0/0")) {
                // simple prefix: "10.0." matches 10.0.x.x
                val prefix = pattern.removeSuffix(".*").removeSuffix(".0/0")
                return peer == prefix || peer.startsWith("$prefix.")
            }
            return peer == pattern
        }
    }
}
