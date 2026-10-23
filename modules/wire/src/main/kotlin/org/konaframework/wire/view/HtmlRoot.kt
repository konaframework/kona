package org.konaframework.wire.view

import org.konaframework.wire.MultipleRootElementsException
import org.konaframework.wire.RootTagMissingFromViewException
import org.konaframework.wire.hydration.Coercion

/** Root-element helpers (`Utils::insertAttributesIntoHtmlRoot`). Single linear scans; no DOM parsing. */
object HtmlRoot {
    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )

    private val TAG_NAME_STOP = charArrayOf(' ', '\t', '\n', '\r', '>', '/')

    /** End index (exclusive) of the root tag name starting after [openAngle]'s `<`. */
    private fun tagNameEnd(html: String, openAngle: Int): Int {
        val from = openAngle + 1
        val stop = html.indexOfAny(TAG_NAME_STOP, from)
        return if (stop < 0) html.length else stop
    }

    /** Insert [attributes] right after the root tag name. Non-string values are JSON-encoded; all values are escaped. */
    @JvmStatic
    fun insertAttributes(html: String, attributes: Map<String, Any?>, component: String): String {
        val start = rootStart(html) ?: throw RootTagMissingFromViewException(component)
        val nameEnd = tagNameEnd(html, start)
        val rendered = StringBuilder()
        for ((key, value) in attributes) {
            val text = if (value is String) value else Coercion.mapper.writeValueAsString(value)
            rendered.append(' ').append(key).append("=\"").append(escape(text)).append('"')
        }
        return html.substring(0, nameEnd) + rendered + html.substring(nameEnd)
    }

    /** Throw when [html] has more than one top-level element (debug-mode check, like Livewire). */
    @JvmStatic
    fun assertSingleRoot(html: String, component: String) {
        if (topLevelElements(html) > 1) throw MultipleRootElementsException(component)
    }

    /** Lowercase tag name of the root element (`div` when missing). */
    @JvmStatic
    fun rootTagName(html: String): String {
        val start = rootStart(html) ?: return "div"
        return html.substring(start + 1, tagNameEnd(html, start)).lowercase()
    }

    /** Index of the first top-level element's `<`, skipping whitespace, comments and doctype. */
    @JvmStatic
    fun rootStart(html: String): Int? {
        var i = 0
        while (i < html.length) {
            val c = html[i]
            when {
                c.isWhitespace() -> i++
                html.startsWith("<!--", i) -> i = html.indexOf("-->", i).let { if (it < 0) return null else it + 3 }
                html.startsWith("<!", i) || html.startsWith("<?", i) -> i = html.indexOf('>', i).let { if (it < 0) return null else it + 1 }
                c == '<' && i + 1 < html.length && html[i + 1].isLetter() -> return i
                else -> return null
            }
        }
        return null
    }

    /** Count top-level elements (ignores comments, text-only whitespace, void/self-closing tags). */
    @JvmStatic
    fun topLevelElements(html: String): Int {
        var depth = 0
        var count = 0
        var i = 0
        while (i < html.length) {
            if (html[i] != '<') {
                i++
                continue
            }
            if (html.startsWith("<!--", i)) {
                val end = html.indexOf("-->", i)
                i = if (end < 0) html.length else end + 3
                continue
            }
            val close = html.startsWith("</", i)
            val tagEnd = tagEnd(html, i)
            if (tagEnd < 0) break
            val nameStart = if (close) i + 2 else i + 1
            var nameEnd = nameStart
            while (nameEnd < tagEnd && (html[nameEnd].isLetterOrDigit() || html[nameEnd] == '-' || html[nameEnd] == ':')) nameEnd++
            val name = html.substring(nameStart, nameEnd).lowercase()
            if (name.isEmpty()) {
                i = tagEnd + 1
                continue
            }
            when {
                close -> depth--
                html[tagEnd - 1] == '/' || name in VOID -> if (depth == 0) count++
                else -> {
                    if (depth == 0) count++
                    depth++
                    if (name == "script" || name == "style" || name == "textarea") {
                        val end = html.indexOf("</$name", tagEnd, ignoreCase = true)
                        if (end < 0) break
                        i = end
                        continue
                    }
                }
            }
            i = tagEnd + 1
        }
        return count
    }

    private fun tagEnd(html: String, from: Int): Int {
        var quote: Char? = null
        for (i in from until html.length) {
            val c = html[i]
            val q = quote
            if (q != null) {
                if (c == q) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
        }
        return -1
    }

    @JvmStatic
    fun escape(value: String): String {
        val out = StringBuilder(value.length + 16)
        for (c in value) {
            when (c) {
                '&' -> out.append("&amp;")
                '"' -> out.append("&quot;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '\'' -> out.append("&#039;")
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
