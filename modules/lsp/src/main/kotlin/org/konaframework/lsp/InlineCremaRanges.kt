package org.konaframework.lsp

/**
 * Finds Crema markup embedded in Java/Kotlin host source.
 *
 * Heuristics: `template()` method return/assignment strings, first argument of
 * `inline(`, and strings preceded by `// language=Crema` or `/* language=Crema */`.
 */
object InlineCremaRanges {
    data class Range(
        val contentStart: Int,
        val contentEnd: Int,
    ) {
        fun contains(offset: Int): Boolean {
            if (offset < contentStart) {
                return false
            }
            return offset <= contentEnd
        }
    }

    @JvmStatic
    fun find(text: String): List<Range> {
        val out = linkedSetOf<Range>()
        out += fromLanguageComments(text)
        out += fromInlineCalls(text)
        out += fromTemplateMethods(text)
        return out.sortedBy { it.contentStart }
    }

    @JvmStatic
    fun containing(text: String, offset: Int): Range? =
        find(text)
            .filter { it.contains(offset) }
            .minByOrNull { it.contentEnd - it.contentStart }

    private fun fromLanguageComments(text: String): List<Range> {
        val out = mutableListOf<Range>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                i = SourceScan.skipStringOrChar(text, i)
                continue
            }
            if (text.startsWith("//", i) || text.startsWith("/*", i)) {
                val (isCrema, end) = SourceScan.readComment(text, i)
                if (isCrema) {
                    val str = stringAfterLanguageComment(text, end)
                    if (str != null) {
                        out += Range(str.contentStart, str.contentEnd)
                    }
                }
                i = end
                continue
            }
            i++
        }
        return out
    }

    private fun fromInlineCalls(text: String): List<Range> {
        val out = mutableListOf<Range>()
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
                if (ident.first == "inline") {
                    val after = SourceScan.skipTrivia(text, ident.second)
                    if (after < text.length) {
                        if (text[after] == '(') {
                            val arg = SourceScan.skipTrivia(text, after + 1)
                            val str = SourceScan.parseString(text, arg)
                            if (str != null) {
                                out += Range(str.contentStart, str.contentEnd)
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

    private fun fromTemplateMethods(text: String): List<Range> {
        val out = mutableListOf<Range>()
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
                if (ident.first == "template") {
                    val afterName = SourceScan.skipTrivia(text, ident.second)
                    if (afterName < text.length) {
                        if (text[afterName] == '(') {
                            val afterOpen = SourceScan.skipTrivia(text, afterName + 1)
                            if (afterOpen < text.length) {
                                if (text[afterOpen] == ')') {
                                    if (isTemplateDeclaration(text, i)) {
                                        collectTemplateString(text, afterOpen + 1, out)
                                    }
                                }
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

    private fun stringAfterLanguageComment(text: String, start: Int): SourceScan.StringSpan? {
        val cap = (start + 160).coerceAtMost(text.length)
        var i = start
        while (i < cap) {
            i = SourceScan.skipTrivia(text, i)
            if (i >= cap) {
                break
            }
            if (text[i] == '"' ) {
                return SourceScan.parseString(text, i)
            }
            if (text[i] == '\'' ) {
                i = SourceScan.skipStringOrChar(text, i)
                continue
            }
            if (text[i] == '{') {
                break
            }
            if (text[i] == ';') {
                break
            }
            i++
        }
        return null
    }

    private val DECL_PREFIX =
        setOf(
            "fun",
            "override",
            "open",
            "public",
            "protected",
            "private",
            "internal",
            "final",
            "abstract",
            "String",
        )

    private val CALL_PREFIX = setOf("return", "if", "while", "when", "for", "else")

    internal fun isTemplateDeclaration(text: String, nameStart: Int): Boolean {
        val prev = SourceScan.skipTriviaBack(text, nameStart)
        if (prev < 0) {
            return false
        }
        if (text[prev] == '.') {
            return false
        }
        if (text[prev] == ')') {
            return true
        }
        if (text[prev] == '?') {
            return true
        }
        if (text[prev] == '>') {
            return true
        }
        var j = prev
        while (j >= 0 && SourceScan.isIdentifierPart(text[j])) {
            j--
        }
        val ident = text.substring(j + 1, prev + 1)
        if (ident in CALL_PREFIX) {
            return false
        }
        if (ident in DECL_PREFIX) {
            return true
        }
        if (ident.isNotEmpty()) {
            if (ident[0].isUpperCase()) {
                return true
            }
        }
        return false
    }

    private fun collectTemplateString(text: String, afterCloseParen: Int, out: MutableList<Range>) {
        var at = SourceScan.skipTrivia(text, afterCloseParen)
        if (at < text.length) {
            if (text[at] == ':') {
                at = SourceScan.skipType(text, SourceScan.skipTrivia(text, at + 1))
                at = SourceScan.skipTrivia(text, at)
            }
        }
        if (at < text.length) {
            if (text[at] == '=') {
                at = SourceScan.skipTrivia(text, at + 1)
                val str = SourceScan.parseString(text, at)
                if (str != null) {
                    out += Range(str.contentStart, str.contentEnd)
                }
                return
            }
        }
        if (at < text.length) {
            if (text[at] == '{') {
                collectReturnStrings(text, at, out)
            }
        }
    }

    private fun collectReturnStrings(text: String, openBrace: Int, out: MutableList<Range>) {
        var i = openBrace + 1
        var depth = 1
        while (i < text.length && depth > 0) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                i = SourceScan.skipStringOrChar(text, i)
                continue
            }
            if (text.startsWith("//", i) || text.startsWith("/*", i)) {
                i = SourceScan.readComment(text, i).second
                continue
            }
            if (c == '{') {
                depth++
                i++
                continue
            }
            if (c == '}') {
                depth--
                i++
                continue
            }
            if (depth == 1 && SourceScan.isIdentifierStart(c)) {
                val ident = SourceScan.readIdentifier(text, i)!!
                if (ident.first == "return") {
                    val next = SourceScan.skipTrivia(text, ident.second)
                    val str = SourceScan.parseString(text, next)
                    if (str != null) {
                        out += Range(str.contentStart, str.contentEnd)
                        i = str.closeEnd
                        continue
                    }
                }
                i = ident.second
                continue
            }
            i++
        }
    }
}
