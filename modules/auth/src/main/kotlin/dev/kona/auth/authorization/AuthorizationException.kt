package dev.kona.auth.authorization

import dev.kona.http.HttpException

/**
 * Thrown when an ability is denied (HTTP 403).
 */
class AuthorizationException @JvmOverloads constructor(
    message: String = "This action is unauthorized.",
) : HttpException(403, message)
