package org.konaframework.support

import org.konaframework.support.collection.Collection
import org.konaframework.support.date.KonaDate
import org.konaframework.support.lang.Lang
import org.konaframework.support.lang.Translator
import org.konaframework.support.str.Str
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class BranchCloseTest {
    @Test
    fun defaultOverloadsAndBranches() {
        val c = Collection.make("a")
        c.put(0, "b")
        assertEquals("b", c.get(0))
        c.put(-1, "neg")
        assertEquals("neg", c.last())
        c.put(10, "c")
        assertEquals("c", c.last())

        assertNotNull(KonaDate.now())
        assertNotNull(KonaDate.now(ZoneOffset.UTC))
        assertNotNull(
            KonaDate.now(
                ZoneOffset.UTC,
                java.time.Clock.fixed(java.time.Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        )
        assertNotNull(KonaDate.today())
        assertNotNull(KonaDate.ofInstant(java.time.Instant.EPOCH))
        assertNotNull(KonaDate.parse("2024-01-01T00:00:00Z"))
        assertNotNull(KonaDate.parse("2024-01-01T00:00:00Z", ZoneOffset.UTC))
        assertNotNull(KonaDate.parse("2024-01-01 00:00:00", ZoneOffset.ofHours(2)))

        assertEquals("Hello", Str.title("hello"))
        assertEquals("Hello", Str.title(" hello "))
        assertEquals("", Str.title("   "))
        assertEquals("A", Str.title("a"))
        assertEquals("", Str.camel(""))
        assertEquals("a", Str.camel("A"))
        assertEquals("helloWorld", Str.camel("hello_world"))
        assertEquals("Abc", Str.studly("abc"))
        assertEquals("", Str.studly("___"))
        assertEquals("hello_world", Str.snake("__Hello__World__"))
        assertEquals("hello_world", Str.snake("HelloWorld"))
        assertEquals("x-y", Str.slug("x y", ""))
        assertEquals("xy", Str.slug("---xy---", "-"))

        val t = Translator()
        assertEquals("en", t.getLocale())
        t.addLines("en", mapOf("hi" to "Hi :name", "only" to "solo"))
        assertEquals("Hi Ada", t.trans("hi", mapOf("name" to "Ada")))
        assertEquals("Hi Ada", t.trans("hi", mapOf("name" to "Ada"), "en"))
        assertEquals("hi", Translator().trans("hi"))
        assertEquals("Hi ", t.get("hi", mapOf("name" to null)))
        assertEquals("solo", t.choice("only", 1))
        assertEquals("solo", t.choice("only", 2))
        assertEquals("missing", t.get("missing", emptyMap(), "en"))
        t.setLocale("en")
        t.setFallback("en")
        assertFalse(t.has("nope", "en"))
        t.setFallback("fr")
        assertEquals("gone", t.get("gone", emptyMap(), "de"))

        Lang.setTranslator(t)
        assertEquals("Hi Zoe", Lang.get("hi", mapOf("name" to "Zoe")))
        assertEquals("Hi Zoe", Lang.get("hi", mapOf("name" to "Zoe"), "en"))
        assertTrue(Str.isNotBlank("z"))
    }
}
