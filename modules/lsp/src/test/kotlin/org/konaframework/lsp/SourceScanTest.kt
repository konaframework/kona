package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceScanTest {
    @Test
    fun skipTriviaWhitespaceAndComments() {
        assertEquals(3, SourceScan.skipTrivia("   x", 0))
        assertEquals(0, SourceScan.skipTrivia("x", 0))
        assertEquals(6, SourceScan.skipTrivia("// hi\nx", 0))
        assertEquals(7, SourceScan.skipTrivia("/* a */x", 0))
        assertEquals("/* unclosed".length, SourceScan.skipTrivia("/* unclosed", 0))
        assertEquals(0, SourceScan.skipTrivia("/not", 0))
        assertEquals(0, SourceScan.skipTrivia("/", 0))
        assertEquals(2, SourceScan.skipTrivia("/*", 0))
        assertEquals(0, SourceScan.skipTrivia("", 0))
        assertEquals(0, SourceScan.skipTrivia("a", -5))
    }

    @Test
    fun skipTriviaBackWhitespaceCommentsAndOverflow() {
        assertEquals(-1, SourceScan.skipTriviaBack("", 0))
        assertEquals(-1, SourceScan.skipTriviaBack("   ", 3))
        assertEquals(0, SourceScan.skipTriviaBack("a  ", 3))
        assertEquals(0, SourceScan.skipTriviaBack("a /* x */  ", 11))
        val unmatched = "a */"
        assertEquals(unmatched.lastIndex, SourceScan.skipTriviaBack(unmatched, unmatched.length))
        assertEquals(0, SourceScan.skipTriviaBack("a // comment", 12))
        assertEquals(0, SourceScan.skipTriviaBack("a", 99))
    }

    @Test
    fun parseStringVariants() {
        assertNull(SourceScan.parseString("x", 0))
        assertNull(SourceScan.parseString("", 0))
        assertNull(SourceScan.parseString("\"hi\"", -1))
        assertNull(SourceScan.parseString("\"hi\"", 4))
        val empty = SourceScan.parseString("\"\"", 0)!!
        assertEquals(1, empty.contentStart)
        assertEquals(1, empty.contentEnd)
        assertFalse(empty.triple)
        val esc = SourceScan.parseString("\"a\\\"b\"", 0)!!
        assertEquals("a\\\"b", "\"a\\\"b\"".substring(esc.contentStart, esc.contentEnd))
        val danglingEsc = SourceScan.parseString("\"\\", 0)!!
        assertEquals("\\".length, danglingEsc.contentEnd - danglingEsc.contentStart)
        val nl = SourceScan.parseString("\"ab\n", 0)!!
        assertEquals(3, nl.contentEnd)
        val unclosed = SourceScan.parseString("\"abc", 0)!!
        assertEquals(4, unclosed.closeEnd)
        val triple = SourceScan.parseString("\"\"\"hi\"\"\"", 0)!!
        assertTrue(triple.triple)
        assertEquals("hi", "\"\"\"hi\"\"\"".substring(triple.contentStart, triple.contentEnd))
        val unclosedTriple = SourceScan.parseString("\"\"\"nope", 0)!!
        assertEquals("\"\"\"nope".length, unclosedTriple.closeEnd)
        val twoQuotes = SourceScan.parseString("\"\"x", 0)!!
        assertFalse(twoQuotes.triple)
    }

    @Test
    fun skipStringOrCharAndComments() {
        assertEquals(0, SourceScan.skipStringOrChar("", 0))
        assertEquals(3, SourceScan.skipStringOrChar("'a'", 0))
        assertEquals(2, SourceScan.skipStringOrChar("''", 0))
        assertEquals(4, SourceScan.skipStringOrChar("'\\n'", 0))
        assertEquals(2, SourceScan.skipStringOrChar("'\\", 0))
        assertEquals(2, SourceScan.skipStringOrChar("'x", 0))
        assertEquals(1, SourceScan.skipStringOrChar("'\\n", 0).let { SourceScan.skipStringOrChar("x", 0) })
        assertEquals(1, SourceScan.skipStringOrChar("z", 0))
        assertEquals(4, SourceScan.skipStringOrChar("\"hi\"!", 0))
        val line = SourceScan.readComment("// language=Crema\n", 0)
        assertTrue(line.first)
        val plain = SourceScan.readComment("// nope", 0)
        assertFalse(plain.first)
        val block = SourceScan.readComment("/* language = Crema */", 0)
        assertTrue(block.first)
        val unclosed = SourceScan.readComment("/* language=Crema", 0)
        assertTrue(unclosed.first)
        assertEquals("/* language=Crema".length, unclosed.second)
        val not = SourceScan.readComment("x", 0)
        assertFalse(not.first)
        assertEquals(1, not.second)
    }

    @Test
    fun identifiersTypesAndBalanced() {
        assertFalse(SourceScan.isIdentifierStart('1'))
        assertTrue(SourceScan.isIdentifierStart('_'))
        assertTrue(SourceScan.isIdentifierPart('9'))
        assertNull(SourceScan.readIdentifier("", 0))
        assertNull(SourceScan.readIdentifier("abc", -1))
        assertNull(SourceScan.readIdentifier("abc", 3))
        assertNull(SourceScan.readIdentifier("1ab", 0))
        assertEquals("ab" to 2, SourceScan.readIdentifier("ab", 0))
        assertEquals(0, SourceScan.skipBalanced("x", 0, '{', '}'))
        assertEquals(2, SourceScan.skipBalanced("{}", 0, '{', '}'))
        val nested = SourceScan.skipBalanced("{a{b}c}", 0, '{', '}')
        assertEquals(7, nested)
        val withStr = SourceScan.skipBalanced("{\"}\" + '}'}", 0, '{', '}')
        assertTrue(withStr > 0)
        val withComment = SourceScan.skipBalanced("{ // {\n}", 0, '{', '}')
        assertEquals("{ // {\n}".length, withComment)
        assertEquals(0, SourceScan.skipType("", 0))
        assertTrue(SourceScan.skipType("() -> String", 0) > 5)
        assertTrue(SourceScan.skipType("Map<String, List<Int>>?", 0) > 10)
        assertEquals(6, SourceScan.skipType("String", 0))
        val heritage = SourceScan.skipHeritage(": Bar(\"{\") {", 0)
        assertEquals(": Bar(\"{\") {".lastIndex, heritage)
        val heritageComment = SourceScan.skipHeritage(": /* { */ Foo {", 0)
        assertEquals(": /* { */ Foo {".lastIndex, heritageComment)
        val heritageEof = SourceScan.skipHeritage(": Foo(", 0)
        assertEquals(": Foo(".length, heritageEof)
        assertEquals(0, SourceScan.skipHeritage("{", 0))
    }
}
