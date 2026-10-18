package org.konaframework.brew

import org.konaframework.brew.features.SupportEvents
import org.konaframework.brew.fixtures.BrokenListener
import org.konaframework.brew.fixtures.EventBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EventsTest {
    private val manager = brew()
        .component("event-bus", EventBus::class.java)
        .component("broken-listener", BrokenListener::class.java)

    @AfterEach
    fun reset() {
        Brew.reset()
    }

    private fun facade(): BrewManager {
        Brew.setManager(manager)
        return manager
    }

    @Test
    fun `dispatch queues effects and fluent targets`() {
        facade()
        val t = Brew.test(EventBus::class.java).call("announce")
        t.assertDispatched("saved", mapOf("id" to 7))
        t.assertDispatched("ping")
        @Suppress("UNCHECKED_CAST")
        val dispatches = t.effects()["dispatches"] as List<Map<String, Any?>>
        assertTrue(dispatches.any { it["self"] == true })
        assertTrue(dispatches.any { it["component"] == "event-bus" })
        t.assertNotDispatched("missing")
    }

    @Test
    fun `On listeners appear on mount and handle __dispatch`() {
        facade()
        val mounted = Brew.test(EventBus::class.java)
        @Suppress("UNCHECKED_CAST")
        val listeners = mounted.effects()["listeners"] as List<String>
        assertTrue("post-saved" in listeners)
        assertTrue("post-3" in listeners)

        val t = Brew.test(EventBus::class.java)
            .call("__dispatch", "post-saved", mapOf("title" to "Hi"))
        assertEquals("Hi", (t.instance() as EventBus).lastTitle)

        Brew.test(EventBus::class.java)
            .call("__dispatch", "post-saved", listOf("List"))
        Brew.test(EventBus::class.java)
            .call("__dispatch", "post-saved", "Scalar")
    }

    @Test
    fun `js redirect and stream effects`() {
        facade()
        val js = Brew.test(EventBus::class.java).call("runJs")
        assertEquals(listOf("alert(1)"), js.effects()["xjs"])

        val redirect = Brew.test(EventBus::class.java).call("goAway")
        @Suppress("UNCHECKED_CAST")
        val r = redirect.effects()["redirect"] as Map<String, Any?>
        assertEquals("/home", r["url"])
        redirect.assertRenderSkipped()

        val stream = Brew.test(EventBus::class.java).call("pushStream")
        @Suppress("UNCHECKED_CAST")
        val streams = stream.effects()["streams"] as List<Map<String, Any?>>
        assertEquals("chunk", streams[0]["content"])
        assertEquals("log", streams[0]["to"])
    }

    @Test
    fun `unknown event throws`() {
        facade()
        assertThrows<EventHandlerDoesNotExistException> {
            Brew.test(EventBus::class.java).call("__dispatch", "nope", emptyMap<String, Any?>())
        }
        assertThrows<EventHandlerDoesNotExistException> {
            Brew.test(EventBus::class.java).call("__dispatch")
        }
    }

    @Test
    fun `magic dollar dispatch`() {
        facade()
        Brew.test(EventBus::class.java)
            .call("\$dispatch", "ping", mapOf("ok" to true))
            .assertDispatched("ping", mapOf("ok" to true))
        Brew.test(EventBus::class.java)
            .call("\$dispatch", "solo", "value")
            .assertDispatched("solo")
        Brew.test(EventBus::class.java)
            .call("\$dispatch", "bare")
            .assertDispatched("bare")
    }

    @Test
    fun `Event fluent helpers and redirect variants`() {
        facade()
        val event = Event("x", mapOf("a" to 1))
            .to(EventBus::class.java)
            .component("other")
            .ref("box")
            .el(".item")
            .self()
        val serialized = event.serialize()
        assertEquals("x", serialized["name"])
        assertEquals(true, serialized["self"])
        assertEquals("box", serialized["ref"])
        assertEquals(".item", serialized["el"])
        assertEquals("other", serialized["component"])

        val empty = Event("y").serialize()
        assertEquals("y", empty["name"])
        assertTrue(!empty.containsKey("params"))

        val c = Brew.test(EventBus::class.java).instance() as EventBus
        c.redirectRoute("home", mapOf("id" to 1), navigate = true)
        assertEquals("home", (c.state().store["redirect"] as Map<*, *>)["route"])
        c.redirectIntended("/fallback", navigate = false)
        assertEquals(true, (c.state().store["redirect"] as Map<*, *>)["intended"])
        c.stream("more", replace = true)
        @Suppress("UNCHECKED_CAST")
        assertEquals(true, (c.state().store["streams"] as List<Map<String, Any?>>).last()["replace"])

        val announced = Brew.test(EventBus::class.java).call("announce")
        assertThrows<AssertionError> {
            announced.assertDispatched("saved", mapOf("id" to 999))
        }
        assertThrows<AssertionError> {
            announced.assertDispatched("never-fired")
        }
        // ping has no params map → emptyMap branch when asserting params...
        assertThrows<AssertionError> {
            announced.assertDispatched("ping", mapOf("ghost" to 1))
        }

        // Non-list / non-map dispatch shapes for Testable branch coverage...
        @Suppress("UNCHECKED_CAST")
        val effects = announced.effects() as MutableMap<String, Any?>
        effects["dispatches"] = "not-a-list"
        announced.assertNotDispatched("anything")
        assertThrows<AssertionError> { announced.assertDispatched("anything") }
        effects["dispatches"] = null
        assertThrows<AssertionError> { announced.assertDispatched("anything") }
        effects["dispatches"] = listOf("skip-me", mapOf("name" to "kept"))
        announced.assertDispatched("kept")
        announced.assertNotDispatched("skip-me")
        effects["dispatches"] = null
        announced.assertNotDispatched("gone")
    }

    @Test
    fun `placeholder replacement for nested maps`() {
        facade()
        val c = Brew.test(EventBus::class.java).instance() as EventBus
        c.meta = mapOf("k" to "v")
        assertEquals("x.v", SupportEvents.replacePlaceholders("x.{meta.k}", c))
        assertEquals("plain", SupportEvents.replacePlaceholders("plain", c))
        assertEquals("x.{missing}", SupportEvents.replacePlaceholders("x.{missing}", c))
        assertEquals("x.{meta.nope}", SupportEvents.replacePlaceholders("x.{meta.nope}", c))
        c.meta = emptyMap()
        assertEquals("3", SupportEvents.replacePlaceholders("{postId}", c))
        // dotted path when root is not a Map falls back to root toString...
        assertEquals("x.3", SupportEvents.replacePlaceholders("x.{postId.k}", c))
        assertTrue("parallel" in org.konaframework.brew.meta.ComponentMeta.of(c).async)
    }

    @Test
    fun `jvm overload bridges and dollar refresh listener`() {
        facade()
        val c = Brew.test(EventBus::class.java).instance() as EventBus
        c.javaClass.getMethod("redirect", String::class.java).invoke(c, "/a")
        c.javaClass.getMethod("redirectRoute", String::class.java).invoke(c, "home")
        c.javaClass.getMethod("redirectIntended").invoke(c)
        c.javaClass.getMethod("stream", String::class.java).invoke(c, "chunk")

        Brew.test(EventBus::class.java).call("__dispatch", "refresh-me")

        // two-arg overload via map with two entries...
        Brew.test(EventBus::class.java)
            .call("__dispatch", "post-saved", mapOf("title" to "A", "extra" to "B"))

        // three entries → no arity match → maxBy...
        runCatching {
            Brew.test(EventBus::class.java)
                .call("__dispatch", "post-saved", mapOf("a" to 1, "b" to 2, "c" to 3))
        }

        // null event payload → emptyMap branch...
        Brew.test(EventBus::class.java).call("__dispatch", "refresh-me", null as Any?)

        // listener pointing at a missing method...
        val broken = object : Component() {
            override fun listeners() = mapOf("ghost" to "noSuchMethod")
            override fun template() = "<div></div>"
        }
        // register via temporary brew test isn't trivial; call SupportEvents path through EventBus override:
        assertThrows<EventHandlerDoesNotExistException> {
            // use a dedicated component
            Brew.test(BrokenListener::class.java).call("__dispatch", "ghost")
        }

        val t = Brew.test(EventBus::class.java).call("announce")
        t.javaClass.getMethod("assertDispatched", String::class.java).invoke(t, "ping")
        t.assertDispatched("saved")
        t.assertDispatched("ping", null)
        t.assertNotDispatched("nope")
        assertTrue(org.konaframework.brew.meta.ComponentMeta.of(c).async.contains("parallel"))
    }

    @Test
    fun `downloads json js echo flash and redirectAction`() {
        facade()
        Brew.test(EventBus::class.java).call("sendFile")
            .assertFileDownloaded("hello.txt", "hello")
        Brew.test(EventBus::class.java).call("streamFile")
            .assertFileDownloaded("out.txt", "streamed")
        assertThrows<AssertionError> {
            Brew.test(EventBus::class.java).assertFileDownloaded()
        }

        val json = Brew.test(EventBus::class.java).call("asJson")
        @Suppress("UNCHECKED_CAST")
        val returns = json.effects()["returns"] as List<*>
        assertEquals(mapOf("ok" to true, "title" to ""), returns[0])
        assertTrue(json.effects()["html"] == null)

        val mounted = Brew.test(EventBus::class.java)
        @Suppress("UNCHECKED_CAST")
        val js = mounted.effects()["js"] as Map<String, String>
        assertEquals("confirm('save?')", js["confirmSave"])
        @Suppress("UNCHECKED_CAST")
        val echo = mounted.effects()["echo"] as Map<String, String>
        assertEquals("onEchoOrder", echo["echo:orders,OrderShipped"])

        val action = Brew.test(EventBus::class.java).call("goAction")
        @Suppress("UNCHECKED_CAST")
        val redirect = action.effects()["redirect"] as Map<String, Any?>
        assertEquals("home.show", redirect["action"])

        val flashed = Brew.test(EventBus::class.java).call("goFlash")
        assertEquals(mapOf("status" to "saved"), flashed.effects()["flash"])
        assertTrue((flashed.snapshot()["memo"] as Map<*, *>)["async"].toString().contains("parallel"))

        val c = Brew.test(EventBus::class.java).instance() as EventBus
        c.javaClass.getMethod("redirectAction", String::class.java).invoke(c, "only-action")
        c.javaClass.getMethod("download", ByteArray::class.java, String::class.java)
            .invoke(c, "x".toByteArray(), "x.bin")
        c.javaClass.getMethod("streamDownload", String::class.java, String::class.java)
            .invoke(c, "z", "z.txt")
        c.streamDownload("z", "z.txt", "text/plain")

        // assertFileDownloaded name/content mismatch branches + JvmOverloads bridges...
        val dl = Brew.test(EventBus::class.java).call("sendFile")
        assertThrows<AssertionError> { dl.assertFileDownloaded("wrong.txt") }
        assertThrows<AssertionError> { dl.assertFileDownloaded("hello.txt", "nope") }
        dl.assertFileDownloaded()
        dl.javaClass.getMethod("assertFileDownloaded").invoke(dl)
        dl.javaClass.getMethod("assertFileDownloaded", String::class.java).invoke(dl, "hello.txt")
        dl.javaClass.getMethod("assertFileDownloaded", String::class.java, String::class.java)
            .invoke(dl, "hello.txt", "hello")

        // echo-private from listeners() map...
        @Suppress("UNCHECKED_CAST")
        val echoMap = Brew.test(EventBus::class.java).effects()["echo"] as Map<String, String>
        assertEquals("onEchoOrder", echoMap["echo-private:inbox,Mail"])
        assertTrue(echoMap.containsKey("echo:orders,OrderShipped"))
        assertTrue((Brew.test(EventBus::class.java).effects()["js"] as Map<*, *>).containsKey("blankNamedJs"))

        assertTrue(org.konaframework.brew.features.SupportEcho.isEcho("echo:x,Y"))
        assertTrue(org.konaframework.brew.features.SupportEcho.isEcho("echo-private:x,Y"))
        assertTrue(org.konaframework.brew.features.SupportEcho.isEcho("echo-presence:x,Y"))
        assertTrue(!org.konaframework.brew.features.SupportEcho.isEcho("post-saved"))

        // flash with a bound session and without...
        val session = org.konaframework.session.StoreSession("sid", org.konaframework.session.ArraySessionStore())
        val request = org.konaframework.http.Request("GET", "/")
            .setAttribute(org.konaframework.http.Request.SESSION_ATTRIBUTE, session)
        manager.withRequest(request) {
            val bus = Brew.test(EventBus::class.java).instance() as EventBus
            bus.flash("status", "saved")
        }
        // request without session attribute...
        manager.withRequest(org.konaframework.http.Request("GET", "/")) {
            val bus = Brew.test(EventBus::class.java).instance() as EventBus
            bus.flash("x", 1)
        }
    }
}
