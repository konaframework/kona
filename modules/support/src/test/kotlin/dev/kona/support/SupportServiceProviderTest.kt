package dev.kona.support

import dev.kona.core.Application
import dev.kona.support.lang.Lang
import dev.kona.support.lang.Translator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SupportServiceProviderTest {
    @Test
    fun registersTranslatorAndWiresLang(@TempDir base: Path) {
        val app = Application(base)
        app.register(SupportServiceProvider())
        val translator = app.resolve(Translator::class.java)
        assertSame(translator, Lang.translator())
        translator.addLines("en", mapOf("hi" to "hello"))
        assertEquals("hello", Lang.get("hi"))
    }
}
