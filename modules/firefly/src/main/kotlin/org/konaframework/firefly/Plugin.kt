package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer

/**
 * A panel extension. [register] and [boot] receive the panel and call the same methods an app calls.
 * There is no separate plugin registry.
 */
open class FireflyPlugin {
    private var viewPath: String? = null

    open fun id(): String = javaClass.simpleName.lowercase()

    fun view(path: String): FireflyPlugin = apply {
        if (path.isBlank()) throw FireflyException("Plugin view path is blank")
        viewPath = path
    }

    fun view(): String? = viewPath

    open fun register(panel: Panel) {}

    open fun boot(panel: Panel) {}

    companion object {
        private var configurer: Consumer<FireflyPlugin>? = null

        @JvmStatic
        fun configureUsing(block: Consumer<FireflyPlugin>?) {
            configurer = block
        }

        internal fun finish(plugin: FireflyPlugin): FireflyPlugin {
            val block = configurer
            if (block != null) block.accept(plugin)
            return plugin
        }
    }
}
