package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function

class KeyValueEntry private constructor(name: String) : Entry<KeyValueEntry>(name) {
    private val keyLabelValue = TextSetting()
    private val valueLabelValue = TextSetting()

    override fun kind(): String = "key-value"

    fun keyLabel(value: String): KeyValueEntry = apply { keyLabelValue.set(value) }

    fun keyLabel(value: Function<Get, String>): KeyValueEntry = apply { keyLabelValue.set(value) }

    fun valueLabel(value: String): KeyValueEntry = apply { valueLabelValue.set(value) }

    fun valueLabel(value: Function<Get, String>): KeyValueEntry = apply { valueLabelValue.set(value) }

    fun emptyMessage(value: String): KeyValueEntry = placeholder(value)

    fun emptyMessage(value: Function<Get, String>): KeyValueEntry = placeholder(value)

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        val rows = rows(state)
        val keyLabel = keyLabelValue.get(get) ?: "Key"
        val valueLabel = valueLabelValue.get(get) ?: "Value"
        if (rows.isEmpty()) {
            return """<p class="ff-placeholder">${escapeHtml(placeholderValue.get(get) ?: "No values")}</p>"""
        }
        val body = rows.entries.joinToString("") { (key, value) ->
            "<tr><th>${escapeHtml(key)}</th><td>${escapeHtml(stringify(value))}</td></tr>"
        }
        return """<table class="ff-key-value"><thead><tr><th>${escapeHtml(keyLabel)}</th><th>${escapeHtml(valueLabel)}</th></tr></thead><tbody>$body</tbody></table>"""
    }

    private fun rows(state: Any?): Map<String, Any?> {
        if (state !is Map<*, *>) return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        for ((key, value) in state) {
            if (key == null) continue
            out[key.toString()] = value
        }
        return out
    }

    companion object {
        @JvmStatic
        fun make(name: String): KeyValueEntry {
            val entry = KeyValueEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<KeyValueEntry>?) = configure(KeyValueEntry::class.java, block)
    }
}
