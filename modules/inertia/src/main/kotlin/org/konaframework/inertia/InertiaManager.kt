package org.konaframework.inertia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.konaframework.crema.CremaEngine
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.crema.assets.Vite
import org.konaframework.crema.assets.ViteException
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds Inertia HTML shells and JSON page responses.
 */
class InertiaManager(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    @Volatile
    var version: String = ""

    /** Crema view name for the root document (e.g. `app`). */
    @Volatile
    var rootView: String? = null

    /** Plain HTML root when [rootView] is unset. Use `@page` for the JSON payload. */
    @Volatile
    var rootHtml: String = DEFAULT_ROOT_HTML

    @Volatile
    var cremaEngine: CremaEngine? = null

    /** Vite entry points injected as `{!! vite !!}` in the Crema root view. */
    @Volatile
    var viteEntries: List<String> = listOf("src/main/resources/js/app.js")

    private val sharedProps = ConcurrentHashMap<String, Any?>()
    private val requestShared = ThreadLocal.withInitial { linkedMapOf<String, Any?>() }
    private val currentRequest = ThreadLocal<Request?>()

    fun setRequest(request: Request?) {
        currentRequest.set(request)
        if (request == null) {
            requestShared.remove()
        }
    }

    fun request(): Request =
        currentRequest.get()
            ?: throw InertiaException("No current request; use Inertia.render(request, …) or HandleInertiaRequests")

    fun share(key: String, value: Any?) {
        sharedProps[key] = value
    }

    fun share(props: Map<String, Any?>) {
        sharedProps.putAll(props)
    }

    fun shareForRequest(props: Map<String, Any?>) {
        requestShared.get().putAll(props)
    }

    fun shared(): Map<String, Any?> = sharedProps.toMap()

    fun clearShared() {
        sharedProps.clear()
    }

    fun isInertiaRequest(request: Request): Boolean =
        request.header("X-Inertia") != null

    /**
     * Returns a 409 response when an Inertia client sends a mismatched asset version.
     */
    fun versionConflict(request: Request): Response? {
        if (!isInertiaRequest(request)) {
            return null
        }
        val client = request.header("X-Inertia-Version") ?: return null
        if (version.isEmpty() || client == version) {
            return null
        }
        return Response(status = 409, body = "")
            .header("X-Inertia", "true")
    }

    fun render(component: String): Response = render(component, emptyMap())

    fun render(component: String, props: Map<String, Any?>): Response =
        render(request(), component, props)

    fun render(request: Request, component: String): Response =
        render(request, component, emptyMap())

    fun render(request: Request, component: String, props: Map<String, Any?>): Response {
        val page = InertiaPage(
            component = component,
            props = mergeProps(props),
            url = request.path,
            version = version.ifEmpty { null },
        )
        return if (isInertiaRequest(request)) {
            jsonPage(page)
        } else {
            htmlPage(page)
        }
    }

    /** External redirect: `X-Inertia-Location` + 409 for Inertia clients, else 302. */
    fun location(url: String): Response {
        val request = currentRequest.get()
        if (request != null && isInertiaRequest(request)) {
            return Response(status = 409, body = "")
                .header("X-Inertia-Location", url)
        }
        return Response(status = 302, body = "")
            .header("Location", url)
    }

    fun decorate(response: Response): Response {
        if (response.headers["X-Inertia"] == "true" || response.headers.containsKey("X-Inertia-Location")) {
            if (version.isNotEmpty()) {
                response.header("X-Inertia-Version", version)
            }
        }
        return response
    }

    private fun mergeProps(props: Map<String, Any?>): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        merged.putAll(sharedProps)
        merged.putAll(requestShared.get())
        merged.putAll(props)
        return merged
    }

    private fun jsonPage(page: InertiaPage): Response {
        val response = Response.json(page.toMap())
            .header("X-Inertia", "true")
            .header("Vary", "X-Inertia")
        if (version.isNotEmpty()) {
            response.header("X-Inertia-Version", version)
        }
        return response
    }

    private fun htmlPage(page: InertiaPage): Response {
        val json = mapper.writeValueAsString(page.toMap())
        val escaped = HtmlAttr.escape(json)
        val viteTags = viteTagsOrEmpty()
        val view = rootView
        val engine = cremaEngine
        val html = if (view != null && engine != null) {
            engine.render(
                view,
                mapOf(
                    "page" to json,
                    "pageAttr" to escaped,
                    "vite" to viteTags,
                ),
            )
        } else {
            rootHtml.replace("@page", escaped).replace("@vite", viteTags)
        }
        return Response.html(html)
    }

    private fun viteTagsOrEmpty(): String =
        try {
            if (viteEntries.isEmpty()) {
                ""
            } else {
                Vite.tags(*viteEntries.toTypedArray())
            }
        } catch (_: ViteException) {
            ""
        }

    companion object {
        val DEFAULT_ROOT_HTML: String =
            """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Kona</title>
              </head>
              <body>
                <div id="app" data-page='@page'></div>
              </body>
            </html>
            """.trimIndent()
    }
}

/** HTML-attribute escaping for `data-page` JSON. */
object HtmlAttr {
    @JvmStatic
    fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
