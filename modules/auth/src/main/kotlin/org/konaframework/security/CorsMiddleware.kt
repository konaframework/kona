package org.konaframework.security

import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * CORS configuration (origins, methods, headers, credentials, preflight).
 */
data class CorsConfig @JvmOverloads constructor(
    val allowedOrigins: List<String> = listOf("*"),
    val allowedMethods: List<String> = listOf(
        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD",
    ),
    val allowedHeaders: List<String> = listOf("*"),
    val exposedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = false,
    val maxAgeSeconds: Long = 600,
)

/**
 * CORS middleware. Short-circuits OPTIONS preflight with 204.
 */
class CorsMiddleware @JvmOverloads constructor(
    private val config: CorsConfig = CorsConfig(),
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        if (request.method.equals("OPTIONS", ignoreCase = true) && isPreflight(request)) {
            val response = Response.noContent()
            applyHeaders(request, response)
            return response
        }
        val response = next.handle(request)
        applyHeaders(request, response)
        return response
    }

    fun applyHeaders(request: Request, response: Response) {
        val origin = request.header("Origin")
        val allowedOrigin = resolveOrigin(origin)
        if (allowedOrigin != null) {
            response.header("Access-Control-Allow-Origin", allowedOrigin)
            if (allowedOrigin != "*") {
                response.header("Vary", mergeVary(response.header("Vary"), "Origin"))
            }
        }
        if (config.allowCredentials) {
            response.header("Access-Control-Allow-Credentials", "true")
        }
        response.header("Access-Control-Allow-Methods", config.allowedMethods.joinToString(", "))
        val requestHeaders = request.header("Access-Control-Request-Headers")
        val allowHeaders = when {
            config.allowedHeaders.contains("*") && requestHeaders != null -> requestHeaders
            config.allowedHeaders.contains("*") -> "Content-Type, Authorization, X-Requested-With"
            else -> config.allowedHeaders.joinToString(", ")
        }
        response.header("Access-Control-Allow-Headers", allowHeaders)
        if (config.exposedHeaders.isNotEmpty()) {
            response.header("Access-Control-Expose-Headers", config.exposedHeaders.joinToString(", "))
        }
        response.header("Access-Control-Max-Age", config.maxAgeSeconds.toString())
    }

    private fun isPreflight(request: Request): Boolean =
        request.header("Origin") != null && request.header("Access-Control-Request-Method") != null

    private fun resolveOrigin(origin: String?): String? {
        if (origin == null) {
            if (!config.allowedOrigins.contains("*")) {
                return null
            }
            if (config.allowCredentials) {
                return null
            }
            return "*"
        }
        if (config.allowedOrigins.contains("*")) {
            if (config.allowCredentials) {
                return origin
            }
            return "*"
        }
        for (allowed in config.allowedOrigins) {
            if (allowed.equals(origin, ignoreCase = true)) {
                return origin
            }
        }
        return null
    }

    private fun mergeVary(existing: String?, value: String): String {
        if (existing.isNullOrBlank()) {
            return value
        }
        val parts = existing.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (parts.none { it.equals(value, ignoreCase = true) }) {
            parts.add(value)
        }
        return parts.joinToString(", ")
    }
}

private fun Response.header(name: String): String? {
    val needle = name.lowercase()
    return headers.entries.find { it.key.lowercase() == needle }?.value
}
