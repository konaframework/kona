package org.konaframework.support.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PrettyJsonTest {
    @Test
    fun scalarsAndEmptyContainers() {
        assertEquals("null\n", PrettyJson.stringify(null))
        assertEquals("true\n", PrettyJson.stringify(true))
        assertEquals("false\n", PrettyJson.stringify(false))
        assertEquals("12\n", PrettyJson.stringify(12))
        assertEquals("1.5\n", PrettyJson.stringify(1.5))
        assertEquals("\"ok\"\n", PrettyJson.stringify("ok"))
        assertEquals("{}\n", PrettyJson.stringify(emptyMap<String, Any?>()))
        assertEquals("[]\n", PrettyJson.stringify(emptyList<Any?>()))
        assertEquals("[]\n", PrettyJson.stringify(emptyArray<Any?>()))
        assertEquals("\"x\\\"y\"\n", PrettyJson.stringify(Token("x\"y")))
    }

    @Test
    fun npmShapedObjectAndArray() {
        val body =
            linkedMapOf<String, Any?>(
                "private" to true,
                "scripts" to linkedMapOf("dev" to "vite", "build" to "vite build"),
                "dependencies" to emptyMap<String, String>(),
                "lib" to listOf("ES2022", "DOM"),
            )
        val json = PrettyJson.stringify(body)
        assertEquals(
            """
            {
              "private": true,
              "scripts": {
                "dev": "vite",
                "build": "vite build"
              },
              "dependencies": {},
              "lib": [
                "ES2022",
                "DOM"
              ]
            }
            """.trimIndent() + "\n",
            json,
        )
        assertFalse(json.contains("\" : \""))
        assertEquals(
            """
            [
              "ES2022",
              "DOM"
            ]
            """.trimIndent() + "\n",
            PrettyJson.stringify(arrayOf("ES2022", "DOM")),
        )
        assertEquals(
            """
            [
              null,
              true
            ]
            """.trimIndent() + "\n",
            PrettyJson.stringify(listOf(null, true)),
        )
    }

    @Test
    fun quotesControlCharacters() {
        assertEquals("\"a\\\"b\"\n", PrettyJson.stringify("a\"b"))
        assertEquals("\"a\\\\b\"\n", PrettyJson.stringify("a\\b"))
        assertEquals("\"a\\nb\"\n", PrettyJson.stringify("a\nb"))
        assertEquals("\"a\\rb\"\n", PrettyJson.stringify("a\rb"))
        assertEquals("\"a\\tb\"\n", PrettyJson.stringify("a\tb"))
        assertEquals("\"a\\u0001b\"\n", PrettyJson.stringify("a\u0001b"))
    }

    private class Token(private val raw: String) {
        override fun toString(): String = raw
    }
}
