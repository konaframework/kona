package org.konaframework.firefly.tables

/**
 * Renders several [TableButton]s inside a ⋯ (kebab) dropdown so row actions stay compact.
 */
internal class ActionMenuButton(
    private val items: List<TableButton>,
) : TableButton("actions") {
    override fun render(cell: Cell, selectionEmpty: Boolean, menuItem: Boolean): String {
        if (items.isEmpty()) return ""
        val panel = buildString {
            for (item in items) {
                append(item.render(cell, selectionEmpty, menuItem = true))
            }
        }
        return """
            <div class="ff-menu ff-row-action-menu" data-ff-menu>
              <button type="button" class="ff-table-button ff-table-more" data-ff-menu-trigger aria-label="Actions" title="Actions">⋮</button>
              <div class="ff-menu-panel" data-ff-menu-panel>$panel</div>
            </div>
        """.trimIndent()
    }
}
