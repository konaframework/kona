package org.konaframework.lsp

/**
 * Lightweight Crema scan for completions and unknown-directive diagnostics.
 * Not a full parser — enough for editor feedback on `*.crema.html` / `*.crema.kt`.
 * Also offers Brew `wire:*` attribute completions and unknown-attribute warnings.
 */
object CremaAnalyzer {
    private val DIRECTIVE =
        Regex("""(?<![\w@])@([A-Za-z_][A-Za-z0-9_]*)""")

    private val COMMENT_BLOCKS =
        Regex("""\{\{--[\s\S]*?--\}\}""")

    data class Position(val line: Int, val character: Int)

    data class Range(
        val start: Position,
        val end: Position,
    )

    data class Diagnostic(
        val range: Range,
        val message: String,
        val severity: Int = 2, // Warning
        val source: String = "crema",
    )

    data class CompletionItem(
        val label: String,
        val kind: Int = 14, // Keyword
        val detail: String = "Crema directive",
        val insertText: String,
    )

    @JvmStatic
    fun diagnostics(text: String): List<Diagnostic> {
        val withoutComments =
            COMMENT_BLOCKS.replace(text) { match ->
                " ".repeat(match.value.length)
            }
        val out = mutableListOf<Diagnostic>()
        DIRECTIVE.findAll(withoutComments).forEach { match ->
            val name = match.groupValues[1]
            if (!CremaDirectives.isKnown(name)) {
                val start = offsetToPosition(text, match.range.first)
                val end = offsetToPosition(text, match.range.last + 1)
                out +=
                    Diagnostic(
                        range = Range(start, end),
                        message = "Unknown Crema directive @$name",
                    )
            }
        }
        BrewWirePatterns.findUnknown(withoutComments).forEach { hit ->
            val start = offsetToPosition(text, hit.start)
            val end = offsetToPosition(text, hit.end)
            out +=
                Diagnostic(
                    range = Range(start, end),
                    message = "Unknown Brew attribute ${hit.name}",
                    source = "brew",
                )
        }
        return out
    }

    /**
     * Completions at a caret. Prefer Brew `wire:*` when typing an attribute;
     * otherwise echo helpers/variables inside `{{ }}` / `{!! !!}`; otherwise
     * Crema `@` directives (filtered by prefix when inside `@name`).
     */
    @JvmStatic
    fun completions(text: String, line: Int, character: Int): List<CompletionItem> {
        val offset = positionToOffset(text, line, character)
        val wirePrefix = BrewWirePatterns.attributePrefixAt(text, offset)
        if (wirePrefix != null) {
            return BrewWireAttributes.completionsForPrefix(wirePrefix).map { label ->
                CompletionItem(
                    label = label,
                    kind = 5, // Field
                    detail = "Brew wire",
                    insertText = label,
                )
            }
        }
        val echoPrefix = echoPrefixAt(text, offset)
        if (echoPrefix != null) {
            return CremaEchoCatalog.completions(echoPrefix).map { item ->
                CompletionItem(
                    label = item.name,
                    kind = if (item.helper) 3 else 6,
                    detail = item.detail,
                    insertText = item.insertText,
                )
            }
        }
        val context = directiveContextAt(text, offset)
        val names =
            if (context == null) {
                CremaDirectives.KNOWN.sorted()
            } else {
                val typed = context.prefix.lowercase()
                CremaDirectives.KNOWN.filter { it.startsWith(typed) }.sorted()
            }
        return names.map { name ->
            val insert = if (context != null) name else "@$name"
            CompletionItem(label = "@$name", insertText = insert)
        }
    }

    data class DirectiveContext(val prefix: String)

    /**
     * When the caret is inside `{{ … }}` or `{!! … !!}`, returns the identifier
     * prefix being typed (may be empty).
     */
    @JvmStatic
    fun echoPrefixAt(text: String, offset: Int): String? {
        val cap = offset.coerceIn(0, text.length)
        val openEcho = lastUnclosed(text, cap, "{{", "}}")
        val openRaw = lastUnclosed(text, cap, "{!!", "!!}")
        val open = listOfNotNull(openEcho, openRaw).maxOrNull() ?: return null
        val contentStart = if (text.startsWith("{!!", open)) open + 3 else open + 2
        var i = cap - 1
        while (i >= contentStart) {
            val ch = text[i]
            if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '$') {
                i--
                continue
            }
            break
        }
        return text.substring(i + 1, cap)
    }

    private fun lastUnclosed(text: String, offset: Int, open: String, close: String): Int? {
        var i = 0
        var last: Int? = null
        while (i < offset) {
            if (text.startsWith(open, i)) {
                last = i
                i += open.length
                continue
            }
            if (text.startsWith(close, i)) {
                last = null
                i += close.length
                continue
            }
            i++
        }
        return last
    }

    /** When the caret is inside `@…`, returns the typed name prefix (may be empty). */
    internal fun directiveContextAt(text: String, offset: Int): DirectiveContext? {
        if (offset <= 0) {
            return null
        }
        var i = offset - 1
        while (i >= 0) {
            val ch = text[i]
            val partOfName = ch.isLetterOrDigit() || ch == '_'
            if (!partOfName) {
                break
            }
            i--
        }
        if (i < 0) {
            return null
        }
        if (text[i] != '@') {
            return null
        }
        return DirectiveContext(prefix = text.substring(i + 1, offset))
    }

    internal fun offsetToPosition(text: String, offset: Int): Position {
        var line = 0
        var character = 0
        var i = 0
        val capped = offset.coerceIn(0, text.length)
        while (i < capped) {
            if (text[i] == '\n') {
                line++
                character = 0
            } else {
                character++
            }
            i++
        }
        return Position(line, character)
    }

    internal fun positionToOffset(text: String, line: Int, character: Int): Int {
        var currentLine = 0
        var i = 0
        while (i < text.length && currentLine < line) {
            if (text[i] == '\n') {
                currentLine++
            }
            i++
        }
        return (i + character).coerceIn(0, text.length)
    }
}
