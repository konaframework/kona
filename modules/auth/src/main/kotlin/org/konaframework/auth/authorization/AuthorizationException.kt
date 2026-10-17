package org.konaframework.auth.authorization

import org.konaframework.http.HttpException

/**
 * Thrown when an ability is denied (HTTP 403).
 */
class AuthorizationException @JvmOverloads constructor(
    message: String = "This action is unauthorized.",
) : HttpException(403, message)
