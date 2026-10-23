package org.konaframework.wire.features

import org.konaframework.wire.WireView
import org.konaframework.wire.ComponentRegistry
import org.konaframework.wire.DirectlyCallingLifecycleHooksNotAllowedException
import org.konaframework.wire.hydration.Coercion
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.lifecycle.ReturnEarly
import org.konaframework.wire.lifecycle.StopPropagation
import org.konaframework.wire.meta.ComponentMeta

/**
 * `mount`, `boot`, `booted`, `hydrate*`, `dehydrate*`, `updating*`, `updated*`, `rendering`, `rendered`, `exception`,
 * plus interface-mixin variants (`boot<Interface>`, `mount<Interface>`, `initialize<Interface>`, …).
 */
class SupportLifecycleHooks : ComponentHook() {
    private val meta: ComponentMeta by lazy { ComponentMeta.of(component) }

    override fun mount(params: Map<String, Any?>, key: String?) {
        val publicParams = params.filterKeys {
            !it.startsWith("__slot_") && !it.startsWith("wire:") && !it.startsWith("@") &&
                it != "lazy" && it != "defer" && it != "bundle"
        }
        for ((name, value) in publicParams) {
            val prop = meta.properties[name] ?: continue
            prop.set(component, Coercion.toType(value, prop.type, prop.genericType))
        }
        callHook("boot")
        callMixinHook("boot")
        callMixinHook("initialize")
        callNamed("mount", publicParams)
        meta.mixins.forEach { mixin -> callNamed("mount$mixin", publicParams) }
        callHook("booted")
        callMixinHook("booted")
    }

    override fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {
        callHook("boot")
        callMixinHook("boot")
        callMixinHook("initialize")
        callHook("hydrate")
        callMixinHook("hydrate")
        for ((name, value) in component.all()) callHook("hydrate" + studly(name), value)
        callHook("booted")
        callMixinHook("booted")
    }

    override fun update(property: String, fullPath: String, value: Any?): (() -> Unit) {
        val nested = fullPath.contains('.')
        val propertyName = studly(fullPath.substringBefore('.'))
        val afterFirstDot = if (nested) fullPath.substringAfter('.') else null
        val afterLastDot = if (nested) fullPath.substringAfterLast('.') else null
        val nestedName = if (nested) studly(fullPath.replace('.', '_')) else null
        callHook("updating", fullPath, value)
        callMixinHook("updating", fullPath, value)
        callHook("updating$propertyName", value, afterFirstDot)
        nestedName?.let { callHook("updating$it", value, afterLastDot) }
        return {
            callHook("updated", fullPath, value)
            callMixinHook("updated", fullPath, value)
            callHook("updated$propertyName", value, afterFirstDot)
            nestedName?.let { callHook("updated$it", value, afterLastDot) }
        }
    }

    override fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? {
        if (meta.isLifecycle(method)) throw DirectlyCallingLifecycleHooksNotAllowedException(method, component.name())
        return null
    }

    override fun render(view: WireView, data: MutableMap<String, Any?>): ((String) -> String?) {
        callHook("rendering", view, data)
        callMixinHook("rendering", view, data)
        return { html ->
            callHook("rendered", view, html)
            callMixinHook("rendered", view, html)
            null
        }
    }

    override fun dehydrate(context: ComponentContext) {
        callHook("dehydrate")
        callMixinHook("dehydrate")
        for ((name, value) in component.all()) callHook("dehydrate" + studly(name), value)
    }

    override fun exception(error: Throwable, stopPropagation: StopPropagation) {
        callHook("exception", error, stopPropagation)
        callMixinHook("exception", error, stopPropagation)
    }

    private fun callHook(name: String, vararg args: Any?) {
        val method = meta.hook(name) ?: return
        manager.wrapped(component) { manager.invoker.call(component, method, args.take(method.parameterCount)) }
    }

    private fun callMixinHook(name: String, vararg args: Any?) {
        for (mixin in meta.mixins) callHook(name + mixin, *args)
    }

    private fun callNamed(name: String, params: Map<String, Any?>) {
        val method = meta.hook(name) ?: return
        val positional = params.filterKeys { it.toIntOrNull() != null }.toSortedMap(compareBy { it.toInt() }).values.toList()
        manager.wrapped(component) { manager.invoker.call(component, method, positional, params) }
    }

    private fun studly(value: String): String = ComponentRegistry.studly(value)
}
