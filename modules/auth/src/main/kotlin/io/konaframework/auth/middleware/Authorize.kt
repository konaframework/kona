package io.konaframework.auth.middleware

import io.konaframework.auth.authorization.Gate
import io.konaframework.http.Handler
import io.konaframework.http.Middleware
import io.konaframework.http.Request
import io.konaframework.http.Response

/**
 * Gate-backed authorization middleware (Laravel `can:` / `Authorize`).
 *
 * Throws [io.konaframework.auth.authorization.AuthorizationException] when denied.
 */
class Authorize(
    private val gate: Gate,
    private val ability: String,
    private val arguments: Array<out Any?>,
    private val argumentResolver: ((Request) -> Array<out Any?>)?,
) : Middleware {
    constructor(gate: Gate, ability: String) : this(gate, ability, emptyArray(), null)

    constructor(gate: Gate, ability: String, arguments: Array<out Any?>) :
        this(gate, ability, arguments, null)

    constructor(
        gate: Gate,
        ability: String,
        argumentResolver: (Request) -> Array<out Any?>,
    ) : this(gate, ability, emptyArray(), argumentResolver)

    override fun handle(request: Request, next: Handler): Response {
        val resolver = argumentResolver
        val args = if (resolver != null) {
            resolver.invoke(request)
        } else {
            arguments
        }
        gate.authorize(ability, *args)
        return next.handle(request)
    }
}
