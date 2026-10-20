package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InlineCremaRangesTest {
    @Test
    fun javaTemplateTextBlock() {
        val q = "\"\"\""
        val src =
            """
            public class Counter {
                @Override
                public String template() {
                    return $q
            <div>{{ count }}</div>
            $q;
                }
            }
            """.trimIndent()
        val ranges = InlineCremaRanges.find(src)
        assertEquals(1, ranges.size)
        val body = src.substring(ranges[0].contentStart, ranges[0].contentEnd)
        assertTrue(body.contains("{{ count }}"))
        val inside = ranges[0].contentStart + body.indexOf("count")
        assertNotNull(InlineCremaRanges.containing(src, inside))
        assertNull(InlineCremaRanges.containing(src, 0))
    }

    @Test
    fun kotlinAssignAndInlineAndLanguageComment() {
        val q = "\"\"\""
        val src =
            """
            class Box : Component() {
                override fun template(): String = $q<span>@csrf</span>$q
                override fun render() = inline($q<p>{{ title }}</p>$q)
                fun other() {
                    // language=Crema
                    val extra = $q<b>Hi</b>$q
                    /* language=Crema */
                    return $q<i>x</i>$q
                }
            }
            """.trimIndent()
        val ranges = InlineCremaRanges.find(src)
        assertTrue(ranges.size >= 3)
        assertTrue(ranges.any { src.substring(it.contentStart, it.contentEnd).contains("@csrf") })
        assertTrue(ranges.any { src.substring(it.contentStart, it.contentEnd).contains("title") })
        assertTrue(ranges.any { src.substring(it.contentStart, it.contentEnd).contains("<b>Hi</b>") })
    }

    @Test
    fun languageCommentEqualsAndIgnoredInsideString() {
        val src =
            """
            fun x() {
                val fake = " // language=Crema "
                // language=Crema
                = "real"
            }
            """.trimIndent()
        val ranges = InlineCremaRanges.find(src)
        assertEquals(1, ranges.size)
        assertEquals("real", src.substring(ranges[0].contentStart, ranges[0].contentEnd))
    }

    @Test
    fun inlineFunIsNotACallAndNonStringArg() {
        val src = "inline fun boom() = 1\ninline(42)\ninline(\"ok\")"
        val ranges = InlineCremaRanges.find(src)
        assertEquals(1, ranges.size)
        assertEquals("ok", src.substring(ranges[0].contentStart, ranges[0].contentEnd))
    }

    @Test
    fun templateCallsAndNonStringReturnsAreIgnored() {
        val call = "val x = component.template()\nreturn template();"
        assertTrue(InlineCremaRanges.find(call).isEmpty())
        assertFalse(InlineCremaRanges.isTemplateDeclaration(call, call.indexOf("template()")))
        val ret = "return template()"
        assertFalse(InlineCremaRanges.isTemplateDeclaration(ret, ret.indexOf("template")))
        assertFalse(InlineCremaRanges.isTemplateDeclaration("template()", 0))
        val java = "public String template() { return null; }"
        assertTrue(InlineCremaRanges.isTemplateDeclaration(java, java.indexOf("template")))
        val nullable = "fun template(): String? = \"\"\"x\"\"\""
        assertTrue(InlineCremaRanges.find(nullable).isNotEmpty())
        val generic = "List<String> template() { return \"hi\"; }"
        assertTrue(InlineCremaRanges.find(generic).any { it.contains(generic.indexOf("hi")) })
        val nullableType = "String? template() { return \"q\"; }"
        assertTrue(InlineCremaRanges.find(nullableType).any { it.contains(nullableType.indexOf("q")) })
        assertFalse(InlineCremaRanges.isTemplateDeclaration("foo template()", "foo template()".indexOf("template")))
        val languageReturn = "// language=Crema\nreturn \"fromReturn\""
        assertEquals(
            "fromReturn",
            InlineCremaRanges.find(languageReturn).single().let {
                languageReturn.substring(it.contentStart, it.contentEnd)
            },
        )
        val block = "fun template() { if (true) { return \"nope\"; } return \"yes\"; }"
        val found = InlineCremaRanges.find(block)
        assertTrue(found.any { block.substring(it.contentStart, it.contentEnd) == "yes" })
        assertTrue(found.any { block.substring(it.contentStart, it.contentEnd) == "nope" }.not())
        val noParen = "fun template = 1"
        assertTrue(InlineCremaRanges.find(noParen).isEmpty())
        val fnType = "fun template(): () -> String = \"fn\""
        assertEquals("fn", InlineCremaRanges.find(fnType).single().let { fnType.substring(it.contentStart, it.contentEnd) })
        val spaced = "fun template ( ) { return \"sp\"; }"
        assertEquals("sp", InlineCremaRanges.find(spaced).single().let { spaced.substring(it.contentStart, it.contentEnd) })
        val withArgs = "fun template(x: Int) = \"no\""
        assertTrue(InlineCremaRanges.find(withArgs).isEmpty())
        val commentThenTemplate =
            "fun template() { // skip\n return \"a\"; }"
        assertEquals("a", InlineCremaRanges.find(commentThenTemplate).single().let {
            commentThenTemplate.substring(it.contentStart, it.contentEnd)
        })
    }

    @Test
    fun languageCommentWithoutStringAndBlockComment() {
        val none = "// language=Crema\nval x = 1"
        assertTrue(InlineCremaRanges.find(none).isEmpty())
        val block = "/* language=Crema */\n\"z\""
        assertEquals("z", InlineCremaRanges.find(block).single().let { block.substring(it.contentStart, it.contentEnd) })
        val quoted = "val c = '\"'\nfun template(): String = \"t\""
        assertEquals("t", InlineCremaRanges.find(quoted).single().let { quoted.substring(it.contentStart, it.contentEnd) })
        val stopsAtBrace = "// language=Crema\nfun x() { return \"nope\"; }"
        assertTrue(InlineCremaRanges.find(stopsAtBrace).isEmpty())
        val skipsChar = "// language=Crema\n'z'; \"yes\""
        // semicolon after char stops the look-ahead
        assertTrue(InlineCremaRanges.find(skipsChar).isEmpty())
        val charThenString = "// language=Crema\n'z' \"yes\""
        assertEquals("yes", InlineCremaRanges.find(charThenString).single().let {
            charThenString.substring(it.contentStart, it.contentEnd)
        })
        val eofComment = "// language=Crema"
        assertTrue(InlineCremaRanges.find(eofComment).isEmpty())
    }
}
