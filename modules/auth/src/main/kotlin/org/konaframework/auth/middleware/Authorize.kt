package org.konaframework.auth.middleware

import org.konaframework.auth.authorization.Gate
import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * Gate-backed authorization middleware (Laravel `can:` / `Authorize`).
 *
 * Throws [org.konaframework.auth.authorization.AuthorizationException] when denied.
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
