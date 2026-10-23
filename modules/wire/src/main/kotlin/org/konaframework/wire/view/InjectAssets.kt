package org.konaframework.wire.view

import org.konaframework.wire.WireManager
import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * Binds the request to Wire for the duration of the handler and, for full HTML documents that rendered a component,
 * injects `@wireStyles` before `</head>` and `@wireScripts` before `</body>` (`brew.inject_assets`).
 */
class InjectAssets(private val manager: WireManager) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val response = manager.withRequest(request) { next.handle(request) }
        if (!manager.config.injectAssets) return response
        if (request.getAttribute(WireManager.RENDERED_ATTRIBUTE) != true) return response
        if (response.headers["Content-Type"]?.startsWith("text/html") != true) return response
        val html = response.body
        val head = html.lastIndexOf("</head>", ignoreCase = true)
        val body = html.lastIndexOf("</body>", ignoreCase = true)
        if (head < 0 && body < 0) return response
        return manager.withRequest(request) {
            val styles = manager.assets.styles()
            val scripts = manager.assets.scripts()
            val out = StringBuilder(html)
            if (body >= 0) out.insert(body, scripts)
            if (head >= 0) out.insert(head, styles)
            response.withBody(out.toString())
        }
    }
}
