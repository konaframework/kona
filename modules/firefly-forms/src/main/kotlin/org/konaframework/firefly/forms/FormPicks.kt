package org.konaframework.firefly.forms

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStore
import org.konaframework.firefly.tables.Table
import java.util.function.Consumer

private class HostedTable(
    private val selectKey: (String) -> List<String>,
    private val html: () -> String,
) {
    fun select(key: String): List<String> = selectKey(key)

    fun render(): String = html()
}

/**
 * A field that opens a table and stores the selected record keys.
 */
open class ModalTableSelect protected constructor(name: String) : Field<ModalTableSelect>(name) {
    private var hosted: HostedTable? = null
    private var multipleFlag: Boolean = false

    fun <T, Q> table(source: Table<T, Q>): ModalTableSelect = apply {
        hosted = HostedTable(
            { key ->
                source.select(key)
                source.selected()
            },
            { source.render() },
        )
    }

    fun multiple(on: Boolean): ModalTableSelect = apply { multipleFlag = on }

    fun choose(schema: Schema, key: String): List<String> {
        val table = hosted ?: throw FireflyException("Modal table select has no table")
        val keys = table.select(key)
        val stored: Any? = if (multipleFlag) keys else keys.lastOrNull()
        schema.set(fieldPath(), stored, false)
        return keys
    }

    override fun kind(): String = "modal-table-select"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val table = hosted
        val body = if (table == null) "" else table.render()
        return buildString {
            append("""<div class="ff-modal-table" data-ff-path="${esc(path)}"${boolAttr("data-ff-multiple", multipleFlag)}>""")
            append(Action.make("choose").label("Choose").render(schema))
            append(body)
            append("</div>")
        }
    }

    companion object {
        private val maker = Maker { name -> ModalTableSelect(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<ModalTableSelect>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): ModalTableSelect = maker.make(name)
    }
}

/**
 * A select whose options come from the record store for the chosen type.
 */
open class MorphToSelect protected constructor(name: String) : Field<MorphToSelect>(name) {
    private val loaders = LinkedHashMap<String, () -> Map<String, String>>()
    private var title: String = "name"
    private var queryHook: Consumer<Any?>? = null

    fun titleAttribute(value: String): MorphToSelect = apply { title = value }

    fun <T, Q> type(alias: String, store: RecordStore<T, Q>): MorphToSelect = apply {
        loaders[alias] = {
            val query = store.query()
            val hook = queryHook
            if (hook != null) hook.accept(query)
            val attribute = title
            val out = LinkedHashMap<String, String>()
            for (record in store.list(query)) {
                val key = store.keyOf(record)
                val raw = store.attributes(record)[attribute]
                out[key] = if (raw == null) "" else raw.toString()
            }
            out
        }
    }

    fun modifyQueryUsing(modify: Consumer<Any?>): MorphToSelect = apply { queryHook = modify }

    fun optionsFor(type: String): Map<String, String> {
        val loader = loaders[type] ?: throw FireflyException("Unknown morph type [$type]")
        return loader()
    }

    fun selectType(schema: Schema, type: String, id: String): Map<String, String> {
        if (!loaders.containsKey(type)) throw FireflyException("Unknown morph type [$type]")
        val value = linkedMapOf("type" to type, "id" to id)
        schema.set(fieldPath(), value, false)
        return value
    }

    override fun kind(): String = "morph-select"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val typeName = morphType(value)
        val options = if (typeName == null) {
            emptyMap()
        } else {
            val loader = loaders[typeName]
            if (loader == null) emptyMap() else loader()
        }
        return buildString {
            append("""<div class="ff-morph" data-ff-path="${esc(path)}"${attr("data-ff-type", typeName)}>""")
            append("""<ul class="ff-options">""")
            for ((key, label) in options) {
                append("""<li data-ff-value="${esc(key)}">${esc(label)}</li>""")
            }
            append("</ul></div>")
        }
    }

    private fun morphType(value: Any?): String? {
        if (value !is Map<*, *>) return null
        val raw = value["type"] ?: return null
        val text = raw.toString()
        if (text.isEmpty()) return null
        return text
    }

    companion object {
        private val maker = Maker { name -> MorphToSelect(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<MorphToSelect>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): MorphToSelect = maker.make(name)
    }
}
