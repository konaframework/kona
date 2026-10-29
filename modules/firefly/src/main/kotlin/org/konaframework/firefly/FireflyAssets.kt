package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyFonts
import org.konaframework.firefly.widgets.WidgetScripts
import org.konaframework.http.Handler
import org.konaframework.http.Response
import org.konaframework.http.Router

/**
 * Same-origin static assets for Firefly chrome and widgets (`/_firefly/…`).
 * Fonts and chart scripts ship in the jars; responses use immutable cache headers
 * so the browser keeps them after the first hit (no Google Fonts CDN).
 */
object FireflyAssets {
    private const val CACHE = "public, max-age=31536000, immutable"

    @JvmStatic
    fun register(router: Router) {
        router.get("${FireflyFonts.PREFIX}/{file}", Handler { request ->
            val file = request.route("file") ?: return@Handler Response.text("Not Found", 404)
            fontResponse(file)
        })
        router.get("/_firefly/widgets/{file}", Handler { request ->
            val file = request.route("file") ?: return@Handler Response.text("Not Found", 404)
            widgetResponse(file)
        })
        router.get("/_firefly/rich-editor/{file}", Handler { request ->
            val file = request.route("file") ?: return@Handler Response.text("Not Found", 404)
            richEditorResponse(file)
        })
    }

    internal fun richEditorResponse(file: String): Response {
        if (file !in RICH_EDITOR_FILES) return Response.text("Not Found", 404)
        val stream = FireflyAssets::class.java.getResourceAsStream("/org/konaframework/firefly/support/rich-editor/$file")
        val bytes = stream.readAllBytes()
        stream.close()
        return Response(200, "", linkedMapOf("Content-Type" to "application/javascript; charset=utf-8", "Cache-Control" to "public, max-age=3600"))
            .withBodyBytes(bytes)
    }

    private val RICH_EDITOR_FILES = setOf("simple.js", "icons.js")

    internal fun fontResponse(file: String): Response {
        if (!FireflyFonts.allowed(file)) {
            return Response.text("Not Found", 404)
        }
        val asset = FireflyFonts.asset(file) ?: return Response.text("Not Found", 404)
        return Response(200, "", linkedMapOf("Content-Type" to "font/woff2", "Cache-Control" to CACHE))
            .withBodyBytes(asset.bytes)
    }

    internal fun widgetResponse(file: String): Response {
        val path = "/_firefly/widgets/$file"
        val body = try {
            WidgetScripts.source(path)
        } catch (_: Exception) {
            return Response.text("Not Found", 404)
        }
        return Response(200, "", linkedMapOf("Content-Type" to "application/javascript; charset=utf-8", "Cache-Control" to CACHE))
            .withBodyBytes(body.toByteArray(Charsets.UTF_8))
    }
}
