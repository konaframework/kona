package org.konaframework.brew.features

import org.konaframework.brew.Component
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import java.util.concurrent.ConcurrentHashMap

private val seenGlobalStyles = ConcurrentHashMap.newKeySet<String>()

/** Attach scoped CSS for the current render (tests / multi-file loaders). */
fun setBrewScopedStyles(component: Component, css: String) {
    component.state().store["brew.styles.scoped"] = css
}

fun setBrewGlobalStyles(component: Component, css: String) {
    component.state().store["brew.styles.global"] = css
}

fun setBrewStyleModules(component: Component, modules: Map<String, String>) {
    component.state().store["brew.styles.modules"] = modules
}

fun resetBrewStyleSeen() {
    seenGlobalStyles.clear()
}

/**
 * Scoped / global component styles and CSS modules (`SupportCssModules` / SFC `<style>`).
 * Styles are collected into effects.styles for the frontend to inject once.
 */
class SupportStyles : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        val scoped = component.state().store["brew.styles.scoped"] as? String
        val global = component.state().store["brew.styles.global"] as? String
        val modules = component.state().store["brew.styles.modules"] as? Map<*, *>
        val payload = linkedMapOf<String, Any?>()
        if (!scoped.isNullOrBlank()) payload["scoped"] = scoped
        if (!global.isNullOrBlank()) {
            val key = component.name() + ":global"
            if (seenGlobalStyles.add(key)) payload["global"] = global
        }
        if (!modules.isNullOrEmpty()) payload["modules"] = modules
        if (payload.isNotEmpty()) context.pushEffect("styles", payload)
    }

    companion object {
        @JvmStatic
        fun setScoped(component: Component, css: String) = setBrewScopedStyles(component, css)

        @JvmStatic
        fun setGlobal(component: Component, css: String) = setBrewGlobalStyles(component, css)

        @JvmStatic
        fun setModules(component: Component, modules: Map<String, String>) = setBrewStyleModules(component, modules)

        @JvmStatic
        fun resetSeen() = resetBrewStyleSeen()
    }
}
