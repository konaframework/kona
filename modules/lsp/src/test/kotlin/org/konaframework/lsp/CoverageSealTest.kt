package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverageSealTest {
    @Test
    fun remainingSourceScanBranches() {
        assertTrue(SourceScan.skipTrivia("//", 0) >= 2)
        assertTrue(SourceScan.skipTrivia("/*x*/y", 0) >= 5)
        assertTrue(SourceScan.skipTrivia("/*x*y*/z", 0) >= 7)
        assertTrue(SourceScan.skipTrivia("/", 0) >= 0)
        SourceScan.skipTriviaBack("a/**/", 5)
        SourceScan.skipTriviaBack("/", 1)
        SourceScan.skipTriviaBack("a/", 2)
        SourceScan.skipTriviaBack("*/", 2)
        SourceScan.skipTriviaBack("a /* hi */", 10)
        SourceScan.skipStringOrChar("'", 0)
        SourceScan.skipStringOrChar("'\\'", 0)
        SourceScan.skipStringOrChar("''x", 0)
        assertTrue(SourceScan.skipType("()", 0) >= 2)
        SourceScan.skipType("() -", 0)
        SourceScan.skipType("() -x", 0)
        SourceScan.skipType("() -> Int", 0)
        SourceScan.skipType("() String", 0)
        SourceScan.skipType("Foo", 0)
        SourceScan.skipTriviaBack("/*a*b*/", 7)
        assertEquals(0, SourceScan.skipBalanced("}", 0, '{', '}'))
        assertEquals(0, SourceScan.skipBalanced("", 0, '{', '}'))
        SourceScan.skipBalanced("{ /* } */ }", 0, '{', '}')
        SourceScan.skipBalanced("{ 'x' }", 0, '{', '}')
        assertTrue(SourceScan.skipHeritage(": Foo() // {\n{", 0) >= 10)
        SourceScan.skipHeritage(": Foo('z') {", 0)
        SourceScan.skipHeritage(": Foo /* { */ Bar {", 0)
        assertTrue(SourceScan.isIdentifierPart('_'))
        assertFalse(SourceScan.isIdentifierPart(' '))
        assertEquals("com.Foo.Bar".length, SourceScan.skipType("com.Foo.Bar", 0))
        SourceScan.skipType("List<Int>?", 0)
    }

    @Test
    fun remainingInlineRangeBranches() {
        val range = InlineCremaRanges.Range(2, 4)
        assertTrue(range.contains(2))
        assertTrue(range.contains(4))
        assertFalse(range.contains(1))
        assertFalse(range.contains(5))
        assertTrue(InlineCremaRanges.find("inline").isEmpty())
        assertTrue(InlineCremaRanges.find("fun template").isEmpty())
        assertTrue(InlineCremaRanges.find("fun template(").isEmpty())
        assertTrue(InlineCremaRanges.isTemplateDeclaration("Integer template()", "Integer template()".indexOf("template")))
        assertTrue(InlineCremaRanges.isTemplateDeclaration("x) template()", "x) template()".indexOf("template")))
        assertTrue(InlineCremaRanges.isTemplateDeclaration("x? template()", "x? template()".indexOf("template")))
        assertTrue(InlineCremaRanges.find("fun template() = 1").isEmpty())
        assertTrue(InlineCremaRanges.find("fun template()").isEmpty())
        assertFalse(InlineCremaRanges.isTemplateDeclaration("{ template()", "{ template()".indexOf("template")))
        assertTrue(InlineCremaRanges.find("fun template() ;").isEmpty())
        assertTrue(InlineCremaRanges.find("fun template() { return 1; }").isEmpty())
        val padded = "// language=Crema\n" + " ".repeat(200) + "\"late\""
        assertTrue(InlineCremaRanges.find(padded).isEmpty())
        val withChar =
            "fun template() { char c = 'x'; /* n */ return \"ok\"; }"
        assertEquals("ok", InlineCremaRanges.find(withChar).single().let { withChar.substring(it.contentStart, it.contentEnd) })
        val longPad = "// language=Crema\n" + "x".repeat(200)
        assertTrue(InlineCremaRanges.find(longPad).isEmpty())
        val inlineEofParen = "inline("
        assertTrue(InlineCremaRanges.find(inlineEofParen).isEmpty())
        val templateOpen = "fun template( "
        assertTrue(InlineCremaRanges.find(templateOpen).isEmpty())
    }

    @Test
    fun remainingHostMemberBranches() {
        val comments = "/* outer */\n// line\nclass Foo { val render = 1; fun Foo() {}; fun template() {}; public int Foo = 3; char c = 'a'; }"
        assertEquals("Foo", HostMembers.classSpans(comments).single().name)
        val members = HostMembers.members(comments, comments.indexOf("render"))
        assertFalse(members.any { it.name == "render" || it.name == "Foo" || it.name == "template" })
        assertTrue(HostMembers.classSpans("class Foo;").isEmpty())
        val siblings =
            """
            class A { public int count = 1; }
            class B { public int other = 2; }
            """.trimIndent()
        val aMembers = HostMembers.members(siblings, siblings.indexOf("count"))
        assertTrue(aMembers.any { it.name == "count" })
        assertFalse(aMembers.any { it.name == "other" })
        val quoted = "class Q { String s = \"class X {}\"; char q = '\\''; }"
        assertEquals("Q", HostMembers.classSpans(quoted).single().name)
        val after = "class A { public int count = 1; } int after = 2;"
        assertTrue(HostMembers.members(after, after.indexOf("after")).isEmpty())
        assertTrue(HostMembers.classSpans("class Foo;").isEmpty())
    }

    @Test
    fun remainingHostDocumentBranches() {
        val src =
            """
            public class Counter {
                public int count = 0;
                public void increment() {}
                public String template() { return "<div>{{ inc }}</div>"; }
            }
            """.trimIndent()
        val at = src.indexOf("{{ inc") + 6
        val pos = CremaAnalyzer.offsetToPosition(src, at)
        val items = HostDocument.completions(src, "java", "file:///C.java", pos.line, pos.character)
        assertTrue(items.any { it.label == "increment" && it.kind == 2 }, items.toString())
        val emptyEcho = CremaAnalyzer.offsetToPosition(src, src.indexOf("{{ ") + 3)
        val fields = HostDocument.completions(src, "java", "file:///C.java", emptyEcho.line, emptyEcho.character)
        assertTrue(fields.any { it.label == "count" && it.kind == 6 }, fields.toString())
    }
}
