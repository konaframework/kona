package org.konaframework.firefly.tables.layout

import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Cell
import org.konaframework.firefly.tables.Column
import org.konaframework.firefly.tables.escapeHtml

/**
 * Nestable column layout node (Split / Stack / Grid) for composed table rows.
 * Leaf children are [Column] instances; nests may contain further [LayoutComponent]s.
 */
abstract class LayoutComponent protected constructor() {
    protected val children = ArrayList<Any>()

    protected fun adopt(components: Array<out Any>): LayoutComponent = apply {
        children.clear()
        for (component in components) {
            when (component) {
                is Column, is LayoutComponent -> children.add(component)
                else -> throw FireflyException(
                    "Layout expects Column or nested layout; got ${component.javaClass.name}",
                )
            }
        }
    }

    /** Flatten every nested [Column] for search, sort, and toggle plumbing. */
    fun flattenColumns(): List<Column> {
        val out = ArrayList<Column>()
        for (child in children) {
            when (child) {
                is Column -> out.add(child)
                is LayoutComponent -> out.addAll(child.flattenColumns())
                else -> throw FireflyException("Layout child must be Column or layout; got ${child.javaClass.name}")
            }
        }
        return out
    }

    /** Test-only: inject a non-column child to exercise defensive branches. */
    internal fun addRawChildForCoverage(child: Any) {
        children.add(child)
    }

    fun render(ctx: LayoutRenderContext): String = renderBody(ctx)

    protected abstract fun renderBody(ctx: LayoutRenderContext): String

    protected fun renderChild(child: Any, ctx: LayoutRenderContext): String =
        when (child) {
            is Column -> {
                if (!ctx.shown(child)) {
                    ""
                } else {
                    val grow = if (child.grows()) "" else """ data-ff-grow="false""""
                    val cell = ctx.rowCellFor(child.name)
                    """<div class="ff-layout-item"$grow>${child.render(ctx.values, ctx.key, cell)}</div>"""
                }
            }
            is LayoutComponent -> child.render(ctx)
            else -> throw FireflyException("Layout child must be Column or layout; got ${child.javaClass.name}")
        }

    protected fun renderChildren(ctx: LayoutRenderContext): String =
        children.joinToString("") { renderChild(it, ctx) }

    companion object {
        internal val BREAKPOINTS = setOf("sm", "md", "lg", "xl", "2xl")

        internal fun requireBreakpoint(raw: String): String {
            val key = raw.trim().lowercase()
            if (key !in BREAKPOINTS) {
                throw FireflyException(
                    "Unknown layout breakpoint [$raw]; use ${BREAKPOINTS.joinToString(", ")}",
                )
            }
            return key
        }

        internal fun escape(value: String): String = escapeHtml(value)
    }
}

/** Values needed to paint a composed layout for one record. */
class LayoutRenderContext(
    val values: Map<String, Any?>,
    val key: String,
    val tableCell: Cell,
    val rowCellFor: (String) -> Cell,
    val shown: (Column) -> Boolean,
)
