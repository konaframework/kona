package org.konaframework.firefly.tables

import org.konaframework.firefly.schemas.htmlAttributes
import java.util.function.Function

internal class InputAttributes {
    private var literal: Map<String, String> = emptyMap()
    private var using: Function<Cell, Map<String, String>>? = null

    fun set(values: Map<String, String>) {
        literal = LinkedHashMap(values)
        using = null
    }

    fun set(provider: Function<Cell, Map<String, String>>) {
        using = provider
        literal = emptyMap()
    }

    fun markup(cell: Cell): String {
        val provider = using
        val source = if (provider != null) provider.apply(cell) else literal
        if (source.isEmpty()) return ""
        val filtered = LinkedHashMap<String, String>()
        for ((key, value) in source) {
            if (key.isEmpty() || key == "type" || key == "name" || key == "value") continue
            filtered[key] = value
        }
        return htmlAttributes(filtered)
    }
}
