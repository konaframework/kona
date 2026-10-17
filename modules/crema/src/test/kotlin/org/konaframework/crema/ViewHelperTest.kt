package org.konaframework.crema

import org.konaframework.core.Application
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ViewHelperTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun tearDown() {
        Views.clearEngine()
    }

    private fun bind(vararg pairs: Pair<String, String>): CremaEngine {
        val finder = MapViewFinder()
        pairs.forEach { (k, v) -> finder.put(k, v) }
        val engine = CremaEngine(finder)
        Views.setEngine(engine)
        return engine
    }

    @Test
    fun topLevelViewMakeWithAndRespond() {
        bind("welcome" to "<h1>{{ name }}</h1>")
        val built = view("welcome").with("name", "Ada")
        assertEquals("welcome", built.name)
        assertEquals(mapOf("name" to "Ada"), built.data())
        assertEquals("<h1>Ada</h1>", built.render())
        assertEquals("<h1>Kona</h1>", view("welcome", mapOf("name" to "Kona")).render())
        val response = view("welcome", mapOf("name" to "Lin")).toResponse(201)
        assertEquals(201, response.status)
        assertTrue(response.body.contains("Lin"))
        assertTrue(response.headers["Content-Type"]!!.contains("html"))
    }

    @Test
    fun viewsFacadeExistsFirstShareAndResponse() {
        val engine = bind(
            "a" to "A-{{ app }}",
            "nested.show" to "N-{{ app }}-{{ title }}",
        )
        assertTrue(Views.configured())
        assertTrue(Views.exists("a"))
        assertTrue(Views.exists("nested/show"))
        assertFalse(Views.exists("missing"))

        Views.share("app", "Kona")
        assertEquals(mapOf("app" to "Kona"), Views.shared())
        assertEquals("A-Kona", Views.render("a"))
        assertEquals("N-Kona-Hi", Views.render("nested.show", mapOf("title" to "Hi")))

        val first = Views.first(listOf("missing", "nested/show"), mapOf("title" to "X"))
        assertEquals("nested.show", first.name)
        assertEquals("N-Kona-X", first.render())

        assertThrows(ViewNotFoundException::class.java) {
            Views.first(listOf("nope", "also-no"))
        }
        assertThrows(ViewNotFoundException::class.java) {
            engine.first(emptyList())
        }

        val html = Views.response("a", status = 202)
        assertEquals(202, html.status)
        assertEquals("A-Kona", html.body)

        Views.share(mapOf("app" to "Override", "extra" to 1))
        assertEquals("A-Override", view("a").render())
        assertEquals(1, Views.shared()["extra"])

        Views.flushShared()
        assertTrue(Views.shared().isEmpty())
        assertEquals("A-", Views.render("a"))
    }

    @Test
    fun slashAndDotNamesAreEquivalent() {
        bind("posts.show" to "Post {{ id }}")
        assertEquals("Post 9", view("posts/show", mapOf("id" to 9)).render())
        assertEquals("Post 9", Views.render("posts.show", mapOf("id" to 9)))
        assertTrue(Views.exists("posts/show"))
    }

    @Test
    fun sharedFillsContextGapsOnly() {
        val engine = bind("t" to "{{ app }}|{{ title }}")
        engine.share("app", "Shared")
        engine.share("title", "FromShare")
        val ctx = CremaContext().merge(mapOf("title" to "Local"))
        assertEquals("Shared|Local", engine.render("t", ctx))
        assertEquals("Shared|Local", engine.renderSource("{{ app }}|{{ title }}", ctx))
    }

    @Test
    fun cremaViewUsesBoundEngine() {
        bind("g" to "ok-{{ n }}")
        assertEquals("ok-1", Crema.view("g", mapOf("n" to 1)).body)
        assertEquals("ok-", Crema.view("g").body)
    }

    @Test
    fun providerBootsViewsAndClearThrows() {
        Views.clearEngine()
        assertFalse(Views.configured())
        assertThrows(IllegalStateException::class.java) { Views.engine() }
        assertThrows(IllegalStateException::class.java) { view("x") }

        val views = temp.resolve("src/main/resources/views")
        Files.createDirectories(views)
        Files.writeString(views.resolve("home.crema.html"), "Home {{ name }}")
        val app = Application(temp)
        app.register(CremaServiceProvider())
        app.boot()
        assertTrue(Views.configured())
        assertEquals("Home Ada", view("home", mapOf("name" to "Ada")).render())
        assertEquals(Views.engine(), app.resolve(CremaEngine::class.java))
    }

    @Test
    fun mapFinderNormalizesPutKeys() {
        val finder = MapViewFinder().put("posts/show", "P")
        assertEquals("P", finder.get("posts.show"))
        assertEquals("P", finder.get("posts/show"))
    }
}
