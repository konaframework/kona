package org.konaframework.brew.features

import org.konaframework.brew.CorruptComponentPayloadException
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook

/**
 * Invalidates snapshots after deploy when [org.konaframework.brew.BrewManager.releaseToken] changes
 * (`SupportReleaseTokens`).
 */
class SupportReleaseTokens : ComponentHook() {
    override fun skip(): Boolean = manager.releaseToken == null

    override fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {
        val expected = manager.releaseToken ?: return
        val provided = memo["release"] as? String
        if (provided != expected) {
            throw CorruptComponentPayloadException(
                "Brew release token mismatch. The page was likely deployed since this component was loaded.",
            )
        }
    }

    override fun dehydrate(context: ComponentContext) {
        manager.releaseToken?.let { context.addMemo("release", it) }
    }
}
