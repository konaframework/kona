package org.konaframework.inertia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.assets.Vite
import org.konaframework.crema.assets.ViteException
import org.konaframework.http.Request
import org.konaframework.http.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds Inertia HTML shells and JSON page responses (Inertia 2 protocol).
 */
class InertiaManager(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    val ssr: SsrGateway = SsrGateway(mapper),
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

    /** Default `encryptHistory` page flag when not overridden per response. */
    @Volatile
    var encryptHistory: Boolean = false

    private val sharedProps = ConcurrentHashMap<String, Any?>()
    private val requestShared = ThreadLocal.withInitial { linkedMapOf<String, Any?>() }
    private val currentRequest = ThreadLocal<Request?>()
    private val encryptHistoryOverride = ThreadLocal<Boolean?>()
    private val clearHistoryFlag = ThreadLocal.withInitial { false }

    fun setRequest(request: Request?) {
        currentRequest.set(request)
        if (request == null) {
            requestShared.remove()
            encryptHistoryOverride.remove()
            clearHistoryFlag.remove()
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

    fun encryptHistory() {
        encryptHistory(true)
    }

    fun encryptHistory(value: Boolean) {
        encryptHistoryOverride.set(value)
    }

    fun clearHistory() {
        clearHistoryFlag.set(true)
        val request = currentRequest.get() ?: return
        val session = request.session() ?: return
        session.flash(CLEAR_HISTORY_SESSION_KEY, true)
    }

    fun isInertiaRequest(request: Request): Boolean =
        request.header(InertiaHeaders.INERTIA) != null

    /**
     * Returns a 409 + [InertiaHeaders.LOCATION] when an Inertia client sends a mismatched asset version.
     */
    fun versionConflict(request: Request): Response? {
        if (!isInertiaRequest(request)) {
            return null
        }
        val client = request.header(InertiaHeaders.VERSION) ?: return null
        if (version.isEmpty() || client == version) {
            return null
        }
        return Response(status = 409, body = "")
            .header(InertiaHeaders.LOCATION, requestUrl(request))
    }

    fun render(component: String): Response = render(component, emptyMap())

    fun render(component: String, props: Map<String, Any?>): Response =
        render(request(), component, props)

    fun render(request: Request, component: String): Response =
        render(request, component, emptyMap())

    fun render(request: Request, component: String, props: Map<String, Any?>): Response {
        val rawProps = mergeRawProps(props)
        val partial = isPartial(request, component)
        val page = buildPage(request, component, rawProps, partial)
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
                .header(InertiaHeaders.LOCATION, url)
        }
        return Response(status = 302, body = "")
            .header("Location", url)
    }

    fun decorate(response: Response): Response {
        if (response.headers[InertiaHeaders.INERTIA] == "true" ||
            response.headers.containsKey(InertiaHeaders.LOCATION)
        ) {
            if (version.isNotEmpty()) {
                response.header(InertiaHeaders.VERSION, version)
            }
        }
        return response
    }

    /**
     * Convert 302 → 303 after PUT/PATCH/DELETE for Inertia clients (browser follows with GET).
     */
    fun seeOtherRedirect(request: Request, response: Response): Response {
        if (!isInertiaRequest(request)) {
            return response
        }
        if (response.status != 302) {
            return response
        }
        if (request.method.uppercase() !in SEE_OTHER_METHODS) {
            return response
        }
        response.status = 303
        return response
    }

    internal fun buildPage(
        request: Request,
        component: String,
        rawProps: Map<String, Any?>,
        partial: Boolean,
    ): InertiaPage {
        val filtered = filterProps(rawProps, request, partial)
        val resolved = resolveProps(filtered)
        val withErrors = ensureErrorsProp(resolved)
        return InertiaPage(
            component = component,
            props = withErrors,
            url = requestUrl(request),
            version = version.ifEmpty { null },
            encryptHistory = resolveEncryptHistory(),
            clearHistory = resolveClearHistory(request),
            deferredProps = if (partial) null else resolveDeferredProps(rawProps),
            mergeProps = resolveAppendMergeProps(rawProps, request, partial),
            prependProps = resolvePrependMergeProps(rawProps, request, partial),
            deepMergeProps = resolveDeepMergeProps(rawProps, request, partial),
            matchPropsOn = resolveMatchPropsOn(rawProps, request, partial),
        )
    }

    private fun mergeRawProps(props: Map<String, Any?>): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        merged.putAll(sharedProps)
        merged.putAll(requestShared.get())
        merged.putAll(props)
        return merged
    }

    private fun filterProps(
        props: Map<String, Any?>,
        request: Request,
        partial: Boolean,
    ): Map<String, Any?> {
        if (!partial) {
            return props.filterValues { value ->
                when {
                    value is IgnoreFirstLoad -> false
                    value is Deferrable && value.shouldDefer() -> false
                    else -> true
                }
            }
        }
        var working: Map<String, Any?> = props
        val only = parseHeaderList(request.header(InertiaHeaders.PARTIAL_ONLY))
        val except = parseHeaderList(request.header(InertiaHeaders.PARTIAL_EXCEPT))
        if (only != null) {
            val narrowed = linkedMapOf<String, Any?>()
            for (key in only) {
                if (props.containsKey(key)) {
                    narrowed[key] = props[key]
                }
            }
            working = narrowed
        }
        if (except != null) {
            val skip = except.toSet()
            working = working.filterKeys { it !in skip }
        }
        val always = props.filterValues { it is AlwaysProp }
        val result = linkedMapOf<String, Any?>()
        result.putAll(working)
        result.putAll(always)
        return result
    }

    private fun resolveProps(props: Map<String, Any?>): Map<String, Any?> {
        val resolved = linkedMapOf<String, Any?>()
        for ((key, value) in props) {
            resolved[key] = resolvePropValue(value)
        }
        return resolved
    }

    private fun ensureErrorsProp(props: Map<String, Any?>): Map<String, Any?> {
        if (props.containsKey("errors")) {
            return props
        }
        val withErrors = linkedMapOf<String, Any?>()
        withErrors["errors"] = emptyMap<String, Any?>()
        withErrors.putAll(props)
        return withErrors
    }

    private fun resolveDeferredProps(rawProps: Map<String, Any?>): Map<String, List<String>>? {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for ((key, value) in rawProps) {
            if (value is Deferrable && value.shouldDefer()) {
                grouped.getOrPut(value.group()) { mutableListOf() }.add(key)
            }
        }
        if (grouped.isEmpty()) {
            return null
        }
        return grouped.mapValues { (_, v) -> v.toList() }
    }

    private fun mergeableProps(
        rawProps: Map<String, Any?>,
        request: Request,
        partial: Boolean,
    ): Map<String, Mergeable> {
        val reset = parseHeaderList(request.header(InertiaHeaders.RESET))?.toSet().orEmpty()
        var candidates = rawProps.filter { (_, v) -> v is Mergeable && (v as Mergeable).shouldMerge() }
            .mapValues { it.value as Mergeable }
            .filterKeys { it !in reset }
        if (partial) {
            val only = parseHeaderList(request.header(InertiaHeaders.PARTIAL_ONLY))
            val except = parseHeaderList(request.header(InertiaHeaders.PARTIAL_EXCEPT))?.toSet()
            if (only != null) {
                val onlySet = only.toSet()
                candidates = candidates.filterKeys { it in onlySet }
            }
            if (except != null) {
                candidates = candidates.filterKeys { it !in except }
            }
        }
        return candidates
    }

    private fun resolveAppendMergeProps(
        rawProps: Map<String, Any?>,
        request: Request,
        partial: Boolean,
    ): List<String>? {
        val keys = mergeableProps(rawProps, request, partial)
            .filter { (_, prop) -> prop.appendsAtRoot() }
            .keys
            .toList()
        return keys.ifEmpty { null }
    }

    private fun resolvePrependMergeProps(
        rawProps: Map<String, Any?>,
        request: Request,
        partial: Boolean,
    ): List<String>? {
        val keys = mergeableProps(rawProps, request, partial)
            .filter { (_, prop) -> prop.prependsAtRoot() }
            .keys
            .toList()
        return keys.ifEmpty { null }
    }

    private fun resolveDeepMergeProps(
        rawProps: Map<String, Any?>,
        request: Request,
        partial: Boolean,
    ): List<String>? {
        val keys = mergeableProps(rawProps, request, partial)
            .filter { (_, prop) -> prop.shouldDeepMerge() }
            .keys
            .toList()
        return keys.ifEmpty { null }
    }

    private fun resolveMatchPropsOn(
        rawProps: Map<String, Any?>,
        request: Request,
        partial: Boolean,
    ): List<String>? {
        val keys = mergeableProps(rawProps, request, partial)
            .flatMap { (key, prop) -> prop.matchesOn().map { path -> "$key.$path" } }
        return keys.ifEmpty { null }
    }

    private fun resolveEncryptHistory(): Boolean =
        encryptHistoryOverride.get() ?: encryptHistory

    private fun resolveClearHistory(request: Request): Boolean {
        if (clearHistoryFlag.get()) {
            return true
        }
        val session = request.session() ?: return false
        val flashed = session.get(CLEAR_HISTORY_SESSION_KEY)
        return when (flashed) {
            true, "true", 1, "1" -> true
            else -> false
        }
    }

    fun isPartial(request: Request, component: String): Boolean =
        isInertiaRequest(request) &&
            request.header(InertiaHeaders.PARTIAL_COMPONENT) == component

    private fun jsonPage(page: InertiaPage): Response {
        val response = Response.json(page.toMap())
            .header(InertiaHeaders.INERTIA, "true")
            .header("Vary", InertiaHeaders.INERTIA)
        if (version.isNotEmpty()) {
            response.header(InertiaHeaders.VERSION, version)
        }
        return response
    }

    private fun htmlPage(page: InertiaPage): Response {
        val pageMap = page.toMap()
        val json = mapper.writeValueAsString(pageMap)
        val escaped = HtmlAttr.escape(json)
        val ssrResult = ssr.dispatch(pageMap)
        val ssrHead = ssrResult?.head.orEmpty()
        val ssrBody = ssrResult?.body.orEmpty()
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
                    "ssrHead" to ssrHead,
                    "ssrBody" to ssrBody,
                ),
            )
        } else {
            rootHtml
                .replace("@ssrHead", ssrHead)
                .replace("@ssrBody", ssrBody)
                .replace("@page", escaped)
                .replace("@vite", viteTags)
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
        const val CLEAR_HISTORY_SESSION_KEY: String = "inertia.clear_history"

        private val SEE_OTHER_METHODS = setOf("PUT", "PATCH", "DELETE")

        val DEFAULT_ROOT_HTML: String =
            """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Kona</title>
                @vite
                @ssrHead
              </head>
              <body>
                <div id="app" data-page='@page'>@ssrBody</div>
              </body>
            </html>
            """.trimIndent()

        fun requestUrl(request: Request): String {
            if (request.query.isEmpty()) {
                return request.path
            }
            val query = request.query.entries.joinToString("&") { (k, v) -> "$k=$v" }
            return "${request.path}?$query"
        }

        fun parseHeaderList(header: String?): List<String>? {
            if (header.isNullOrBlank()) {
                return null
            }
            val parts = header.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return parts.ifEmpty { null }
        }
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
