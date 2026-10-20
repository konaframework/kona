package org.konaframework.support

import org.konaframework.core.Application
import org.konaframework.support.lang.Lang
import org.konaframework.support.lang.Translator
import org.konaframework.support.lang.translate
import org.konaframework.support.date.KonaDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId

class SupportServiceProviderTest {
    @AfterEach
    fun resetDate() {
        KonaDate.resetDefaultTimezone()
    }

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

    @Test
    fun seedsLocaleAndTimezoneFromAppConfig(@TempDir base: Path) {
        val app = Application.builder(base)
            .configure {
                it.set("app.locale", "fr")
                it.set("app.fallback_locale", "es")
                it.set("app.timezone", "Africa/Nairobi")
            }
            .withProvider(SupportServiceProvider())
            .boot()
        val translator = app.resolve(Translator::class.java)
        assertEquals("fr", translator.getLocale())
        assertEquals("es", translator.getFallback())
        assertEquals("fr", Lang.getLocale())
        assertEquals("es", Lang.getFallback())
        assertEquals(ZoneId.of("Africa/Nairobi"), KonaDate.defaultTimezone())

        Application.builder(base)
            .configure {
                it.set("app.locale", "  ")
                it.set("app.fallback_locale", "")
                it.set("app.timezone", "Not/AZone")
            }
            .withProvider(SupportServiceProvider())
            .boot()
        assertEquals("en", Lang.getLocale())
        assertEquals("en", Lang.getFallback())
        assertEquals(ZoneId.of("UTC"), KonaDate.defaultTimezone())

        Application.builder(base)
            .configure { it.set("app.timezone", "   ") }
            .withProvider(SupportServiceProvider())
            .boot()
        assertEquals(ZoneId.of("UTC"), KonaDate.defaultTimezone())
    }
}
