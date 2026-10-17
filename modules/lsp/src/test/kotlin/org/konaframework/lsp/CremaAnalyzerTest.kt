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
    }

    @Test
    fun diagnosticsFlagsUnknownAndSkipsComments() {
        val text =
            """
            @extends('layouts.app')
            {{-- @fakeInComment --}}
            @section('content')
            @unknownThing
            @endsection
            """.trimIndent()
        val diags = CremaAnalyzer.diagnostics(text)
        assertEquals(1, diags.size)
        assertTrue(diags[0].message.contains("@unknownThing"))
        assertEquals(2, diags[0].severity)
        assertEquals("crema", diags[0].source)
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
}
