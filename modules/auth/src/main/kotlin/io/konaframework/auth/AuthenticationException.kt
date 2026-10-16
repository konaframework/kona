package io.konaframework.auth

import io.konaframework.http.HttpException

/**
 * Thrown when authentication is required (HTTP 401).
 */
class AuthenticationException @JvmOverloads constructor(
    message: String = "Unauthenticated.",
) : HttpException(401, message)
