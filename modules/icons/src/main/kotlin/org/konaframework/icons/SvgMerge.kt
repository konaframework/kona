package org.konaframework.icons

internal fun mergeSvgAttributes(contents: String, attributes: Map<String, String>): String {
    val trimmed = contents.trim()
    if (!trimmed.startsWith("<svg", ignoreCase = true)) {
        throw IconNotFoundException("SVG root missing")
    }
    val openEnd = trimmed.indexOf('>')
    if (openEnd <= 0) {
        throw IconNotFoundException("SVG root missing")
    }
    val openTag = trimmed.substring(0, openEnd)
    val rest = trimmed.substring(openEnd)
    val existing = parseSvgOpenAttributes(openTag)
    val merged = LinkedHashMap(existing)
    for ((key, value) in attributes) {
        if (key.equals("class", ignoreCase = true)) {
            val prior = merged["class"]
            merged["class"] = if (prior.isNullOrBlank()) value else "$prior $value".trim()
        } else {
            merged[key] = value
        }
    }
    val attrText = merged.entries.joinToString(" ") { (key, value) ->
        """$key="${escapeHtmlAttr(value)}""""
    }
    return if (attrText.isEmpty()) "<svg$rest" else "<svg $attrText$rest"
}

internal fun parseSvgOpenAttributes(openTag: String): LinkedHashMap<String, String> {
    val result = LinkedHashMap<String, String>()
    val doubleQuoted = Regex("""([\w:-]+)\s*=\s*"([^"]*)"""")
    for (match in doubleQuoted.findAll(openTag)) {
        result[match.groupValues[1]] = match.groupValues[2]
    }
    val singleQuoted = Regex("""([\w:-]+)\s*=\s*'([^']*)'""")
    for (match in singleQuoted.findAll(openTag)) {
        result.putIfAbsent(match.groupValues[1], match.groupValues[2])
    }
    return result
}

internal fun escapeHtmlAttr(value: String): String {
    val out = StringBuilder(value.length)
    for (ch in value) {
        when (ch) {
            '&' -> out.append("&amp;")
            '"' -> out.append("&quot;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(ch)
        }
    }
    return out.toString()
}
