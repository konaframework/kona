package org.konaframework.brew.features

import org.konaframework.brew.Isolate
import org.konaframework.brew.Modelable
import org.konaframework.brew.Reactive
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.meta.ComponentMeta
import org.konaframework.brew.view.HtmlRoot

/**
 * Tracks nested children in `memo.children`, reactive/modelable bindings, isolate flag,
 * and HTML attribute forwarding for non-prop mount params.
 */
class SupportNesting : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        val meta = ComponentMeta.of(component)
        val forwarded = linkedMapOf<String, Any?>()
        val reactive = linkedMapOf<String, Any?>()
        val stack = manager.currentStack()
        val parent = stack.getOrNull(stack.size - 2)
        if (parent != null) component.state().store["parent"] = parent

        val nestedListeners = linkedMapOf<String, String>()
        for ((name, value) in params) {
            when {
                name.startsWith("@") && value != null ->
                    nestedListeners[name.removePrefix("@")] = value.toString()
                name.startsWith("wire:") || name.startsWith("__slot_") ||
                    name == "lazy" || name == "defer" || name == "bundle" -> continue
                meta.properties.containsKey(name) -> {
                    val prop = meta.properties.getValue(name)
                    if (prop.annotations.any { it is Reactive }) reactive[name] = value
                }
                else -> forwarded[name] = value
            }
        }
        if (nestedListeners.isNotEmpty()) {
            component.state().store["nestedListeners"] = nestedListeners
            if (parent != null) {
                @Suppress("UNCHECKED_CAST")
                val bag = parent.state().store.getOrPut("childListeners") {
                    linkedMapOf<String, Map<String, String>>()
                } as MutableMap<String, Map<String, String>>
                bag[component.id()] = nestedListeners
            }
        }
        if (reactive.isNotEmpty()) component.state().store["reactive"] = reactive
        if (forwarded.isNotEmpty()) component.state().store["attributes"] = forwarded
        if (key != null) component.state().store["key"] = key

        val modelable = meta.properties.filterValues { p -> p.annotations.any { it is Modelable } }.keys
        if (modelable.isNotEmpty()) component.state().store["bindings"] = modelable.toList()

        if (component.javaClass.isAnnotationPresent(Isolate::class.java)) {
            component.state().store["isolate"] = true
        }
    }

    override fun dehydrate(context: ComponentContext) {
        @Suppress("UNCHECKED_CAST")
        val children = component.state().store["children"] as? Map<String, Any?>
        if (!children.isNullOrEmpty()) context.addMemo("children", children)

        @Suppress("UNCHECKED_CAST")
        val reactive = component.state().store["reactive"] as? Map<String, Any?>
        if (!reactive.isNullOrEmpty()) context.addMemo("props", reactive)

        @Suppress("UNCHECKED_CAST")
        val bindings = component.state().store["bindings"] as? List<*>
        if (!bindings.isNullOrEmpty()) context.addMemo("bindings", bindings)

        if (component.state().store["isolate"] == true) context.addMemo("isolate", true)

        component.state().store["key"]?.let { context.addMemo("key", it) }
    }

    override fun render(view: org.konaframework.brew.BrewView, data: MutableMap<String, Any?>): ((String) -> String?)? {
        @Suppress("UNCHECKED_CAST")
        val attrs = component.state().store["attributes"] as? Map<String, Any?> ?: return null
        data["attributes"] = attrs
        return { html ->
            HtmlRoot.insertAttributes(html, attrs.mapKeys { "data-brew-attr-${it.key}" }, component.name())
        }
    }

    companion object {
        /** Register a mounted child under the current parent. */
        fun trackChild(parent: org.konaframework.brew.Component, childId: String, tag: String, key: String?) {
            @Suppress("UNCHECKED_CAST")
            val children = parent.state().store.getOrPut("children") { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
            children[key ?: childId] = listOf(tag, childId)
        }

        /** Invoke the parent's `@event="method"` listener when a child dispatches [event]. */
        fun notifyParent(child: org.konaframework.brew.Component, event: String, params: Map<String, Any?>) {
            @Suppress("UNCHECKED_CAST")
            val listeners = child.state().store["nestedListeners"] as? Map<String, String> ?: return
            val method = listeners[event] ?: return
            val parent = child.parent() ?: return
            val args = params.values.toList()
            val candidates = ComponentMeta.of(parent).actions[method] ?: return
            val target = candidates.firstOrNull { it.parameterCount == args.size }
                ?: candidates.maxBy { it.parameterCount }
            parent.state().manager.wrapped(parent) {
                parent.state().manager.invoker.call(parent, target, args)
            }
        }
    }
}
