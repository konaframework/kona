package dev.kona.brew

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.crema.CremaEngine
import dev.kona.crema.CremaServiceProvider
import dev.kona.crema.MapViewFinder
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64

class BrewTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun tearDown() {
        Brew.reset()
        Kona.reset()
    }

    class Counter : Component() {
        @JvmField
        var count: Int = 0

        @JvmField
        var label: String = "n"

        private var mounted = false

        override fun mount() {
            mounted = true
        }

        fun wasMounted(): Boolean = mounted

        fun increment() {
            count++
        }

        fun decrement() {
            count--
        }

        fun setFromMap(params: Map<String, Any?>) {
            val v = params["count"]
            if (v is Number) {
                count = v.toInt()
            }
        }

        fun echo(value: String) {
            label = value
        }

        fun ping() {
            label = "ping"
        }

        fun ping(value: String) {
            label = "ping-$value"
        }

        fun boom() {
            throw IllegalStateException("nope")
        }

        fun brewFail() {
            throw BrewException("brew-fail")
        }

        fun tooMany(a: Int, b: Int) {
            count = a + b
        }

        fun needsInt(n: Int) {
            count = n
        }

        override fun render(): String = "<span>$label=$count</span>"
    }

    class CremaCounter(
        private val engine: CremaEngine,
    ) : Component() {
        @JvmField
        var count: Int = 0

        fun increment() {
            count++
        }

        override fun render(): String =
            Brew.view(engine, "brew.counter", mapOf("count" to count))
    }

    class Bare : Component() {
        override fun render(): String = "bare"
    }

    @Test
    fun mountRenderSnapshotAndActions() {
        val manager = BrewManager()
        manager.register("counter") { Counter() }
        val html = manager.mount("counter")
        assertTrue(html.contains("data-brew="))
        assertTrue(html.contains("data-brew-name=\"counter\""))
        assertTrue(html.contains("id=\"brew-"))
        assertTrue(html.contains("<span>n=0</span>"))

        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        val after = manager.update("counter", snapshot, "increment")
        assertTrue(after.html.contains("<span>n=1</span>"))

        val down = manager.update("counter", after.snapshot, "decrement")
        assertTrue(down.html.contains("<span>n=0</span>"))

        val mapped = manager.update("counter", down.snapshot, "setFromMap", mapOf("count" to 5))
        assertTrue(mapped.html.contains("<span>n=5</span>"))

        val echoed = manager.update("counter", mapped.snapshot, "echo", mapOf("value" to "x"))
        assertTrue(echoed.html.contains("<span>x=5</span>"))
    }

    @Test
    fun mountWithConfigureAndSync() {
        val manager = BrewManager()
        manager.register("counter") { Counter() }
        val html = manager.mount("counter") { c ->
            (c as Counter).count = 3
        }
        assertTrue(html.contains("<span>n=3</span>"))
        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        val synced = manager.update("counter", snapshot, "sync", mapOf("count" to 9, "label" to "c"))
        assertTrue(synced.html.contains("<span>c=9</span>"))
    }

    @Test
    fun registryAndFacade() {
        Brew.reset()
        assertThrows(BrewException::class.java) { Brew.manager() }
        val manager = BrewManager()
        manager.register("counter", java.util.function.Supplier { Counter() })
        assertTrue(manager.registry.contains("counter"))
        assertEquals(setOf("counter"), manager.registry.names())
        Brew.setManager(manager)
        assertTrue(Brew.mount("counter").contains("data-brew-name=\"counter\""))
        Brew.reset()
        assertThrows(BrewException::class.java) { Brew.manager() }
    }

    @Test
    fun unknownComponentMethodAndBadSnapshot() {
        val manager = BrewManager()
        manager.register("counter") { Counter() }
        assertThrows(BrewException::class.java) { manager.registry.resolve("missing") }
        val html = manager.mount("counter")
        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        assertThrows(BrewException::class.java) {
            manager.update("counter", snapshot, "noSuchMethod")
        }
        assertThrows(BrewException::class.java) { BrewSnapshot.decode("") }
        assertThrows(BrewException::class.java) { BrewSnapshot.decode("!!!not-base64!!!") }
        val badJson = Base64.getEncoder().encodeToString("{".toByteArray())
        assertThrows(BrewException::class.java) { BrewSnapshot.decode(badJson) }
    }

    @Test
    fun updateValidationsAndNameMismatch() {
        val manager = BrewManager()
        manager.register("counter") { Counter() }
        val html = manager.mount("counter")
        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        assertThrows(BrewException::class.java) {
            manager.update("other", snapshot, "increment")
        }
        assertThrows(BrewException::class.java) {
            manager.update("", """eyJuYW1lIjoiIiwiZGF0YSI6e30sImlkIjoiIn0=""", "increment")
        }
        assertThrows(BrewException::class.java) {
            BrewActions.invoke(Counter(), "")
        }
        assertThrows(BrewException::class.java) {
            BrewActions.invoke(Counter(), "tooMany", mapOf("a" to 1))
        }
        assertThrows(BrewException::class.java) {
            BrewActions.invoke(Counter(), "needsInt", mapOf("value" to 1))
        }
        assertThrows(BrewException::class.java) {
            BrewActions.invoke(Counter(), "boom")
        }
        val brewFail = assertThrows(BrewException::class.java) {
            BrewActions.invoke(Counter(), "brewFail")
        }
        assertEquals("brew-fail", brewFail.message)

        BrewActions.invoke(Counter(), "echo", emptyMap())
        BrewActions.invoke(Counter(), "echo", mapOf("other" to "z"))
        val nullValue = hashMapOf<String, Any?>("value" to null)
        BrewActions.invoke(Counter(), "echo", nullValue)
        val pinged = Counter()
        BrewActions.invoke(pinged, "ping")
        assertEquals("ping", pinged.label)
        BrewActions.invoke(pinged, "ping", mapOf("value" to "x"))
        assertEquals("ping", pinged.label) // zero-arg overload preferred
        assertThrows(BrewException::class.java) {
            BrewActions.invoke(Counter(), "toString")
        }
        assertThrows(BrewException::class.java) {
            BrewActions.invoke(Bare(), "mount")
        }
        BrewActions.invoke(Bare(), "render")
        BrewActions.invoke(Counter(), "mount")
    }

    @Test
    fun morphAndAssets() {
        assertEquals("<div>b</div>", BrewMorph.replace("<div>a</div>", "<div>b</div>"))
        assertEquals("<div>a</div>", BrewMorph.replace("<div>a</div>", ""))
        assertTrue(BrewAssets.javascript().contains("/brew/message"))
        assertEquals("""<script src="/brew/brew.js" defer></script>""", BrewAssets.scriptTag())
        assertEquals("""<script src="/x.js" defer></script>""", BrewAssets.scriptTag("/x.js"))
        assertTrue(BrewAssets.inlineScriptTag().startsWith("<script>"))
        assertTrue(BrewAssets.inlineScriptTag().contains("wire:click"))
        val emptyLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String?) = null
        }
        assertThrows(BrewException::class.java) { BrewAssets.readScript(emptyLoader) }
        assertTrue(BrewAssets.readScript(BrewAssets::class.java.classLoader).contains("fetch"))
    }

    @Test
    fun httpMessageEndpointAndScript() {
        val app = Kona.create(arrayOf("--base=$temp"))
            .providers(HttpServiceProvider(), BrewServiceProvider())
            .boot()
        val brew = app.resolve(BrewManager::class.java)
        brew.register("counter") { Counter() }
        val kernel = app.resolve(HttpKernel::class.java)

        val script = kernel.handle(Request.get("/brew/brew.js"))
        assertEquals(200, script.status)
        assertTrue(script.headers["Content-Type"]!!.contains("javascript"))
        assertTrue(script.body.contains("data-brew-click"))

        val initial = brew.mount("counter")
        val snapshot = Regex("""data-brew="([^"]+)"""").find(initial)!!.groupValues[1]
        val body = jacksonObjectMapper().writeValueAsString(
            mapOf(
                "name" to "counter",
                "snapshot" to snapshot,
                "method" to "increment",
                "params" to emptyMap<String, Any>(),
            ),
        )
        val ok = kernel.handle(
            Request(
                "POST",
                "/brew/message",
                headers = mapOf("Content-Type" to "application/json"),
                body = body,
            ),
        )
        assertEquals(200, ok.status)
        assertTrue(ok.body.contains("n=1"))

        val alias = kernel.handle(
            Request(
                "POST",
                "/_brew/update",
                headers = mapOf("Content-Type" to "application/json"),
                body = body.replace("increment", "decrement"),
            ),
        )
        assertEquals(200, alias.status)

        val bad = kernel.handle(
            Request(
                "POST",
                "/brew/message",
                headers = mapOf("Content-Type" to "application/json"),
                body = "{}",
            ),
        )
        assertEquals(422, bad.status)
        assertTrue(bad.body.contains("error"))

        val blank = kernel.handle(
            Request("POST", "/brew/message", headers = mapOf("Content-Type" to "application/json"), body = ""),
        )
        assertEquals(422, blank.status)

        val invalidJson = kernel.handle(
            Request("POST", "/brew/message", headers = mapOf("Content-Type" to "application/json"), body = "{"),
        )
        assertEquals(422, invalidJson.status)

        val missingMethod = kernel.handle(
            Request(
                "POST",
                "/brew/message",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"name":"counter","snapshot":"$snapshot","method":""}""",
            ),
        )
        assertEquals(422, missingMethod.status)

        val noParamsKey = kernel.handle(
            Request(
                "POST",
                "/brew/message",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"name":"counter","snapshot":"$snapshot","method":"sync","params":[]}""",
            ),
        )
        assertEquals(200, noParamsKey.status)

        val blankNameSnapshot = BrewSnapshot.encode("", Counter())
        val named = brew.update("counter", blankNameSnapshot, "sync", emptyMap())
        assertTrue(named.html.contains("data-brew-name=\"counter\""))
    }

    @Test
    fun providerWithoutHttpStillBindsManager() {
        val app = Application(temp)
        app.register(BrewServiceProvider()).boot()
        assertTrue(app.resolve(BrewManager::class.java).registry.names().isEmpty())
        assertEquals(app.resolve(BrewManager::class.java), Brew.manager())
    }

    @Test
    fun cremaIntegration() {
        val finder = MapViewFinder().put("brew.counter", "<em>{{ count }}</em>").put("plain", "ok")
        val engine = CremaEngine(finder)
        assertEquals("ok", Brew.view(engine, "plain"))
        val manager = BrewManager()
        manager.register("crema-counter") { CremaCounter(engine) }
        val html = manager.mount("crema-counter")
        assertTrue(html.contains("<em>0</em>"))
        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        val next = manager.update("crema-counter", snapshot, "increment")
        assertTrue(next.html.contains("<em>1</em>"))

        val app = Kona.create(arrayOf("--base=$temp"))
            .providers(CremaServiceProvider(), BrewServiceProvider())
            .boot()
        assertTrue(app.container.bound(BrewManager::class.java))
    }

    @Test
    fun stateWriteSkipsNullPrimitivesAndUnknownKeys() {
        val c = Counter()
        c.count = 2
        BrewState.write(c, emptyMap())
        assertEquals(2, c.count)
        BrewState.write(c, mapOf("count" to null, "unknown" to 1, "brewId" to "x", "name" to "y"))
        assertEquals(2, c.count)
        BrewState.write(c, mapOf("count" to 7))
        assertEquals(7, c.count)
        assertEquals(7, BrewState.read(c)["count"])
        BrewState.write(c, hashMapOf("label" to null))
        assertEquals(null, c.label)
    }

    @Test
    fun snapshotRoundTripPreservesId() {
        val c = Counter()
        c.brewId = "brew-fixed"
        c.count = 4
        val encoded = BrewSnapshot.encode("counter", c)
        val payload = BrewSnapshot.decode(encoded)
        assertEquals("counter", payload.name)
        assertEquals("brew-fixed", payload.id)
        assertEquals(4, (payload.data["count"] as Number).toInt())
    }

    @Test
    fun paramsSyncAlongsideAction() {
        val manager = BrewManager()
        manager.register("counter") { Counter() }
        val html = manager.mount("counter")
        val snapshot = Regex("""data-brew="([^"]+)"""").find(html)!!.groupValues[1]
        val result = manager.update(
            "counter",
            snapshot,
            "increment",
            mapOf("label" to "L", "value" to "ignored-as-field"),
        )
        assertTrue(result.html.contains("<span>L=1</span>"))
    }

    @Test
    fun renderWithoutAssignIdWhenAlreadySet() {
        val c = Counter()
        c.brewId = "brew-keep"
        val html = BrewRenderer.render("counter", c, assignId = false, callMount = true)
        assertTrue(html.contains("id=\"brew-keep\""))
        assertTrue(c.wasMounted())
        val again = BrewRenderer.render("counter", c, assignId = true, callMount = false)
        assertTrue(again.contains("id=\"brew-keep\""))
        assertEquals("bare", BrewRenderer.render("bare", Bare()).let { Regex(""">([^<]+)<""").find(it)!!.groupValues[1] })
    }

    @Test
    fun updateAssignsIdWhenSnapshotOmitsIt() {
        val manager = BrewManager()
        manager.register("counter") { Counter() }
        val c = Counter()
        c.count = 1
        val snapshot = BrewSnapshot.encode("counter", c)
        val payload = BrewSnapshot.decode(snapshot)
        assertEquals("", payload.id)
        val result = manager.update("counter", snapshot, "increment")
        assertTrue(result.id.startsWith("brew-"))
        assertTrue(result.html.contains("n=2"))
    }
}
