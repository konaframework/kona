package org.konaframework.wire.features

import org.konaframework.wire.Transition
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.lifecycle.ReturnEarly
import org.konaframework.wire.meta.ComponentMeta

/**
 * `@Transition(type=…, skip=…)` on actions and `$this->transition()` / `skipTransition()`
 * emit an `effects.transition` payload for the client View Transitions API.
 */
class SupportTransitions : ComponentHook() {
    override fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? {
        val methods = ComponentMeta.of(component).actions[method].orEmpty()
        for (m in methods) {
            val ann = m.getAnnotation(Transition::class.java) ?: continue
            if (ann.type.isNotBlank()) component.state().store["transitionType"] = ann.type
            if (ann.skip) component.state().store["transitionSkip"] = true
        }
        return null
    }

    override fun dehydrate(context: ComponentContext) {
        val type = component.state().store["transitionType"] as? String
        val skip = component.state().store["transitionSkip"] as? Boolean
        if (type != null || skip == true) {
            val payload = linkedMapOf<String, Any?>()
            if (type != null) payload["type"] = type
            if (skip == true) payload["skip"] = true
            context.addEffect("transition", payload)
        }
    }
}
