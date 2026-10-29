package org.konaframework.firefly.tables.layout

import org.konaframework.firefly.schemas.ColumnLayout

/**
 * CSS-grid layout for columns inside a record.
 * Use [make] with a column count (tracks from `lg` up, same as schema grids).
 * For a responsive **record** grid, use [org.konaframework.firefly.tables.Table.contentGrid].
 */
class Grid private constructor(private val columnLayout: ColumnLayout) : LayoutComponent() {
    fun schema(vararg components: Any): Grid = apply { adopt(components) }

    override fun renderBody(ctx: LayoutRenderContext): String {
        val style = columnLayout.styleAttribute()
        return """<div class="ff-layout-grid" data-ff-columns="${columnLayout.dataValue()}" style="$style">${renderChildren(ctx)}</div>"""
    }

    companion object {
        @JvmStatic
        fun make(columns: Int): Grid = Grid(ColumnLayout.of(columns))
    }
}
