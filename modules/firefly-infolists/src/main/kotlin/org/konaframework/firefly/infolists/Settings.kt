package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Icon
import java.util.function.Function
import java.util.function.Predicate

internal class TextSetting {
    private var literal: String? = null
    private var using: Function<Get, String>? = null

    fun set(value: String) {
        literal = value
        using = null
    }

    fun set(provider: Function<Get, String>) {
        using = provider
        literal = null
    }

    fun get(get: Get): String? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class BoolSetting(private var literal: Boolean) {
    private var using: Predicate<Get>? = null

    fun set(value: Boolean) {
        literal = value
        using = null
    }

    fun set(predicate: Predicate<Get>) {
        using = predicate
    }

    fun get(get: Get): Boolean {
        val predicate = using
        if (predicate != null) return predicate.test(get)
        return literal
    }
}

internal class IntSetting {
    private var literal: Int? = null
    private var using: Function<Get, Int>? = null

    fun set(value: Int) {
        literal = value
        using = null
    }

    fun set(provider: Function<Get, Int>) {
        using = provider
        literal = null
    }

    fun get(get: Get): Int? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class MapSetting {
    private var literal: Map<String, String>? = null
    private var using: Function<Get, Map<String, String>>? = null

    fun set(value: Map<String, String>) {
        literal = value
        using = null
    }

    fun set(provider: Function<Get, Map<String, String>>) {
        using = provider
        literal = null
    }

    fun get(get: Get): Map<String, String>? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class AnySetting {
    private var literal: Any? = null
    private var using: Function<Get, Any?>? = null
    private var set = false

    fun set(value: Any?) {
        literal = value
        using = null
        set = true
    }

    fun set(provider: Function<Get, Any?>) {
        using = provider
        literal = null
        set = true
    }

    fun get(get: Get): Pair<Boolean, Any?> {
        val provider = using
        if (provider != null) return true to provider.apply(get)
        if (set) return true to literal
        return false to null
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

private val ATTR_NAME = Regex("^[A-Za-z_:][A-Za-z0-9_:.-]*$")

internal fun extraAttrs(values: Map<String, String>?): String {
    if (values == null || values.isEmpty()) return ""
    val out = StringBuilder()
    for ((key, value) in values) {
        if (!ATTR_NAME.matches(key)) continue
        out.append(' ')
        out.append(key)
        out.append("=\"")
        out.append(escapeHtml(value))
        out.append('"')
    }
    return out.toString()
}

internal fun attr(name: String, value: String?): String {
    if (value == null) return ""
    return """ $name="${escapeHtml(value)}""""
}

internal fun flag(name: String, on: Boolean): String {
    if (!on) return ""
    return """ $name="true""""
}

internal fun wrapSpan(className: String, inner: String): String {
    if (inner.isEmpty()) return ""
    return """<span class="$className">$inner</span>"""
}

internal fun choice(label: String, value: String, allowed: Set<String>, aliases: Map<String, String> = emptyMap()): String {
    val mapped = aliases[value] ?: value
    if (allowed.contains(mapped)) return mapped
    throw FireflyException("Unknown $label [$value]")
}

private val ALIGN = setOf("start", "center", "end", "justify")
private val SIZE = setOf("xs", "sm", "md", "lg", "xl")
private val WEIGHT = setOf("thin", "light", "normal", "medium", "semibold", "bold", "extrabold")
private val FONT = setOf("sans", "serif", "mono")
private val ICON_AT = setOf("before", "after")

internal fun alignmentOf(value: String): String = choice("alignment", value, ALIGN, mapOf("left" to "start", "right" to "end"))

internal fun sizeOf(value: String): String = choice(
    "size",
    value,
    SIZE,
    mapOf("small" to "sm", "medium" to "md", "base" to "md", "large" to "lg"),
)

internal fun weightOf(value: String): String = choice("weight", value, WEIGHT)

internal fun fontOf(value: String): String = choice("font", value, FONT)

internal fun iconPositionOf(value: String): String = choice("icon position", value, ICON_AT)

internal fun iconHtml(name: String?): String {
    if (name == null || name.isEmpty()) return ""
    return try {
        Icon.fromName(name).svg()
    } catch (ex: FireflyException) {
        """<span class="ff-icon" data-ff-icon="${escapeHtml(name)}"></span>"""
    }
}

internal fun cssColor(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val hex = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    if (hex.matches(trimmed)) return trimmed
    val fn = Regex("^(rgb|rgba|hsl|hsla)\\([0-9.,%\\s]+\\)$")
    if (fn.matches(trimmed)) return trimmed
    val named = Regex("^[a-zA-Z]{1,32}$")
    if (named.matches(trimmed)) return trimmed
    return null
}

internal object ItemFrame {
    private val stack = ThreadLocal.withInitial { ArrayDeque<Map<String, Any?>>() }

    fun <T> use(item: Map<String, Any?>, block: () -> T): T {
        val deque = stack.get()
        deque.addLast(item)
        try {
            return block()
        } finally {
            deque.removeLast()
        }
    }

    fun current(): Map<String, Any?>? {
        val deque = stack.get()
        if (deque.isEmpty()) return null
        return deque.last()
    }
}

internal val MISS = Any()

internal fun digName(item: Any?, name: String): Any? {
    if (item == null) return null
    if (name.isEmpty()) return item
    var current = item
    for (part in name.split('.')) {
        if (current !is Map<*, *>) return null
        if (!current.containsKey(part)) return null
        current = current[part]
    }
    return current
}

internal fun itemLookup(item: Map<String, Any?>, path: String): Any? {
    if (path.isEmpty() || path == "." || path == ".." || path.contains("..")) return MISS
    var current: Any? = item
    val parts = path.split('/')
    for (part in parts) {
        if (part.isEmpty() || part == ".") continue
        if (current !is Map<*, *>) return MISS
        if (!current.containsKey(part)) return MISS
        current = current[part]
    }
    return current
}
