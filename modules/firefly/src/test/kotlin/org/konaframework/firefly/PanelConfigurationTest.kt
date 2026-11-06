package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Hook
import org.konaframework.firefly.widgets.Page
import org.konaframework.firefly.widgets.Widget
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router

class PanelConfigurationTest {
    @Test
    fun panelHookConfiguresOnceAndKeepsInit() {
        val plain = Panel()
        assertSame(plain, plain.panel(plain))

        val counted = CountedPanel()
        val router = Router()
        counted.mount(router)
        val home = send(router, "GET", "/desk/orders")
        assertEquals(200, home.status)
        assertTrue(home.body.contains("FromInit"))
        assertTrue(home.body.contains("ff-list"))
        assertEquals("firefly-desk", counted.guardName())
        counted.render(Request("GET", "/desk/orders"))
        assertEquals(1, counted.calls)
        assertThrows<FireflyException> { counted.mount(Router()) }

        val early = object : Panel() {
            override fun panel(panel: Panel): Panel = panel.id("early").brand("Early")
        }
        assertTrue(early.render(Request("GET", "/early")).contains("Early"))
    }

    @Test
    fun panelHookMustReturnThisPanel() {
        val swapped = object : Panel() {
            override fun panel(panel: Panel): Panel = Panel().id("other")
        }
        val mounted = assertThrows<FireflyException> { swapped.mount(Router()) }
        assertTrue(mounted.message!!.contains("must return this panel"))
        val rendered = assertThrows<FireflyException> { swapped.render(Request("GET", "/")) }
        assertTrue(rendered.message!!.contains("must return this panel"))
    }

    @Test
    fun varargRegistrationDelegatesToSingularMethods() {
        val notes = object : Resource("notes") {
            override fun getPages(): Map<String, Page> = mapOf("list" to ListPage("list"))
        }
        val router = Router()
        Panel()
            .path("bundle")
            .authenticate(false)
            .pages()
            .resources()
            .widgets()
            .plugins()
            .pages(ListPage("orders"), CustomPage("reports"))
            .resources(notes)
            .widgets(Widget.make("pulse").heading("Pulse"))
            .plugins(object : FireflyPlugin() {
                override fun id(): String = "note"
                override fun register(panel: Panel) {
                    panel.hook(Hook.BODY_END, "<p>Noted</p>")
                }
            })
            .mount(router)
        val orders = send(router, "GET", "/bundle/orders")
        assertEquals(200, orders.status)
        assertTrue(orders.body.contains("ff-list"))
        assertTrue(orders.body.contains("Noted"))
        assertEquals(200, send(router, "GET", "/bundle/reports").status)
        assertEquals(200, send(router, "GET", "/bundle/notes/list").status)
        val home = send(router, "GET", "/bundle")
        assertTrue(home.body.contains("Pulse") || home.body.contains("ff-widget"))
    }

    private fun send(router: Router, method: String, path: String): Response {
        val match = router.find(method, path) ?: throw AssertionError("missing $method $path")
        return match.route.action.handle(Request(method, path))
    }
}

private class CountedPanel : Panel() {
    var calls: Int = 0

    init {
        brand("FromInit")
    }

    override fun panel(panel: Panel): Panel {
        calls += 1
        return panel.id("desk").path("desk").authenticate(false).page(ListPage("orders"))
    }
}
