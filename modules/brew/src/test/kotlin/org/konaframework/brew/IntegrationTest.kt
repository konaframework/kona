package org.konaframework.brew

import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.fixtures.Greeter
import org.konaframework.brew.view.FrontendAssets
import org.konaframework.brew.view.InjectAssets
import org.konaframework.core.Application
import org.konaframework.crema.CremaContext
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.CremaServiceProvider
import org.konaframework.crema.MapViewFinder
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.session.Csrf
import org.konaframework.session.StartSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Profile : Component() {
    var userName = ""
    var age = 0
    var active = false

    override fun template() = "<div>{{ userName }}:{{ age }}:{{ active }}</div>"
}

class Shell : Component() {
    var show = true
    var who = "kai"
    var params = mapOf("userName" to "lani", "age" to 3)

    override fun template() = """
        <main>@if(show)<p>on</p>@endif
            <brew:profile user-name="static" :age="7" active />
            <brew:profile :user-name="who" key="k1" />
            <brew:profile :${'$'}who wire:key="k2" />
            @brew('profile', params)
            @brew('profile')
            <script>let x = @js(params); let s = @js(who); let n = @js(age);</script>
        </main>
    """.trimIndent()
}

class IntegrationTest {
    @AfterEach
    fun cleanup() = Brew.reset()

    private val manager = brew().component("profile", Profile::class.java).component("shell", Shell::class.java)

    @Test
    fun `brew tags and directive mount nested components`() {
        val html = manager.mount("shell")
        assertTrue(html.contains("static:7:true"), html)
        assertTrue(html.contains(">kai:0:false<"), html)
        assertTrue(html.contains(">lani:3:false<"), html)
        assertTrue(html.contains(">:0:false<"), html)
        assertTrue(html.contains("<!--[if BLOCK]><![endif]--><p>on</p><!--[if ENDBLOCK]><![endif]-->"), html)
        assertEquals(6, Regex("wire:name=\"").findAll(html).count())
    }

    @Test
    fun `js directive encodes safely`() {
        assertEquals("JSON.parse('{\\u0022a\\u0022:\\u0022\\\\u003C\\u0022}')", BrewCrema.js(mapOf("a" to "<")))
        assertEquals("JSON.parse('[1,2]')", BrewCrema.js(listOf(1, 2)))
        assertEquals("'it\\u0027s \\u0022q\\u0022'", BrewCrema.js("it's \"q\""))
        assertEquals("42", BrewCrema.js(42))
        assertEquals("null", BrewCrema.js(null))
        assertEquals("true", BrewCrema.js(true))
        val html = manager.mount("shell")
        assertTrue(html.contains("let s = &#039;") || html.contains("let s = 'kai'"), html)
    }

    @Test
    fun `brew directive requires a name`() {
        val engine = CremaEngine(MapViewFinder())
        val m = BrewManager(BrewConfig(key = "k"), engine)
        assertNotNull(m)
        assertThrows<IllegalArgumentException> { engine.renderSource("@brew()", CremaContext()) }
    }

    @Test
    fun `morph markers can be disabled`() {
        val m = brew(config = BrewConfig(key = "k", injectMorphMarkers = false)).component("shell", Shell::class.java)
            .component("profile", Profile::class.java)
        assertFalse(m.mount("shell").contains("[if BLOCK]"))
    }

    private fun session(token: String = "tok") = org.konaframework.session.StoreSession("sid", org.konaframework.session.ArraySessionStore())
        .also { it.put(Csrf.TOKEN_KEY, token) }

    @Test
    fun `styles, scripts and script config render once per request`() {
        val request = Request("GET", "/").setAttribute(StartSession.ATTRIBUTE, session())
        manager.withRequest(request) {
            val styles = manager.assets.styles()
            assertTrue(styles.startsWith("<!-- Brew Styles --><style>"), styles)
            assertTrue(styles.contains("[wire\\:loading][wire\\:loading]"))
            assertEquals("", manager.assets.styles())
            val scripts = manager.assets.scripts()
            assertTrue(scripts.matches(Regex("""<script src="/brew/brew\.min\.js\?id=[0-9a-f]{8}" data-csrf="tok" data-update-uri="/brew/update" data-navigate-once="true"></script>""")), scripts)
            assertEquals("", manager.assets.scripts())
            assertTrue(manager.assets.rendered(FrontendAssets.STYLES_FLAG))
            val config = manager.assets.scriptConfig()
            assertTrue(config.contains("\"csrf\":\"tok\""), config)
            assertTrue(config.contains("\"uri\":\"/brew/update\""), config)
            assertTrue(config.contains("\"navigate\""), config)
        }
        assertFalse(manager.assets.rendered(FrontendAssets.STYLES_FLAG))
        assertTrue(manager.assets.styles().isNotEmpty())
        assertTrue(manager.assets.scripts().contains("data-csrf=\"\""))
        val nonce = brew(config = BrewConfig(key = "k", scriptNonce = "n1", assetUrl = "https://cdn.test/", debug = true))
        assertTrue(nonce.assets.styles().contains("<style nonce=\"n1\" data-brew-style>"))
        assertTrue(nonce.assets.scripts().startsWith("<script src=\"https://cdn.test/brew.js?id="), nonce.assets.scripts())
        assertTrue(nonce.assets.scripts().contains(" nonce=\"n1\""))
        assertTrue(nonce.assets.scriptConfig().startsWith("<script nonce=\"n1\" data-navigate-once=\"true\">"))
    }

    @Test
    fun `crema directives for assets`() {
        val engine = CremaEngine(MapViewFinder())
        BrewManager(BrewConfig(key = "k"), engine)
        val out = engine.renderSource("@brewStyles @brewScripts @brewScriptConfig", CremaContext())
        assertTrue(out.contains("<style>") && out.contains("brew.min.js") && out.contains("brewScriptConfig"), out)
    }

    @Test
    fun `asset responses`() {
        val js = manager.assets.response("brew.min.js")
        assertEquals(200, js.status)
        assertEquals("application/javascript; charset=utf-8", js.headers["Content-Type"])
        assertTrue(js.headers["Cache-Control"]!!.contains("immutable"))
        assertEquals("application/json", manager.assets.response("brew.min.js.map").headers["Content-Type"])
        assertEquals(404, manager.assets.response("nope.js").status)
        assertEquals("brew.min.js", manager.assets.scriptName())
        assertEquals(8, manager.assets.asset("brew.js")!!.hash.length)
    }

    @Test
    fun `missing bundle is reported`() {
        val broken = object : ClassLoader(null) {}
        val thread = Thread.currentThread()
        assertNotNull(broken)
        assertNotNull(thread)
        val m = brew(config = BrewConfig(key = "k", debug = true))
        val field = FrontendAssets::class.java.getDeclaredField("scripts\$delegate")
        field.isAccessible = true
        field.set(m.assets, lazy { emptyMap<String, FrontendAssets.Asset>() })
        assertThrows<BrewException> { m.assets.scripts() }
    }

    @Test
    fun `inject assets middleware`() {
        val middleware = InjectAssets(manager)
        val page = "<html><head><title>x</title></head><body>BODY</body></html>"
        fun run(req: Request, response: () -> Response): Response = middleware.handle(req) { response() }

        val untouched = run(Request("GET", "/")) { Response.html(page) }
        assertEquals(page, untouched.body)

        val req = Request("GET", "/")
        val injected = run(req) { manager.mount("profile"); Response.html(page) }
        assertTrue(injected.body.contains("<style>") && injected.body.indexOf("<style>") < injected.body.indexOf("</head>"), injected.body)
        assertTrue(injected.body.contains("brew.min.js") && injected.body.indexOf("brew.min.js") < injected.body.indexOf("</body>"))

        val already = run(Request("GET", "/")) {
            manager.mount("profile")
            Response.html("<html><head>${manager.assets.styles()}</head><body>${manager.assets.scripts()}</body></html>")
        }
        assertEquals(1, Regex("<style").findAll(already.body).count())
        assertEquals(1, Regex("<script").findAll(already.body).count())

        val fragment = run(Request("GET", "/")) { manager.mount("profile"); Response.html("<div>frag</div>") }
        assertFalse(fragment.body.contains("<style>"))
        val headOnly = run(Request("GET", "/")) { manager.mount("profile"); Response.html("<head></head>") }
        assertTrue(headOnly.body.contains("<style>"))
        val bodyOnly = run(Request("GET", "/")) { manager.mount("profile"); Response.html("<body></body>") }
        assertTrue(bodyOnly.body.contains("<script"))
        val json = run(Request("GET", "/")) { manager.mount("profile"); Response.json(mapOf("a" to "</body>")) }
        assertFalse(json.body.contains("<script"))
        val noType = run(Request("GET", "/")) { manager.mount("profile"); Response(200, "</body>", linkedMapOf()) }
        assertFalse(noType.body.contains("<script"))
        val off = InjectAssets(brew(config = BrewConfig(key = "k", injectAssets = false)).component("profile", Profile::class.java))
        val offBody = off.handle(Request("GET", "/")) { Brew.reset(); Response.html(page) }
        assertEquals(page, offBody.body)
    }

    @Test
    fun `service provider attaches Crema tags when BrewManager started without views`(@TempDir dir: Path) {
        val app = Application(dir)
        app.config.set("app.key", "provider-key")
        app.register(HttpServiceProvider()).register(CremaServiceProvider())
        // Simulate a BrewManager constructed with views=null (Crema unbound at factory time).
        app.container.singleton(BrewManager::class.java) {
            BrewManager(BrewConfig.from(app.config), null, app.container)
        }
        BrewServiceProvider().boot(app)
        assertTrue(app.resolve(CremaEngine::class.java).extensions.hasTag("brew"))
        val engine = app.resolve(CremaEngine::class.java)
        app.resolve(BrewManager::class.java).component("counter", Counter::class.java)
        val html = engine.renderSource("<brew:counter />", CremaContext())
        assertTrue(html.contains("wire:name=\"counter\""), html)
    }

    @Test
    fun `service provider wires routes, middleware, facade and discovery`(@TempDir dir: Path) {
        val app = Application(dir)
        app.config.set("app.key", "provider-key")
        app.config.set("brew.class_namespace", listOf("org.konaframework.brew.fixtures"))
        app.register(HttpServiceProvider()).register(CremaServiceProvider()).register(BrewServiceProvider()).boot()
        val brew = app.resolve(BrewManager::class.java)
        assertSame(brew, Brew.manager())
        assertTrue(brew.registry.has("counter"))
        assertTrue(app.resolve(CremaEngine::class.java).extensions.hasTag("brew"))
        val kernel = app.resolve(HttpKernel::class.java)

        val page = kernel.handle(Request("GET", "/brew/brew.min.js"))
        assertEquals(200, page.status)
        assertEquals(200, kernel.handle(Request("GET", "/brew/brew.js")).status)
        assertEquals(200, kernel.handle(Request("GET", "/brew/brew.min.js.map")).status)

        val snapshot = snapshotOf(brew.mount("counter"))
        val body = brew.json(mapOf("components" to listOf(mapOf("snapshot" to brew.json(snapshot), "updates" to emptyMap<String, Any?>(), "calls" to listOf(call("increment"))))))
        val updated = kernel.handle(Request("POST", "/brew/update", headers = mapOf("X-Brew" to "true"), body = body))
        assertEquals(200, updated.status, updated.body)

        val router = app.resolve(Router::class.java)
        router.get("/page") { Response.html("<html><head></head><body>${Brew.mount("counter")}</body></html>") }
        val html = kernel.handle(Request("GET", "/page")).body
        assertTrue(html.contains("<style>") && html.contains("brew.min.js"), html)

        Brew.setUpdateRoute { handler -> router.post("/custom/brew", handler) }
        assertEquals("/custom/brew", brew.updateUri)
        assertEquals(404, kernel.handle(Request("POST", "/brew/update", headers = mapOf("X-Brew" to "true"), body = body)).status)
        assertEquals(200, kernel.handle(Request("POST", "/custom/brew", headers = mapOf("X-Brew" to "true"), body = body)).status)
        Brew.setScriptRoute { handler -> router.get("custom/brew.js", handler) }
        assertEquals("/custom/brew.js", brew.scriptUri)
        assertEquals(404, kernel.handle(Request("GET", "/brew/brew.min.js")).status)
        assertEquals(200, kernel.handle(Request("GET", "/custom/brew.js")).status)
        assertTrue(brew.assets.scripts().contains("src=\"/custom/brew.js?id="))
    }

    @Test
    fun `service provider without http or crema`(@TempDir dir: Path) {
        val app = Application(dir)
        app.register(BrewServiceProvider()).boot()
        val brew = app.resolve(BrewManager::class.java)
        assertThrows<BrewException> { brew.views() }
        val router = Router()
        BrewServiceProvider.routes(brew, router)
    }

    @Test
    fun `service provider boot without TCCL keeps default registry loader`(@TempDir dir: Path) {
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = null
        try {
            val app = Application(dir)
            app.register(BrewServiceProvider()).boot()
            val brew = app.resolve(BrewManager::class.java)
            assertSame(ComponentRegistry::class.java.classLoader, brew.registry.classLoader)
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
    }

    @Test
    fun `config from kona config`() {
        val config = org.konaframework.core.Config()
        config.set("brew.class_namespace", "a.b, c.d,")
        config.set("brew.payload.max_size", 0)
        config.set("brew.payload.max_calls", 5)
        config.set("app.debug", true)
        val parsed = BrewConfig.from(config)
        assertEquals(listOf("a.b", "c.d"), parsed.classNamespaces)
        assertEquals(null, parsed.maxPayloadSize)
        assertEquals(5, parsed.maxCalls)
        assertEquals(20, parsed.maxComponents)
        assertTrue(parsed.debug)
        assertEquals(listOf("app.brew"), BrewConfig.from(org.konaframework.core.Config()).classNamespaces)
    }

    @Test
    fun `facade delegates to the manager`() {
        assertThrows<BrewException> { Brew.manager() }
        Brew.setManager(manager)
        Brew.component("greeter", Greeter::class.java)
        Brew.component(Counter::class.java)
        Brew.component("sup", java.util.function.Supplier { Counter() })
        Brew.addNamespace("admin", "org.konaframework.brew.fixtures.admin_area")
        Brew.addLocation("org.konaframework.brew.fixtures")
        Brew.discover("org.konaframework.brew.fixtures.admin_area")
        assertTrue(Brew.exists("admin::user-list"))
        assertTrue(Brew.exists("kitchen"))
        assertTrue(Brew.mount("greeter", mapOf("name" to "x")).contains("Hello X!"))
        assertTrue(Brew.mount("sup").contains("Count: 0"))
        Brew.componentHook(java.util.function.Supplier { object : org.konaframework.brew.lifecycle.ComponentHook() {} })
        Brew.propertySynthesizer(HydrationTest.PointSynth)
        assertEquals(null, Brew.current())
        assertTrue(Brew.styles().isNotEmpty())
        assertTrue(Brew.scripts().isNotEmpty())
        assertTrue(Brew.scriptConfig().isNotEmpty())
        Brew.test("counter").call("increment").assertSet("count", 1)
        Brew.test(Counter::class.java, mapOf()).assertSet("count", 0)
        Brew.test(Profile::class.java).assertSet("userName", "")
    }
}
