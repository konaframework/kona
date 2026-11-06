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
    fun activeParentGroupMarkedWhenHoldingCurrentPage() {
        fun build() =
            panel()
                .navigationGroups(
                    NavigationGroup.make("Sales").collapsed(),
                    NavigationGroup.make("Accounting"),
                )
                .resource(listed("orders").navigationGroup("Sales"))
                .resource(listed("invoices").navigationGroup("Accounting"))
        val idle = sidebar(body(build(), "/admin"))
        assertFalse(idle.contains("ff-nav-group-active"))
        val onOrders = sidebar(body(build(), "/admin/orders/list"))
        assertTrue(
            onOrders.contains(
                """<details class="ff-nav-group ff-nav-group-collapsible ff-nav-group-active" data-ff-nav-group="Sales" open>""",
            ),
        )
        assertTrue(onOrders.contains("""<section class="ff-nav-group" data-ff-nav-group="Accounting">"""))
        val onInvoices = sidebar(body(build(), "/admin/invoices/list"))
        assertTrue(
            onInvoices.contains("""<section class="ff-nav-group ff-nav-group-active" data-ff-nav-group="Accounting">"""),
        )
        assertFalse(onInvoices.contains("ff-nav-group-collapsible ff-nav-group-active"))
    }

    @Test
    fun navigationAccordionMarksSideNavOnly() {
        val with =
            panel()
                .navigationAccordion()
                .navigationGroups(
                    NavigationGroup.make("Sales").collapsed(),
                    NavigationGroup.make("Accounting").collapsed(),
                )
                .resource(listed("orders").navigationGroup("Sales"))
                .resource(listed("invoices").navigationGroup("Accounting"))
        // Script source also mentions the attribute name — assert on the nav opening tag only.
        assertTrue(body(with).contains("""<nav class="ff-nav ff-nav-side" data-ff-nav-accordion>"""))
        val without =
            panel()
                .navigationGroups(NavigationGroup.make("Sales").collapsible())
                .resource(listed("orders").navigationGroup("Sales"))
        assertFalse(body(without).contains("""<nav class="ff-nav ff-nav-side" data-ff-nav-accordion>"""))
        // APP chrome ignores accordion even when requested.
        val app =
            Panel()
                .path("admin")
                .authenticate(false)
                .navigation(NavigationType.APP)
                .navigationAccordion()
                .navigationGroups(NavigationGroup.make("Sales").collapsed())
                .resource(listed("orders").navigationGroup("Sales"))
        assertFalse(body(app).contains("""<nav class="ff-nav ff-nav-side" data-ff-nav-accordion>"""))
        val inverse =
            Panel()
                .path("admin")
                .authenticate(false)
                .navigation(NavigationType.INVERSE)
                .navigationAccordion()
                .navigationGroups(NavigationGroup.make("Sales").collapsed())
                .resource(listed("orders").navigationGroup("Sales"))
        assertFalse(body(inverse).contains("""<nav class="ff-nav ff-nav-side" data-ff-nav-accordion>"""))
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
        // Null hides it; text and color are escaped. Each badge is drawn twice: the desktop nav and the drawer.
        assertEquals(4, html().split("ff-nav-badge\"").size - 1)
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
        // SIDE keeps group headings; visibility of empty/denied groups is the same under APP roots.
        Panel().id("desk").path("admin").guard(guard).navigation(NavigationType.SIDE)
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
        assertTrue(html.contains("ff-nav-item ff-cluster ff-nav-group ff-nav-dropdown"))
        assertTrue(html.contains("""class="ff-nav-dropdown-trigger" aria-haspopup="menu" aria-expanded="false""""))
        assertTrue(html.contains("""<ul class="ff-nav-list" role="menu">"""))
        assertTrue(html.indexOf("loose") < html.indexOf("Sales") || html.indexOf("Loose") < html.indexOf("Sales"))
        assertTrue(html.substringAfter("ff-nav-group").contains("""href="/admin/orders/list""""))
        // The group's links live in its menu, not as a second top-level row.
        assertTrue(html.substringAfter(">Sales<").substringBefore("</ul>").contains("""href="/admin/orders/list""""))
    }

    @Test
    fun topMenusKeepNestingAndTheDrawerIsAnAccordion() {
        val panel = Panel().path("admin").authenticate(false).topNavigation()
            .navigationGroups(
                NavigationGroup.make("Sales"),
                NavigationGroup.make("Accounting"),
            )
            .resource(listed("orders").navigationGroup("Sales"))
            .resource(listed("invoices").navigationGroup("Accounting"))
        val html = body(panel, "/admin/orders/list")
        val top = html.substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>")
        assertTrue(top.contains("""aria-haspopup="menu""""))
        assertTrue(top.contains("""href="/admin/orders/list""""))
        assertFalse(html.contains("""<nav class="ff-nav ff-nav-side">"""))
        val drawer = html.substringAfter("""<nav class="ff-nav ff-nav-drawer" data-ff-nav-accordion>""").substringBefore("</nav>")
        assertTrue(drawer.contains("""data-ff-nav-group="Sales" open"""))
        assertTrue(drawer.contains("""<details class="ff-nav-group ff-nav-group-collapsible"""))
        // Accounting is not collapsible on the desktop tree; the drawer still folds it, and it is shut.
        assertTrue(drawer.contains("""data-ff-nav-group="Accounting""""))
        assertFalse(drawer.contains("""data-ff-nav-group="Accounting" open"""))
        assertTrue(html.contains("""<div class="ff-sidebar-offcanvas" hidden></div>"""))
        val desk = NavigationItem("desk").label("Desk").url("/admin/desk")
        desk.child(NavigationItem("notes").label("Notes").url("/admin/notes"))
        val manualPanel = Panel().path("admin").authenticate(false).topNavigation()
            .page(ListPage("desk"))
            .item(desk)
        manualPanel.cluster("Bin").child(NavigationItem("bags").label("Bags").url("/admin/bags"))
        val manual = body(manualPanel, "/admin/desk")
        val manualTop = manual.substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>")
        assertTrue(manualTop.contains("""aria-label="Open Desk menu""""), manualTop)
        assertTrue(manualTop.contains("""href="/admin/notes""""), manualTop)
        assertTrue(manualTop.contains(">Bin<"), manualTop)
        assertTrue(manualTop.contains("""href="/admin/bags""""), manualTop)
        assertTrue(manualTop.contains("""role="menu""""), manualTop)
        // Nothing registered: the drawer nav is omitted, the off-canvas aside remains.
        val quiet = body(
            Panel().path("admin").authenticate(false).topNavigation()
                .page(ListPage("one").shouldRegisterNavigation(false))
                .item(NavigationItem("hidden").label("Hidden").url("/admin/hidden").visible(false)),
            "/admin/one",
        )
        assertFalse(quiet.contains("""<nav class="ff-nav ff-nav-drawer""""))
        assertTrue(quiet.contains("ff-sidebar-offcanvas"))
    }

    private fun promotedSales(): Panel = Panel().path("admin").authenticate(false)
        .navigationGroups(NavigationGroup.make("Sales").icon("shopping-bag"))
        .resource(listed("orders").navigationGroup("Sales").navigationLabel("Sales orders"))
        .resource(listed("quotes").navigationGroup("Sales").navigationSort(1))
        .resource(listed("loose").navigationLabel("Loose"))

    @Test
    fun appNavigationPromotesGroupsToSidebarRoots() {
        val panel = promotedSales()
        val html = body(panel, "/admin/orders/list")
        val side = sidebar(html)
        // APP: group label is an L1 cluster in the sidebar; leaves are not SIDE-style group sections.
        assertTrue(side.contains(">Sales<"))
        assertTrue(side.contains("ff-cluster"))
        assertFalse(side.contains("""data-ff-nav-group="Sales""""))
        assertFalse(side.contains(">Sales orders<"))
        assertFalse(side.contains(">quotes<"))
        assertTrue(side.contains(">Loose<"))
        // Active group’s children sit in the top bar.
        val top = html.substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>")
        assertTrue(top.contains(">Sales orders<"))
        assertTrue(top.contains("""href="/admin/quotes/list""""))
        assertFalse(top.contains(">Loose<"))
        assertFalse(top.contains(">Sales<"))
        // Second member: the cluster stays active via its child, not its own (first-member) URL.
        val quotes = body(promotedSales(), "/admin/quotes/list")
        assertTrue(sidebar(quotes).contains("ff-nav-item ff-cluster ff-active"))
        assertTrue(quotes.substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>").contains("ff-active"))
    }

    @Test
    fun appNavigationOmitsBlankIconsAndEmptyClusterUrls() {
        val plain = Panel().path("admin").authenticate(false)
            .navigationGroups(NavigationGroup.make("Tools"))
            .item(NavigationItem("calc").label("Calc").group("Tools"))
            .resource(listed("loose"))
        val side = sidebar(body(plain))
        assertTrue(side.contains(">Tools<"))
        assertTrue(side.contains("ff-cluster"))
        assertFalse(side.contains("href=\"/admin/calc\""))
        // No groups: APP stays a flat list (the early return in appLevelOnes).
        val flat = sidebar(body(Panel().path("admin").authenticate(false).resource(listed("loose"))))
        assertFalse(flat.contains("ff-cluster"))
        assertTrue(flat.contains("""href="/admin/loose/list""""), flat)
    }

    @Test
    fun inverseNavigationPutsGroupsOnTopAndChildrenInTheSidebar() {
        val panel = promotedSales().navigation(NavigationType.INVERSE)
        val html = body(panel, "/admin/orders/list")
        val top = html.substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>")
        val side = sidebar(html)
        assertTrue(html.contains("""<aside class="ff-sidebar""""))
        assertTrue(top.contains(">Sales<"))
        assertTrue(top.contains("ff-cluster"))
        assertTrue(top.contains(">Loose<"))
        assertFalse(top.contains(">Sales orders<"))
        assertFalse(top.contains("""data-ff-nav-group="Sales""""))
        assertTrue(side.contains(">Sales orders<"))
        assertTrue(side.contains("""href="/admin/quotes/list""""))
        assertFalse(side.contains(">Loose<"))
        assertFalse(side.contains(">Sales<"))
        assertFalse(side.contains("""data-ff-nav-group="Sales""""))
        val quotes = body(promotedSales().navigation(NavigationType.INVERSE), "/admin/quotes/list")
        assertTrue(quotes.substringAfter("""<nav class="ff-nav ff-nav-top">""").substringBefore("</nav>").contains("ff-nav-item ff-cluster ff-active"))
        assertTrue(sidebar(quotes).contains("ff-active"))
        // Active root with no children: top bar keeps the roots, sidebar nav is omitted.
        val loose = body(promotedSales().navigation(NavigationType.INVERSE), "/admin/loose/list")
        assertTrue(loose.contains("""<nav class="ff-nav ff-nav-top">"""))
        assertFalse(loose.contains("""<nav class="ff-nav ff-nav-side">"""))
        assertTrue(loose.contains("ff-sidebar-brand"))
    }

    @Test
    fun inverseNavigationOmitsBothMenusWhenNothingIsVisible() {
        val panel = Panel().path("admin").authenticate(false).navigation(NavigationType.INVERSE)
            .item(NavigationItem("hidden").label("Hidden").url("/admin/hidden").visible(false))
            .page(ListPage("one"))
        val html = body(panel, "/admin/one")
        assertFalse(html.contains("""<nav class="ff-nav ff-nav-top">"""))
        assertFalse(html.contains("""<nav class="ff-nav ff-nav-side">"""))
        assertTrue(html.contains("""<aside class="ff-sidebar""""))
    }
}
