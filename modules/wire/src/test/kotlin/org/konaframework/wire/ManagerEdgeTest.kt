package org.konaframework.wire

import org.konaframework.wire.fixtures.Counter
import org.konaframework.wire.fixtures.Kitchen
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.lifecycle.StopPropagation
import org.konaframework.http.Request
import org.konaframework.session.StartSession
import org.konaframework.session.TokenMismatchException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkipsOnMount : Component() {
    fun mount() = skipRender()

    override fun template() = "<section>never</section>"
}

class BrokenRender : Component() {
    var swallow = true

    @Computed
    fun boom(): String = throw IllegalStateException("render failed")

    fun exception(e: Throwable, stop: StopPropagation) {
        if (swallow) stop()
    }

    override fun template() = "<div>{{ boom }}</div>"
}

class ManagerEdgeTest {
    private val manager = brew().component("counter", Counter::class.java).component("kitchen", Kitchen::class.java)
        .component("skips", SkipsOnMount::class.java).component("broken", BrokenRender::class.java)

    private fun resign(snapshot: Map<String, Any?>, mutate: (MutableMap<String, Any?>) -> Unit): Map<String, Any?> {
        val copy = LinkedHashMap(snapshot)
        val data = LinkedHashMap(copy["data"] as Map<String, Any?>)
        mutate(data)
        copy["data"] = data
        copy.remove("checksum")
        copy["checksum"] = manager.checksum.generate(copy)
        return copy
    }

    @Test
    fun `mount effects are serialized when present`() {
        manager.componentHook {
            object : ComponentHook() {
                override fun mount(params: Map<String, Any?>, key: String?) {
                    manager.current()
                }

                override fun dehydrate(context: org.konaframework.wire.lifecycle.ComponentContext) {
                    context.addEffect("listeners", listOf("saved"))
                }
            }
        }
        assertEquals("{\"listeners\":[\"saved\"]}", attr(manager.mount("counter"), "wire:effects"))
    }

    @Test
    fun `snapshot structure validation`() {
        val ok = snapshotOf(manager.mount("counter"))
        assertThrows<CorruptComponentPayloadException> { manager.update(ok - "memo", emptyMap(), emptyList()) }
        val noName = ok.toMutableMap().also { it["memo"] = (ok["memo"] as Map<*, *>) - "name" }
        assertThrows<CorruptComponentPayloadException> { manager.update(noName, emptyMap(), emptyList()) }
        assertThrows<CorruptComponentPayloadException> { manager.update(ok, emptyMap(), listOf(mapOf("method" to "x", "params" to "y"))) }
    }

    @Test
    fun `hydration skips unknown keys and keeps non-nullable defaults`() {
        val ok = snapshotOf(manager.mount("kitchen"))
        val result = manager.updateComponent(resign(ok) { it["gone"] = 1; it["number"] = null; it["nullable"] = null }, emptyMap(), emptyList())
        assertEquals(1, (result.component as Kitchen).number)
        assertNull((result.component as Kitchen).nullable)
        val text = manager.updateComponent(ok, mapOf("text" to null), emptyList())
        assertEquals("a", (text.component as Kitchen).text)
    }

    @Test
    fun `unlimited nesting and metadata-less calls`() {
        val unlimited = brew(config = WireConfig(key = "test-key", maxNestingDepth = null)).component("kitchen", Kitchen::class.java)
        val k = unlimited.mountComponent("kitchen", emptyMap(), null)
        val deep = unlimited.updateComponent(k.snapshot, mapOf("matrix.0.0" to 9), listOf(mapOf("method" to "\$refresh", "params" to emptyList<Any?>())))
        assertEquals(9, (deep.component as Kitchen).matrix[0][0])
        assertTrue(deep.effects.containsKey("html"))
    }

    @Test
    fun `skip on mount falls back to an empty root and swallowed render errors`() {
        val html = manager.mount("skips")
        assertTrue(html.startsWith("<div wire:snapshot="), html)
        assertTrue(html.endsWith("></div>"))
        val broken = manager.mountComponent("broken", emptyMap(), null)
        assertTrue(broken.html!!.startsWith("<div wire:snapshot="))
        val update = manager.updateComponent(broken.snapshot, emptyMap(), emptyList())
        assertFalse(update.effects.containsKey("html"))
        assertThrows<IllegalStateException> { manager.updateComponent(broken.snapshot, mapOf("swallow" to false), emptyList()) }
    }

    @Test
    fun `http payload shape and csrf without a stored token`() {
        val snapshot = manager.json(snapshotOf(manager.mount("counter")))
        fun post(component: Map<String, Any?>) = manager.handleUpdate(
            Request("POST", "/wire/update", headers = mapOf("X-Brew" to "1"), body = manager.json(mapOf("components" to listOf(component)))),
        )
        assertEquals(404, post(mapOf("snapshot" to snapshot, "updates" to listOf<Any?>(), "calls" to listOf<Any?>())).status)
        assertEquals(404, post(mapOf("snapshot" to snapshot, "updates" to mapOf<String, Any?>(), "calls" to mapOf<String, Any?>())).status)
        val session = org.konaframework.session.StoreSession("sid", org.konaframework.session.ArraySessionStore())
        val request = Request(
            "POST", "/wire/update", headers = mapOf("X-Brew" to "1"),
            body = manager.json(mapOf("_token" to "x", "components" to listOf(mapOf("snapshot" to snapshot, "updates" to mapOf<String, Any?>(), "calls" to listOf<Any?>())))),
        ).setAttribute(StartSession.ATTRIBUTE, session)
        assertThrows<TokenMismatchException> { manager.handleUpdate(request) }
    }
}
