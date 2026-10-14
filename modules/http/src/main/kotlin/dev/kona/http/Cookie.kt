package dev.kona.http

import java.time.Duration

/**
 * HTTP cookie helpers (request parsing + Set-Cookie attributes).
 */
data class Cookie @JvmOverloads constructor(
    val name: String,
    val value: String,
    val maxAge: Duration? = null,
    val path: String = "/",
    val domain: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = true,
    val sameSite: SameSite? = SameSite.Lax,
) {
    fun toSetCookieHeader(): String {
        val parts = mutableListOf("${encode(name)}=${encode(value)}")
        maxAge?.let { parts += "Max-Age=${it.seconds.coerceAtLeast(0)}" }
        if (path.isNotBlank()) {
            parts += "Path=$path"
        }
        domain?.takeIf { it.isNotBlank() }?.let { parts += "Domain=$it" }
        if (secure) {
            parts += "Secure"
        }
        if (httpOnly) {
            parts += "HttpOnly"
        }
        sameSite?.let { parts += "SameSite=${it.headerValue}" }
        return parts.joinToString("; ")
    }

    enum class SameSite(val headerValue: String) {
        Lax("Lax"),
        Strict("Strict"),
        None("None"),
    }

    companion object {
        @JvmStatic
        fun parseHeader(header: String?): Map<String, String> {
            if (header.isNullOrBlank()) {
                return emptyMap()
            }
            return header.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) {
                        null
                    } else {
                        decode(part.substring(0, idx).trim()) to decode(part.substring(idx + 1).trim())
                    }
                }
                .toMap()
        }

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

        private fun decode(value: String): String =
            java.net.URLDecoder.decode(value.replace("+", "%20"), Charsets.UTF_8)
    }
}
