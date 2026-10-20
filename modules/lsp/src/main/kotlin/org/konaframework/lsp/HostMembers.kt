package org.konaframework.lsp

/**
 * Public fields / properties / methods of the enclosing class, scanned from
 * Java or Kotlin source text (no PSI). Used for `{{` completions inside inline
 * Brew templates.
 */
object HostMembers {
    data class Member(
        val name: String,
        val method: Boolean,
    )

    data class ClassSpan(
        val name: String,
        val keywordStart: Int,
        val bodyStart: Int,
        val bodyEnd: Int,
    )

    private val SKIP_NAMES =
        setOf(
            "template",
            "render",
            "class",
            "interface",
            "enum",
            "object",
            "if",
            "for",
            "while",
            "switch",
            "catch",
            "return",
            "new",
            "else",
            "try",
            "do",
            "when",
            "fun",
            "var",
            "val",
            "void",
        )

    @JvmStatic
    fun members(text: String, offset: Int): List<Member> {
        val cls = enclosing(text, offset) ?: return emptyList()
        val nested =
            classSpans(text).filter { span ->
                if (span.keywordStart > cls.keywordStart) {
                    span.bodyEnd <= cls.bodyEnd
                } else {
                    false
                }
            }
        val from = cls.bodyStart.coerceIn(0, text.length)
        val to = (cls.bodyEnd + 1).coerceIn(from, text.length)
        val raw = text.substring(from, to)
        val masked = StringBuilder(raw)
        for (n in nested) {
            val a = (n.keywordStart - cls.bodyStart).coerceAtLeast(0)
            val b = (n.bodyEnd - cls.bodyStart).coerceAtMost(raw.length - 1)
            val until = (b + 1).coerceAtMost(masked.length)
            for (k in a until until) {
                val ch = masked[k]
                if (ch == '\n') {
                    masked[k] = '\n'
                } else {
                    masked[k] = ' '
                }
            }
        }
        return parseMembers(masked.toString(), cls.name)
    }

    @JvmStatic
    fun complete(text: String, offset: Int, prefix: String): List<Member> {
        val p = prefix.lowercase()
        val all = members(text, offset)
        if (p.isEmpty()) {
            return all
        }
        return all.filter { it.name.lowercase().startsWith(p) }
    }

    @JvmStatic
    fun enclosing(text: String, offset: Int): ClassSpan? =
        classSpans(text)
            .filter { span ->
                if (offset < span.bodyStart) {
                    false
                } else {
                    offset <= span.bodyEnd + 1
                }
            }
            .maxByOrNull { it.bodyStart }

    @JvmStatic
    fun classSpans(text: String): List<ClassSpan> {
        val out = mutableListOf<ClassSpan>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                i = SourceScan.skipStringOrChar(text, i)
                continue
            }
            if (text.startsWith("//", i) || text.startsWith("/*", i)) {
                i = SourceScan.readComment(text, i).second
                continue
            }
            if (SourceScan.isIdentifierStart(c)) {
                val ident = SourceScan.readIdentifier(text, i)!!
                if (ident.first == "class" || ident.first == "object" || ident.first == "interface") {
                    var at = SourceScan.skipTrivia(text, ident.second)
                    val name = SourceScan.readIdentifier(text, at)
                    if (name != null) {
                        at = SourceScan.skipTrivia(text, name.second)
                        at = SourceScan.skipHeritage(text, at)
                        if (at < text.length) {
                            if (text[at] == '{') {
                                val close = SourceScan.skipBalanced(text, at, '{', '}')
                                out +=
                                    ClassSpan(
                                        name = name.first,
                                        keywordStart = i,
                                        bodyStart = at + 1,
                                        bodyEnd = (close - 1).coerceAtLeast(at),
                                    )
                            }
                        }
                    }
                }
                i = ident.second
                continue
            }
            i++
        }
        return out
    }

    private val KOTLIN_PROP = Regex("""\b(?:var|val)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    private val KOTLIN_FUN =
        Regex(
            """\b(?:override\s+|open\s+|private\s+|protected\s+|internal\s+|public\s+)*fun\s+(?:<[^>\n]*>\s*)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
        )
    private val JAVA_MEMBER =
        Regex(
            """\b(?:public|protected)\s+(?:static\s+|final\s+|synchronized\s+|native\s+|abstract\s+|default\s+)*(?:[\w.<>,\[\]?]+\s+)+([A-Za-z_][A-Za-z0-9_]*)\s*([;(=(])""",
        )

    private fun parseMembers(body: String, className: String): List<Member> {
        val out = linkedMapOf<String, Member>()
        KOTLIN_PROP.findAll(body).forEach { m ->
            val name = m.groupValues[1]
            if (name !in SKIP_NAMES) {
                out.putIfAbsent(name, Member(name, false))
            }
        }
        KOTLIN_FUN.findAll(body).forEach { m ->
            val name = m.groupValues[1]
            if (name !in SKIP_NAMES && name != className) {
                out[name] = Member(name, true)
            }
        }
        JAVA_MEMBER.findAll(body).forEach { m ->
            val name = m.groupValues[1]
            if (name in SKIP_NAMES || name == className) {
                return@forEach
            }
            val method = m.groupValues[2] == "("
            out.putIfAbsent(name, Member(name, method))
        }
        return out.values.toList()
    }
}
