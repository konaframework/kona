package io.konaframework.http

/**
 * Path helpers shared by the HTTP adapter and router (slash collapse + display decoding).
 */
object HttpPaths {
    /**
     * Collapse repeated `/`, ensure a leading `/`, drop a trailing `/` (except root).
     * Does not rewrite scheme-relative `//host/...` request-targets the JDK rejects before dispatch.
     */
    @JvmStatic
    fun normalize(path: String): String {
        if (path.isEmpty() || path == "/") {
            return "/"
        }
        val collapsed = path.replace(Regex("/{2,}"), "/")
        val withLead = if (collapsed.startsWith("/")) collapsed else "/$collapsed"
        return if (withLead.length > 1) withLead.trimEnd('/') else withLead
    }

    /** Resolve a request path from JDK URI rawPath / path fields. */
    @JvmStatic
    fun fromUriParts(rawPath: String?, path: String?): String {
        val raw = rawPath?.takeIf { it.isNotEmpty() } ?: path.orEmpty().ifEmpty { "/" }
        return normalize(raw)
    }

    /** Human-facing path for error messages (normalized + percent-decoded). */
    @JvmStatic
    fun display(path: String): String {
        val normalized = normalize(path)
        return try {
            java.net.URLDecoder.decode(normalized, Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            normalized
        }
    }
}
