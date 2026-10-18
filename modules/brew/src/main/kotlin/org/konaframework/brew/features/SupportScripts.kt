package org.konaframework.brew.features

import org.konaframework.brew.Js
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.meta.ComponentMeta

/**
 * Dehydrates `@Js` methods (returning a JS source string) into `effects.js`,
 * and forwards `@script` / `@assets` bags collected during render into effects.
 */
class SupportScripts : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        val meta = ComponentMeta.of(component)
        if (context.mounting) {
            val js = linkedMapOf<String, String>()
            for ((name, methods) in meta.actions) {
                val method = methods.firstOrNull { it.isAnnotationPresent(Js::class.java) && it.parameterCount == 0 }
                    ?: continue
                val ann = method.getAnnotation(Js::class.java)
                val key = if (ann.name.isBlank()) name else ann.name
                method.isAccessible = true
                val raw = method.invoke(component)
                val body = if (raw == null) "" else raw.toString()
                if (body.isNotEmpty()) js[key] = body
            }
            if (js.isNotEmpty()) context.addEffect("js", js)

            val scripts = component.state().store["scripts"] as? List<*> ?: emptyList<Any?>()
            if (scripts.isNotEmpty()) context.addEffect("scripts", scripts)

            val assets = component.state().store["assets"] as? List<*> ?: emptyList<Any?>()
            if (assets.isNotEmpty()) context.addEffect("assets", assets)
        }
    }
}
