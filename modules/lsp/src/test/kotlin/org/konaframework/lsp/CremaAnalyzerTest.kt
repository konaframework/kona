package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CremaAnalyzerTest {
    @Test
    fun knownDirectivesAndCompletionsList() {
        assertTrue(CremaDirectives.isKnown("extends"))
        assertTrue(CremaDirectives.isKnown("csrf"))
        assertTrue(CremaDirectives.isKnown("asset"))
        assertFalse(CremaDirectives.isKnown("nope"))
        assertTrue(CremaDirectives.COMPLETION_LABELS.contains("@foreach"))
        assertEquals(CremaDirectives.KNOWN.size, CremaDirectives.COMPLETION_LABELS.size)
        assertTrue(BrewWireAttributes.BASE.contains("wire:model"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:click.prevent"))
        assertFalse(BrewWireAttributes.isKnownAttribute("wire:notARealThing"))
        assertTrue(BrewWireAttributes.completionsForPrefix("wire:mo").any { it == "wire:model" })
        assertEquals(
            BrewWireAttributes.COMPLETION_LABELS,
            BrewWireAttributes.completionsForPrefix("wire:"),
        )
        assertEquals(
            BrewWireAttributes.COMPLETION_LABELS,
            BrewWireAttributes.completionsForPrefix(""),
        )
        assertEquals(
            BrewWireAttributes.COMPLETION_LABELS,
            BrewWireAttributes.completionsForPrefix("wire"),
        )
    }

    @Test
    fun diagnosticsFlagsUnknownAndSkipsComments() {
        val text =
            """
            @extends('layouts.app')
            {{-- @fakeInComment --}}
            @section('content')
            @unknownThing
            <button wire:notReal="x">Go</button>
            {{-- <div wire:fakeInComment></div> --}}
            @endsection
            """.trimIndent()
        val diags = CremaAnalyzer.diagnostics(text)
        assertEquals(2, diags.size)
        assertTrue(diags.any { it.message.contains("@unknownThing") })
        assertTrue(diags.any { it.message.contains("wire:notReal") && it.source == "brew" })
        assertEquals(2, diags[0].severity)
        assertTrue(diags.any { it.source == "crema" })
        assertTrue(diags[0].range.start.line >= 0)
    }

    @Test
    fun completionsFilterByPrefixAfterAt() {
        val text = "@ex"
        val items = CremaAnalyzer.completions(text, 0, text.length)
        assertTrue(items.any { it.label == "@extends" })
        assertTrue(items.all { it.label.startsWith("@ex") || it.insertText.startsWith("ex") })
        assertEquals("extends", items.first { it.label == "@extends" }.insertText)
    }

    @Test
    fun completionsFullListWithoutAtContext() {
        val items = CremaAnalyzer.completions("hello", 0, 0)
        assertEquals(CremaDirectives.KNOWN.size, items.size)
        assertTrue(items.all { it.insertText.startsWith("@") })
    }

    @Test
    fun wireCompletionsInsideOpenTag() {
        val text = """<button wire:cli"""
        val items = CremaAnalyzer.completions(text, 0, text.length)
        assertTrue(items.any { it.label == "wire:click" })
        assertTrue(items.all { it.detail == "Brew wire" })
        assertEquals("wire:click", items.first { it.label == "wire:click" }.insertText)

        val short = """<input w"""
        val shortItems = CremaAnalyzer.completions(short, 0, short.length)
        assertTrue(shortItems.any { it.label.startsWith("wire:") })

        val afterColon = """<div wire:"""
        val colonItems = CremaAnalyzer.completions(afterColon, 0, afterColon.length)
        assertEquals(BrewWireAttributes.COMPLETION_LABELS.size, colonItems.size)
    }

    @Test
    fun brewWirePatternsEdgeCases() {
        assertNull(BrewWirePatterns.attributePrefixAt("", 0))
        assertNull(BrewWirePatterns.attributePrefixAt("wire:model", 10))
        assertNull(BrewWirePatterns.attributePrefixAt("x wire:mo", 9))
        assertEquals("wire:mo", BrewWirePatterns.attributePrefixAt("<a wire:mo", 10))
        assertEquals("wi", BrewWirePatterns.attributePrefixAt("<a wi", 5))
        assertNull(BrewWirePatterns.attributePrefixAt("text wi", 7))
        assertNull(BrewWirePatterns.attributePrefixAt("<a> wi", 6))
        // Closed tag then text — not inside an open tag
        assertNull(BrewWirePatterns.attributePrefixAt("> wire:mo", 9))
        // Nested open tag still completes on the inner tag
        assertEquals("w", BrewWirePatterns.attributePrefixAt("<a wire:mo><b w", 15))
        // Prefix that is not a wire: stem (hits return null after wire.startsWith check)
        assertNull(BrewWirePatterns.attributePrefixAt("<a wx", 5))
        // Attr start right after '<'
        assertEquals("wire:x", BrewWirePatterns.attributePrefixAt("<wire:x", 7))
        // Quote immediately before attr name (end of previous attribute value)
        assertEquals("wire:k", BrewWirePatterns.attributePrefixAt("""<a"wire:k""", 9))
        assertEquals("wire:k", BrewWirePatterns.attributePrefixAt("<a'wire:k", 9))
        // Forward scan hits another '<' before '>'
        assertNull(BrewWirePatterns.attributePrefixAt("<a wire:mo <b", 10))
        // Completed tag with '>' after caret region — walk forward finds '>'
        assertEquals("wire:model", BrewWirePatterns.attributePrefixAt("<x wire:model>", 13))
        assertTrue(BrewWirePatterns.isAttrNamePart('.'))
        assertTrue(BrewWirePatterns.isAttrNamePart(':'))
        assertTrue(BrewWirePatterns.isAttrNamePart('_'))
        assertTrue(BrewWirePatterns.isAttrNamePart('-'))
        assertTrue(BrewWirePatterns.isAttrNamePart('a'))
        assertTrue(BrewWirePatterns.isAttrNamePart('1'))
        assertFalse(BrewWirePatterns.isAttrNamePart(' '))
        assertFalse(BrewWirePatterns.isAttrNamePart('='))
        assertEquals(0, BrewWirePatterns.findAll("no wires here").size)
        assertEquals(1, BrewWirePatterns.findAll("<x wire:model>").size)
        // Bare `wire:` with no name → not a hit
        assertEquals(0, BrewWirePatterns.findAll("<x wire:>").size)
        assertEquals(0, BrewWirePatterns.findUnknown("<x wire:model.live>").size)
        assertEquals(1, BrewWirePatterns.findUnknown("<x wire:bogus>").size)
        assertFalse(BrewWireAttributes.isKnownAttribute("href"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:model="))
        assertNull(BrewWirePatterns.attributePrefixAt("abc", -1))
        assertNull(BrewWirePatterns.attributePrefixAt("abc", 99))
        // unfinished open tag still counts as inside
        assertEquals("wire:", BrewWirePatterns.attributePrefixAt("<span wire:", 11))
        // tab before attr name
        assertEquals("wire:k", BrewWirePatterns.attributePrefixAt("<x\twire:k", 9))
        // Empty attr-name walk (caret on whitespace after tag open)
        assertNull(BrewWirePatterns.attributePrefixAt("<a ", 3))
        // Non-whitespace / non-quote delimiter before putative attr
        assertNull(BrewWirePatterns.attributePrefixAt("<x=wire:k", 9))
    }

    @Test
    fun directiveContextAndPositions() {
        assertNull(CremaAnalyzer.directiveContextAt("", 0))
        assertNull(CremaAnalyzer.directiveContextAt("abc", 2))
        assertNull(CremaAnalyzer.directiveContextAt("csrf", 4))
        // Walk back hits a non-@ delimiter
        assertNull(CremaAnalyzer.directiveContextAt("#if", 3))
        assertEquals("", CremaAnalyzer.directiveContextAt("@", 1)?.prefix)
        assertEquals("if", CremaAnalyzer.directiveContextAt("@if", 3)?.prefix)
        assertEquals("foo_bar", CremaAnalyzer.directiveContextAt("@foo_bar", 8)?.prefix)

        val multiline = "a\nb\nc"
        assertEquals(CremaAnalyzer.Position(2, 1), CremaAnalyzer.offsetToPosition(multiline, 5))
        assertEquals(0, CremaAnalyzer.positionToOffset(multiline, 0, 0))
        assertEquals(2, CremaAnalyzer.positionToOffset(multiline, 1, 0))
        assertEquals(multiline.length, CremaAnalyzer.positionToOffset(multiline, 10, 0))
        assertEquals(CremaAnalyzer.Position(0, 0), CremaAnalyzer.offsetToPosition("", 5))
    }

    @Test
    fun echoPrefixInsideMustache() {
        assertNull(CremaAnalyzer.echoPrefixAt("hello", 3))
        assertNull(CremaAnalyzer.echoPrefixAt("{{ done }} x", 12))
        assertEquals("", CremaAnalyzer.echoPrefixAt("{{", 2))
        assertEquals("co", CremaAnalyzer.echoPrefixAt("{{ co", 5))
        assertEquals("a_b", CremaAnalyzer.echoPrefixAt("{{ a_b", 6))
        assertEquals("\$u", CremaAnalyzer.echoPrefixAt("{{ \$u", 5))
        assertEquals("count", CremaAnalyzer.echoPrefixAt("{{ count }}", 8))
        assertEquals("x", CremaAnalyzer.echoPrefixAt("{!! x", 5))
        assertNull(CremaAnalyzer.echoPrefixAt("{!! x !!} y", 11))
        assertNull(CremaAnalyzer.echoPrefixAt("", 0))
        assertNull(CremaAnalyzer.echoPrefixAt("{{", 0))
    }

    @Test
    fun echoCompletionsOfferUrlHelper() {
        val empty = CremaAnalyzer.completions("{{ ", 0, 3)
        assertTrue(empty.any { it.label == "url" }, empty.toString())
        assertTrue(empty.any { it.label == "loop.index" }, empty.toString())
        assertTrue(empty.none { it.label.startsWith("@") }, empty.toString())
        val typed = CremaAnalyzer.completions("{{ ur", 0, 5)
        assertEquals(listOf("url"), typed.map { it.label })
        assertEquals("url('')", typed.first().insertText)
        assertEquals("url(path) · Crema helper", typed.first().detail)
        val none = CremaAnalyzer.completions("{{ zzz", 0, 6)
        assertTrue(none.isEmpty(), none.toString())
        val loop = CremaAnalyzer.completions("{{ loop.", 0, 8)
        assertTrue(loop.any { it.label == "loop.index" }, loop.toString())
        assertTrue(loop.any { it.label == "loop.remaining" }, loop.toString())
        assertTrue(CremaEchoCatalog.completions("").any { it.name == "csrf_token" })
        assertTrue(CremaEchoCatalog.HELPERS.all { it.helper })
        assertTrue(CremaEchoCatalog.VARIABLES.none { it.helper })
        assertEquals("loop.", CremaAnalyzer.echoPrefixAt("{{ loop.", 8))
    }
}
