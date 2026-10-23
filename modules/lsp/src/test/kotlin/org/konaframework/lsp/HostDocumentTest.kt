package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostDocumentTest {
    @Test
    fun languageIdFromUriAndDeclared() {
        assertEquals("java", HostDocument.languageId("java", "file:///x.kt"))
        assertEquals("crema", HostDocument.languageId("", "file:///a.crema.html"))
        assertEquals("crema", HostDocument.languageId("", "file:///a.crema.kt#frag"))
        assertEquals("java", HostDocument.languageId("", "file:///A.JAVA?q=1"))
        assertEquals("kotlinscript", HostDocument.languageId("", "file:///b.kts"))
        assertEquals("kotlin", HostDocument.languageId("", "file:///b.kt"))
        assertEquals("", HostDocument.languageId("", "file:///readme.md"))
        assertTrue(HostDocument.analyzeWholeFile("crema", "file:///x.crema.html"))
        assertTrue(HostDocument.analyzeWholeFile("kotlin", "file:///x.crema.kt"))
        assertTrue(HostDocument.analyzeWholeFile("", "file:///x.crema.html"))
        assertFalse(HostDocument.analyzeWholeFile("java", "file:///Counter.java"))
        assertFalse(HostDocument.analyzeWholeFile("kotlin", "file:///Box.kt"))
        assertFalse(HostDocument.analyzeWholeFile("kotlinscript", "file:///x.kts"))
        assertTrue(HostDocument.analyzeWholeFile("", "file:///x.crema.html.bak".replace(".bak", "")))
        assertTrue(HostDocument.analyzeWholeFile("", "untitled:1"))
    }

    @Test
    fun javaCompletionsAndDiagnosticsStayInsideTemplate() {
        val q = "\"\"\""
        val src =
            """
            public class Counter {
                public int count = 0;
                public void increment() {}
                public String template() {
                    return $q
            <div>{{ cou }}
            <button wire:cli>
            @csrf @nope
            </div>
            $q;
                }
            }
            """.trimIndent()
        val uri = "file:///Counter.java"
        val echoAt = src.indexOf("{{ cou") + 6
        val echoPos = CremaAnalyzer.offsetToPosition(src, echoAt)
        val members =
            HostDocument.completions(src, "java", uri, echoPos.line, echoPos.character)
        assertTrue(members.any { it.label == "count" }, members.toString())
        assertTrue(members.filter { it.detail == "Wire member" }.any { it.label == "count" })

        val outside = HostDocument.completions(src, "java", uri, 0, 0)
        assertTrue(outside.isEmpty())

        val wireAt = src.indexOf("wire:cli") + "wire:cli".length
        val wirePos = CremaAnalyzer.offsetToPosition(src, wireAt)
        val wires = HostDocument.completions(src, "java", uri, wirePos.line, wirePos.character)
        assertTrue(wires.any { it.label == "wire:click" }, wires.toString())

        val at = src.indexOf("@csrf") + 1
        val atPos = CremaAnalyzer.offsetToPosition(src, at)
        val dirs = HostDocument.completions(src, "java", uri, atPos.line, atPos.character)
        assertTrue(dirs.any { it.label == "@csrf" })

        val diags = HostDocument.diagnostics(src, "java", uri)
        assertTrue(diags.any { it.message.contains("@nope") })
        assertTrue(diags.all { it.range.start.line >= echoPos.line - 1 })
    }

    @Test
    fun wholeFileEchoMembersAndEmptyFallback() {
        val crema = "<div>{{  }}</div>"
        val echoAt = crema.indexOf("{{ ") + 3
        val echoPos = CremaAnalyzer.offsetToPosition(crema, echoAt)
        val items =
            HostDocument.completions(crema, "crema", "file:///x.crema.html", echoPos.line, echoPos.character)
        assertTrue(items.any { it.label == "url" }, items.toString())
        assertTrue(items.any { it.label == "loop.index" }, items.toString())
        val javaNoMembers =
            """
            public class Empty {
                public String template() { return "{{ }}"; }
            }
            """.trimIndent()
        val caret = javaNoMembers.indexOf("{{ ") + 3
        val pos = CremaAnalyzer.offsetToPosition(javaNoMembers, caret)
        val fallback =
            HostDocument.completions(javaNoMembers, "java", "file:///Empty.java", pos.line, pos.character)
        assertTrue(fallback.isNotEmpty())
        val emptyDiags = HostDocument.diagnostics("", "java", "file:///Empty.java")
        assertTrue(emptyDiags.isEmpty())

        val mixed = "class Foo { var count = 1\n<div>{{ co"
        val mixedPos = CremaAnalyzer.offsetToPosition(mixed, mixed.length)
        val mixedItems =
            HostDocument.completions(mixed, "crema", "file:///x.crema.html", mixedPos.line, mixedPos.character - 0)
        assertTrue(mixedItems.any { it.label == "count" }, mixedItems.toString())
    }
}
