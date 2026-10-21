package org.konaframework.support.text

/**
 * Assemble scaffold files from independently indented pieces. Interpolating a
 * `trimIndent()` block into another `trimIndent()` string drops the outer
 * common indent to column 0 (malformed `vite.config.js`, Brew HTML, …).
 */
object SourceBlocks {
    /** Join already-written sections with a blank line; each block is `trimIndent`ed. */
    @JvmStatic
    fun join(vararg blocks: String): String {
        val parts =
            blocks.map { it.trimIndent().trimEnd() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return "\n"
        }
        return parts.joinToString("\n\n") + "\n"
    }

    /**
     * Replace a whole line containing [token] with [block], re-indented to that
     * line. Blank [block] removes the line.
     */
    @JvmStatic
    @JvmOverloads
    fun replaceLine(source: String, token: String, block: String? = null): String {
        val pattern = Regex("^[ \\t]*${Regex.escape(token)}\\r?\\n?", RegexOption.MULTILINE)
        if (block.isNullOrBlank()) {
            return source.replace(pattern, "")
        }
        val host =
            source.lineSequence().firstOrNull { it.contains(token) } ?: return source
        val indent = host.takeWhile { it == ' ' || it == '\t' }
        val body =
            block.trimIndent().lines().joinToString("\n") { line ->
                if (line.isEmpty()) {
                    line
                } else {
                    indent + line
                }
            }
        return source.replace(pattern, body + "\n")
    }
}
