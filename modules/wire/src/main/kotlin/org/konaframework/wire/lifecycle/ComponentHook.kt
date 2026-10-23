package org.konaframework.wire.lifecycle

import org.konaframework.wire.WireManager
import org.konaframework.wire.WireView
import org.konaframework.wire.Component

/** Per-request effects + memo for one component (`ComponentContext`). */
class ComponentContext(val component: Component, val mounting: Boolean) {
    val effects: MutableMap<String, Any?> = linkedMapOf()
    val memo: MutableMap<String, Any?> = linkedMapOf()

    fun addEffect(key: String, value: Any?): ComponentContext {
        effects[key] = value
        return this
    }

    /** Append [value] to a list effect (e.g. `dispatches`, `xjs`). */
    fun pushEffect(key: String, value: Any?): ComponentContext {
        @Suppress("UNCHECKED_CAST")
        val list = effects.getOrPut(key) { mutableListOf<Any?>() } as MutableList<Any?>
        list += value
        return this
    }

    fun addMemo(key: String, value: Any?): ComponentContext {
        memo[key] = value
        return this
    }
}

/** Lets a `call` hook short-circuit the action (`$returnEarly`). */
class ReturnEarly {
    var called: Boolean = false
        private set
    var value: Any? = null
        private set

    operator fun invoke(value: Any? = null) {
        called = true
        this.value = value
    }
}

/** Lets an `exception` hook swallow the error (`$stopPropagation`). */
class StopPropagation {
    var stopped: Boolean = false
        private set

    operator fun invoke() {
        stopped = true
    }
}

/**
 * Feature hook (`Livewire\ComponentHook`). One instance is created per component per request; override only the
 * phases the feature needs. Finishers returned from [update], [call], and [render] run after the phase completes.
 */
abstract class ComponentHook {
    lateinit var component: Component
        internal set
    lateinit var manager: WireManager
        internal set

    /** Skip this hook entirely for the component (checked once at creation). */
    open fun skip(): Boolean = false

    open fun mount(params: Map<String, Any?>, key: String?) {}

    open fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {}

    /** Before a property update. Return a finisher to run after **all** updates are applied. */
    open fun update(property: String, fullPath: String, value: Any?): (() -> Unit)? = null

    /** Before an action call. Return a finisher that may transform the return value. */
    open fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? = null

    /** Before rendering; may add view data. Return a finisher that may replace the HTML (return null to keep). */
    open fun render(view: WireView, data: MutableMap<String, Any?>): ((String) -> String?)? = null

    open fun dehydrate(context: ComponentContext) {}

    open fun destroy() {}

    open fun exception(error: Throwable, stopPropagation: StopPropagation) {}
}

/** Ordered hook instances attached to one component. */
class HookSet internal constructor(private val hooks: List<ComponentHook>) {
    fun mount(params: Map<String, Any?>, key: String?) = hooks.forEach { it.mount(params, key) }

    fun hydrate(memo: Map<String, Any?>, context: ComponentContext) = hooks.forEach { it.hydrate(memo, context) }

    fun update(property: String, fullPath: String, value: Any?): () -> Unit {
        val finishers = hooks.mapNotNull { it.update(property, fullPath, value) }
        return { finishers.forEach { it() } }
    }

    fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): (Any?) -> Any? {
        val finishers = hooks.mapNotNull { it.call(method, params, returnEarly, metadata, context) }
        return { value -> finishers.fold(value) { acc, f -> f(acc) } }
    }

    fun render(view: WireView, data: MutableMap<String, Any?>): (String) -> String {
        val finishers = hooks.mapNotNull { it.render(view, data) }
        return { html -> finishers.fold(html) { acc, f -> f(acc) ?: acc } }
    }

    fun dehydrate(context: ComponentContext) = hooks.forEach { it.dehydrate(context) }

    fun destroy() = hooks.forEach { it.destroy() }

    fun exception(error: Throwable): Boolean {
        val stop = StopPropagation()
        for (hook in hooks) {
            hook.exception(error, stop)
            if (stop.stopped) return true
        }
        return false
    }
}
