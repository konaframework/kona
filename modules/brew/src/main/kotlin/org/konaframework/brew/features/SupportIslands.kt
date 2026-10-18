package org.konaframework.brew.features

import org.konaframework.brew.Component
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook

/** Collects named `@island` regions rendered during the request into `effects.islands`. */
class SupportIslands : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        @Suppress("UNCHECKED_CAST")
        val islands = component.state().store["islands"] as? Map<String, Any?> ?: return
        context.addEffect("islands", islands)
    }
}

fun recordIsland(component: Component, name: String, html: String, meta: Map<String, Any?> = emptyMap()) {
    @Suppress("UNCHECKED_CAST")
    val islands = component.state().store.getOrPut("islands") { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
    islands[name] = linkedMapOf("html" to html) + meta
}
