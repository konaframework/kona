package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer

class ColorEntry private constructor(name: String) : Entry<ColorEntry>(name) {
    override fun kind(): String = "color"

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        if (isBlank(state)) {
            return """<span class="ff-placeholder">${escapeHtml(placeholderValue.get(get) ?: "")}</span>"""
        }
        val items = if (state is Collection<*>) state.map { it } else listOf(state)
        val body = items.joinToString("") { item ->
            val raw = stringify(item)
            val safe = cssColor(raw)
            val style = if (safe == null) "" else """ style="background-color: ${escapeHtml(safe)}""""
            val invalid = if (safe == null) """ data-ff-invalid="true"""" else ""
            val swatch = """<span class="ff-swatch" role="img" aria-label="${escapeHtml(safe ?: "")}"$style$invalid></span>"""
            link(get, swatch)
        }
        return """<div class="ff-colors"${flag("data-ff-copyable", copyableFlag.get(get))}>$body</div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): ColorEntry {
            val entry = ColorEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<ColorEntry>?) = configure(ColorEntry::class.java, block)
    }
}
