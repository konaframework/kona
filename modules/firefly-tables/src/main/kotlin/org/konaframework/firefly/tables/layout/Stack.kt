package org.konaframework.firefly.tables.layout

import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Align

/**
 * Vertical stack of columns (or nested layouts) inside a row / card.
 */
class Stack private constructor() : LayoutComponent() {
    private var spaceValue = 0
    private var alignment = Align.START
    private var visibleFromBreakpoint: String? = null

    /** Vertical gap: `0`, `1`, `2`, or `3` (Tailwind-scale spacing). */
    fun space(value: Int): Stack = apply {
        when (value) {
            0, 1, 2, 3 -> spaceValue = value
            else -> throw FireflyException("Stack space must be 0, 1, 2, or 3")
        }
    }

    fun alignment(value: Align): Stack = apply { alignment = value }

    /** Hide this stack below the given breakpoint. */
    fun visibleFrom(breakpoint: String): Stack = apply {
        visibleFromBreakpoint = requireBreakpoint(breakpoint)
    }

    override fun renderBody(ctx: LayoutRenderContext): String {
        val spaceAttr = if (spaceValue == 0) "" else """ data-ff-space="$spaceValue""""
        val alignAttr = """ data-ff-align="${alignment.name.lowercase()}""""
        val visibleAttr =
            if (visibleFromBreakpoint == null) {
                ""
            } else {
                """ data-ff-visible-from="${escape(visibleFromBreakpoint!!)}""""
            }
        return """<div class="ff-layout-stack"$spaceAttr$alignAttr$visibleAttr>${renderChildren(ctx)}</div>"""
    }

    companion object {
        @JvmStatic
        fun make(vararg components: Any): Stack = Stack().also { it.adopt(components) }
    }
}
