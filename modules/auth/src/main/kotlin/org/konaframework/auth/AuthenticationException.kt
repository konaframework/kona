package org.konaframework.auth

import org.konaframework.http.HttpException

/**
 * Thrown when authentication is required (HTTP 401).
 */
class AuthenticationException @JvmOverloads constructor(
    message: String = "Unauthenticated.",
) : HttpException(401, message)
