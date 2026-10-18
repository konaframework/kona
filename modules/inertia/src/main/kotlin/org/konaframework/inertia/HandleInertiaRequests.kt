package org.konaframework.inertia

import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * Middleware that shares props, enforces asset versioning, and tags Inertia responses.
 *
 * Alias: [InertiaMiddleware].
 */
open class HandleInertiaRequests : Middleware {
    /** Props merged into every Inertia page for this request. */
    open fun share(request: Request): @JvmSuppressWildcards Map<String, Any?> = emptyMap()

    /** Asset version; `null` keeps the manager’s current version. */
    open fun version(request: Request): String? = null

    override fun handle(request: Request, next: Handler): Response {
        val manager = Inertia.manager()
        manager.setRequest(request)
        try {
            val shared = share(request)
            if (shared.isNotEmpty()) {
                manager.shareForRequest(shared)
            }
            version(request)?.let { manager.version = it }
            manager.versionConflict(request)?.let { return manager.decorate(it) }
            return manager.decorate(next.handle(request))
        } finally {
            manager.setRequest(null)
        }
    }
}

/** Name alias for [HandleInertiaRequests]. */
typealias InertiaMiddleware = HandleInertiaRequests
