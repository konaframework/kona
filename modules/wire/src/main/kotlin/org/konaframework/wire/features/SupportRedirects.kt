package org.konaframework.wire.features

import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook

/**
 * Emits `effects.redirect` from `$this->redirect*` helpers.
 * Flash persistence uses the Kona session when a request is bound (`Component.flash`).
 */
class SupportRedirects : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        @Suppress("UNCHECKED_CAST")
        val flash = component.state().store["flash"] as? Map<String, Any?>
        if (!flash.isNullOrEmpty()) context.addEffect("flash", flash)

        val redirect = component.state().store["redirect"] as? Map<*, *> ?: return
        context.addEffect("redirect", redirect)
    }
}
