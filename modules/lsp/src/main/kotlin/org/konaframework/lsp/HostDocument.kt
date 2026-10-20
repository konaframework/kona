package org.konaframework.lsp

/**
 * Host-language routing for kona-lsp: Crema files are scanned whole;
 * Java/Kotlin buffers only contribute the inline Crema string ranges.
 */
object HostDocument {
    @JvmStatic
    fun languageId(declared: String, uri: String): String {
        if (declared.isNotEmpty()) {
            return declared
        }
        val path =
            uri.substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
        return when {
            path.endsWith(".crema.html", ignoreCase = true) -> "crema"
            path.endsWith(".crema.kt", ignoreCase = true) -> "crema"
            path.endsWith(".java", ignoreCase = true) -> "java"
            path.endsWith(".kts", ignoreCase = true) -> "kotlinscript"
            path.endsWith(".kt", ignoreCase = true) -> "kotlin"
            else -> ""
        }
    }

    @JvmStatic
    fun analyzeWholeFile(languageId: String, uri: String): Boolean {
        val resolved = languageId(languageId, uri).lowercase()
        val u = uri.lowercase()
        if (u.endsWith(".crema.kt") || u.contains(".crema.html")) {
            return true
        }
        if (resolved == "java" || resolved == "kotlin" || resolved == "kotlinscript") {
            return false
        }
        return true
    }

    @JvmStatic
    fun completions(
        text: String,
        languageId: String,
        uri: String,
        line: Int,
        character: Int,
    ): List<CremaAnalyzer.CompletionItem> {
        if (analyzeWholeFile(languageId, uri)) {
            val offset = CremaAnalyzer.positionToOffset(text, line, character)
            return withHostMembers(text, offset, text, offset, CremaAnalyzer.completions(text, line, character))
        }
        val offset = CremaAnalyzer.positionToOffset(text, line, character)
        val range = InlineCremaRanges.containing(text, offset) ?: return emptyList()
        val excerpt = text.substring(range.contentStart, range.contentEnd.coerceAtMost(text.length))
        val local = (offset - range.contentStart).coerceIn(0, excerpt.length)
        val pos = CremaAnalyzer.offsetToPosition(excerpt, local)
        val items = CremaAnalyzer.completions(excerpt, pos.line, pos.character)
        return withHostMembers(excerpt, local, text, range.contentStart + local, items)
    }

    @JvmStatic
    fun diagnostics(text: String, languageId: String, uri: String): List<CremaAnalyzer.Diagnostic> {
        if (analyzeWholeFile(languageId, uri)) {
            return CremaAnalyzer.diagnostics(text)
        }
        val out = mutableListOf<CremaAnalyzer.Diagnostic>()
        for (range in InlineCremaRanges.find(text)) {
            val excerpt = text.substring(range.contentStart, range.contentEnd.coerceAtMost(text.length))
            for (d in CremaAnalyzer.diagnostics(excerpt)) {
                out += remap(d, text, range)
            }
        }
        return out
    }

    private fun withHostMembers(
        excerpt: String,
        excerptOffset: Int,
        hostText: String,
        hostOffset: Int,
        items: List<CremaAnalyzer.CompletionItem>,
    ): List<CremaAnalyzer.CompletionItem> {
        val echo = CremaAnalyzer.echoPrefixAt(excerpt, excerptOffset) ?: return items
        val members = HostMembers.complete(hostText, hostOffset, echo)
        if (members.isEmpty()) {
            return items
        }
        return members.map { member ->
            CremaAnalyzer.CompletionItem(
                label = member.name,
                kind =
                    if (member.method) {
                        2
                    } else {
                        6
                    },
                detail = "Brew member",
                insertText = member.name,
            )
        }
    }

    private fun remap(
        d: CremaAnalyzer.Diagnostic,
        hostText: String,
        range: InlineCremaRanges.Range,
    ): CremaAnalyzer.Diagnostic {
        val excerptEnd = range.contentEnd.coerceAtMost(hostText.length)
        val excerpt = hostText.substring(range.contentStart, excerptEnd)
        val startOff =
            CremaAnalyzer.positionToOffset(excerpt, d.range.start.line, d.range.start.character)
        val endOff = CremaAnalyzer.positionToOffset(excerpt, d.range.end.line, d.range.end.character)
        return d.copy(
            range =
                CremaAnalyzer.Range(
                    start = CremaAnalyzer.offsetToPosition(hostText, range.contentStart + startOff),
                    end = CremaAnalyzer.offsetToPosition(hostText, range.contentStart + endOff),
                ),
        )
    }
}
