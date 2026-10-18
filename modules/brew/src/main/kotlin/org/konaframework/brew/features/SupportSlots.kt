package org.konaframework.brew.features

import org.konaframework.brew.lifecycle.ComponentHook

/** Stores default/named slots on the component for `$slots` / view data. */
class SupportSlots : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        for ((name, value) in params) {
            if (!name.startsWith("__slot_")) continue
            if (value == null) continue
            put(component, name.removePrefix("__slot_"), value.toString())
        }
    }

    override fun render(view: org.konaframework.brew.BrewView, data: MutableMap<String, Any?>): ((String) -> String?)? {
        @Suppress("UNCHECKED_CAST")
        val slots = component.state().store["slots"] as? Map<String, String> ?: emptyMap()
        data["slots"] = SlotBag(slots)
        return null
    }

    companion object {
        fun put(component: org.konaframework.brew.Component, name: String, html: String) {
            @Suppress("UNCHECKED_CAST")
            val slots = component.state().store.getOrPut("slots") { linkedMapOf<String, String>() } as MutableMap<String, String>
            slots[name] = html
        }
    }
}

/** Blade-like `$slots` bag. */
class SlotBag(private val slots: Map<String, String>) {
    fun has(name: String): Boolean = slots.containsKey(name)
    fun get(name: String): String = slots[name].orEmpty()
    fun default(): String = slots["default"].orEmpty()
    fun all(): Map<String, String> = slots
}
