package org.konaframework.firefly.tables

import java.util.function.Function
import java.util.function.Predicate

/** Row or table state passed into a chainable closure. [get] reads a path. */
class Cell(private val lookup: (String) -> Any?) {
    fun get(path: String): Any? = lookup(path)

    companion object {
        @JvmStatic
        fun of(lookup: Function<String, Any?>): Cell = Cell { lookup.apply(it) }
    }
}

internal class TextSetting {
    private var literal: String = ""
    private var using: Function<Cell, String>? = null
    private var assigned = false

    fun set(value: String) {
        literal = value
        using = null
        assigned = true
    }

    fun set(value: Function<Cell, String>) {
        using = value
        assigned = true
    }

    fun get(cell: Cell, fallback: String): String {
        val fn = using
        if (fn != null) return fn.apply(cell)
        if (assigned) return literal
        return fallback
    }
}

internal class FlagSetting(default: Boolean) {
    private var literal = default
    private var using: Predicate<Cell>? = null

    fun set(value: Boolean) {
        literal = value
        using = null
    }

    fun set(value: Predicate<Cell>) {
        using = value
    }

    fun get(cell: Cell): Boolean {
        val fn = using
        if (fn != null) return fn.test(cell)
        return literal
    }
}

internal class ValueSetting {
    private var literal: Any? = null
    private var using: Function<Cell, Any?>? = null
    private var assigned = false

    fun set(value: Any?) {
        literal = value
        using = null
        assigned = true
    }

    fun set(value: Function<Cell, Any?>) {
        using = value
        assigned = true
    }

    fun get(cell: Cell): Any? {
        val fn = using
        if (fn != null) return fn.apply(cell)
        if (assigned) return literal
        return null
    }
}

internal fun escapeHtml(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        if (ch == '&') {
            out.append("&amp;")
        } else if (ch == '<') {
            out.append("&lt;")
        } else if (ch == '>') {
            out.append("&gt;")
        } else if (ch == '"') {
            out.append("&quot;")
        } else {
            out.append(ch)
        }
    }
    return out.toString()
}

internal fun truthy(value: Any?): Boolean {
    org.konaframework.core.Config.toBoolean(value)?.let { return it }
    return when (value) {
        null -> false
        is Collection<*> -> value.isNotEmpty()
        // Non-boolean strings already failed coercion only when non-empty / unknown tokens.
        else -> true
    }
}
