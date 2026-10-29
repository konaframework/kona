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

private val HTML_ATTRIBUTE = Regex("^[A-Za-z_:][A-Za-z0-9_:.-]*$")

/** Renders a map as HTML attributes. Names that are not HTML attribute names are skipped. */
fun htmlAttributes(values: Map<String, String>?): String {
    if (values == null || values.isEmpty()) return ""
    val out = StringBuilder()
    for ((key, value) in values) {
        if (!HTML_ATTRIBUTE.matches(key)) continue
        out.append(' ')
        out.append(key)
        out.append("=\"")
        out.append(escapeHtml(value))
        out.append('"')
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

/** Turn a field name into a title-case label: `first_name` → `First Name`. */
fun humanizeName(name: String): String {
    if (name.isBlank()) return name
    val spaced = name
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace(Regex("[._\\-]+"), " ")
        .trim()
    if (spaced.isEmpty()) return name
    val out = StringBuilder()
    for (part in spaced.split(Regex("\\s+"))) {
        if (out.isNotEmpty()) out.append(' ')
        val first = part[0]
        if (first.isLowerCase()) {
            out.append(first.titlecase())
        } else {
            out.append(first)
        }
        if (part.length > 1) out.append(part, 1, part.length)
    }
    return out.toString()
}
