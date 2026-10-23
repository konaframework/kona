package org.konaframework.wire

/**
 * A server-queued browser event (`$this->dispatch(...)`).
 * Fluent [to] / [self] / [component] / [ref] refine delivery; serialized into `effects.dispatches`.
 */
class Event(
    val name: String,
    params: Map<String, Any?> = emptyMap(),
) {
    private val params: MutableMap<String, Any?> = params.toMutableMap()
    private var to: String? = null
    private var self: Boolean = false
    private var ref: String? = null
    private var el: String? = null

    fun to(componentName: String): Event {
        to = componentName
        return this
    }

    fun to(componentClass: Class<out Component>): Event {
        to = ComponentRegistry.nameFor(componentClass, null)
        return this
    }

    fun self(): Event {
        self = true
        return this
    }

    fun component(name: String): Event = to(name)

    fun ref(name: String): Event {
        ref = name
        return this
    }

    fun el(selector: String): Event {
        el = selector
        return this
    }

    fun serialize(): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("name" to name)
        if (params.isNotEmpty()) out["params"] = params.toMap()
        if (self) out["self"] = true
        if (to != null) out["component"] = to
        if (ref != null) out["ref"] = ref
        if (el != null) out["el"] = el
        return out
    }
}
