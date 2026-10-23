package org.konaframework.wire.features

import org.konaframework.wire.CorruptComponentPayloadException
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook

/**
 * Invalidates snapshots after deploy when [org.konaframework.wire.WireManager.releaseToken] changes
 * (`SupportReleaseTokens`).
 */
class SupportReleaseTokens : ComponentHook() {
    override fun skip(): Boolean = manager.releaseToken == null

    override fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {
        val expected = manager.releaseToken ?: return
        val provided = memo["release"] as? String
        if (provided != expected) {
            throw CorruptComponentPayloadException(
                "Wire release token mismatch. The page was likely deployed since this component was loaded.",
            )
        }
    }

    override fun dehydrate(context: ComponentContext) {
        manager.releaseToken?.let { context.addMemo("release", it) }
    }
}
