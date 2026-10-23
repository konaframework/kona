package org.konaframework.wire.features

import org.konaframework.wire.Url
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.meta.ComponentMeta

/**
 * `@Url` properties and `queryString()` — syncs property values to `effects.url`
 * so the client can update the address bar.
 */
class SupportQueryString : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        val meta = ComponentMeta.of(component)
        for ((name, prop) in meta.properties) {
            val ann = prop.annotations.filterIsInstance<Url>().firstOrNull() ?: continue
            val queryKey = ann.`as`.ifBlank { name }
            if (params.containsKey(queryKey)) prop.set(component, params[queryKey])
            else if (params.containsKey(name)) prop.set(component, params[name])
        }
        methodQueryString().keys.forEach { key ->
            if (params.containsKey(key)) {
                ComponentMeta.of(component).properties[key]?.set(component, params[key])
            }
        }
    }

    override fun dehydrate(context: ComponentContext) {
        val query = linkedMapOf<String, Any?>()
        val meta = ComponentMeta.of(component)
        for ((name, prop) in meta.properties) {
            val ann = prop.annotations.filterIsInstance<Url>().firstOrNull() ?: continue
            val queryKey = ann.`as`.ifBlank { name }
            val value = prop.get(component)
            if (!ann.keep && value.toString().isEmpty()) continue
            if (ann.except.isNotEmpty() && value.toString() == ann.except) continue
            query[queryKey] = linkedMapOf("value" to value, "history" to ann.history, "keep" to ann.keep)
        }
        methodQueryString().forEach { (key, value) -> query.putIfAbsent(key, linkedMapOf("value" to value)) }
        if (query.isNotEmpty()) {
            component.state().store["url"] = query
            context.addEffect("url", query)
        }
    }

    private fun methodQueryString(): Map<String, Any?> {
        val method = runCatching { component.javaClass.getMethod("queryString") }.getOrNull() ?: return emptyMap()
        val raw = method.invoke(component)
        if (raw !is Map<*, *>) return emptyMap()
        return raw.entries.associate { (k, v) -> k.toString() to v }
    }
}
