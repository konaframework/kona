package org.konaframework.inertia

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.MapViewFinder
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
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
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.function.Supplier

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
        assertTrue(response.body.contains("errors"))
        assertTrue(response.body.contains("encryptHistory"))
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
        assertFalse(page["encryptHistory"].asBoolean())
        assertFalse(page["clearHistory"].asBoolean())
        assertTrue(page["props"].has("errors"))
        assertTrue(page["props"].has("flash"))
    }

    @Test
    fun versionMismatchReturns409WithLocation() {
        val app = boot()
        Inertia.version("server")
        pushInertia(app)
        app.resolve(Router::class.java).get("/dash") {
            Inertia.render("Pages/Home")
        }

        val response = app.resolve(HttpKernel::class.java).handle(
            Request(
                "GET",
                "/dash",
                headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "client"),
                query = mapOf("tab" to "1"),
            ),
        )
        assertEquals(409, response.status)
        assertEquals("/dash?tab=1", response.headers["X-Inertia-Location"])
        assertEquals("server", response.headers["X-Inertia-Version"])
        assertNull(response.headers["X-Inertia"])
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
    fun partialReloadHonorsOnlyExceptAndAlways() {
        val app = boot()
        pushInertia(app)
        app.resolve(Router::class.java).get("/users") {
            Inertia.render(
                "Users/Index",
                mapOf(
                    "users" to listOf("a"),
                    "companies" to listOf("c"),
                    "meta" to mapOf("page" to 1),
                    "secret" to Inertia.always("keep"),
                ),
            )
        }

        val only = app.resolve(HttpKernel::class.java).handle(
            Request(
                "GET",
                "/users",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "Users/Index",
                    "X-Inertia-Partial-Data" to "users,meta",
                ),
            ),
        )
        val onlyProps = mapper.readTree(only.body)["props"]
        assertTrue(onlyProps.has("users"))
        assertTrue(onlyProps.has("meta"))
        assertTrue(onlyProps.has("secret"))
        assertTrue(onlyProps.has("errors"))
        assertFalse(onlyProps.has("companies"))

        val except = app.resolve(HttpKernel::class.java).handle(
            Request(
                "GET",
                "/users",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "Users/Index",
                    "X-Inertia-Partial-Data" to "users,companies,meta",
                    "X-Inertia-Partial-Except" to "companies",
                ),
            ),
        )
        val exceptProps = mapper.readTree(except.body)["props"]
        assertTrue(exceptProps.has("users"))
        assertFalse(exceptProps.has("companies"))
        assertTrue(exceptProps.has("meta"))

        val otherComponent = app.resolve(HttpKernel::class.java).handle(
            Request(
                "GET",
                "/users",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "Other",
                    "X-Inertia-Partial-Data" to "users",
                ),
            ),
        )
        val full = mapper.readTree(otherComponent.body)["props"]
        assertTrue(full.has("users"))
        assertTrue(full.has("companies"))
    }

    @Test
    fun optionalDeferredAndMergeProps() {
        val app = boot()
        pushInertia(app)
        app.resolve(Router::class.java).get("/feed") {
            Inertia.render(
                "Feed",
                mapOf(
                    "title" to "Feed",
                    "stats" to Inertia.optional { mapOf("n" to 3) },
                    "lazy" to Inertia.lazy { "late" },
                    "comments" to Inertia.defer({ listOf("c1") }, "sidebar"),
                    "roles" to Inertia.defer { listOf("admin") },
                    "items" to Inertia.merge(listOf(1, 2)),
                    "tree" to Inertia.deepMerge(mapOf("a" to 1)),
                    "front" to Inertia.merge(listOf(9)).prepend(),
                    "matched" to Inertia.merge(listOf(mapOf("id" to 1))).matchOn("id"),
                    "combo" to Inertia.defer { listOf(1) }.deepMerge(),
                ),
            )
        }

        val full = app.resolve(HttpKernel::class.java).handle(
            Request("GET", "/feed", headers = mapOf("X-Inertia" to "true")),
        )
        val fullPage = mapper.readTree(full.body)
        val fullProps = fullPage["props"]
        assertEquals("Feed", fullProps["title"].asText())
        assertFalse(fullProps.has("stats"))
        assertFalse(fullProps.has("lazy"))
        assertFalse(fullProps.has("comments"))
        assertFalse(fullProps.has("roles"))
        assertFalse(fullProps.has("combo"))
        assertEquals(listOf(1, 2), mapper.convertValue(fullProps["items"], List::class.java))
        val deferred = fullPage["deferredProps"]
        assertEquals(listOf("comments"), mapper.convertValue(deferred["sidebar"], List::class.java))
        assertTrue(mapper.convertValue(deferred["default"], List::class.java).containsAll(listOf("roles", "combo")))
        assertTrue(
            mapper.convertValue(fullPage["mergeProps"], List::class.java).containsAll(listOf("items", "matched")),
        )
        assertEquals(listOf("front"), mapper.convertValue(fullPage["prependProps"], List::class.java))
        assertTrue(
            mapper.convertValue(fullPage["deepMergeProps"], List::class.java).containsAll(listOf("tree", "combo")),
        )
        assertEquals(listOf("matched.id"), mapper.convertValue(fullPage["matchPropsOn"], List::class.java))

        val partial = app.resolve(HttpKernel::class.java).handle(
            Request(
                "GET",
                "/feed",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "Feed",
                    "X-Inertia-Partial-Data" to "stats,comments,items",
                ),
            ),
        )
        val partialPage = mapper.readTree(partial.body)
        val partialProps = partialPage["props"]
        assertEquals(3, partialProps["stats"]["n"].asInt())
        assertEquals(listOf("c1"), mapper.convertValue(partialProps["comments"], List::class.java))
        assertFalse(partialPage.has("deferredProps"))
        assertEquals(listOf("items"), mapper.convertValue(partialPage["mergeProps"], List::class.java))
    }

    @Test
    fun validationErrorsAndErrorBagFromSession() {
        val session = FakeSession()
        session.put("errors", mapOf("email" to listOf("Required", "Invalid"), "name" to listOf("Too short")))
        session.put("success", "Saved")
        val request = Request.get("/form").setAttribute("session", session)

        val middleware = HandleInertiaRequests()
        val errors = middleware.resolveValidationErrors(request)
        assertEquals("Required", errors["email"])
        assertEquals("Too short", errors["name"])

        val bagged = middleware.resolveValidationErrors(
            Request(
                "GET",
                "/form",
                headers = mapOf("X-Inertia-Error-Bag" to "createUser"),
            ).setAttribute("session", session),
        )
        assertTrue(bagged.containsKey("createUser"))
        @Suppress("UNCHECKED_CAST")
        val nested = bagged["createUser"] as Map<String, Any?>
        assertEquals("Required", nested["email"])

        assertEquals(emptyMap<String, Any?>(), middleware.resolveValidationErrors(Request.get("/")))
        assertEquals(
            emptyMap<String, Any?>(),
            middleware.resolveValidationErrors(Request.get("/").setAttribute("session", FakeSession())),
        )
        assertEquals(
            emptyMap<String, Any?>(),
            HandleInertiaRequests.coerceErrorBag("nope"),
        )
        assertEquals(
            mapOf("a" to "1", "b" to "x", "c" to ""),
            HandleInertiaRequests.coerceErrorBag(
                mapOf(
                    "a" to arrayOf("1", "2"),
                    "b" to "x",
                    null to "skip",
                    "c" to emptyList<String>(),
                ),
            ),
        )

        val flash = middleware.resolveFlash(request)
        assertEquals("Saved", flash["success"])
        assertNull(flash["error"])
    }

    @Test
    fun seeOtherAfterPutPatchDelete() {
        val manager = InertiaManager()
        Inertia.setManager(manager)
        val inertiaReq = Request("PUT", "/x", headers = mapOf("X-Inertia" to "true"))
        val redirected = manager.seeOtherRedirect(inertiaReq, Response.redirect("/back"))
        assertEquals(303, redirected.status)
        assertEquals("/back", redirected.headers["Location"])

        assertEquals(302, manager.seeOtherRedirect(Request.get("/"), Response.redirect("/back")).status)
        assertEquals(
            200,
            manager.seeOtherRedirect(
                Request("PUT", "/x", headers = mapOf("X-Inertia" to "true")),
                Response.json(mapOf("ok" to true)),
            ).status,
        )
        assertEquals(
            302,
            manager.seeOtherRedirect(
                Request("POST", "/x", headers = mapOf("X-Inertia" to "true")),
                Response.redirect("/back"),
            ).status,
        )

        val app = boot()
        pushInertia(app)
        app.resolve(Router::class.java).put("/items/1") {
            Response.redirect("/items")
        }
        val response = app.resolve(HttpKernel::class.java).handle(
            Request("PUT", "/items/1", headers = mapOf("X-Inertia" to "true")),
        )
        assertEquals(303, response.status)
    }

    @Test
    fun historyEncryptionAndClearHistory() {
        val manager = InertiaManager()
        manager.encryptHistory = true
        Inertia.setManager(manager)
        val encrypted = manager.render(Request.get("/"), "Pages/A")
        assertTrue(mapper.readTree(extractDataPage(encrypted.body))["encryptHistory"].asBoolean())

        manager.encryptHistory = false
        manager.setRequest(Request.get("/"))
        Inertia.encryptHistory(true)
        Inertia.clearHistory()
        val flagged = manager.render("Pages/B")
        val page = mapper.readTree(extractDataPage(flagged.body))
        assertTrue(page["encryptHistory"].asBoolean())
        assertTrue(page["clearHistory"].asBoolean())
        manager.setRequest(null)

        val session = FakeSession()
        session.flash(InertiaManager.CLEAR_HISTORY_SESSION_KEY, true)
        val fromSession = manager.render(
            Request.get("/").setAttribute("session", session),
            "Pages/C",
        )
        assertTrue(mapper.readTree(extractDataPage(fromSession.body))["clearHistory"].asBoolean())

        val app = Application(temp)
        app.config.set("inertia.history.encrypt", "true")
        app.config.set("inertia.ssr.enabled", "1")
        app.config.set("inertia.ssr.url", "http://127.0.0.1:9")
        app.register(HttpServiceProvider())
        app.register(InertiaServiceProvider())
        app.boot()
        assertTrue(app.resolve(InertiaManager::class.java).encryptHistory)
        assertTrue(app.resolve(InertiaManager::class.java).ssr.enabled)
        assertEquals("http://127.0.0.1:9", app.resolve(InertiaManager::class.java).ssr.url)
    }

    @Test
    fun ssrGatewayParsesAndFallsBack() {
        val gateway = SsrGateway()
        assertNull(gateway.dispatch(mapOf("component" to "X")))
        assertFalse(gateway.isHealthy())

        gateway.enabled = true
        gateway.url = "http://127.0.0.1:1"
        assertNull(gateway.dispatch(mapOf("component" to "X")))
        assertFalse(gateway.isHealthy())

        assertEquals(
            SsrResponse(head = "H1\nH2", body = "<p>hi</p>"),
            gateway.parse("""{"head":["H1","H2"],"body":"<p>hi</p>"}"""),
        )
        assertEquals(
            SsrResponse(head = "<meta>", body = "body"),
            gateway.parse("""{"head":"<meta>","body":"body"}"""),
        )
        assertEquals(SsrResponse(head = "", body = ""), gateway.parse("{}"))
        assertEquals("http://x/render", SsrGateway.join("http://x/", "render"))
        assertEquals("http://x/render", SsrGateway.join("http://x", "/render"))

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/render") { exchange ->
            val payload = """{"head":["<title>SSR</title>"],"body":"<p>ssr</p>"}"""
            val bytes = payload.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/health") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.createContext("/fail") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        try {
            val port = server.address.port
            gateway.url = "http://127.0.0.1:$port"
            assertTrue(gateway.isHealthy())
            assertEquals(SsrResponse("<title>SSR</title>", "<p>ssr</p>"), gateway.dispatch(mapOf("c" to 1)))

            val manager = InertiaManager()
            manager.ssr.enabled = true
            manager.ssr.url = "http://127.0.0.1:$port"
            manager.rootHtml = "<head>@ssrHead</head><div data-page='@page'>@ssrBody</div>"
            Inertia.setManager(manager)
            val html = manager.render(Request.get("/"), "Pages/Ssr", mapOf("ok" to true))
            assertTrue(html.body.contains("<title>SSR</title>"))
            assertTrue(html.body.contains("<p>ssr</p>"))
            assertTrue(html.body.contains("Pages/Ssr"))

            manager.ssr.url = "http://127.0.0.1:$port/fail-base"
            // wrong path → fallback CSR
            val fallback = manager.render(Request.get("/"), "Pages/Fb")
            assertTrue(fallback.body.contains("Pages/Fb"))
            assertFalse(fallback.body.contains("<title>SSR</title>"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun emptyInertiaResponseRedirectsBack() {
        val app = boot()
        pushInertia(app)
        app.resolve(Router::class.java).post("/empty") {
            Response(status = 200, body = "")
        }
        val response = app.resolve(HttpKernel::class.java).handle(
            Request(
                "POST",
                "/empty",
                headers = mapOf("X-Inertia" to "true", "Referer" to "/previous"),
            ),
        )
        assertEquals(302, response.status)
        assertEquals("/previous", response.headers["Location"])
    }

    @Test
    fun cremaRootViewAndCustomRootHtml() {
        val engine = CremaEngine(
            MapViewFinder().put(
                "app",
                """<html><head>{!! ssrHead !!}</head><body data-page="{!! pageAttr !!}">{!! ssrBody !!}</body></html>""",
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
        manager.rootHtml = "<main data-page='@page'>@ssrBody</main>"
        val plain = manager.render(Request.get("/"), "Pages/B")
        assertTrue(plain.body.startsWith("<main data-page='"))
        assertTrue(plain.body.contains("Pages/B"))
        assertTrue(plain.body.contains("&quot;component&quot;"))
        assertTrue(plain.body.endsWith("</main>"))

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
    fun facadeRequiresManagerAndPropHelpers() {
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

        assertEquals(1, Inertia.optional(Supplier { 1 }).resolve())
        assertEquals(2, Inertia.optional { 2 }.resolve())
        assertEquals("lazy", Inertia.lazy(Supplier { "lazy" }).resolve())
        assertEquals("lazy2", Inertia.lazy { "lazy2" }.resolve())
        assertEquals("a", Inertia.always("a").resolve())
        assertEquals("b", Inertia.always(Supplier { "b" }).resolve())
        assertEquals(listOf(1), Inertia.defer(Supplier { listOf(1) }).resolve())
        assertEquals("side", Inertia.defer({ "g" }, "side").group())
        assertEquals("g", Inertia.defer({ "g" }, "side").resolve())
        assertEquals(listOf(1), Inertia.merge(listOf(1)).resolve())
        assertTrue(Inertia.deepMerge(mapOf("x" to 1)).shouldDeepMerge())
        assertEquals(listOf(2), Inertia.merge(Supplier { listOf(2) }).resolve())
        val deferMerge = Inertia.defer { 1 }.merge().prepend()
        assertTrue(deferMerge.shouldMerge())
        assertTrue(deferMerge.prependsAtRoot())
        assertFalse(deferMerge.appendsAtRoot())
        assertEquals(emptyList<String>(), deferMerge.appendsAtPaths())
        assertEquals(emptyList<String>(), deferMerge.prependsAtPaths())
        assertEquals(emptyList<String>(), deferMerge.matchesOn())
        assertTrue(deferMerge.shouldDefer())
        assertNull(InertiaManager.parseHeaderList("  "))
        assertNull(InertiaManager.parseHeaderList(null))
        assertEquals(listOf("a", "b"), InertiaManager.parseHeaderList("a, b,"))
        assertEquals("/p", InertiaManager.requestUrl(Request.get("/p")))
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
        app.config.set("inertia.history.encrypt", "false")
        app.config.set("inertia.ssr.enabled", "false")
        app.register(HttpServiceProvider())
        app.container.instance(CremaEngine::class.java, CremaEngine(MapViewFinder().put("shell", "V{!! pageAttr !!}")))
        app.register(InertiaServiceProvider())
        app.boot()

        val inertia = app.resolve(InertiaManager::class.java)
        assertEquals("shell", inertia.rootView)
        assertEquals("cfg-v", inertia.version)
        assertEquals("<div>@page</div>", inertia.rootHtml)
        assertTrue(inertia.cremaEngine != null)
        assertFalse(inertia.encryptHistory)
        assertFalse(inertia.ssr.enabled)

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
        assertTrue(InertiaManager.DEFAULT_ROOT_HTML.contains("@ssrHead"))

        val manager = InertiaManager()
        Inertia.setManager(manager)
        manager.setRequest(Request.get("/current"))
        val viaFacade = Inertia.render("Pages/Via")
        assertTrue(viaFacade.body.contains("Pages/Via"))
        val viaRequest = Inertia.render(Request.get("/explicit"), "Pages/Explicit", mapOf("a" to 1))
        assertTrue(viaRequest.body.contains("Pages/Explicit"))
        assertTrue(manager.render(Request.get("/two-arg"), "Pages/TwoArg").body.contains("Pages/TwoArg"))

        assertEquals(302, Inertia.location("/go").status)

        manager.rootView = "missing"
        manager.cremaEngine = null
        manager.rootHtml = "<x>@page</x>"
        assertTrue(manager.render(Request.get("/"), "Pages/Z").body.startsWith("<x>"))

        manager.rootView = null
        manager.cremaEngine = CremaEngine(MapViewFinder().put("unused", "x"))
        assertTrue(manager.render(Request.get("/"), "Pages/Y").body.startsWith("<x>"))

        manager.version = ""
        val json = manager.render(
            Request("GET", "/", headers = mapOf("X-Inertia" to "true")),
            "Pages/J",
        )
        assertNull(json.headers["X-Inertia-Version"])
        assertNull(manager.decorate(Response.text("t").header("X-Inertia", "true")).headers["X-Inertia-Version"])

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

        // reset props + mergeable reset header
        val resetPage = manager.buildPage(
            Request(
                "GET",
                "/",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "P",
                    "X-Inertia-Partial-Data" to "items",
                    "X-Inertia-Reset" to "items",
                ),
            ),
            "P",
            mapOf("items" to Inertia.merge(listOf(1))),
            partial = true,
        )
        assertNull(resetPage.mergeProps)

        val exceptOnlyMerge = manager.buildPage(
            Request(
                "GET",
                "/",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "P",
                    "X-Inertia-Partial-Except" to "items",
                ),
            ),
            "P",
            mapOf("items" to Inertia.merge(listOf(1)), "other" to Inertia.merge(listOf(2))),
            partial = true,
        )
        assertEquals(listOf("other"), exceptOnlyMerge.mergeProps)

        assertEquals(
            false,
            manager.buildPage(Request.get("/"), "P", emptyMap(), false).clearHistory,
        )
        sessionClearFalse(manager)
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

    private fun sessionClearFalse(manager: InertiaManager) {
        val session = FakeSession()
        session.put(InertiaManager.CLEAR_HISTORY_SESSION_KEY, "no")
        assertFalse(
            manager.buildPage(
                Request.get("/").setAttribute("session", session),
                "P",
                emptyMap(),
                false,
            ).clearHistory,
        )
    }

    @Test
    fun coverageGapsForPropsSsrAndMiddleware() {
        val manager = InertiaManager()
        Inertia.setManager(manager)

        // no-arg encryptHistory + clearHistory with and without session
        manager.setRequest(Request.get("/"))
        Inertia.encryptHistory()
        manager.encryptHistory()
        Inertia.clearHistory()
        assertTrue(manager.buildPage(Request.get("/"), "P", emptyMap(), false).encryptHistory)
        val clearSession = FakeSession()
        manager.setRequest(Request.get("/").setAttribute("session", clearSession))
        Inertia.clearHistory()
        assertEquals(true, clearSession.get(InertiaManager.CLEAR_HISTORY_SESSION_KEY))
        manager.setRequest(null)

        for (flag in listOf("true", 1, "1")) {
            val session = FakeSession().also { it.put(InertiaManager.CLEAR_HISTORY_SESSION_KEY, flag) }
            assertTrue(
                manager.buildPage(
                    Request.get("/").setAttribute("session", session),
                    "P",
                    emptyMap(),
                    false,
                ).clearHistory,
            )
        }

        assertNull(InertiaManager.parseHeaderList(","))

        // only-header key missing from props
        val missingOnly = manager.buildPage(
            Request(
                "GET",
                "/",
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "P",
                    "X-Inertia-Partial-Data" to "missing,title",
                ),
            ),
            "P",
            mapOf("title" to "t"),
            partial = true,
        )
        assertTrue(missingOnly.props.containsKey("title"))
        assertFalse(missingOnly.props.containsKey("missing"))

        // Deferrable that opts out of deferral
        val sticky = object : Deferrable, ResolvableProp {
            override fun group(): String = "default"
            override fun shouldDefer(): Boolean = false
            override fun resolve(): Any? = "sticky"
        }
        val deferred = manager.buildPage(
            Request.get("/"),
            "P",
            mapOf("sticky" to sticky),
            partial = false,
        )
        assertNull(deferred.deferredProps)
        assertEquals("sticky", deferred.props["sticky"])
        val withGone = manager.buildPage(
            Request.get("/"),
            "P",
            mapOf("sticky" to sticky, "gone" to Inertia.defer { "x" }),
            partial = false,
        )
        assertEquals(listOf("gone"), withGone.deferredProps!!["default"])
        assertFalse(withGone.props.containsKey("gone"))

        assertEquals(listOf(1), Inertia.merge(listOf(1)).merge().resolve())
        val mergedDefer = Inertia.defer { 1 }.merge()
        assertTrue(mergedDefer.appendsAtRoot())
        assertFalse(mergedDefer.prependsAtRoot())
        val deepDefer = Inertia.defer { 1 }.deepMerge()
        assertTrue(deepDefer.shouldDeepMerge())
        assertFalse(deepDefer.appendsAtRoot())
        assertFalse(deepDefer.prependsAtRoot())
        val plainDefer = Inertia.defer { 1 }
        assertFalse(plainDefer.appendsAtRoot())
        assertFalse(plainDefer.prependsAtRoot())
        assertFalse(plainDefer.shouldMerge())
        val prepended = Inertia.defer { 1 }.prepend()
        assertTrue(prepended.prependsAtRoot())
        assertFalse(prepended.appendsAtRoot())
        assertFalse(Inertia.defer { 1 }.prepend().deepMerge().prependsAtRoot())

        // resolvePropValue branches
        assertNull(resolvePropValue(null))
        val fn: () -> Any? = { 3 }
        assertEquals(3, resolvePropValue(fn))
        assertEquals(4, resolvePropValue(java.util.concurrent.Callable { 4 }))
        assertEquals(5, resolvePropValue(java.util.function.Supplier { 5 }))

        // SSR non-2xx + null head
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/render") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.createContext("/health") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        try {
            val gateway = SsrGateway()
            gateway.enabled = true
            gateway.url = "http://127.0.0.1:${server.address.port}"
            assertNull(gateway.dispatch(mapOf("a" to 1)))
            assertFalse(gateway.isHealthy())
            assertEquals(SsrResponse("", "b"), gateway.parse("""{"head":null,"body":"b"}"""))
            assertEquals(SsrResponse("H", ""), gateway.parse("""{"head":"H","body":null}"""))
            assertEquals(SsrResponse("", ""), gateway.parse("""{"head":null}"""))
            assertTrue(gateway.isSuccessStatus(200))
            assertTrue(gateway.isSuccessStatus(299))
            assertFalse(gateway.isSuccessStatus(199))
            assertFalse(gateway.isSuccessStatus(300))
        } finally {
            server.stop(0)
        }

        // clearHistory with no current request
        manager.clearHistory()

        val app = Application(temp)
        app.config.set("inertia.ssr.enabled", "TRUE")
        app.config.set("inertia.history.encrypt", "TRUE")
        app.register(HttpServiceProvider())
        app.register(InertiaServiceProvider())
        app.boot()
        assertTrue(app.resolve(InertiaManager::class.java).ssr.enabled)
        assertTrue(app.resolve(InertiaManager::class.java).encryptHistory)

        // empty defaultShare + empty bodyBytes path
        pushInertia(
            app,
            object : HandleInertiaRequests() {
                override fun defaultShare(request: Request): Map<String, Any?> = emptyMap()
            },
        )
        app.resolve(Router::class.java).get("/bare") {
            Inertia.render("Pages/Bare")
        }
        app.resolve(Router::class.java).post("/bytes") {
            Response(status = 200, body = "", bodyBytes = byteArrayOf(1, 2, 3))
        }
        val bare = app.resolve(HttpKernel::class.java).handle(
            Request("GET", "/bare", headers = mapOf("X-Inertia" to "true")),
        )
        assertEquals(200, bare.status)
        val bytesKeep = app.resolve(HttpKernel::class.java).handle(
            Request(
                "POST",
                "/bytes",
                headers = mapOf("X-Inertia" to "true"),
            ),
        )
        assertEquals(200, bytesKeep.status)

        assertEquals(
            mapOf("a" to "", "b" to "", "c" to "", "d" to "ok", "e" to "1", "f" to "z"),
            HandleInertiaRequests.coerceErrorBag(
                mapOf(
                    "a" to listOf(null),
                    "b" to arrayOf<Any?>(),
                    "c" to null,
                    "d" to listOf("ok", "more"),
                    "e" to arrayOf(1, 2),
                    "f" to "z",
                ),
            ),
        )
    }

    private fun extractDataPage(html: String): String {
        val marker = "data-page='"
        val start = html.indexOf(marker) + marker.length
        val end = html.indexOf("'", start)
        return html.substring(start, end)
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
    }

    private fun boot(): Application =
        Kona.create(arrayOf("--base=$temp"))
            .providers(HttpServiceProvider(), InertiaServiceProvider())
            .boot()

    private fun pushInertia(app: Application, middleware: HandleInertiaRequests = HandleInertiaRequests()) {
        app.resolve(HttpKernel::class.java).pushMiddleware(middleware)
    }

    private class FakeSession : RequestSession {
        private val data = linkedMapOf<String, Any?>()

        override fun get(key: String): Any? = data[key]

        override fun put(key: String, value: Any?): RequestSession {
            data[key] = value
            return this
        }

        override fun flash(key: String, value: Any?): RequestSession = put(key, value)

        override fun flashInput(input: Map<String, String>): RequestSession = this

        override fun old(key: String, default: String?): String? = default

        override fun oldInput(): Map<String, String> = emptyMap()

        override fun previousUrl(): String? = null

        override fun setPreviousUrl(url: String): RequestSession = this
    }
}
