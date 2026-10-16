package io.konaframework.support

import io.konaframework.core.Application
import io.konaframework.support.lang.Lang
import io.konaframework.support.lang.Translator
import io.konaframework.support.lang.translate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
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
        assertEquals("hello", translate("hi"))
    }

    @Test
    fun loadsLangDirectoryFromBasePath(@TempDir base: Path) {
        val lang = base.resolve("lang")
        Files.createDirectories(lang)
        Files.writeString(lang.resolve("en.json"), """{"hi":"Hello :name"}""")
        val app = Application.builder(base)
            .withProvider(SupportServiceProvider())
            .boot()
        assertEquals("Hello Ada", translate("hi", mapOf("name" to "Ada")))
        assertSame(app.resolve(Translator::class.java), Lang.translator())
    }

    @Test
    fun loadsLangPathFromConfig(@TempDir base: Path) {
        val custom = base.resolve("i18n")
        Files.createDirectories(custom)
        Files.writeString(custom.resolve("en.properties"), "ok=yes")
        val app = Application.builder(base)
            .configure { it.set("lang.path", "i18n") }
            .withProvider(SupportServiceProvider())
            .boot()
        assertEquals("yes", app.resolve(Translator::class.java).get("ok"))

        val abs = base.resolve("abs-lang")
        Files.createDirectories(abs)
        Files.writeString(abs.resolve("en.json"), """{"abs":"ok"}""")
        val absApp = Application.builder(base)
            .configure { it.set("lang.path", abs.toAbsolutePath().toString()) }
            .withProvider(SupportServiceProvider())
            .boot()
        assertEquals("ok", absApp.resolve(Translator::class.java).get("abs"))

        val missing = Application.builder(base)
            .configure { it.set("lang.path", "does-not-exist") }
            .withProvider(SupportServiceProvider())
            .boot()
        assertNull(SupportServiceProvider.resolveLangPath(missing))
    }
}
