package org.konaframework.wire.features

import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook

/** Emits `effects.streams` from `$this->stream(...)`. */
class SupportStreaming : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        @Suppress("UNCHECKED_CAST")
        val streams = component.state().store["streams"] as? List<Map<String, Any?>>
        if (!streams.isNullOrEmpty()) context.addEffect("streams", streams)
    }
}
