package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.auth.authorization.Gate
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import org.konaframework.http.Request
import org.konaframework.http.Router
import java.util.function.Supplier

class NavigationGroupsTest {
    @BeforeEach
    fun seed() {
        azDatabase()
        Firefly.reset()
        Firefly.gate(Gate().policy(AzPost::class.java, AzPostPolicy()))
    }

    @AfterEach
    fun cleanup() {
        Firefly.reset()
        Gate.clearDefault()
    }

    private fun listed(slug: String): Resource = object : Resource(slug) {
        override fun getPages(): Map<String, org.konaframework.firefly.widgets.Page> = mapOf("list" to ListPage("list"))
    }

    private fun body(panel: Panel, path: String = "/admin"): String {
        val router = Router()
        panel.mount(router)
        return router.find("GET", path)!!.route.action.handle(Request("GET", path, emptyMap(), emptyMap(), "")).body
    }

    private fun sidebar(html: String): String = html.substringAfter("""<nav class="ff-nav ff-nav-side">""").substringBefore("</nav>")

    private fun panel(): Panel = Panel().path("admin").authenticate(false).navigation(NavigationType.SIDE)

    // ——— NavigationGroup / NavigationBadge ———

    @Test
    fun groupBuilderAndGuards() {
        val group = NavigationGroup.make("Sales")
        assertEquals("Sales", group.label)
        assertFalse(group.isCollapsible())
        assertFalse(group.isCollapsed())
        assertNull(group.navigationIcon())
        assertEquals("", group.iconHtml())
        group.icon("shopping-bag").collapsible()
        assertTrue(group.isCollapsible())
        assertEquals("shopping-bag", group.navigationIcon())
        assertTrue(group.iconHtml().contains("ff-nav-icon"))
        assertTrue(NavigationGroup.make("Folded").collapsed().isCollapsed())
        assertTrue(NavigationGroup.make("Folded").collapsed().isCollapsible())
        assertFalse(NavigationGroup.make("Open").collapsible().collapsed(false).isCollapsed())
        assertFalse(NavigationGroup.make("Off").collapsible().collapsible(false).isCollapsible())
        // An unknown icon falls back to the default icon rather than failing the page.
        assertTrue(NavigationGroup.make("Odd").icon("no-such-icon-anywhere").iconHtml().contains("ff-nav-icon"))
        assertThrows<FireflyException> { NavigationGroup.make(" ") }
        assertEquals("primary", NavigationBadge("3").color)
        assertEquals("danger", NavigationBadge("3", "danger").color)
    }

    @Test
    fun duplicateGroupsFailAndRedeclaringReplaces() {
        assertThrows<FireflyException> { Panel().navigationGroups(NavigationGroup.make("A"), NavigationGroup.make("A")) }
        val panel = panel()
            .navigationGroups(NavigationGroup.make("Old"))
            .navigationGroups(NavigationGroup.make("New"))
            .resource(listed("orders").navigationGroup("Old"))
        // The second call replaces the first; "Old" is still drawn as an undeclared group.
        assertTrue(sidebar(body(panel)).contains("""data-ff-nav-group="Old""""))
    }

    // ——— Resource and page settings ———

    @Test
    fun resourceSettings() {
        val resource = listed("orders")
        assertEquals("orders", resource.navigationLabel().lowercase())
        assertNull(resource.navigationGroup())
        assertNull(resource.navigationSort())
        assertNull(resource.navigationBadge())
        assertTrue(resource.shouldRegisterNavigation())
        resource.navigationLabel("Sales orders").navigationGroup("Sales").navigationSort(5).shouldRegisterNavigation(false)
        assertEquals("Sales orders", resource.navigationLabel())
        assertEquals("Sales", resource.navigationGroup())
        assertEquals(5, resource.navigationSort())
        assertFalse(resource.shouldRegisterNavigation())
        assertThrows<FireflyException> { resource.navigationLabel(" ") }
        assertThrows<FireflyException> { resource.navigationGroup(" ") }
        assertThrows<FireflyException> { resource.navigationBadgeColor(" ") }
        var count: Any? = 3
        resource.navigationBadge(Supplier { count })
        assertEquals("3", resource.navigationBadge()!!.text)
        assertEquals("primary", resource.navigationBadge()!!.color)
        resource.navigationBadgeColor("success")
        assertEquals("success", resource.navigationBadge()!!.color)
        resource.navigationBadge("danger", Supplier { count })
        assertEquals("danger", resource.navigationBadge()!!.color)
        count = null
        assertNull(resource.navigationBadge())
        count = " "
        assertNull(resource.navigationBadge())
        count = 0
        assertEquals("0", resource.navigationBadge()!!.text)
    }

    @Test
    fun pageSettings() {
        val page = PanelPage("reports")
        assertEquals("reports", page.navigationLabel())
        assertNull(page.navigationGroup())
        assertNull(page.navigationSort())
        assertNull(page.navigationBadge())
        assertTrue(page.shouldRegisterNavigation())
        page.navigationLabel("Reports").navigationGroup("Insights").navigationSort(2).shouldRegisterNavigation(false)
        assertEquals("Reports", page.navigationLabel())
        assertEquals("Insights", page.navigationGroup())
        assertEquals(2, page.navigationSort())
        assertFalse(page.shouldRegisterNavigation())
        assertThrows<FireflyException> { page.navigationLabel("") }
        assertThrows<FireflyException> { page.navigationGroup("") }
        page.navigationBadge("warning", Supplier { 7 })
        assertEquals("warning", page.navigationBadge()!!.color)
        page.navigationBadge(Supplier { 8 }).navigationBadgeColor("info")
        assertEquals("8", page.navigationBadge()!!.text)
        assertEquals("info", page.navigationBadge()!!.color)
        assertEquals("Dashboard", PanelPage("").navigationLabel())
        assertEquals("Home", PanelPage("").navigationLabel("Home").navigationLabel())
    }

    @Test
    fun itemSettings() {
        val item = NavigationItem("x")
        assertNull(item.groupName())
        assertNull(item.sortValue())
        assertNull(item.badgeFor(SchemaBridge.open(emptyMap())))
        item.group("G").sort(4).badge(NavigationBadge("9", "gray"))
        assertEquals("G", item.groupName())
        assertEquals(4, item.sortValue())
        val get = SchemaBridge.open(emptyMap())
        assertEquals("9", item.badgeFor(get)!!.text)
        item.badge(java.util.function.Function { NavigationBadge("10") })
        assertEquals("10", item.badgeFor(get)!!.text)
        assertThrows<FireflyException> { item.group(" ") }
    }

    // ——— Rendered navigation ———

    @Test
    fun discoveryGroupsOrderAndAppearance() {
        val panel = panel()
            .navigationGroups(
                NavigationGroup.make("Sales").icon("shopping-bag").collapsible(),
                NavigationGroup.make("Accounting"),
            )
            .resource(listed("invoices").navigationGroup("Accounting").navigationSort(1))
            .resource(listed("orders").navigationGroup("Sales").navigationSort(2).navigationLabel("Sales orders"))
            .resource(listed("quotes").navigationGroup("Sales").navigationSort(1))
            .resource(listed("zeta").navigationGroup("Zeta group"))
            .resource(listed("loose"))
            .page(PanelPage("reports").navigationGroup("Accounting").navigationSort(0).navigationLabel("Reports"))
        val html = sidebar(body(panel))
        // Ungrouped first (dashboard, loose), then Sales, Accounting (declared), then Zeta group (undeclared).
        val order = listOf(
            """href="/admin/loose/list"""",
            """data-ff-nav-group="Sales"""",
            """href="/admin/quotes/list"""",
            "Sales orders",
            """data-ff-nav-group="Accounting"""",
            "Reports",
            """href="/admin/invoices/list"""",
            """data-ff-nav-group="Zeta group"""",
        ).map { html.indexOf(it).also { at -> assertTrue(at >= 0, it) } }
        assertEquals(order.sorted(), order)
        // Sales is collapsible (details, open); Accounting is a plain section.
        assertTrue(html.contains("""<details class="ff-nav-group ff-nav-group-collapsible" data-ff-nav-group="Sales" open>"""))
        assertTrue(html.contains("""<section class="ff-nav-group" data-ff-nav-group="Accounting">"""))
        assertTrue(html.contains("ff-nav-group-label"))
        // Group icon is drawn in Sales' heading.
        assertTrue(html.substringAfter("""data-ff-nav-group="Sales"""").substringBefore("</summary>").contains("ff-nav-icon"))
        // The dashboard (no group) is not inside any group.
        assertFalse(html.substringBefore("""data-ff-nav-group="Sales"""").contains("ff-nav-group-label"))
    }

    @Test
    fun foldedGroupOpensWhenHoldingTheCurrentPage() {
        fun build() = panel()
            .navigationGroups(NavigationGroup.make("Sales").collapsed())
            .resource(listed("orders").navigationGroup("Sales"))
        assertTrue(sidebar(body(build(), "/admin")).contains("""data-ff-nav-group="Sales"><summary"""))
        assertTrue(sidebar(body(build(), "/admin/orders/list")).contains("""data-ff-nav-group="Sales" open>"""))
    }

    @Test
    fun withoutGroupsTheListStaysFlatAndSortedByLabelThenSort() {
        val panel = panel()
            .resource(listed("b").navigationLabel("Bravo"))
            .resource(listed("a").navigationLabel("Alpha"))
            .resource(listed("c").navigationLabel("Charlie").navigationSort(-1))
        val html = sidebar(body(panel))
        assertFalse(html.contains("ff-nav-group"))
        assertTrue(html.indexOf("Charlie") < html.indexOf("Alpha"))
        assertTrue(html.indexOf("Alpha") < html.indexOf("Bravo"))
    }

    @Test
    fun badgesRenderWithColorAndRecomputeEveryRequest() {
        var count = 4
        val panel = panel()
            .resource(listed("orders").navigationBadge("danger") { count })
            .resource(listed("quotes").navigationBadge { null })
            .page(PanelPage("reports").navigationBadge { "<b>" })
        val router = Router()
        panel.mount(router)
        fun html(): String = router.find("GET", "/admin")!!.route.action.handle(Request("GET", "/admin", emptyMap(), emptyMap(), "")).body
        assertTrue(html().contains("""<span class="ff-nav-badge" data-ff-color="danger">4</span>"""))
        count = 12
        assertTrue(html().contains("""<span class="ff-nav-badge" data-ff-color="danger">12</span>"""))
        // Null hides it; text and color are escaped.
        assertEquals(2, html().split("ff-nav-badge\"").size - 1)
        assertTrue(html().contains("&lt;b&gt;"))
        assertFalse(html().contains("<b>"))
    }

    @Test
    fun shouldRegisterNavigationHidesOnlyTheLink() {
        val hidden = listed("secret").shouldRegisterNavigation(false)
        val panel = panel()
            .resource(hidden)
            .resource(listed("shown"))
            .page(PanelPage("quiet").shouldRegisterNavigation(false))
            .page(PanelPage("child").parent("quiet"))
        val router = Router()
        panel.mount(router)
        val html = router.find("GET", "/admin")!!.route.action.handle(Request("GET", "/admin", emptyMap(), emptyMap(), "")).body
        assertFalse(html.contains("""href="/admin/secret/list""""))
        assertFalse(html.contains("""href="/admin/quiet""""))
        assertTrue(html.contains("""href="/admin/shown/list""""))
        // Routes still answer.
        assertEquals(200, router.find("GET", "/admin/secret/list")!!.route.action.handle(Request("GET", "/admin/secret/list", emptyMap(), emptyMap(), "")).status)
        assertEquals(200, router.find("GET", "/admin/quiet")!!.route.action.handle(Request("GET", "/admin/quiet", emptyMap(), emptyMap(), "")).status)
    }

    @Test
    fun deniedItemsAndEmptyGroupsAreHidden() {
        val guard = AzGuard()
        val posts = object : Resource("posts") {
            init {
                model(AzPost::class.java)
                navigationGroup("Content")
            }

            override fun form(schema: Schema): Schema = schema

            override fun table(table: Table<*, *>): Table<*, *> = table
        }
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .navigationGroups(NavigationGroup.make("Content").collapsible(), NavigationGroup.make("Empty"))
            .resource(posts)
            .resource(listed("free").navigationGroup("Misc"))
            .mount(router)
        fun html(): String = router.find("GET", "/admin")!!.route.action.handle(Request("GET", "/admin", emptyMap(), emptyMap(), "")).body

        guard.signIn("viewer")
        assertTrue(html().contains("""data-ff-nav-group="Content""""))
        assertTrue(html().contains("""href="/admin/posts/list""""))

        guard.signIn("blocked")
        val denied = html()
        assertFalse(denied.contains("""data-ff-nav-group="Content""""))
        assertFalse(denied.contains("""href="/admin/posts/list""""))
        // A declared group with no members never renders; groups with visible members still do.
        assertFalse(denied.contains("""data-ff-nav-group="Empty""""))
        assertTrue(denied.contains("""data-ff-nav-group="Misc""""))
    }

    @Test
    fun manualItemsCanJoinGroupsAndIgnoreGroupsWhenNested() {
        val panel = panel()
            .navigationGroups(NavigationGroup.make("Tools"))
            .item(NavigationItem("calc").label("Calc").url("/admin/calc").group("Tools").sort(2))
            .item(NavigationItem("clock").label("Clock").url("/admin/clock").group("Tools").sort(1).badge(NavigationBadge("new", "info")))
            .item(NavigationItem("plain").label("Plain").url("/admin/plain"))
        val html = sidebar(body(panel))
        assertTrue(html.indexOf("Plain") < html.indexOf("""data-ff-nav-group="Tools""""))
        assertTrue(html.indexOf("Clock") < html.indexOf("Calc"))
        assertTrue(html.contains("""data-ff-color="info">new</span>"""))
    }

    @Test
    fun topNavigationRendersGroupsAsClusters() {
        val panel = Panel().path("admin").authenticate(false).topNavigation()
            .navigationGroups(NavigationGroup.make("Sales").icon("shopping-bag"))
            .resource(listed("orders").navigationGroup("Sales"))
            .resource(listed("loose"))
        val html = body(panel).substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>")
        assertTrue(html.contains("""<li class="ff-nav-item ff-cluster ff-nav-group">"""))
        assertTrue(html.indexOf("loose") < html.indexOf("Sales") || html.indexOf("Loose") < html.indexOf("Sales"))
        assertTrue(html.substringAfter("ff-nav-group").contains("""href="/admin/orders/list""""))
    }

    @Test
    fun appNavigationKeepsGroupsInTheSidebar() {
        val panel = Panel().path("admin").authenticate(false)
            .navigationGroups(NavigationGroup.make("Sales"))
            .resource(listed("orders").navigationGroup("Sales"))
        val html = body(panel, "/admin/orders/list")
        assertTrue(sidebar(html).contains("""data-ff-nav-group="Sales""""))
        assertTrue(sidebar(html).contains("""href="/admin/orders/list""""))
    }
}
