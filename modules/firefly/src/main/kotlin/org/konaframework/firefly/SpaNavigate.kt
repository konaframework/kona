package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException
import java.nio.charset.StandardCharsets

/** In-panel fetch navigation emitted when [Panel.spa] is on. */
internal object SpaNavigate {
    fun markup(): String = scriptTag(read("/org/konaframework/firefly/spa-navigate.js"))

    internal fun scriptTag(source: String): String = "<script data-ff-panel-spa>${source.trim()}</script>"

    internal fun read(path: String): String {
        val stream = SpaNavigate::class.java.getResourceAsStream(path)
            ?: throw FireflyException("Missing Firefly SPA navigate script")
        return stream.use { it.readAllBytes().toString(StandardCharsets.UTF_8) }
    }
}
