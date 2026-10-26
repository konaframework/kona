package org.konaframework.firefly.support

enum class Hook(val marker: String) {
    SIDEBAR_START("sidebar.start"),
    SIDEBAR_END("sidebar.end"),
    TOPBAR_START("topbar.start"),
    TOPBAR_END("topbar.end"),
    BODY_START("body.start"),
    BODY_END("body.end"),
    FOOTER("footer"),
}

class RenderHooks {
    private val items = linkedMapOf<Hook, MutableList<String>>()

    fun add(hook: Hook, html: String): RenderHooks = apply {
        items.getOrPut(hook) { mutableListOf() }.add(html)
    }

    fun render(hook: Hook): String = items[hook].orEmpty().joinToString("")
}
