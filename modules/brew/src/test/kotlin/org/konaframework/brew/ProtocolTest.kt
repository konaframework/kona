package org.konaframework.brew

import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.fixtures.Greeter
import org.konaframework.brew.fixtures.TwoRoots
import org.konaframework.http.Request
import org.konaframework.session.Csrf
import org.konaframework.session.Session
import org.konaframework.session.StartSession
import org.konaframework.session.TokenMismatchException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProtocolTest {
    private val manager = brew().component("counter", Counter::class.java).component("greeter", Greeter::class.java)

    @Test
    fun `mount renders root attributes and snapshot shape`() {
        val html = manager.mount("counter")
        assertTrue(html.startsWith("<div wire:snapshot=\""), html)
        val snapshot = snapshotOf(html)
        assertEquals(mapOf("count" to 0), snapshot["data"])
        @Suppress("UNCHECKED_CAST")
        val memo = snapshot["memo"] as Map<String, Any?>
        assertEquals(listOf("id", "name", "path", "method", "children", "scripts", "assets", "errors", "locale"), memo.keys.toList())
        assertEquals("counter", memo["name"])
        assertEquals("GET", memo["method"])
        assertEquals(attr(html, "wire:id"), memo["id"])
        assertEquals("counter", attr(html, "wire:name"))
        assertEquals("[]", attr(html, "wire:effects"))
        assertTrue(snapshot["checksum"] is String)
        assertTrue(html.contains("<span>Count: 0</span>"))
    }

    @Test
    fun `update applies calls, re-renders and returns new checksum`() {
        val snapshot = snapshotOf(manager.mount("counter"))
        val (next, effects) = manager.update(snapshot, emptyMap(), listOf(call("increment"), call("add", 5), call("add", 2, 3)))
        assertEquals(mapOf("count" to 12), next["data"])
        assertTrue((effects["html"] as String).contains("Count: 12"))
        assertEquals(listOf(null, null, null), effects["returns"])
        val (_, more) = manager.update(next, mapOf("count" to "3"), listOf(call("double")))
        assertEquals(listOf(6), more["returns"])
    }

    @Test
    fun `mount params, defaults, and inline views`() {
        val html = manager.mount("greeter", mapOf("name" to "kona"))
        assertTrue(html.contains("Hello KONA!"), html)
        assertTrue(manager.mount("greeter").contains("Hello WORLD!"))
    }

    @Test
    fun `tampered or malformed snapshots are rejected`() {
        val snapshot = snapshotOf(manager.mount("counter")).toMutableMap()
        snapshot["data"] = mapOf("count" to 99)
        assertThrows<CorruptComponentPayloadException> { manager.update(snapshot, emptyMap(), emptyList()) }
        assertThrows<CorruptComponentPayloadException> { manager.update(mapOf("data" to 1), emptyMap(), emptyList()) }
        val ok = snapshotOf(manager.mount("counter"))
        assertThrows<CorruptComponentPayloadException> { manager.update(ok, emptyMap(), listOf(mapOf("method" to 1))) }
        val badMemo = ok.toMutableMap().also { it["memo"] = mapOf("id" to 1) }
        assertThrows<CorruptComponentPayloadException> { manager.update(badMemo, emptyMap(), emptyList()) }
        assertThrows<CorruptComponentPayloadException> { manager.update(ok - "checksum", emptyMap(), emptyList()) }
    }

    @Test
    fun `checksum differs across keys`() {
        val other = brew(config = BrewConfig(key = "other")).component("counter", Counter::class.java)
        val snapshot = snapshotOf(manager.mount("counter"))
        assertThrows<CorruptComponentPayloadException> { other.update(snapshot, emptyMap(), emptyList()) }
    }

    @Test
    fun `debug mode rejects multiple roots`() {
        val debug = brew(config = BrewConfig(key = "k", debug = true)).component("two", TwoRoots::class.java)
        assertThrows<MultipleRootElementsException> { debug.mount("two") }
        assertTrue(manager.component("two", TwoRoots::class.java).mount("two").contains("<div>b</div>"))
    }

    private fun request(body: String, headers: Map<String, String> = mapOf("X-Brew" to "true")): Request =
        Request("POST", "/brew/update", headers = headers, body = body)

    private fun payload(vararg components: Map<String, Any?>, token: String? = null): String =
        manager.json(linkedMapOf("_token" to token, "components" to components.toList()))

    private fun component(snapshot: Map<String, Any?>, updates: Map<String, Any?> = emptyMap(), calls: List<Any?> = emptyList()) =
        mapOf("snapshot" to manager.json(snapshot), "updates" to updates, "calls" to calls)

    @Test
    fun `http update endpoint round-trips bundled components`() {
        val a = snapshotOf(manager.mount("counter"))
        val b = snapshotOf(manager.mount("counter"))
        val response = manager.handleUpdate(request(payload(component(a, calls = listOf(call("increment"))), component(b, mapOf("count" to 7)))))
        assertEquals(200, response.status)
        val body = parse(response.body)
        assertEquals(emptyList<Any?>(), body["assets"])
        @Suppress("UNCHECKED_CAST")
        val results = body["components"] as List<Map<String, Any?>>
        assertEquals(2, results.size)
        assertEquals(mapOf("count" to 1), parse(results[0]["snapshot"] as String)["data"])
        assertEquals(mapOf("count" to 7), parse(results[1]["snapshot"] as String)["data"])
        @Suppress("UNCHECKED_CAST")
        assertTrue(((results[1]["effects"] as Map<String, Any?>)["html"] as String).contains("Count: 7"))
    }

    @Test
    fun `http guards`() {
        val snapshot = snapshotOf(manager.mount("counter"))
        assertEquals(403, manager.handleUpdate(request(payload(component(snapshot)), emptyMap())).status)
        assertEquals(404, manager.handleUpdate(request("{nope")).status)
        assertEquals(404, manager.handleUpdate(request("""{"components":[]}""")).status)
        assertEquals(404, manager.handleUpdate(request("""{"components":[{"snapshot":1}]}""")).status)
        assertEquals(404, manager.handleUpdate(request("""{"components":[1]}""")).status)
        assertEquals(404, manager.handleUpdate(request("""{"x":1}""")).status)
        val corrupt = """{"components":[{"snapshot":"{bad","updates":{},"calls":[]}]}"""
        assertThrows<CorruptComponentPayloadException> { manager.handleUpdate(request(corrupt)) }

        val tiny = brew(config = BrewConfig(key = "test-key", maxPayloadSize = 10, maxComponents = 1, maxCalls = 1, maxNestingDepth = 1))
            .component("counter", Counter::class.java)
        val e = assertThrows<PayloadGuardException> { tiny.handleUpdate(request(payload(component(snapshot)))) }
        assertEquals(413, e.status)

        val limits = brew(config = BrewConfig(key = "test-key", maxComponents = 1, maxCalls = 1, maxNestingDepth = 1))
            .component("counter", Counter::class.java)
        val many = payload(component(snapshot), component(snapshot))
        assertEquals(422, assertThrows<PayloadGuardException> { limits.handleUpdate(request(many)) }.status)
        assertEquals(
            422,
            assertThrows<PayloadGuardException> { limits.update(snapshot, emptyMap(), listOf(call("increment"), call("increment"))) }.status,
        )
        assertEquals(422, assertThrows<PayloadGuardException> { limits.update(snapshot, mapOf("count.a" to 1), emptyList()) }.status)

        val unlimited = brew(config = BrewConfig(key = "test-key", maxPayloadSize = null, maxComponents = null, maxCalls = null, maxNestingDepth = null))
            .component("counter", Counter::class.java)
        assertEquals(200, unlimited.handleUpdate(request(payload(component(snapshot), component(snapshot)))).status)
    }

    private fun session(token: String = "abc"): Session {
        val store = org.konaframework.session.ArraySessionStore()
        val session = org.konaframework.session.StoreSession("sid", store)
        session.put(Csrf.TOKEN_KEY, token)
        return session
    }

    @Test
    fun `csrf is enforced when a session is present`() {
        val snapshot = snapshotOf(manager.mount("counter"))
        val good = request(payload(component(snapshot), token = "abc")).setAttribute(StartSession.ATTRIBUTE, session())
        assertEquals(200, manager.handleUpdate(good).status)
        val header = request(payload(component(snapshot)), mapOf("X-Brew" to "1", "X-CSRF-TOKEN" to "abc"))
            .setAttribute(StartSession.ATTRIBUTE, session())
        assertEquals(200, manager.handleUpdate(header).status)
        val xsrf = request(payload(component(snapshot)), mapOf("X-Brew" to "1", "X-XSRF-TOKEN" to "abc"))
            .setAttribute(StartSession.ATTRIBUTE, session())
        assertEquals(200, manager.handleUpdate(xsrf).status)
        val bad = request(payload(component(snapshot), token = "nope")).setAttribute(StartSession.ATTRIBUTE, session())
        assertThrows<TokenMismatchException> { manager.handleUpdate(bad) }
        val off = brew(config = BrewConfig(key = "test-key", csrf = false)).component("counter", Counter::class.java)
        val ignored = request(payload(component(snapshot), token = "nope")).setAttribute(StartSession.ATTRIBUTE, session())
        assertEquals(200, off.handleUpdate(ignored).status)
    }

    @Test
    fun `request context exposes path, method, token and brew request flag`() {
        val req = Request("GET", "/dash").setAttribute(StartSession.ATTRIBUTE, session("tok"))
        val html = manager.withRequest(req) { manager.mount("counter") }
        @Suppress("UNCHECKED_CAST")
        val memo = snapshotOf(html)["memo"] as Map<String, Any?>
        assertEquals("dash", memo["path"])
        assertEquals(true, req.getAttribute(BrewManager.RENDERED_ATTRIBUTE))
        assertEquals("tok", manager.withRequest(req) { manager.csrfToken() })
        assertNull(manager.csrfToken())
        assertNull(manager.currentRequest())
        assertNull(manager.current())
        Brew.setManager(manager)
        assertFalse(Brew.isBrewRequest())
        val snapshot = snapshotOf(html)
        val updateReq = request(payload(component(snapshot)))
        manager.handleUpdate(updateReq)
        assertEquals(true, updateReq.getAttribute(BrewManager.UPDATE_ATTRIBUTE))
        assertTrue(manager.withRequest(updateReq) { Brew.isBrewRequest() })
        Brew.reset()
    }
}
