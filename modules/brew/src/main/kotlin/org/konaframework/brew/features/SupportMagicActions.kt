package org.konaframework.brew.features

import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.lifecycle.ReturnEarly
import org.konaframework.brew.meta.ComponentMeta

/** `$refresh`, `$commit`, `$set(path, value)`, `$toggle(path)` (`SupportMagicActions`). */
class SupportMagicActions : ComponentHook() {
    override fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? {
        when (method) {
            "\$refresh", "\$commit" -> returnEarly()
            "\$set" -> {
                manager.updateProperty(component, params[0].toString(), params.getOrNull(1), context)()
                returnEarly()
            }
            "\$toggle" -> {
                val path = params[0].toString()
                val segments = path.split('.')
                val root = ComponentMeta.of(component).properties[segments.first()]?.get(component)
                val current = if (segments.size == 1) root else manager.synths.getPath(root, segments.drop(1))
                manager.updateProperty(component, path, current != true, context)()
                returnEarly()
            }
            "\$dispatch" -> {
                val name = params[0].toString()
                @Suppress("UNCHECKED_CAST")
                val payload = when (val raw = params.getOrNull(1)) {
                    is Map<*, *> -> raw.entries.associate { (k, v) -> k.toString() to v }
                    null -> emptyMap()
                    else -> mapOf("value" to raw)
                }
                component.dispatch(name, payload)
                returnEarly()
            }
            "\$parent" -> {
                val parent = component.parent() ?: run {
                    returnEarly()
                    return null
                }
                val rawMethod = params.getOrNull(0)
                if (rawMethod == null) {
                    returnEarly()
                    return null
                }
                val parentMethod = rawMethod.toString()
                val parentParams = params.drop(1)
                if (parentMethod.startsWith("\$")) {
                    val early = ReturnEarly()
                    val finish = parent.state().hooks.call(parentMethod, parentParams, early, metadata, context)
                    returnEarly(finish(early.value))
                    return null
                }
                val candidates = ComponentMeta.of(parent).actions[parentMethod]
                    ?: throw org.konaframework.brew.MethodNotFoundException(parentMethod, parent.name())
                val target = candidates.firstOrNull { it.parameterCount == parentParams.size }
                    ?: candidates.maxBy { it.parameterCount }
                returnEarly(manager.wrapped(parent) { manager.invoker.call(parent, target, parentParams) })
                return null
            }
        }
        return null
    }
}
