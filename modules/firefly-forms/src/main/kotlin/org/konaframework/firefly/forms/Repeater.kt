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
        val value = FormJson.decode(schema.get(fieldPath()))
        if (value !is List<*>) return emptyList()
        val out = ArrayList<Map<String, Any?>>()
        for (item in value) {
            if (item is Map<*, *>) {
                out.add(stringKeys(item))
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
        val flags = ItemFlags(reorderableFlag, cloneableFlag, deletableFlag, collapsibleFlag, collapsedFlag)
        val numbered = itemLabelUsing == null && itemLabelText.get(get) == null
        return buildString {
            append("""<div class="ff-repeater" x-data="ffRepeater" data-ff-repeater data-ff-path="${esc(path)}"${boolAttr("data-ff-addable", addableFlag)}${boolAttr("data-ff-deletable", deletableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}${boolAttr("data-ff-cloneable", cloneableFlag)}${boolAttr("data-ff-collapsible", collapsibleFlag)}${boolAttr("data-ff-collapsed", collapsedFlag)}${attr("data-ff-grid", gridCount?.toString())}${attr("data-ff-order", orderColumnName)}${attr("data-ff-relationship", relationName)}${attr("data-ff-min", minCount?.toString())}${attr("data-ff-max", maxCount?.toString())}${attr("data-ff-label-template", if (numbered) "Item {n}" else null)}>""")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(FormJson.encode(rows))}" data-ff-repeater-value />""")
            append("""<ol class="ff-repeater-items" data-ff-repeater-items>""")
            for ((index, row) in rows.withIndex()) {
                append(itemHtml(schema, path, index.toString(), labelFor(index, get), row, flags))
            }
            append("</ol>")
            append("""<template data-ff-repeater-template>${itemHtml(schema, path, INDEX_TOKEN, labelFor(rows.size, get), emptyMap(), flags)}</template>""")
            if (addableFlag) {
                append("""<button type="button" class="ff-action-outlined ff-repeater-add" data-ff-repeater-add="true">${FormIcons.svg(FormIcons.PLUS)}<span>${esc(addLabelText.get(get) ?: "Add")}</span></button>""")
            }
            val adding = addAction
            if (adding != null) append(adding.render(schema))
            append("</div>")
        }
    }

    private fun itemHtml(schema: Schema, path: String, index: String, label: String, row: Map<String, Any?>, flags: ItemFlags): String = buildString {
        val columns = gridCount
        val gridStyle = if (columns == null) "" else """ style="--ff-item-cols: $columns""""
        append(itemOpen("ff-repeater-item", "data-ff-repeater-item", index, flags))
        append(itemHead(label, flags))
        append("""<div class="ff-repeater-item-body"$gridStyle>""")
        for (child in blueprint) {
            if (child is Field<*>) append(child.renderItem(schema, "$path.$index.${child.name}", row[child.name]))
        }
        val removing = deleteAction
        if (removing != null) append(removing.render(schema))
        val cloning = cloneAction
        if (cloning != null) append(cloning.render(schema))
        for (extra in extraItemActions) append(extra.render(schema))
        append("</div></li>")
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
        val value = FormJson.decode(schema.get(fieldPath()))
        if (value !is List<*>) return emptyList()
        val out = ArrayList<Map<String, Any?>>()
        for (item in value) {
            if (item is Map<*, *>) out.add(stringKeys(item))
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
        val flags = ItemFlags(reorderableFlag, cloneableFlag, deletableFlag, collapsibleFlag, false)
        return buildString {
            append("""<div class="ff-builder" x-data="ffBuilder" data-ff-builder data-ff-path="${esc(path)}"${boolAttr("data-ff-addable", addableFlag)}${boolAttr("data-ff-deletable", deletableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}${boolAttr("data-ff-cloneable", cloneableFlag)}${boolAttr("data-ff-collapsible", collapsibleFlag)}${boolAttr("data-ff-numbers", numbersFlag)}${attr("data-ff-min", minCount?.toString())}${attr("data-ff-max", maxCount?.toString())}>""")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(FormJson.encode(rows))}" data-ff-builder-value />""")
            append("""<ol class="ff-repeater-items ff-builder-items" data-ff-repeater-items>""")
            for ((index, row) in rows.withIndex()) {
                if (index > 0) {
                    val between = betweenAction
                    if (between != null) append(between.render(schema))
                }
                val rawType = row["type"]
                val type = if (rawType == null) "" else rawType.toString()
                val data = row["data"]
                val map = if (data is Map<*, *>) stringKeys(data) else emptyMap()
                append(blockHtml(schema, path, index.toString(), index, type, map, flags))
            }
            append("</ol>")
            for (block in blocks) {
                append("""<template data-ff-builder-template="${esc(block.name)}">${blockHtml(schema, path, INDEX_TOKEN, rows.size, block.name, emptyMap(), flags)}</template>""")
            }
            if (addableFlag) {
                append("""<div class="ff-builder-add" data-ff-builder-picker>""")
                append("""<button type="button" class="ff-action-outlined ff-builder-add-trigger" data-ff-builder-toggle aria-haspopup="menu">${FormIcons.svg(FormIcons.PLUS)}<span>${esc(addLabelText.get(get) ?: "Add block")}</span></button>""")
                append("""<div class="ff-builder-menu" role="menu" data-ff-builder-menu hidden>""")
                for (block in blocks) append("""<button type="button" class="ff-builder-menu-item" role="menuitem" data-ff-block="${esc(block.name)}">${esc(block.label)}</button>""")
                append("</div></div>")
            }
            append("</div>")
        }
    }

    private fun blockHtml(schema: Schema, path: String, index: String, position: Int, type: String, data: Map<String, Any?>, flags: ItemFlags): String = buildString {
        val block = blocks.firstOrNull { it.name == type }
        val number = if (numbersFlag) "${position + 1}. " else ""
        val title = if (block == null) type else block.label
        append(itemOpen("ff-repeater-item ff-builder-item", """data-ff-builder-item data-ff-type="${esc(type)}"""", index, flags))
        append(itemHead("$number$title", flags))
        append("""<div class="ff-repeater-item-body">""")
        if (block != null) {
            for (child in block.schema) {
                if (child is Field<*>) append(child.renderItem(schema, "$path.$index.data.${child.name}", data[child.name]))
            }
        }
        append("</div></li>")
    }

    companion object {
        private val maker = Maker { name -> Builder(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Builder>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Builder = maker.make(name)
    }
}

internal const val INDEX_TOKEN = "__INDEX__"

/** Item header controls shared by [Repeater] and [Builder] items. */
internal class ItemFlags(
    val reorder: Boolean,
    val clone: Boolean,
    val delete: Boolean,
    val collapse: Boolean,
    val collapsed: Boolean,
)

internal fun itemOpen(css: String, marker: String, index: String, flags: ItemFlags): String {
    val collapsed = if (flags.collapse && flags.collapsed) """ data-ff-collapsed="true"""" else ""
    return """<li class="$css" $marker data-ff-index="$index"$collapsed>"""
}

internal fun itemHead(label: String, flags: ItemFlags): String = buildString {
    append("""<div class="ff-repeater-item-head">""")
    if (flags.reorder) {
        append("""<div class="ff-repeater-item-order">""")
        append(FormIcons.button("ff-icon-button", "Move up", "data-ff-item-up", FormIcons.ARROW_UP))
        append(FormIcons.button("ff-icon-button", "Move down", "data-ff-item-down", FormIcons.ARROW_DOWN))
        append("</div>")
    }
    append("""<span class="ff-repeater-label" data-ff-item-label>${esc(label)}</span>""")
    append("""<div class="ff-repeater-item-actions">""")
    if (flags.clone) append(FormIcons.button("ff-icon-button", "Clone", "data-ff-item-clone", FormIcons.DUPLICATE))
    if (flags.delete) append(FormIcons.button("ff-icon-button ff-icon-button-danger", "Delete", "data-ff-item-delete", FormIcons.TRASH))
    if (flags.collapse) append(FormIcons.button("ff-icon-button ff-item-collapse", "Collapse", "data-ff-item-collapse", FormIcons.CHEVRON_DOWN))
    append("</div></div>")
}

internal fun stringKeys(map: Map<*, *>): Map<String, Any?> {
    val row = LinkedHashMap<String, Any?>()
    for ((key, cell) in map) {
        if (key is String) row[key] = cell
    }
    return row
}
