package org.konaframework.lsp

/**
 * Detects Brew `wire:…` attribute typing / attribute spans in Crema HTML.
 * Mirrors the IDEA plugin ([kona-idea] BrewWirePatterns).
 */
object BrewWirePatterns {
    data class AttrHit(
        val name: String,
        val start: Int,
        val end: Int,
    )

    /**
     * Prefix of a `wire:` attribute being typed at [offset]
     * (e.g. `wire:mo` inside `<input wire:mo|`).
     */
    @JvmStatic
    fun attributePrefixAt(text: CharSequence, offset: Int): String? {
        if (offset < 0 || offset > text.length) {
            return null
        }
        var i = offset - 1
        while (i >= 0 && isAttrNamePart(text[i])) {
            i--
        }
        val start = i + 1
        if (start >= offset) {
            return null
        }
        val prefix = text.subSequence(start, offset).toString()
        if (!looksLikeAttrStart(text, start)) {
            return null
        }
        if (!insideOpenTag(text, start)) {
            return null
        }
        val lower = prefix.lowercase()
        // Full `wire…` or a stem (`w` / `wi` / `wir`) while typing inside a tag
        if (lower.startsWith("wire") || "wire".startsWith(lower)) {
            return prefix
        }
        return null
    }

    @JvmStatic
    fun findAll(text: CharSequence): List<AttrHit> {
        val out = mutableListOf<AttrHit>()
        var i = 0
        while (i < text.length) {
            if (regionMatches(text, i, "wire:")) {
                val start = i
                var j = i + "wire:".length
                while (j < text.length && isAttrNamePart(text[j])) {
                    j++
                }
                if (j > i + "wire:".length) {
                    out += AttrHit(text.subSequence(start, j).toString(), start, j)
                }
                i = j
            } else {
                i++
            }
        }
        return out
    }

    @JvmStatic
    fun findUnknown(text: CharSequence): List<AttrHit> =
        findAll(text).filter { !BrewWireAttributes.isKnownAttribute(it.name) }

    @JvmStatic
    fun isAttrNamePart(c: Char): Boolean =
        c == ':' || c == '.' || c == '-' || c == '_' || c.isLetterOrDigit()

    private fun looksLikeAttrStart(text: CharSequence, start: Int): Boolean {
        if (start == 0) {
            return false
        }
        val prev = text[start - 1]
        if (prev == '<' || prev == '"' || prev == '\'') {
            return true
        }
        return prev.isWhitespace()
    }

    private fun insideOpenTag(text: CharSequence, offset: Int): Boolean {
        var i = offset - 1
        var sawOpen = false
        while (i >= 0) {
            val c = text[i]
            if (c == '>') {
                return false
            }
            if (c == '<') {
                sawOpen = true
                break
            }
            i--
        }
        if (!sawOpen) {
            return false
        }
        var j = offset
        while (j < text.length) {
            val c = text[j]
            if (c == '>') {
                return true
            }
            if (c == '<') {
                return false
            }
            j++
        }
        return true
    }

    private fun regionMatches(text: CharSequence, offset: Int, needle: String): Boolean {
        if (offset + needle.length > text.length) {
            return false
        }
        for (k in needle.indices) {
            if (text[offset + k].lowercaseChar() != needle[k].lowercaseChar()) {
                return false
            }
        }
        return true
    }
}
