package org.konaframework.firefly.forms

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.BiFunction
import java.util.function.Consumer

class BuilderBlock(val name: String, val label: String, val schema: List<Component>)

open class Repeater protected constructor(name: String) : Field<Repeater>(name) {
    private val blueprint = ArrayList<Component>()
    private var addableFlag: Boolean = true
    private var deletableFlag: Boolean = true
    private var reorderableFlag: Boolean = false
    private var cloneableFlag: Boolean = false
    private var collapsibleFlag: Boolean = false
    private var collapsedFlag: Boolean = false
    private var gridCount: Int? = null
    private var minCount: Int? = null
    private var maxCount: Int? = null
    private var orderColumnName: String? = null
    private var relationName: String? = null
    private val itemLabelText = Phrase()
    private var itemLabelUsing: BiFunction<Int, Get, String>? = null
    private val addLabelText = Phrase()
    private var addAction: Action? = null
    private var deleteAction: Action? = null
    private var cloneAction: Action? = null
    private val extraItemActions = ArrayList<Action>()

    fun schema(vararg components: Component): Repeater = apply {
        blueprint.clear()
        blueprint.addAll(components)
    }

    fun simple(field: Field<*>): Repeater = schema(field)

    fun addable(on: Boolean): Repeater = apply { addableFlag = on }

    fun deletable(on: Boolean): Repeater = apply { deletableFlag = on }

    fun reorderable(on: Boolean): Repeater = apply { reorderableFlag = on }

    fun cloneable(on: Boolean): Repeater = apply { cloneableFlag = on }

    fun collapsible(on: Boolean): Repeater = apply { collapsibleFlag = on }

    fun collapsed(on: Boolean): Repeater = apply { collapsedFlag = on }

    fun grid(count: Int): Repeater = apply {
        if (count < 1) throw FireflyException("Repeater grid must be at least 1")
        gridCount = count
    }

    fun minItems(count: Int): Repeater = apply { minCount = count }

    fun maxItems(count: Int): Repeater = apply { maxCount = count }

    fun defaultItems(count: Int): Repeater = apply {
        defaultValue(List(count) { linkedMapOf<String, Any?>() })
    }

    fun orderColumn(column: String): Repeater = apply { orderColumnName = column }

    fun relationship(name: String): Repeater = apply { relationName = name }

    fun itemLabel(value: String): Repeater = apply {
        itemLabelText.set(value)
        itemLabelUsing = null
    }

    fun itemLabel(value: BiFunction<Int, Get, String>): Repeater = apply { itemLabelUsing = value }

    fun addActionLabel(value: String): Repeater = apply { addLabelText.set(value) }

    fun addActionLabel(value: java.util.function.Function<Get, String>): Repeater = apply { addLabelText.set(value) }

    fun addAction(action: Action): Repeater = apply { addAction = action }

    fun deleteAction(action: Action): Repeater = apply { deleteAction = action }

    fun cloneAction(action: Action): Repeater = apply { cloneAction = action }

    fun extraItemActions(vararg actions: Action): Repeater = apply {
        extraItemActions.clear()
        extraItemActions.addAll(actions)
    }

    fun items(schema: Schema): List<Map<String, Any?>> {
        val value = schema.get(fieldPath())
        if (value !is List<*>) return emptyList()
        val out = ArrayList<Map<String, Any?>>()
        for (item in value) {
            if (item is Map<*, *>) {
                val row = LinkedHashMap<String, Any?>()
                for ((key, cell) in item) {
                    if (key is String) row[key] = cell
                }
                out.add(row)
            } else {
                out.add(linkedMapOf())
            }
        }
        return out
    }

    fun addItem(schema: Schema): List<String> = write(schema, items(schema) + linkedMapOf())

    fun removeItem(schema: Schema, index: Int): List<String> {
        val next = items(schema).toMutableList()
        if (index < 0 || index >= next.size) throw FireflyException("Repeater item is missing")
        next.removeAt(index)
        return write(schema, next)
    }

    fun cloneItem(schema: Schema, index: Int): List<String> {
        val next = items(schema).toMutableList()
        if (index < 0 || index >= next.size) throw FireflyException("Repeater item is missing")
        next.add(index + 1, LinkedHashMap(next[index]))
        return write(schema, next)
    }

    fun moveItem(schema: Schema, from: Int, to: Int): List<String> {
        val next = items(schema).toMutableList()
        if (from < 0 || to < 0 || from >= next.size || to >= next.size) throw FireflyException("Repeater item is missing")
        val row = next.removeAt(from)
        next.add(to, row)
        return write(schema, next)
    }

    override fun kind(): String = "repeater"

    override fun extraErrors(schema: Schema): Map<String, List<String>> {
        val path = fieldPath()
        val rows = items(schema)
        val errors = ArrayList<String>()
        val min = minCount
        if (min != null && rows.size < min) errors.add("The $path field needs at least $min items.")
        val max = maxCount
        if (max != null && rows.size > max) errors.add("The $path field may not have more than $max items.")
        val seen = HashMap<String, MutableSet<String>>()
        for ((index, row) in rows.withIndex()) {
            for (child in blueprint) {
                if (child !is Field<*>) continue
                val cell = row[child.name]
                if (child.isDistinct()) {
                    val values = seen.getOrPut(child.name) { HashSet() }
                    val token = if (cell == null) "" else cell.toString()
                    if (token.isNotEmpty() && !values.add(token)) errors.add("The ${child.name} field has a duplicate value.")
                }
                if (child.isRequired(schema) && (cell == null || cell.toString().isBlank())) {
                    errors.add("The $path.$index.${child.name} field is required.")
                }
            }
        }
        if (errors.isEmpty()) return emptyMap()
        return mapOf(path to errors)
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val rows = items(schema)
        return buildString {
            append("""<div class="ff-repeater" x-data="ffRepeater" data-ff-path="${esc(path)}"${boolAttr("data-ff-addable", addableFlag)}${boolAttr("data-ff-deletable", deletableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}${boolAttr("data-ff-cloneable", cloneableFlag)}${boolAttr("data-ff-collapsible", collapsibleFlag)}${boolAttr("data-ff-collapsed", collapsedFlag)}${attr("data-ff-grid", gridCount?.toString())}${attr("data-ff-order", orderColumnName)}${attr("data-ff-relationship", relationName)}>""")
            for ((index, row) in rows.withIndex()) {
                append("""<div class="ff-repeater-item" data-ff-index="$index"><span class="ff-repeater-label">${esc(labelFor(index, get))}</span>""")
                for (child in blueprint) {
                    if (child is Field<*>) {
                        val itemPath = "$path.$index.${child.name}"
                        append(child.renderAt(schema, itemPath, row[child.name]))
                    }
                }
                val removing = deleteAction
                if (removing != null) append(removing.render(schema))
                val cloning = cloneAction
                if (cloning != null) append(cloning.render(schema))
                for (extra in extraItemActions) append(extra.render(schema))
                append("</div>")
            }
            if (addableFlag) append("""<button type="button" data-ff-repeater-add="true">${esc(addLabelText.get(get) ?: "Add")}</button>""")
            val adding = addAction
            if (adding != null) append(adding.render(schema))
            append("</div>")
        }
    }

    private fun labelFor(index: Int, get: Get): String {
        val using = itemLabelUsing
        if (using != null) return using.apply(index, get)
        return itemLabelText.get(get) ?: "Item ${index + 1}"
    }

    private fun write(schema: Schema, rows: List<Map<String, Any?>>): List<String> {
        val column = orderColumnName
        val next = if (column == null) {
            rows
        } else {
            rows.mapIndexed { index, row ->
                val copy = LinkedHashMap(row)
                copy[column] = index + 1
                copy
            }
        }
        return schema.set(fieldPath(), next, true)
    }

    companion object {
        private val maker = Maker { name -> Repeater(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Repeater>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Repeater = maker.make(name)
    }
}

open class Builder protected constructor(name: String) : Field<Builder>(name) {
    private val blocks = ArrayList<BuilderBlock>()
    private var addableFlag: Boolean = true
    private var deletableFlag: Boolean = true
    private var reorderableFlag: Boolean = false
    private var cloneableFlag: Boolean = false
    private var collapsibleFlag: Boolean = false
    private var numbersFlag: Boolean = false
    private var minCount: Int? = null
    private var maxCount: Int? = null
    private val addLabelText = Phrase()
    private var betweenAction: Action? = null

    fun blocks(vararg blocks: BuilderBlock): Builder = apply {
        this.blocks.clear()
        this.blocks.addAll(blocks)
    }

    fun addable(on: Boolean): Builder = apply { addableFlag = on }

    fun deletable(on: Boolean): Builder = apply { deletableFlag = on }

    fun reorderable(on: Boolean): Builder = apply { reorderableFlag = on }

    fun cloneable(on: Boolean): Builder = apply { cloneableFlag = on }

    fun collapsible(on: Boolean): Builder = apply { collapsibleFlag = on }

    fun blockNumbers(on: Boolean): Builder = apply { numbersFlag = on }

    fun minItems(count: Int): Builder = apply { minCount = count }

    fun maxItems(count: Int): Builder = apply { maxCount = count }

    fun addActionLabel(value: String): Builder = apply { addLabelText.set(value) }

    fun addActionLabel(value: java.util.function.Function<Get, String>): Builder = apply { addLabelText.set(value) }

    fun addBetweenAction(action: Action): Builder = apply { betweenAction = action }

    fun items(schema: Schema): List<Map<String, Any?>> {
        val value = schema.get(fieldPath())
        if (value !is List<*>) return emptyList()
        val out = ArrayList<Map<String, Any?>>()
        for (item in value) {
            if (item is Map<*, *>) {
                val row = LinkedHashMap<String, Any?>()
                for ((key, cell) in item) {
                    if (key is String) row[key] = cell
                }
                out.add(row)
            }
        }
        return out
    }

    fun addBlock(schema: Schema, type: String): List<String> {
        val known = blocks.firstOrNull { it.name == type } ?: throw FireflyException("Unknown block [$type]")
        val row = linkedMapOf<String, Any?>("type" to known.name, "data" to linkedMapOf<String, Any?>())
        return schema.set(fieldPath(), items(schema) + row, true)
    }

    override fun kind(): String = "builder"

    override fun extraErrors(schema: Schema): Map<String, List<String>> {
        val path = fieldPath()
        val rows = items(schema)
        val errors = ArrayList<String>()
        val min = minCount
        if (min != null && rows.size < min) errors.add("The $path field needs at least $min blocks.")
        val max = maxCount
        if (max != null && rows.size > max) errors.add("The $path field may not have more than $max blocks.")
        if (errors.isEmpty()) return emptyMap()
        return mapOf(path to errors)
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val rows = items(schema)
        return buildString {
            append("""<div class="ff-builder" x-data="ffBuilder" data-ff-path="${esc(path)}"${boolAttr("data-ff-addable", addableFlag)}${boolAttr("data-ff-deletable", deletableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}${boolAttr("data-ff-cloneable", cloneableFlag)}${boolAttr("data-ff-collapsible", collapsibleFlag)}${boolAttr("data-ff-numbers", numbersFlag)}>""")
            for ((index, row) in rows.withIndex()) {
                if (index > 0) {
                    val between = betweenAction
                    if (between != null) append(between.render(schema))
                }
                val rawType = row["type"]
                val type = if (rawType == null) "" else rawType.toString()
                val block = blocks.firstOrNull { it.name == type }
                val number = if (numbersFlag) "${index + 1}. " else ""
                val title = if (block == null) type else block.label
                append("""<div class="ff-builder-item" data-ff-type="${esc(type)}"><span class="ff-builder-label">$number${esc(title)}</span>""")
                val data = row["data"]
                val map = if (data is Map<*, *>) data else emptyMap<Any?, Any?>()
                if (block != null) {
                    for (child in block.schema) {
                        if (child is Field<*>) {
                            val cell = map[child.name]
                            append(child.renderAt(schema, "$path.$index.data.${child.name}", cell))
                        }
                    }
                }
                append("</div>")
            }
            if (addableFlag) {
                append("""<div class="ff-builder-picker">""")
                for (block in blocks) append("""<button type="button" data-ff-block="${esc(block.name)}">${esc(block.label)}</button>""")
                append("""<span class="ff-builder-add">${esc(addLabelText.get(get) ?: "Add block")}</span></div>""")
            }
            append("</div>")
        }
    }

    companion object {
        private val maker = Maker { name -> Builder(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Builder>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Builder = maker.make(name)
    }
}
