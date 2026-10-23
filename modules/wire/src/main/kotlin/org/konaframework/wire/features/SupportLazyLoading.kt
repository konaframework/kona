package org.konaframework.wire.features

import org.konaframework.wire.Defer
import org.konaframework.wire.Lazy
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook

/**
 * `@Lazy` / `@Defer` / `lazy` / `defer` mount params — emits a placeholder and
 * `memo.lazy` / `memo.defer` so the client can finish loading.
 */
class SupportLazyLoading : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        if (manager.lazyDisabled) return
        val lazyAnn = component.javaClass.getAnnotation(Lazy::class.java)
        val deferAnn = component.javaClass.getAnnotation(Defer::class.java)
        val lazy = params.containsKey("lazy") || lazyAnn != null
        val defer = params.containsKey("defer") || deferAnn != null
        if (!lazy && !defer) return
        component.state().store["lazy"] = lazy
        component.state().store["defer"] = defer
        component.state().store["lazyBundle"] = bundleFlag(lazyAnn, deferAnn, params)
        component.skipRender(placeholderHtml())
    }

    override fun dehydrate(context: ComponentContext) {
        if (component.state().store["lazy"] == true) {
            context.addMemo("lazyLoaded", false)
            context.addEffect("lazy", true)
        }
        if (component.state().store["defer"] == true) {
            context.addMemo("defer", true)
            context.addEffect("defer", true)
        }
        if (component.state().store["lazyBundle"] == true) context.addMemo("lazyBundle", true)
    }

    private fun bundleFlag(lazyAnn: Lazy?, deferAnn: Defer?, params: Map<String, Any?>): Boolean {
        if (lazyAnn != null && lazyAnn.bundle) return true
        if (deferAnn != null && deferAnn.bundle) return true
        return params["bundle"] == true
    }

    private fun placeholderHtml(): String {
        for (candidate in listOf(component.placeholder(), manager.config.lazyPlaceholder)) {
            if (candidate != null && candidate.isNotEmpty()) return candidate
        }
        return "<div wire:lazy></div>"
    }
}
