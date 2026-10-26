package org.konaframework.firefly.forms

object MarkdownHtml {
    fun render(source: String): String {
        val text = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        val blocks = ArrayList<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index += 1
                continue
            }
            if (line.startsWith("```")) {
                val lang = line.removePrefix("```").trim()
                val body = ArrayList<String>()
                index += 1
                while (index < lines.size && !lines[index].startsWith("```")) {
                    body.add(lines[index])
                    index += 1
                }
                if (index < lines.size) index += 1
                val klass = if (lang.isEmpty()) "" else " class=\"language-${esc(lang)}\""
                blocks.add("<pre><code$klass>${esc(body.joinToString("\n"))}</code></pre>")
                continue
            }
            if (line == "---") {
                blocks.add("<hr />")
                index += 1
                continue
            }
            if (line.startsWith(">")) {
                val quote = ArrayList<String>()
                while (index < lines.size && lines[index].startsWith(">")) {
                    quote.add(lines[index].removePrefix(">").removePrefix(" ").trim())
                    index += 1
                }
                blocks.add("<blockquote>${inline(quote.joinToString(" "))}</blockquote>")
                continue
            }
            if (index + 1 < lines.size && line.contains('|') && isRule(lines[index + 1])) {
                val header = cells(line)
                index += 2
                val rows = ArrayList<List<String>>()
                while (index < lines.size && lines[index].contains('|')) {
                    rows.add(cells(lines[index]))
                    index += 1
                }
                blocks.add(table(header, rows))
                continue
            }
            if (isItem(line)) {
                val ordered = line.first().isDigit()
                val items = ArrayList<String>()
                while (index < lines.size && isItem(lines[index])) {
                    items.add(itemBody(lines[index]))
                    index += 1
                }
                val tag = if (ordered) "ol" else "ul"
                blocks.add("<$tag>${items.joinToString("") { it }}</$tag>")
                continue
            }
            val marks = headingMarks(line)
            if (marks > 0) {
                blocks.add("<h$marks>${inline(line.substring(marks + 1).trim())}</h$marks>")
                index += 1
                continue
            }
            val paragraph = ArrayList<String>()
            while (index < lines.size && lines[index].isNotBlank() && !lines[index].startsWith("```") && !isItem(lines[index]) && lines[index] != "---") {
                paragraph.add(lines[index])
                index += 1
            }
            blocks.add("<p>${inline(paragraph.joinToString(" "))}</p>")
        }
        return """<div class="ff-prose">${blocks.joinToString("")}</div>"""
    }

    fun inline(raw: String): String {
        val escaped = esc(raw)
        val codes = ArrayList<String>()
        var marked = CODE.replace(escaped) { match ->
            codes.add(match.groupValues[1])
            "\u0000${codes.size - 1}\u0000"
        }
        marked = IMAGE.replace(marked) { match -> image(match.groupValues[1], match.groupValues[2]) }
        marked = LINK.replace(marked) { match -> link(match.groupValues[1], match.groupValues[2]) }
        marked = AUTO.replace(marked) { match -> link(match.value, match.value) }
        marked = STRIKE.replace(marked) { match -> "<del>${match.groupValues[1]}</del>" }
        marked = BOLD.replace(marked) { match -> "<strong>${match.groupValues[1]}</strong>" }
        marked = EM.replace(marked) { match -> "<em>${match.groupValues[1]}</em>" }
        return CODE_SLOT.replace(marked) { match -> "<code>${codes[match.groupValues[1].toInt()]}</code>" }
    }

    private fun image(alt: String, url: String): String {
        val safe = safeUrl(url) ?: return esc(alt)
        return """<img src="$safe" alt="${esc(alt)}" />"""
    }

    private fun link(label: String, url: String): String {
        val safe = safeUrl(url) ?: return label
        return """<a href="$safe">$label</a>"""
    }

    private fun safeUrl(url: String): String? {
        val text = url.trim()
        val lower = text.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:") || lower.startsWith("/") || lower.startsWith("#")) {
            return esc(text)
        }
        return null
    }

    private fun isRule(line: String): Boolean = line.contains("---") && line.contains('|')

    private fun cells(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    private fun table(header: List<String>, rows: List<List<String>>): String = buildString {
        append("<table><thead><tr>")
        for (cell in header) append("<th>${inline(cell)}</th>")
        append("</tr></thead><tbody>")
        for (row in rows) {
            append("<tr>")
            for (cell in row) append("<td>${inline(cell)}</td>")
            append("</tr>")
        }
        append("</tbody></table>")
    }

    private fun headingMarks(line: String): Int {
        var marks = 0
        while (marks < line.length && line[marks] == '#') marks += 1
        if (marks < 1) return 0
        if (marks > 6) return 0
        if (marks >= line.length) return 0
        if (line[marks] != ' ') return 0
        return marks
    }

    private fun isItem(line: String): Boolean {
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) return true
        return line.length > 2 && line[0].isDigit() && line[1] == '.' && line[2] == ' '
    }

    private fun itemBody(line: String): String {
        val raw = if (line.length > 2 && line[0].isDigit()) line.substring(3) else line.drop(2)
        if (raw.startsWith("[ ] ")) return """<li class="ff-task"><input type="checkbox" disabled /> ${inline(raw.substring(4))}</li>"""
        if (raw.startsWith("[x] ") || raw.startsWith("[X] ")) return """<li class="ff-task"><input type="checkbox" disabled checked /> ${inline(raw.substring(4))}</li>"""
        return "<li>${inline(raw)}</li>"
    }

    private val CODE = Regex("`([^`]+)`")
    private val CODE_SLOT = Regex("\u0000(\\d+)\u0000")
    private val IMAGE = Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)")
    private val LINK = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
    private val AUTO = Regex("(?<![(\\[])https://[^\\s<\"]+")
    private val STRIKE = Regex("~~([^~]+)~~")
    private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
    private val EM = Regex("\\*([^*]+)\\*")
}

object HtmlSanitizer {
    fun clean(html: String): String {
        var text = SCRIPT.replace(html, "")
        text = STYLE.replace(text, "")
        text = IFRAME.replace(text, "")
        text = ON_ATTR.replace(text, "")
        text = JS_URL.replace(text, "")
        return text
    }

    private val SCRIPT = Regex("(?is)<script.*?>.*?</script>")
    private val STYLE = Regex("(?is)<style.*?>.*?</style>")
    private val IFRAME = Regex("(?is)<iframe.*?>.*?</iframe>")
    private val ON_ATTR = Regex("(?i)\\son[a-z]+=(\"[^\"]*\"|'[^']*')")
    private val JS_URL = Regex("(?i)javascript:")
}
