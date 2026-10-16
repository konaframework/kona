package io.konaframework.security

import io.konaframework.http.Handler
import io.konaframework.http.Middleware
import io.konaframework.http.Request
import io.konaframework.http.Response

/**
 * Configurable security response headers middleware.
 */
data class SecurityHeadersConfig @JvmOverloads constructor(
    val xContentTypeOptions: String? = "nosniff",
    val xFrameOptions: String? = "SAMEORIGIN",
    val referrerPolicy: String? = "no-referrer-when-downgrade",
    val xXssProtection: String? = "0",
    val permissionsPolicy: String? = null,
    val strictTransportSecurity: String? = null,
    val contentSecurityPolicy: String? = null,
    val custom: Map<String, String> = emptyMap(),
)

class SecurityHeadersMiddleware @JvmOverloads constructor(
    private val config: SecurityHeadersConfig = SecurityHeadersConfig(),
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val response = next.handle(request)
        apply(response)
        return response
    }

    fun apply(response: Response) {
        setIfPresent(response, "X-Content-Type-Options", config.xContentTypeOptions)
        setIfPresent(response, "X-Frame-Options", config.xFrameOptions)
        setIfPresent(response, "Referrer-Policy", config.referrerPolicy)
        setIfPresent(response, "X-XSS-Protection", config.xXssProtection)
        setIfPresent(response, "Permissions-Policy", config.permissionsPolicy)
        setIfPresent(response, "Strict-Transport-Security", config.strictTransportSecurity)
        setIfPresent(response, "Content-Security-Policy", config.contentSecurityPolicy)
        config.custom.forEach { (name, value) -> response.header(name, value) }
    }

    private fun setIfPresent(response: Response, name: String, value: String?) {
        if (value != null) {
            response.header(name, value)
        }
    }
}
