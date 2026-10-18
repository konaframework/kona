package org.konaframework.brew

import org.konaframework.brew.fixtures.Calculator
import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.fixtures.Kitchen
import org.konaframework.brew.fixtures.Status
import org.konaframework.brew.testing.Testable
import org.konaframework.http.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.function.Predicate

class Guarded : Component() {
    var items = mutableListOf("a", "b")
    var html = "<b>bold</b>"
    var flag = false

    fun deny() {
        throw HttpException(403, "nope")
    }

    fun missing() {
        throw HttpException(404, "gone")
    }

    fun unauthorized() {
        throw HttpException(401, "who")
    }

    override fun render(): Any = view("guarded", mapOf("extra" to "E"))
}

class TestableTest {
    private val manager = brew(mapOf("guarded" to "<div><p>first</p><p>{{ html }}</p><i>{!! html !!}</i><p>last</p></div>"))
        .component("counter", Counter::class.java)
        .component("guarded", Guarded::class.java)
        .component("calculator", Calculator::class.java)
        .component("kitchen", Kitchen::class.java)

    private fun test(name: String, params: Map<String, Any?> = emptyMap()) = Testable.create(manager, name, params)

    @Test
    fun `interactions round-trip through snapshots`() {
        val t = test("counter")
        t.call("increment").call("add", 2).assertSet("count", 3).assertSet("count", "3").assertSet("count", 3, true)
        t.set("count", 10).assertSet("count", 10).assertNotSet("count", 11).assertNotSet("count", "10", true)
        t.set(mapOf("count" to 1)).assertSee("Count: 1").assertDontSee("Count: 2")
        t.refresh().commit().assertSet("count", 1)
        t.call("double").assertReturned(2)
        t.call("quiet").assertRenderSkipped().assertSee("Count: 1")
        t.call("increment").assertRenderNotSkipped().assertSee("Count: 3")
        assertTrue(t.instance() is Counter)
        assertEquals(t.id(), (t.snapshot()["memo"] as Map<*, *>)["id"])
        assertTrue(t.effects().containsKey("html"))
        assertTrue(test("counter").html().contains("wire:snapshot"))
        assertFalse(test("counter").html(true).contains("wire:snapshot"))
        assertThrows<AssertionError> { t.assertSet("count", 99) }
        assertThrows<AssertionError> { t.assertNotSet("count", 3) }
        assertThrows<AssertionError> { t.assertReturned(99) }
        assertThrows<AssertionError> { t.assertRenderSkipped() }
        assertThrows<AssertionError> { t.call("quiet").assertRenderNotSkipped() }
        assertThrows<AssertionError> { t.assertSee("nope") }
        assertThrows<AssertionError> { t.assertDontSee("Count") }
        assertThrows<AssertionError> { test("counter").assertReturned(null) }
    }

    @Test
    fun `toggle, nested get and computed get`() {
        val g = test("guarded")
        g.toggle("flag").assertSet("flag", true).toggle("flag").assertSet("flag", false)
        g.assertSet("items.1", "b").assertCount("items", 2).assertCount("html", 11)
        assertThrows<AssertionError> { g.assertCount("items", 3) }
        assertThrows<AssertionError> { g.assertCount("flag", 1) }
        assertThrows<AssertionError> { g.get("nope") }
        val c = test("calculator")
        assertEquals(5, c.get("sum"))
        c.assertSet("sum", 5).assertViewHas("sum", 5).assertViewHas("a")
        assertEquals(5, c.viewData("sum"))
        val k = test("kitchen")
        k.assertCount("lookup", 1).assertCount("array", 2).assertSet("status", Status.DRAFT).assertSet("status", "DRAFT")
        k.assertSet("price", "9.99").assertNotSet("text", null).assertNotSet("nullable", 0)
        k.assertSet("nullable", null)
    }

    @Test
    fun `html assertions`() {
        val g = test("guarded")
        g.assertSee("<b>bold</b>").assertSeeHtml("<i><b>bold</b></i>").assertDontSeeHtml("<u>")
        g.assertSee(listOf("first", "last")).assertSee(arrayOf("first")).assertDontSee(listOf("zzz"))
        g.assertSeeInOrder(listOf("first", "<b>bold</b>", "last")).assertSeeHtmlInOrder(listOf("<p>first", "<i>"))
        assertThrows<AssertionError> { g.assertSeeInOrder(listOf("last", "first")) }
        assertThrows<AssertionError> { g.assertSeeHtml("<u>") }
        assertThrows<AssertionError> { g.assertDontSeeHtml("<i>") }
        g.assertViewIs("guarded").assertViewHas("extra", "E").assertViewHas("extra", Predicate<Any?> { it == "E" })
        g.assertViewHas("extra", { v: Any? -> v == "E" } as (Any?) -> Boolean as Any)
        assertThrows<AssertionError> { g.assertViewIs("other") }
        assertThrows<AssertionError> { g.assertViewHas("absent") }
        assertThrows<AssertionError> { g.assertViewHas("extra", "F") }
    }

    @Test
    fun `status assertions capture http exceptions`() {
        val g = test("guarded")
        g.assertOk().assertStatus(200)
        g.call("deny").assertForbidden()
        g.call("missing").assertNotFound()
        g.call("unauthorized").assertUnauthorized()
        assertThrows<AssertionError> { g.assertOk() }
        g.set("flag", true).assertOk()
    }
}
