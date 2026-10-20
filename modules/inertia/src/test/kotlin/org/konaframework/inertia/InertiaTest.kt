package org.konaframework.inertia

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.MapViewFinder
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.inertia.assets.AssetManifest
import org.konaframework.inertia.assets.Vite
import org.konaframework.inertia.assets.ViteException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class InertiaTest {
    @TempDir
    lateinit var temp: Path

    private val mapper = jacksonObjectMapper()

    @AfterEach
    fun tearDown() {
        Inertia.reset()
        Vite.reset()
        Kona.reset()
    }

    @Test
    fun firstVisitReturnsHtmlShellWithDataPage() {
        val app = boot()
        pushInertia(app)
        app.resolve(Router::class.java).get("/") {
            Inertia.render("Pages/Home", mapOf("title" to "Hi"))
        }

        val response = app.resolve(HttpKernel::class.java).handle(Request.get("/"))
        assertEquals(200, response.status)
        assertTrue(response.headers["Content-Type"]!!.contains("text/html"))
        assertTrue(response.body.contains("data-page="))
        assertTrue(response.body.contains("Pages/Home"))
        assertTrue(response.body.contains("Hi"))
        assertNull(response.headers["X-Inertia"])
    }

    @Test
    fun inertiaRequestReturnsJsonPage() {
        val app = boot()
        Inertia.version("v1")
        pushInertia(app)
        app.resolve(Router::class.java).get("/home") {
            Inertia.render("Pages/Home", mapOf("n" to 1))
        }

        val response = app.resolve(HttpKernel::class.java).handle(
            Request("GET", "/home", headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "v1")),
        )
        assertEquals(200, response.status)
        assertEquals("true", response.headers["X-Inertia"])
        assertEquals("v1", response.headers["X-Inertia-Version"])
        assertEquals("X-Inertia", response.headers["Vary"])
        val page = mapper.readTree(response.body)
        assertEquals("Pages/Home", page["component"].asText())
        assertEquals(1, page["props"]["n"].asInt())
        assertEquals("/home", page["url"].asText())
        assertEquals("v1", page["version"].asText())
    }

    @Test
    fun versionMismatchReturns409() {
        val app = boot()
        Inertia.version("server")
        pushInertia(app)
        app.resolve(Router::class.java).get("/") {
            Inertia.render("Pages/Home")
        }

        val response = app.resolve(HttpKernel::class.java).handle(
            Request("GET", "/", headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "client")),
        )
        assertEquals(409, response.status)
        assertEquals("true", response.headers["X-Inertia"])
        assertEquals("server", response.headers["X-Inertia-Version"])
    }

    @Test
    fun sharedPropsMergeIntoPage() {
        val app = boot()
        Inertia.share("appName", "Kona")
        Inertia.share(mapOf("flash" to "ok"))
        pushInertia(
            app,
            object : HandleInertiaRequests() {
                override fun share(request: Request): Map<String, Any?> =
                    mapOf("path" to request.path)

                override fun version(request: Request): String = "shared-v"
            },
        )
        app.resolve(Router::class.java).get("/x") {
            Inertia.render("Pages/X", mapOf("local" to true, "flash" to "override"))
        }

        val response = app.resolve(HttpKernel::class.java).handle(
            Request("GET", "/x", headers = mapOf("X-Inertia" to "true")),
        )
        val props = mapper.readTree(response.body)["props"]
        assertEquals("Kona", props["appName"].asText())
        assertEquals("/x", props["path"].asText())
        assertEquals("override", props["flash"].asText())
        assertTrue(props["local"].asBoolean())
        assertEquals("shared-v", response.headers["X-Inertia-Version"])
        assertEquals(mapOf("appName" to "Kona", "flash" to "ok"), Inertia.manager().shared())
    }

    @Test
    fun cremaRootViewAndCustomRootHtml() {
        val engine = CremaEngine(
            MapViewFinder().put(
                "app",
                """<html><body data-page="{!! pageAttr !!}"></body></html>""",
            ),
        )
        val manager = InertiaManager()
        manager.cremaEngine = engine
        manager.rootView = "app"
        Inertia.setManager(manager)

        val html = manager.render(Request.get("/"), "Pages/A", mapOf("a" to 1))
        assertTrue(html.body.contains("data-page="))
        assertTrue(html.body.contains("Pages/A"))

        manager.rootView = null
        manager.rootHtml = "<main data-page='@page'></main>"
        val plain = manager.render(Request.get("/"), "Pages/B")
        assertTrue(plain.body.startsWith("<main data-page='"))
        assertTrue(plain.body.contains("Pages/B"))
        assertTrue(plain.body.contains("&quot;component&quot;"))
        assertTrue(plain.body.endsWith("'></main>"))

        assertEquals("&amp;&lt;&gt;&quot;&#39;", HtmlAttr.escape("&<>\"'"))
        assertEquals("", HtmlAttr.escape(""))
        assertEquals("ok", HtmlAttr.escape("ok"))
    }

    @Test
    fun locationRedirects() {
        val manager = InertiaManager()
        Inertia.setManager(manager)
        assertEquals("https://example.com", manager.location("https://example.com").headers["Location"])
        assertEquals(302, manager.location("https://example.com").status)

        manager.setRequest(Request("GET", "/", headers = mapOf("X-Inertia" to "true")))
        val inertiaLoc = manager.location("https://example.com")
        assertEquals(409, inertiaLoc.status)
        assertEquals("https://example.com", inertiaLoc.headers["X-Inertia-Location"])
        assertEquals("https://x", Inertia.location("https://x").headers["X-Inertia-Location"])
        manager.setRequest(null)
    }

    @Test
    fun facadeRequiresManagerAndExplicitRender() {
        assertThrows(InertiaException::class.java) { Inertia.manager() }
        assertThrows(InertiaException::class.java) { Inertia.render("X") }

        val manager = InertiaManager()
        Inertia.setManager(manager)
        assertThrows(InertiaException::class.java) { Inertia.render("X") }

        val response = Inertia.render(Request.get("/r"), "Pages/R", emptyMap())
        assertTrue(response.body.contains("Pages/R"))
        assertTrue(InertiaPage("C", emptyMap(), "/", null).toMap().containsKey("component"))
        manager.clearShared()
        assertTrue(manager.shared().isEmpty())
        assertFalse(manager.isInertiaRequest(Request.get("/")))
        assertNull(manager.versionConflict(Request("GET", "/", headers = mapOf("X-Inertia" to "true"))))
        manager.version = ""
        assertNull(
            manager.versionConflict(
                Request("GET", "/", headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "x")),
            ),
        )
    }

    @Test
    fun serviceProviderConfigAndViteBinding() {
        Files.createDirectories(temp.resolve("public/build"))
        Files.writeString(
            temp.resolve("public/build/manifest.json"),
            """{"resources/js/app.js":{"file":"assets/app.js","css":["assets/app.css"],"isEntry":true}}""",
        )
        val app = Application(temp)
        app.config.set("inertia.root_view", "shell")
        app.config.set("inertia.version", "cfg-v")
        app.config.set("inertia.root_html", "<div>@page</div>")
        app.config.set("inertia.public_path", temp.resolve("public").toString())
        app.register(HttpServiceProvider())
        app.container.instance(CremaEngine::class.java, CremaEngine(MapViewFinder().put("shell", "V{!! pageAttr !!}")))
        app.register(InertiaServiceProvider())
        app.boot()

        val inertia = app.resolve(InertiaManager::class.java)
        assertEquals("shell", inertia.rootView)
        assertEquals("cfg-v", inertia.version)
        assertEquals("<div>@page</div>", inertia.rootHtml)
        assertTrue(inertia.cremaEngine != null)

        assertEquals("/build/assets/app.js", Vite.asset("resources/js/app.js"))
        assertTrue(Vite.tags("resources/js/app.js").contains("assets/app.css"))
        assertTrue(Vite.tags("resources/js/app.js").contains("assets/app.js"))
    }

    @Test
    fun viteHotAndManifestErrors() {
        val public = temp.resolve("public")
        Files.createDirectories(public.resolve("build"))
        assertThrows(ViteException::class.java) { AssetManifest(public).loadManifest() }
        assertThrows(ViteException::class.java) { Vite.manifest() }
        assertThrows(ViteException::class.java) { Vite.tags("x") }

        Files.writeString(public.resolve("build/manifest.json"), "[1]")
        assertThrows(ViteException::class.java) { AssetManifest(public).loadManifest() }

        Files.writeString(
            public.resolve("build/manifest.json"),
            """{"entry.js":{"src":"entry.js"}}""",
        )
        val manifest = AssetManifest(public)
        assertThrows(ViteException::class.java) { manifest.entry("missing.js") }
        assertThrows(ViteException::class.java) { manifest.tags("entry.js") }
        assertThrows(ViteException::class.java) { manifest.assetUrl("entry.js") }

        Files.writeString(public.resolve("hot"), "   ")
        assertNull(manifest.hotUrl())
        Files.writeString(public.resolve("hot"), "http://127.0.0.1:5173\n")
        assertEquals("http://127.0.0.1:5173", manifest.hotUrl())
        assertEquals(
            """
            <script type="module" src="http://127.0.0.1:5173/@vite/client"></script>
            <script type="module" src="http://127.0.0.1:5173/resources/js/app.js"></script>
            """.trimIndent(),
            manifest.tags("resources/js/app.js"),
        )
        assertEquals("http://127.0.0.1:5173/resources/js/app.js", manifest.assetUrl("resources/js/app.js"))

        Vite.setManifest(manifest)
        assertEquals(manifest, Vite.manifest())
        Vite.reset()
        assertThrows(ViteException::class.java) { Vite.asset("x") }
    }

    @Test
    fun decorateAddsVersionOnlyOnInertiaResponses() {
        val manager = InertiaManager()
        manager.version = "v9"
        val plain = manager.decorate(Response.text("ok"))
        assertNull(plain.headers["X-Inertia-Version"])
        val tagged = manager.decorate(Response.json(mapOf("a" to 1)).header("X-Inertia", "true"))
        assertEquals("v9", tagged.headers["X-Inertia-Version"])
        val loc = manager.decorate(Response(status = 409, body = "").header("X-Inertia-Location", "/x"))
        assertEquals("v9", loc.headers["X-Inertia-Version"])
    }

    @Test
    fun htmlShellIncludesViteTagsWhenManifestPresent() {
        Files.createDirectories(temp.resolve("public/build"))
        Files.writeString(
            temp.resolve("public/build/manifest.json"),
            """{"src/main/resources/js/app.js":{"file":"assets/app.js","css":["assets/app.css"],"isEntry":true},"entry2.js":{"file":"assets/e2.js","isEntry":true}}""",
        )
        Vite.setManifest(AssetManifest(temp.resolve("public")))
        val manager = InertiaManager()
        manager.rootHtml = "<html>@vite<div data-page='@page'></div></html>"
        Inertia.setManager(manager)
        val html = manager.render(Request.get("/"), "Pages/Home")
        assertTrue(html.body.contains("/build/assets/app.js"))
        assertTrue(html.body.contains("/build/assets/app.css"))
        assertTrue(Vite.tags("entry2.js").contains("e2.js"))
        assertFalse(Vite.tags("entry2.js").contains("stylesheet"))

        manager.viteEntries = emptyList()
        val noVite = manager.render(Request.get("/"), "Pages/Home")
        assertFalse(noVite.body.contains("/build/assets/app.js"))
    }

    @Test
    fun remainingBranchCoverage() {
        assertTrue(InertiaManager.DEFAULT_ROOT_HTML.contains("data-page"))

        val manager = InertiaManager()
        Inertia.setManager(manager)
        manager.setRequest(Request.get("/current"))
        val viaFacade = Inertia.render("Pages/Via")
        assertTrue(viaFacade.body.contains("Pages/Via"))
        val viaRequest = Inertia.render(Request.get("/explicit"), "Pages/Explicit", mapOf("a" to 1))
        assertTrue(viaRequest.body.contains("Pages/Explicit"))
        assertTrue(manager.render(Request.get("/two-arg"), "Pages/TwoArg").body.contains("Pages/TwoArg"))

        // Non-Inertia current request → 302 location
        assertEquals(302, Inertia.location("/go").status)

        // rootView set without engine falls back to rootHtml
        manager.rootView = "missing"
        manager.cremaEngine = null
        manager.rootHtml = "<x>@page</x>"
        assertTrue(manager.render(Request.get("/"), "Pages/Z").body.startsWith("<x>"))

        // engine set without rootView also uses rootHtml
        manager.rootView = null
        manager.cremaEngine = CremaEngine(MapViewFinder().put("unused", "x"))
        assertTrue(manager.render(Request.get("/"), "Pages/Y").body.startsWith("<x>"))

        // decorate / json with empty version
        manager.version = ""
        val json = manager.render(
            Request("GET", "/", headers = mapOf("X-Inertia" to "true")),
            "Pages/J",
        )
        assertNull(json.headers["X-Inertia-Version"])
        assertNull(manager.decorate(Response.text("t").header("X-Inertia", "true")).headers["X-Inertia-Version"])

        // Default public path (no inertia.public_path)
        Files.createDirectories(temp.resolve("public/build"))
        Files.writeString(
            temp.resolve("public/build/manifest.json"),
            """{"a.js":{"file":"assets/a.js"}}""",
        )
        val app = Application(temp)
        app.register(HttpServiceProvider())
        app.register(InertiaServiceProvider())
        app.boot()
        assertEquals("/build/assets/a.js", Vite.asset("a.js"))
    }

    @Test
    fun spaShellUsesDevServerOrHashedBuild() {
        val public = temp.resolve("public")
        Files.createDirectories(public)
        val entry = "src/main/resources/js/app.js"
        Files.writeString(public.resolve("hot"), "http://127.0.0.1:5173\n")
        Vite.setManifest(AssetManifest(public))
        val manager = InertiaManager()
        manager.rootView = "app"
        manager.cremaEngine = CremaEngine(
            MapViewFinder().put("app", "<head>{!! vite !!}</head>"),
        )
        Inertia.setManager(manager)
        val dev = manager.render(Request.get("/"), "Pages/Welcome").body
        assertTrue(dev.contains("http://127.0.0.1:5173/@vite/client"), dev)
        assertTrue(dev.contains("http://127.0.0.1:5173/$entry"), dev)
        assertFalse(dev.contains("/build/$entry"), dev)

        Files.delete(public.resolve("hot"))
        Files.createDirectories(public.resolve("build/.vite"))
        Files.writeString(
            public.resolve("build/.vite/manifest.json"),
            """{"$entry":{"file":"assets/app-Ab12.js","css":["assets/app-Cd34.css"],"isEntry":true}}""",
        )
        val prod = manager.render(Request.get("/"), "Pages/Welcome").body
        assertTrue(prod.contains("/build/assets/app-Ab12.js"), prod)
        assertTrue(prod.contains("/build/assets/app-Cd34.css"), prod)
        assertFalse(prod.contains("/build/$entry"), prod)

        val asset = public.resolve("build/assets/app-Ab12.js")
        Files.createDirectories(asset.parent)
        Files.writeString(asset, "console.log('spa')")
        val app = Application(temp)
        app.register(HttpServiceProvider())
        app.register(InertiaServiceProvider())
        app.boot()
        val kernel = app.resolve(HttpKernel::class.java)
        val served = kernel.handle(Request.get("/build/assets/app-Ab12.js"))
        assertEquals(200, served.status)
        assertEquals("console.log('spa')", String(served.bodyBytes(), Charsets.UTF_8))
        assertEquals(404, kernel.handle(Request.get("/build/$entry")).status)
    }

    private fun boot(): Application =
        Kona.create(arrayOf("--base=$temp"))
            .providers(HttpServiceProvider(), InertiaServiceProvider())
            .boot()

    private fun pushInertia(app: Application, middleware: HandleInertiaRequests = HandleInertiaRequests()) {
        app.resolve(HttpKernel::class.java).pushMiddleware(middleware)
    }
}
