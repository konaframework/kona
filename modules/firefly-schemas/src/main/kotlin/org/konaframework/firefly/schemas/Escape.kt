package org.konaframework.firefly.schemas

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

internal fun isTruthy(value: Any?): Boolean {
    if (value == null) return false
    if (value is Boolean) return value
    if (value is String) return value.isNotEmpty()
    if (value is Number) return value.toDouble() != 0.0
    if (value is Collection<*>) return value.isNotEmpty()
    return true
}
