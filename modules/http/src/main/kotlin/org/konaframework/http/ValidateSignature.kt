package org.konaframework.http

/**
 * Rejects requests whose query signature is missing, wrong, or expired (Laravel `ValidateSignature`).
 */
class ValidateSignature(
    private val urls: UrlGenerator,
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        if (!urls.hasValidSignature(request)) {
            throw HttpException(403, "Invalid signature.")
        }
        return next.handle(request)
    }
}
