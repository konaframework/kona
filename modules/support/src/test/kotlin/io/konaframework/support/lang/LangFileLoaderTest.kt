package io.konaframework.support.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LangFileLoaderTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun loadsJsonPropertiesAndNestedLocaleDirs() {
        val lang = dir.resolve("lang")
        Files.createDirectories(lang.resolve("en").resolve("nested-dir"))
        Files.writeString(
            lang.resolve("en.json"),
            """{"hi":"Hello","nested":{"x":"X"},"n":null,"arr":[1,2],"num":3}""",
        )
        Files.writeString(
            lang.resolve("FR.PROPERTIES"),
            """
            # comment

            greet=Bonjour :name
            empty=
            badline
            !also
            =novaluekey

            """.trimIndent(),
        )
        Files.writeString(
            lang.resolve("en").resolve("messages.json"),
            """{"welcome":"Welcome :name"}""",
        )
        Files.writeString(
            lang.resolve("en").resolve("app.properties"),
            "title=App",
        )
        Files.writeString(lang.resolve("en").resolve("plain.txt"), "raw=value")
        Files.writeString(lang.resolve("broken.json"), "{not-json")
        Files.writeString(lang.resolve("empty.json"), "[]")
        Files.writeString(lang.resolve("readme.txt"), "ignored=at-top-level")
        Files.writeString(lang.resolve("en").resolve("EXTRA.JSON"), """{"y":"Y"}""")
        Files.createDirectories(lang.resolve("de"))
        Files.writeString(lang.resolve("de").resolve("skip.bin"), "not=used")

        val translator = Translator("en")
        LangFiles.loadInto(translator, lang)

        assertEquals("Hello", translator.get("hi"))
        assertEquals("X", translator.get("nested.x"))
        assertEquals("3", translator.get("num"))
        assertEquals("[1,2]", translator.get("arr"))
        assertEquals("Welcome Ada", translator.get("messages.welcome", mapOf("name" to "Ada")))
        assertEquals("App", translator.get("app.title"))
        assertEquals("Y", translator.get("EXTRA.y"))
        assertEquals("Bonjour Zoe", translator.get("greet", mapOf("name" to "Zoe"), "fr"))
        assertTrue(!translator.has("n"))

        val single = Translator()
        LangFiles.loadInto(single, lang.resolve("en.json"))
        assertEquals("Hello", single.get("hi"))

        assertEquals("x", LangFileLoader().load(Translator(), dir.resolve("missing")).get("x"))

        // Character device / special file: exists but neither regular file nor directory.
        LangFiles.loadInto(Translator(), Path.of("/dev/null"))

        val propsOnly = Translator()
        LangFiles.loadInto(propsOnly, lang.resolve("FR.PROPERTIES"))
        assertEquals("Bonjour Ada", propsOnly.get("greet", mapOf("name" to "Ada"), "fr"))
    }
}
