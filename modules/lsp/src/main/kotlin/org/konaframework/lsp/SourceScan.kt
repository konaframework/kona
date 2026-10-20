package org.konaframework.lsp

/**
 * Comment / string / identifier walking for Java and Kotlin source text.
 * Not a parser — enough to find inline Crema strings without a PSI.
 */
internal object SourceScan {
    data class StringSpan(
        val openStart: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val closeEnd: Int,
        val triple: Boolean,
    )

    fun skipTrivia(text: String, start: Int): Int {
        var i = start.coerceAtLeast(0)
        while (i < text.length) {
            val c = text[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c == '/') {
                if (i + 1 < text.length) {
                    if (text[i + 1] == '/') {
                        i += 2
                        while (i < text.length) {
                            if (text[i] == '\n') {
                                break
                            }
                            i++
                        }
                        continue
                    }
                    if (text[i + 1] == '*') {
                        i += 2
                        while (true) {
                            if (i + 1 >= text.length) {
                                i = text.length
                                break
                            }
                            if (text[i] == '*') {
                                if (text[i + 1] == '/') {
                                    i += 2
                                    break
                                }
                            }
                            i++
                        }
                        continue
                    }
                }
            }
            break
        }
        return i
    }

    /** Index of the last non-trivia character before [exclusiveEnd], or -1. */
    fun skipTriviaBack(text: String, exclusiveEnd: Int): Int {
        var i = exclusiveEnd - 1
        if (i >= text.length) {
            i = text.length - 1
        }
        while (i >= 0) {
            val c = text[i]
            if (c.isWhitespace()) {
                i--
                continue
            }
            if (c == '/') {
                if (i > 0) {
                    if (text[i - 1] == '*') {
                        val open = findBlockCommentOpen(text, i - 2)
                        if (open >= 0) {
                            i = open - 1
                            continue
                        }
                        return i
                    }
                }
            }
            val lineStart = text.lastIndexOf('\n', i).let { nl -> if (nl < 0) 0 else nl + 1 }
            val slashes = text.indexOf("//", lineStart)
            if (slashes >= 0 && slashes < i) {
                i = slashes - 1
                continue
            }
            return i
        }
        return -1
    }

    fun parseString(text: String, start: Int): StringSpan? {
        if (start < 0 || start >= text.length) {
            return null
        }
        if (text[start] != '"') {
            return null
        }
        val triple = start + 2 < text.length && text[start + 1] == '"' && text[start + 2] == '"'
        if (triple) {
            val contentStart = start + 3
            var i = contentStart
            while (i < text.length) {
                if (text.startsWith("\"\"\"", i)) {
                    return StringSpan(start, contentStart, i, i + 3, true)
                }
                i++
            }
            return StringSpan(start, contentStart, text.length, text.length, true)
        }
        val contentStart = start + 1
        var i = contentStart
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                i += 1
                if (i < text.length) {
                    i += 1
                }
                continue
            }
            if (c == '"') {
                return StringSpan(start, contentStart, i, i + 1, false)
            }
            if (c == '\n') {
                return StringSpan(start, contentStart, i, i, false)
            }
            i++
        }
        return StringSpan(start, contentStart, text.length, text.length, false)
    }

    fun skipStringOrChar(text: String, start: Int): Int {
        if (start >= text.length) {
            return start
        }
        if (text[start] == '\'') {
            var i = start + 1
            if (i >= text.length) {
                return i
            }
            if (text[i] == '\\') {
                i += 1
                if (i < text.length) {
                    i += 1
                }
            } else {
                i += 1
            }
            if (i < text.length) {
                if (text[i] == '\'') {
                    i += 1
                }
            }
            return i
        }
        val str = parseString(text, start)
        if (str != null) {
            return str.closeEnd
        }
        return start + 1
    }

    fun readComment(text: String, start: Int): Pair<Boolean, Int> {
        if (text.startsWith("//", start)) {
            var i = start + 2
            while (i < text.length && text[i] != '\n') {
                i++
            }
            return LANGUAGE_CREMA.containsMatchIn(text.substring(start, i)) to i
        }
        if (text.startsWith("/*", start)) {
            val close = text.indexOf("*/", start + 2)
            val end = if (close < 0) text.length else close + 2
            return LANGUAGE_CREMA.containsMatchIn(text.substring(start, end)) to end
        }
        return false to start + 1
    }

    fun isIdentifierStart(c: Char): Boolean = c == '_' || c.isLetter()

    fun isIdentifierPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    fun readIdentifier(text: String, start: Int): Pair<String, Int>? {
        if (start < 0 || start >= text.length) {
            return null
        }
        if (!isIdentifierStart(text[start])) {
            return null
        }
        var i = start + 1
        while (i < text.length && isIdentifierPart(text[i])) {
            i++
        }
        return text.substring(start, i) to i
    }

    fun skipBalanced(text: String, openAt: Int, open: Char, close: Char): Int {
        var i = openAt
        if (i >= text.length) {
            return i
        }
        if (text[i] != open) {
            return i
        }
        var depth = 1
        i++
        while (i < text.length && depth > 0) {
            if (text[i] == '"') {
                i = skipStringOrChar(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipStringOrChar(text, i)
                continue
            }
            if (text.startsWith("//", i)) {
                i = readComment(text, i).second
                continue
            }
            if (text.startsWith("/*", i)) {
                i = readComment(text, i).second
                continue
            }
            if (text[i] == open) {
                depth++
            } else if (text[i] == close) {
                depth--
            }
            i++
        }
        return i
    }

    fun skipType(text: String, start: Int): Int {
        var i = start
        if (i >= text.length) {
            return i
        }
        if (text[i] == '(') {
            i = skipBalanced(text, i, '(', ')')
            i = skipTrivia(text, i)
            if (i + 1 < text.length) {
                if (text[i] == '-') {
                    if (text[i + 1] == '>') {
                        return skipType(text, skipTrivia(text, i + 2))
                    }
                }
            }
            return i
        }
        while (i < text.length) {
            if (isIdentifierPart(text[i])) {
                i++
                continue
            }
            if (text[i] == '.') {
                i++
                continue
            }
            break
        }
        i = skipTrivia(text, i)
        if (i < text.length) {
            if (text[i] == '<') {
                i = skipBalanced(text, i, '<', '>')
                i = skipTrivia(text, i)
            }
        }
        if (i < text.length) {
            if (text[i] == '?') {
                i++
            }
        }
        return i
    }

    fun skipHeritage(text: String, start: Int): Int {
        var i = start
        var paren = 0
        while (i < text.length) {
            if (paren == 0) {
                if (text[i] == '{') {
                    return i
                }
                if (text[i] == ';') {
                    return i
                }
            }
            if (text[i] == '"') {
                i = skipStringOrChar(text, i)
                continue
            }
            if (text[i] == '\'') {
                i = skipStringOrChar(text, i)
                continue
            }
            if (text.startsWith("//", i)) {
                i = readComment(text, i).second
                continue
            }
            if (text.startsWith("/*", i)) {
                i = readComment(text, i).second
                continue
            }
            if (text[i] == '(') {
                paren++
            } else if (text[i] == ')') {
                paren--
            }
            i++
        }
        return i
    }

    private fun findBlockCommentOpen(text: String, from: Int): Int {
        var j = from
        while (j > 0) {
            if (text[j] == '*') {
                if (text[j - 1] == '/') {
                    return j - 1
                }
            }
            j--
        }
        return -1
    }

    private val LANGUAGE_CREMA = Regex("""(?i)language\s*=\s*Crema\b""")
}
