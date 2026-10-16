package io.konaframework.http

/**
 * Reason phrases for common HTTP statuses (including codes the JDK HttpServer leaves blank).
 */
object HttpStatus {
    private val PHRASES = mapOf(
        200 to "OK",
        201 to "Created",
        202 to "Accepted",
        204 to "No Content",
        301 to "Moved Permanently",
        302 to "Found",
        303 to "See Other",
        304 to "Not Modified",
        307 to "Temporary Redirect",
        308 to "Permanent Redirect",
        400 to "Bad Request",
        401 to "Unauthorized",
        403 to "Forbidden",
        404 to "Not Found",
        405 to "Method Not Allowed",
        408 to "Request Timeout",
        409 to "Conflict",
        410 to "Gone",
        415 to "Unsupported Media Type",
        419 to "Page Expired",
        422 to "Unprocessable Entity",
        429 to "Too Many Requests",
        500 to "Internal Server Error",
        501 to "Not Implemented",
        502 to "Bad Gateway",
        503 to "Service Unavailable",
        504 to "Gateway Timeout",
    )

    @JvmStatic
    fun reasonPhrase(status: Int): String? = PHRASES[status]
}
