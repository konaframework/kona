package org.konaframework.support.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslatorTest {
    @Test
    fun localeFallbackReplaceChoice() {
        val t = Translator("en", "en")
            .addLines("en", mapOf(
                "hello" to "Hello :name",
                "apples" to "one apple|many apples",
                "plain" to "just one",
            ))
            .addLines("es", mapOf("hello" to "Hola :name"))

        assertEquals("en", t.getLocale())
        assertEquals("en", t.getFallback())
        t.setLocale("es")
        assertEquals("es", t.getLocale())
        t.setFallback("en")
        assertEquals("en", t.getFallback())

        assertTrue(t.has("hello"))
        assertTrue(t.has("hello", "en"))
        assertFalse(t.has("missing"))
        assertEquals("Hola Ada", t.get("hello", mapOf("name" to "Ada")))
        assertEquals("Hello Ada", t.get("hello", mapOf("name" to "Ada"), "en"))
        assertEquals("Hello Ada", t.trans("hello", mapOf("name" to "Ada"), "en"))
        assertEquals("missing.key", t.get("missing.key"))
        assertEquals("Hello ", t.get("hello", mapOf("name" to null), "en"))

        t.setLocale("fr")
        assertEquals("Hello Zoe", t.get("hello", mapOf("name" to "Zoe")))
        assertFalse(t.has("hello", "fr"))
        assertTrue(t.has("hello", "en"))

        assertEquals("one apple", t.choice("apples", 1, locale = "en"))
        assertEquals("many apples", t.choice("apples", 5, locale = "en"))
        assertEquals("just one", t.choice("plain", 9, locale = "en"))
        assertEquals("gone", t.choice("gone", 2))
        assertEquals(
            "many apples",
            t.choice("apples", 2, mapOf("count" to 99), "en"),
        )
    }

    @Test
    fun langFacade() {
        val t = Translator().addLines("en", mapOf("x" to "X :v"))
        Lang.setTranslator(t)
        assertSame(t, Lang.translator())
        assertEquals("X 1", Lang.get("x", mapOf("v" to 1)))
        assertEquals("X 2", Lang.get("x", mapOf("v" to 2), "en"))
        Lang.setLocale("en")
        assertEquals("en", Lang.getLocale())
        Lang.setFallback("es")
        assertEquals("es", Lang.getFallback())
    }
}
