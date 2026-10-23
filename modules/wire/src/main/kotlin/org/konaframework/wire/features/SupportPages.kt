package org.konaframework.wire.features

import org.konaframework.wire.WireView
import org.konaframework.wire.Component
import org.konaframework.wire.Layout
import org.konaframework.wire.Title
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router

/**
 * Full-page components: layouts, titles, layout slots, route registration,
 * and `effects.url` / `effects.title` for SPA navigate responses.
 */
class SupportPages : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        val layoutAnn = component.javaClass.getAnnotation(Layout::class.java)
        if (layoutAnn != null) component.state().store["layout"] = layoutAnn.value
        val titleAnn = component.javaClass.getAnnotation(Title::class.java)
        if (titleAnn != null) component.state().store["title"] = titleAnn.value
        if (params["lazy"] == false) component.state().store["lazyDisabled"] = true
    }

    override fun dehydrate(context: ComponentContext) {
        component.state().store["title"]?.let { context.addEffect("title", it) }
        @Suppress("UNCHECKED_CAST")
        val url = component.state().store["url"] as? Map<String, Any?>
        if (!url.isNullOrEmpty()) context.addEffect("url", url)
        if (component.state().store["disableBackButtonCache"] == true) {
            context.addEffect("disableBackButtonCache", true)
        }
    }

    override fun render(view: WireView, data: MutableMap<String, Any?>): ((String) -> String?)? {
        view.layoutName?.let { component.state().store["layout"] = it }
        view.titleText?.let { component.state().store["title"] = it }
        if (view.slots.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val slots = component.state().store.getOrPut("layoutSlots") { linkedMapOf<String, String>() } as MutableMap<String, String>
            slots.putAll(view.slots)
        }
        return null
    }
}

/** Register a full-page Wire component route (`Route::livewire` / `router.brew`). */
fun wireRoute(router: Router, uri: String, name: String, manager: org.konaframework.wire.WireManager): org.konaframework.http.Route {
    return router.get(uri) { request -> brewRenderPage(manager, name, request) }
}

/** Render [name] as a full HTML page with layout + title. */
fun brewRenderPage(manager: org.konaframework.wire.WireManager, name: String, request: Request): Response {
    val routeParams = request.attributes
        .filterKeys { it != "session" && !it.startsWith("_") }
        .mapValues { it.value }
    val params = routeParams + request.query.mapValues { it.value as Any? }
    return manager.withRequest(request) {
        val result = manager.mountComponent(name, params, null)
        val html = brewWrapInLayout(manager, result.component, result.html!!)
        Response.html(html)
    }
}

/** Apply layout / title around component HTML. */
fun brewWrapInLayout(manager: org.konaframework.wire.WireManager, component: Component, body: String): String {
    val layout = (component.state().store["layout"] as? String) ?: manager.config.layout
    val title = component.state().store["title"] as? String
    @Suppress("UNCHECKED_CAST")
    val slots = (component.state().store["layoutSlots"] as? Map<String, String>).orEmpty()
    val data = linkedMapOf<String, Any?>(
        "slot" to body,
        "title" to (title ?: component.name()),
    )
    data.putAll(slots)
    return try {
        manager.views().render(layout, data)
    } catch (_: Exception) {
        """<!DOCTYPE html><html><head><title>${title ?: component.name()}</title></head><body>$body</body></html>"""
    }
}
