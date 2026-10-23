package org.konaframework.wire.features

import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import java.util.Base64

/** Emits `effects.download` from `$this->download(...)` / `$this->streamDownload(...)`. */
class SupportDownloads : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        @Suppress("UNCHECKED_CAST")
        val download = component.state().store["download"] as? Map<String, Any?> ?: return
        context.addEffect("download", download)
    }

    companion object {
        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    }
}
