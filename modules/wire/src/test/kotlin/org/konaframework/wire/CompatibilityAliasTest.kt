package org.konaframework.wire

import org.konaframework.brew.Brew
import org.konaframework.brew.BrewServiceProvider
import org.konaframework.core.Config
import org.konaframework.http.Router
import org.konaframework.wire.fixtures.Counter
import org.konaframework.wire.fixtures.Greeter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.function.Supplier

class CompatibilityAliasTest {
    @Test
    fun `brew facade delegates to wire`() {
        assertThrows<WireException> { Brew.manager() }
        val manager = brew()
        Brew.setManager(manager)
        assertSame(manager, Brew.manager())
        assertSame(manager, Wire.manager())
        Brew.component("greeter", Greeter::class.java)
        Brew.component(Counter::class.java)
        Brew.component("sup", Supplier { Counter() })
        Brew.addNamespace("admin", "org.konaframework.wire.fixtures.admin_area")
        Brew.addLocation("org.konaframework.wire.fixtures")
        Brew.discover("org.konaframework.wire.fixtures.admin_area")
        assertTrue(Brew.exists("kitchen"))
        assertTrue(Brew.mount("greeter", mapOf("name" to "x")).contains("Hello X!"))
        assertTrue(Brew.mount("sup").contains("Count: 0"))
        Brew.componentHook(Supplier { object : org.konaframework.wire.lifecycle.ComponentHook() {} })
        Brew.propertySynthesizer(HydrationTest.PointSynth)
        Brew.releaseToken("tok")
        Brew.addPersistentMiddleware()
        Brew.updateMiddleware()
        assertEquals(null, Brew.current())
        assertFalse(Brew.isBrewRequest())
        assertFalse(Brew.isWireRequest())
        assertFalse(Wire.isBrewRequest())
        assertTrue(Brew.styles().isNotEmpty())
        assertTrue(Brew.scripts().isNotEmpty())
        assertTrue(Brew.scriptConfig().isNotEmpty())
        Brew.test("counter").assertSet("count", 0)
        Brew.test(Counter::class.java).assertSet("count", 0)
        Brew.withoutLazyLoading { "ok" }
        val router = Router()
        Brew.route(router, "/compat", "counter")
        Brew.setUpdateRoute { handler -> router.post("/compat-update", handler) }
        Brew.setScriptRoute { handler -> router.get("/compat.js", handler) }
        assertEquals(WireServiceProvider::class.java, BrewServiceProvider()::class.java.superclass)
        Brew.reset()
        assertThrows<WireException> { Wire.manager() }
    }

    @Test
    fun `wire config keys win over brew fallback`() {
        val config = Config()
        config.set("brew.path", "/brew")
        config.set("wire.path", "/wire-preferred")
        config.set("wire.view_prefix", "wire")
        config.set("brew.view_prefix", "brew")
        config.set("wire.class_namespace", "app.wire.only")
        config.set("wire.inject_assets", false)
        config.set("brew.inject_assets", true)
        config.set("wire.nonce", "n1")
        config.set("wire.asset_url", "/assets")
        config.set("wire.payload.max_size", 10)
        val parsed = WireConfig.from(config)
        assertEquals("/wire-preferred", parsed.path)
        assertEquals("wire", parsed.viewPrefix)
        assertEquals(listOf("app.wire.only"), parsed.classNamespaces)
        assertFalse(parsed.injectAssets)
        assertEquals("n1", parsed.scriptNonce)
        assertEquals("/assets", parsed.assetUrl)
        assertEquals(10, parsed.maxPayloadSize)
    }

    @Test
    fun `legacy brew config still loads`() {
        val config = Config()
        config.set("brew.path", "/legacy")
        config.set("brew.view_prefix", "brew")
        val parsed = WireConfig.from(config)
        assertEquals("/legacy", parsed.path)
        assertEquals("brew", parsed.viewPrefix)
    }

    @Test
    fun `default namespaces include app brew`() {
        assertEquals(
            listOf(WireConfig.DEFAULT_CLASS_NAMESPACE, WireConfig.LEGACY_CLASS_NAMESPACE),
            WireConfig.from(Config()).classNamespaces,
        )
    }

    @BrewComponent("legacy-named")
    class LegacyNamed : Component() {
        override fun template() = "<i>legacy</i>"
    }

    @Test
    fun `deprecated brew component annotation is honored`() {
        assertEquals("legacy-named", ComponentRegistry.nameFor(LegacyNamed::class.java, null))
    }
}
