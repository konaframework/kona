package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

class TableColumn private constructor(private val heading: String) {
    private var hidden = false

    fun hiddenHeaderLabel(): TableColumn = apply { hidden = true }

    fun hiddenHeaderLabel(on: Boolean): TableColumn = apply { hidden = on }

    fun isHeaderHidden(): Boolean = hidden

    fun label(): String = heading

    companion object {
        @JvmStatic
        fun make(label: String): TableColumn = TableColumn(label)
    }
}

class RepeatableEntry private constructor(name: String) : Entry<RepeatableEntry>(name) {
    private val containedFlag = BoolSetting(true)
    private val gridValue = IntSetting()
    private var columns = emptyList<TableColumn>()

    override fun kind(): String = "repeatable"

    fun schema(vararg components: Component): RepeatableEntry = apply { replaceChildren(components.toList()) }

    fun grid(count: Int): RepeatableEntry = apply { gridValue.set(count) }

    fun grid(provider: Function<Get, Int>): RepeatableEntry = apply { gridValue.set(provider) }

    fun contained(on: Boolean): RepeatableEntry = apply { containedFlag.set(on) }

    fun contained(predicate: Predicate<Get>): RepeatableEntry = apply { containedFlag.set(predicate) }

    fun table(vararg column: TableColumn): RepeatableEntry = apply { columns = column.toList() }

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        val items = items(state)
        if (items.isEmpty()) {
            return """<p class="ff-placeholder">${escapeHtml(placeholderValue.get(get) ?: "No items")}</p>"""
        }
        val grid = gridValue.get(get)
        val contained = containedFlag.get(get)
        val body = if (columns.isEmpty()) {
            items.mapIndexed { index, item ->
                ItemFrame.use(item) {
                    """<article class="ff-repeatable-item" data-ff-index="$index">${childHtml(schema)}</article>"""
                }
            }.joinToString("")
        } else {
            tableHtml(schema, items)
        }
        return """<div class="ff-repeatable"${flag("data-ff-contained", contained)}${attr("data-ff-grid", if (grid == null) null else grid.toString())}>$body</div>"""
    }

    private fun tableHtml(schema: Schema, items: List<Map<String, Any?>>): String {
        val head = columns.joinToString("") { column ->
            val hidden = if (column.isHeaderHidden()) """ class="ff-sr"""" else ""
            "<th$hidden>${escapeHtml(column.label())}</th>"
        }
        val rows = items.joinToString("") { item ->
            ItemFrame.use(item) {
                val cells = hostedChildren().joinToString("") { child ->
                    "<td>${child.render(schema)}</td>"
                }
                "<tr>$cells</tr>"
            }
        }
        return """<table class="ff-repeatable-table"><thead><tr>$head</tr></thead><tbody>$rows</tbody></table>"""
    }

    private fun items(state: Any?): List<Map<String, Any?>> {
        if (state == null) return emptyList()
        if (state is Map<*, *>) {
            val row = stringKey(state)
            if (row.isEmpty()) return emptyList()
            return listOf(row)
        }
        if (state !is Iterable<*>) return emptyList()
        val out = ArrayList<Map<String, Any?>>()
        for (item in state) {
            if (item is Map<*, *>) {
                val row = stringKey(item)
                if (row.isNotEmpty()) out.add(row)
            }
        }
        return out
    }

    companion object {
        @JvmStatic
        fun make(name: String): RepeatableEntry {
            val entry = RepeatableEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<RepeatableEntry>?) = configure(RepeatableEntry::class.java, block)
    }
}

private fun stringKey(state: Map<*, *>): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    for ((key, value) in state) {
        if (key == null) continue
        out[key.toString()] = value
    }
    return out
}
