package org.konaframework.crema

import org.konaframework.core.Application
import org.konaframework.core.Env
import org.konaframework.core.Kona
import org.konaframework.http.Router
import org.konaframework.http.Url
import org.konaframework.http.UrlGenerator
import org.konaframework.support.lang.Lang
import org.konaframework.support.lang.Translator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CremaHelpersTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Url.clearGenerator()
        Kona.reset()
        Env.clear()
        Lang.setTranslator(Translator())
    }

    @Test
    fun catalogListsUrlAndFiltersPrefix() {
        assertTrue(CremaHelpers.HELPERS.any { it.name == "url" })
        assertTrue(CremaHelpers.VARIABLES.any { it.name == "loop.index" })
        assertTrue(CremaHelpers.isCallable("url"))
        assertFalse(CremaHelpers.isCallable("isset"))
        assertFalse(CremaHelpers.isCallable("nope"))
        assertTrue(CremaHelpers.completions("").contains("url"))
        assertTrue(CremaHelpers.completions("ur").contains("url"))
        assertTrue(CremaHelpers.completions("loop.").contains("loop.index"))
        assertTrue(CremaHelpers.completions("loop.").contains("loop.remaining"))
        assertFalse(CremaHelpers.completions("zzz").contains("url"))
        assertEquals("url('')", CremaHelpers.HELPERS.first { it.name == "url" }.insertText)
        assertEquals("url(path)", CremaHelpers.HELPERS.first { it.name == "url" }.signature)
        assertEquals("Rooted or absolute URL", CremaHelpers.HELPERS.first { it.name == "url" }.detail)
        assertTrue(CremaHelpers.HELPERS.first { it.name == "url" }.callable)
        assertFalse(CremaHelpers.HELPERS.first { it.name == "isset" }.callable)
        assertEquals("Validation errors map", CremaHelpers.VARIABLES.first { it.name == "errors" }.detail)
    }

    @Test
    fun parseCallAcceptsHelpersAndRejectsNonCalls() {
        assertEquals("url" to "'/x'", CremaHelpers.parseCall("url('/x')"))
        assertEquals("csrf_token" to "", CremaHelpers.parseCall("csrf_token()"))
        assertEquals("nested" to "a(b, c(d))", CremaHelpers.parseCall("nested(a(b, c(d)))"))
        assertEquals("q" to "'a)b'", CremaHelpers.parseCall("q('a)b')"))
        assertEquals("q" to "\"a)b\"", CremaHelpers.parseCall("q(\"a)b\")"))
        assertEquals("url" to "'/x'", CremaHelpers.parseCall("  url('/x')  "))
        assertNull(CremaHelpers.parseCall("url"))
        assertNull(CremaHelpers.parseCall("('/x')"))
        assertNull(CremaHelpers.parseCall("1url('/x')"))
        assertNull(CremaHelpers.parseCall("url('/x') extra"))
        assertNull(CremaHelpers.parseCall("url('/x'"))
        assertNull(CremaHelpers.parseCall("url('oops)"))
        assertNull(CremaHelpers.parseCall("foo.bar('/x')"))
        assertNull(CremaHelpers.parseCall("url('/x')()"))
        assertNull(CremaHelpers.parseCall("url((x)"))
        assertEquals("url" to "x", CremaHelpers.parseCall("url(x)"))
        assertNull(CremaHelpers.call("nope", emptyList(), CremaContext()))
    }

    @Test
    fun urlUsesGeneratorAndFallsBack() {
        assertEquals("/branding/kona-logo.svg", CremaHelpers.url("/branding/kona-logo.svg"))
        assertEquals("/", CremaHelpers.url(null))
        assertEquals("/", CremaHelpers.url("  "))
        assertEquals("/logo.svg", CremaHelpers.url("logo.svg"))
        assertEquals("https://cdn.example/a.svg", CremaHelpers.url("https://cdn.example/a.svg"))
        assertEquals("http://cdn.example/a.svg", CremaHelpers.url("http://cdn.example/a.svg"))
        assertEquals(
            "/branding/kona-logo.svg",
            CremaHelpers.call("url", listOf("/branding/kona-logo.svg"), CremaContext()),
        )
        Url.setGenerator(UrlGenerator(Router()).setRoot("https://app.test"))
        assertEquals("https://app.test/branding/kona-logo.svg", CremaHelpers.url("/branding/kona-logo.svg"))
    }

    @Test
    fun routeUsesNamedRoutesOrFallsBack() {
        assertEquals("", CremaHelpers.route(null))
        assertEquals("", CremaHelpers.route("  "))
        assertEquals("/login", CremaHelpers.route("login"))
        val router = Router()
        router.get("/signin") { org.konaframework.http.Response.text("ok") }.name("login")
        Url.setGenerator(UrlGenerator(router).setRoot("https://app.test"))
        assertEquals("https://app.test/signin", CremaHelpers.route("login"))
        assertEquals("", CremaHelpers.call("route", emptyList<Any?>(), CremaContext()))
        assertEquals("/home", CremaHelpers.call("route", listOf("home"), CremaContext()))
    }

    @Test
    fun assetJoinsPrefixAndCsrfOldEnvConfigLang() {
        val ctx = CremaContext()
        assertEquals("/css/app.css", CremaHelpers.asset("css/app.css", ctx))
        assertEquals("/css/app.css", CremaHelpers.asset("/css/app.css", ctx))
        assertEquals("/", CremaHelpers.asset(null, ctx))
        assertEquals("/", CremaHelpers.asset("   ", ctx))
        assertEquals("/x", CremaHelpers.asset("  /x  ", ctx))
        ctx.set("asset_url", "https://cdn.example")
        assertEquals("https://cdn.example/css/app.css", CremaHelpers.asset("css/app.css", ctx))
        val alt = CremaContext().set("assetUrl", "https://other.example/")
        assertEquals("https://other.example/app.js", CremaHelpers.asset("app.js", alt))
        assertEquals("https://cdn.example", CremaHelpers.asset("", ctx))
        assertEquals("/css/app.css", CremaHelpers.call("asset", listOf("css/app.css"), CremaContext()))

        assertEquals("", CremaHelpers.csrfToken(CremaContext()))
        assertEquals("tok", CremaHelpers.csrfToken(CremaContext().set("_token", "tok")))
        assertEquals("alt", CremaHelpers.csrfToken(CremaContext().set("csrf_token", "alt")))
        assertEquals("7", CremaHelpers.csrfToken(CremaContext().set("_token", 7)))
        assertEquals("tok", CremaHelpers.call("csrf_token", emptyList<Any?>(), CremaContext().set("_token", "tok")))

        val old = CremaContext().set("old", mapOf("email" to "a@b.c"))
        assertEquals("a@b.c", CremaHelpers.old("email", "x", old))
        assertEquals("x", CremaHelpers.old("missing", "x", old))
        assertNull(CremaHelpers.old("missing", null, old))
        val flashed = CremaContext().set("_old_input", mapOf("name" to "Ada"))
        assertEquals("Ada", CremaHelpers.old("name", null, flashed))
        val camel = CremaContext().set("oldInput", mapOf("city" to "Nairobi"))
        assertEquals("Nairobi", CremaHelpers.old("city", null, camel))
        assertEquals("d", CremaHelpers.old("email", "d", CremaContext().set("old", "not-a-map")))
        assertEquals("a@b.c", CremaHelpers.call("old", listOf("email"), old))
        assertEquals("z", CremaHelpers.call("old", listOf("missing", "z"), old))
        assertNull(CremaHelpers.call("old", emptyList<Any?>(), CremaContext()))
        assertEquals("z", CremaHelpers.call("old", listOf(null, "z"), CremaContext()))

        Env.set("APP_NAME", "Kona")
        assertEquals("Kona", CremaHelpers.env("APP_NAME", null))
        assertEquals("fallback", CremaHelpers.env("MISSING", "fallback"))
        assertEquals("fallback", CremaHelpers.env("", "fallback"))
        assertEquals("Kona", CremaHelpers.call("env", listOf("APP_NAME"), CremaContext()))
        assertEquals("fallback", CremaHelpers.call("env", listOf("MISSING", "fallback"), CremaContext()))
        assertEquals("fallback", CremaHelpers.call("env", listOf("", "fallback"), CremaContext()))
        assertEquals("fallback", CremaHelpers.call("env", listOf(null, "fallback"), CremaContext()))

        assertNull(CremaHelpers.config("app.name"))
        assertNull(CremaHelpers.config(""))
        Kona.create(temp).boot()
        Kona.app().config.set("app.name", "Echo")
        assertEquals("Echo", CremaHelpers.config("app.name"))
        assertEquals("Echo", CremaHelpers.call("config", listOf("app.name"), CremaContext()))
        assertNull(CremaHelpers.call("config", emptyList<Any?>(), CremaContext()))

        val translator = Translator()
        translator.addLines("en", mapOf("hi" to "Hello", "greet" to "Hi :name"))
        Lang.setTranslator(translator)
        assertEquals("", CremaHelpers.trans("", null))
        assertEquals("Hello", CremaHelpers.trans("hi", null))
        assertEquals("Hi Ada", CremaHelpers.trans("greet", mapOf("name" to "Ada")))
        assertEquals("Hello", CremaHelpers.call("trans", listOf("hi"), CremaContext()))
        assertEquals("Hello", CremaHelpers.call("__", listOf("hi"), CremaContext()))
        assertEquals("", CremaHelpers.call("trans", emptyList<Any?>(), CremaContext()))
        assertEquals("", CremaHelpers.call("__", emptyList<Any?>(), CremaContext()))
        assertEquals("Hi Ada", CremaHelpers.call("trans", listOf("greet", mapOf("name" to "Ada")), CremaContext()))
        assertEquals("Hi Ada", CremaHelpers.call("__", listOf("greet", mapOf("name" to "Ada")), CremaContext()))
        val langCtx = CremaContext().set("lang", mapOf("k" to "FromCtx"))
        assertEquals("FromCtx", CremaHelpers.lang("k", null, langCtx))
        assertEquals("", CremaHelpers.lang("k", null, CremaContext().set("lang", mapOf("k" to null))))
        assertEquals("Hello", CremaHelpers.lang("hi", null, CremaContext()))
        assertEquals("k", CremaHelpers.lang("k", null, CremaContext().set("lang", "not-a-map")))
        assertEquals("k", CremaHelpers.lang("k", null, CremaContext().set("lang", mapOf("other" to "x"))))
        assertEquals("FromCtx", CremaHelpers.call("lang", listOf("k"), langCtx))
        assertEquals("FromCtx", CremaHelpers.call("lang", listOf("k", mapOf("n" to "Ada")), langCtx))
        assertEquals("Hello", CremaHelpers.call("lang", listOf("hi", "not-a-map"), CremaContext()))
        assertEquals("", CremaHelpers.call("lang", emptyList<Any?>(), CremaContext()))
        assertEquals("/", CremaHelpers.call("url", listOf(null), CremaContext()))
    }

    @Test
    fun echoesExpandUrlHelpers() {
        val finder = MapViewFinder()
        finder.put("logo", """<img src="{{ url('/branding/kona-logo.svg') }}" />""")
        finder.put("link", """<a href="{{ url('/') }}">home</a>""")
        val crema = CremaEngine(finder)
        assertEquals(
            """<img src="/branding/kona-logo.svg" />""",
            crema.render("logo"),
        )
        Url.setGenerator(UrlGenerator(Router()).setRoot("https://app.test"))
        assertEquals(
            """<img src="https://app.test/branding/kona-logo.svg" />""",
            crema.render("logo"),
        )
        assertEquals("""<a href="https://app.test/">home</a>""", crema.render("link"))
    }

    @Test
    fun iconHelperRendersSvgFromKonaIcons() {
        assertTrue(CremaHelpers.HELPERS.any { it.name == "icon" })
        assertTrue(CremaHelpers.isCallable("icon"))
        assertEquals("", CremaHelpers.icon(null))
        assertEquals("", CremaHelpers.icon("  "))
        val bare = CremaHelpers.icon("heroicon-o-bell")
        assertTrue(bare.contains("<svg"), bare)
        assertTrue(bare.contains("viewBox"), bare)
        val styled = CremaHelpers.icon("heroicon-o-home", "w-5 h-5")
        assertTrue(styled.contains("w-5 h-5"), styled)
        assertEquals(bare, CremaHelpers.call("icon", listOf("heroicon-o-bell"), CremaContext()))
        val finder = MapViewFinder()
        finder.put("bell", "{!! icon('heroicon-o-bell', 'mark') !!}")
        val html = CremaEngine(finder).render("bell")
        assertTrue(html.contains("<svg"), html)
        assertTrue(html.contains("mark"), html)
        assertFalse(html.contains("&lt;svg"), html)
    }
}
