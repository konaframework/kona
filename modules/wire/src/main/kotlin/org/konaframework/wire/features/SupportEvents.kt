package org.konaframework.wire.features

import org.konaframework.wire.Component
import org.konaframework.wire.Event
import org.konaframework.wire.EventHandlerDoesNotExistException
import org.konaframework.wire.On
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.lifecycle.ReturnEarly
import org.konaframework.wire.meta.ComponentMeta

/**
 * `@On` listeners, `listeners()`, `$this->dispatch()`, and the `__dispatch` magic action.
 * Dehydrates `effects.listeners` (on mount) and `effects.dispatches`.
 */
class SupportEvents : ComponentHook() {
    override fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? {
        if (method != "__dispatch") return null
        val name = params.getOrNull(0) as? String ?: throw EventHandlerDoesNotExistException("(missing)")
        @Suppress("UNCHECKED_CAST")
        val eventParams = when (val raw = params.getOrNull(1)) {
            is Map<*, *> -> raw.entries.associate { (k, v) -> k.toString() to v }
            is List<*> -> raw.mapIndexed { i, v -> i.toString() to v }.toMap()
            null -> emptyMap()
            else -> mapOf("0" to raw)
        }
        val listeners = resolvedListeners(component)
        val handler = listeners[name] ?: throw EventHandlerDoesNotExistException(name)
        if (handler.startsWith('$')) {
            // Magic actions (`$refresh`) are handled by SupportMagicActions on a follow-up call...
            returnEarly()
            manager.wrapped(component) {
                val early = ReturnEarly()
                val finish = component.state().hooks.call(handler, emptyList(), early, metadata, context)
                finish(early.value)
            }
            return { it }
        }
        val candidates = ComponentMeta.of(component).actions[handler]
            ?: throw EventHandlerDoesNotExistException(name)
        val args = eventParams.values.toList()
        val target = candidates.firstOrNull { it.parameterCount == args.size }
            ?: candidates.maxBy { it.parameterCount }
        returnEarly(
            manager.wrapped(component) {
                manager.invoker.call(component, target, args)
            },
        )
        return { it }
    }

    override fun dehydrate(context: ComponentContext) {
        if (context.mounting) {
            val names = resolvedListeners(component).keys.toList()
            if (names.isNotEmpty()) context.addEffect("listeners", names)
        }
        @Suppress("UNCHECKED_CAST")
        val dispatched = component.state().store["dispatched"] as? List<Event>
        if (!dispatched.isNullOrEmpty()) {
            context.addEffect("dispatches", dispatched.map { it.serialize() })
        }
    }

    companion object {
        fun resolvedListeners(component: Component): Map<String, String> {
            val fromMethods = linkedMapOf<String, String>()
            for ((methodName, methods) in ComponentMeta.of(component).actions) {
                for (m in methods) {
                    for (ann in m.getAnnotationsByType(On::class.java)) {
                        for (event in ann.value) {
                            if (event.isNotBlank()) fromMethods[event] = methodName
                        }
                    }
                }
            }
            val merged = fromMethods + component.listeners()
            return replaceDynamicPlaceholders(merged, component)
        }

        fun replaceDynamicPlaceholders(listeners: Map<String, String>, component: Component): Map<String, String> {
            val out = linkedMapOf<String, String>()
            for ((event, method) in listeners) {
                out[replacePlaceholders(event, component)] = method
            }
            return out
        }

        fun replacePlaceholders(event: String, component: Component): String {
            if (!event.contains('{')) return event
            val meta = ComponentMeta.of(component)
            return Regex("""\{([^}]+)\}""").replace(event) { match ->
                val path = match.groupValues[1]
                val root = path.substringBefore('.')
                val prop = meta.properties[root]?.get(component)
                if (prop == null) return@replace match.value
                if (!path.contains('.')) return@replace prop.toString()
                if (prop !is Map<*, *>) return@replace prop.toString()
                val nested = prop[path.substringAfter('.')]
                if (nested == null) match.value else nested.toString()
            }
        }
    }
}
