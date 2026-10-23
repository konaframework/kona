package org.konaframework.wire.features

import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook

/** Queues `$this->js(...)` expressions into `effects.xjs`. */
class SupportJsEvaluation : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        @Suppress("UNCHECKED_CAST")
        val scripts = component.state().store["xjs"] as? List<String>
        if (!scripts.isNullOrEmpty()) context.addEffect("xjs", scripts)
    }
}
