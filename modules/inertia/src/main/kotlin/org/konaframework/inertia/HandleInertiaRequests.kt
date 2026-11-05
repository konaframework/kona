package org.konaframework.inertia

import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * Middleware that shares props, enforces asset versioning, converts 303 redirects,
 * and tags Inertia responses.
 *
 * Alias: [InertiaMiddleware].
 *
 * Default shared props (merged before [share]):
 * - `errors` as [AlwaysProp] from the session (`errors` flash), with `X-Inertia-Error-Bag`
 * - `flash.success` / `error` / `warning` / `info` / `status` from the session
 */
open class HandleInertiaRequests : Middleware {
    /** Props merged into every Inertia page for this request (after defaults). */
    open fun share(request: Request): @JvmSuppressWildcards Map<String, Any?> = emptyMap()

    /** Asset version; `null` keeps the manager’s current version. */
    open fun version(request: Request): String? = null

    /**
     * Default shared props (errors + flash). Override [share] to add more; override this
     * to replace the defaults entirely.
     */
    open fun defaultShare(request: Request): @JvmSuppressWildcards Map<String, Any?> =
        mapOf(
            "errors" to Inertia.always(resolveValidationErrors(request)),
            "flash" to resolveFlash(request),
        )

    /** Resolve session validation errors for the Inertia `errors` prop. */
    open fun resolveValidationErrors(request: Request): Map<String, Any?> {
        val session = request.session() ?: return emptyMap()
        val raw = session.get(ERRORS_SESSION_KEY) ?: return emptyMap()
        val bag = coerceErrorBag(raw)
        val errorBag = request.header(InertiaHeaders.ERROR_BAG)
        if (!errorBag.isNullOrBlank()) {
            return mapOf(errorBag to bag)
        }
        return bag
    }

    /** Flash-style session keys exposed under `props.flash`. */
    open fun resolveFlash(request: Request): Map<String, Any?> {
        val session = request.session() ?: return emptyFlash()
        val flash = linkedMapOf<String, Any?>()
        for (key in FLASH_KEYS) {
            flash[key] = session.get(key)
        }
        return flash
    }

    override fun handle(request: Request, next: Handler): Response {
        val manager = Inertia.manager()
        manager.setRequest(request)
        try {
            val shared = linkedMapOf<String, Any?>()
            shared.putAll(defaultShare(request))
            shared.putAll(share(request))
            if (shared.isNotEmpty()) {
                manager.shareForRequest(shared)
            }
            version(request)?.let { manager.version = it }
            manager.versionConflict(request)?.let { return manager.decorate(it) }
            var response = next.handle(request)
            response = onEmptyInertiaResponse(request, response)
            response = manager.seeOtherRedirect(request, response)
            return manager.decorate(response)
        } finally {
            manager.setRequest(null)
        }
    }

    /**
     * Inertia clients that receive an empty 200 treat it as a failure — redirect back
     * (Laravel `onEmptyResponse` parity).
     */
    protected open fun onEmptyInertiaResponse(request: Request, response: Response): Response {
        if (!Inertia.manager().isInertiaRequest(request)) {
            return response
        }
        if (response.status != 200) {
            return response
        }
        if (response.body.isNotEmpty() || response.bodyBytes().isNotEmpty()) {
            return response
        }
        return Response.back(request)
    }

    companion object {
        const val ERRORS_SESSION_KEY: String = "errors"

        val FLASH_KEYS: List<String> = listOf("success", "error", "warning", "info", "status")

        internal fun coerceErrorBag(raw: Any?): Map<String, Any?> {
            if (raw !is Map<*, *>) {
                return emptyMap()
            }
            val out = linkedMapOf<String, Any?>()
            for ((key, value) in raw) {
                if (key == null) continue
                out[key.toString()] = firstErrorMessage(value)
            }
            return out
        }

        internal fun firstErrorMessage(value: Any?): String {
            val first = when (value) {
                is List<*> -> value.firstOrNull()
                is Array<*> -> value.firstOrNull()
                else -> value
            }
            if (first == null) {
                return ""
            }
            return first.toString()
        }

        private fun emptyFlash(): Map<String, Any?> =
            FLASH_KEYS.associateWith { null }
    }
}

/** Name alias for [HandleInertiaRequests]. */
typealias InertiaMiddleware = HandleInertiaRequests
