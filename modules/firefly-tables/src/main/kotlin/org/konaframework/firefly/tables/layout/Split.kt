package org.konaframework.firefly.tables.layout

/**
 * Side-by-side column group that can stack below a breakpoint (`from("md")`).
 */
class Split private constructor() : LayoutComponent() {
    private var fromBreakpoint: String? = null

    /** Start the horizontal split at this breakpoint; below it, children stack. */
    fun from(breakpoint: String): Split = apply { fromBreakpoint = requireBreakpoint(breakpoint) }

    override fun renderBody(ctx: LayoutRenderContext): String {
        val fromAttr =
            if (fromBreakpoint == null) "" else """ data-ff-from="${escape(fromBreakpoint!!)}""""
        return """<div class="ff-layout-split"$fromAttr>${renderChildren(ctx)}</div>"""
    }

    companion object {
        @JvmStatic
        fun make(vararg components: Any): Split = Split().also { it.adopt(components) }
    }
}
