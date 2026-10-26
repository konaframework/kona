package org.konaframework.firefly.forms

internal fun esc(text: String): String = buildString {
    for (ch in text) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }
}

internal fun attr(name: String, value: String?): String {
    if (value == null) return ""
    return " $name=\"${esc(value)}\""
}

internal fun boolAttr(name: String, on: Boolean): String =
    " $name=\"${if (on) "true" else "false"}\""

internal fun entangle(path: String): String {
    val safe = if (path.matches(Regex("[A-Za-z0-9_.]+"))) path else ""
    val directive = if (safe.isEmpty()) "" else "@entangle($safe)"
    return """<span class="ff-entangle" x-data="ffField" data-ff-path="${esc(path)}">$directive</span>"""
}

internal fun textTag(tag: String, css: String, text: String?): String {
    if (text == null || text.isEmpty()) return ""
    return """<$tag class="$css">${esc(text)}</$tag>"""
}
