package org.konaframework.firefly.widgets

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.discovered.BoomWidget
import org.konaframework.firefly.widgets.discovered.FoundWidget
import org.konaframework.firefly.widgets.discovered.JavaFound
import org.konaframework.firefly.widgets.discovered.NeedsArg
import org.konaframework.firefly.widgets.discovered.NotAWidget
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue

class FireflyWidgetsTest {
    @AfterEach
    fun clear() {
        ChartWidget.configureUsing(null)
    }

    @Test
    fun widgetSettersViewAndConfigureUsing() {
        val widget = Widget.make("box")
            .label("Box")
            .label { "Closure" }
            .heading("Head")
            .heading { "H" }
            .description("About")
            .description { "D" }
            .extraAttributes(mapOf("data-kind" to "widget"))
            .extraAttributes { mapOf("data-role" to it.get("/role").toString()) }
        widget.columnSpan(2)
        widget.columnSpan("full")
        widget.columnSpan { "1" }
        widget.poll("10s")
        widget.view("firefly.widgets.box")
        val html = widget.render(mapOf("role" to "admin"))
        assertTrue(html.contains("data-ff-view=\"firefly.widgets.box\""))
        assertTrue(html.contains("data-role=\"admin\""))
        assertTrue(html.contains("wire:poll.10s"))
        assertTrue(html.contains(WidgetScripts.WIDGET))
        assertEquals("plain", Widget.make("plain").name)
    }

    @Test
    fun formWrapsHiddenRecordIdForEditPosts() {
        val page = Page().form(Schema.make().components(Text.make("Hello")))
        org.junit.jupiter.api.Assertions.assertNull(page.mountedTable())
        org.junit.jupiter.api.Assertions.assertNotNull(page.mountedForm())
        org.junit.jupiter.api.Assertions.assertNull(Page().mountedForm())
        val listed = org.konaframework.firefly.tables.Table.memory(mapOf("id" to "1"))
        org.junit.jupiter.api.Assertions.assertSame(listed, Page().table(listed).mountedTable())
        val edit = page.render(
            mapOf(
                "csrfField" to """<input type="hidden" name="_token" value="tok" />""",
                "formAction" to "/admin/orders/edit",
                "record" to "42",
            ),
        )
        assertTrue(edit.contains("""id="ff-form""""))
        assertTrue(edit.contains("""name="id" value="42""""))
        assertTrue(edit.contains("""action="/admin/orders/edit""""))
        assertTrue(edit.contains("Hello"))
        val blank = page.render(mapOf("record" to "  "))
        assertFalse(blank.contains("""name="id""""))
    }

    @Test
    fun chartsRenderOnAPageForBothEngines() {
        val revenue = RevenueChart()
        val orders = OrdersChart()
        val page = Dashboard()
            .title("Shop")
            .headerActions(Action.make("create").label("Create"))
            .footerActions(Action.make("export").label("Export"))
            .schema(Schema().components(Text.make("Welcome")))
            .table(
                Table.memory(mapOf("id" to "1", "name" to "Ada"), mapOf("id" to "2", "name" to "Bea"))
                    .columns(TextColumn.make("name").searchable(true))
                    .modifyQueryUsing { query -> query.where("name", "Ada") },
            )
            .widgets(revenue, orders)
        val html = page.render()
        assertTrue(html.contains("ff-dashboard"))
        assertTrue(html.contains("ff-page-heading"))
        assertTrue(html.contains("Shop"))
        assertTrue(html.contains("Create"))
        assertTrue(html.contains("Export"))
        assertTrue(html.contains("Welcome"))
        assertTrue(html.contains("Ada"))
        assertFalse(html.contains(">Bea<") || html.contains("Bea"))
        assertTrue(html.contains("data-ff-engine=\"chartjs\""))
        assertTrue(html.contains("data-ff-engine=\"apexcharts\""))
        assertTrue(html.contains("data-ff-library-type=\"bar\""))
        assertTrue(html.contains("data-ff-library-type=\"area\""))
        assertEquals(1, html.split(WidgetScripts.CHART_JS).size - 1)
        assertEquals(1, html.split(WidgetScripts.APEX).size - 1)
        assertEquals(1, html.split(WidgetScripts.CHART).size - 1)
        assertTrue(revenue.render().contains("Revenue"))
        assertTrue(orders.render().contains("apexcharts.min.js"))
        assertFalse(orders.render().contains("chart.umd.js"))
        // Mode chips are opt-in via modeLabel(); the base Page leaves them off.
        assertFalse(html.contains("ff-page-mode"))
        val badged = object : Page() {
            override fun modeLabel(): String? = "edit"
            override fun fallbackTitle(): String = "Record"
        }.render()
        assertTrue(badged.contains("""data-ff-mode="edit""""))
        assertTrue(badged.contains("ff-page-mode"))
        val capped = Page().title("Wide").maxWidth("full").render()
        assertTrue(capped.contains("""data-ff-max-width="full""""))
        val fromDefault = Page().title("Defaulted").defaultMaxWidth("xl").render()
        assertTrue(fromDefault.contains("""data-ff-max-width="xl""""))
        val explicitWins = Page().title("Page").maxWidth("sm").defaultMaxWidth("full").render()
        assertTrue(explicitWins.contains("""data-ff-max-width="sm""""))
        assertFalse(explicitWins.contains("""data-ff-max-width="full""""))
        assertThrows<FireflyException> { Page().maxWidth("nope") }
        assertThrows<FireflyException> { Page().defaultMaxWidth("nope") }
    }

    @Test
    fun chartOptionsFiltersAndClosures() {
        val chart = ChartWidget.make("sales")
            .label("Sales")
            .label { "Sales" }
            .extraAttributes(mapOf("data-chart" to "1"))
            .extraAttributes { mapOf("data-live" to "1") }
            .engine(ChartEngine.CHART_JS)
        chart.columnSpan(2)
        chart.columnSpan("2")
        chart.columnSpan { "3" }
        chart.engine(ChartEngine.CHART_JS)
            .engine { ChartEngine.APEX_CHARTS }
            .type("area")
            .type { "donut" }
            .categories(listOf("Mon"))
            .categories { listOf("Tue") }
            .series(listOf(ChartSeries("A \"b\\c\n<d", listOf(1))))
            .series { listOf(ChartSeries("Closed", listOf(4))) }
            .series("east", listOf(ChartSeries("East", listOf(9))))
            .series("west", listOf(ChartSeries("West", listOf(8))))
            .options(mapOf("legend" to true, "hidden" to false, "cutout" to 40, "empty" to null, "note" to "ok"))
            .options { mapOf("from" to it.get("/from")) }
            .filters(mapOf("east" to "East", "west" to "West"))
            .filters { mapOf("east" to "East", "west" to "West") }
            .activeFilter("east")
            .activeFilter { "west" }
            .maxHeight("240px")
            .maxHeight { "120px" }
            .extraInputAttributes(mapOf("data-input" to "1"))
            .extraInputAttributes { mapOf("data-input" to "2") }
        val html = chart.render()
        assertTrue(html.contains("data-ff-engine=\"apexcharts\""))
        assertTrue(html.contains("data-ff-library-type=\"donut\""))
        assertTrue(html.contains("data-ff-max-height=\"120px\""))
        assertTrue(html.contains("selected"))
        assertTrue(html.contains("data-input=\"2\""))
        assertTrue(html.contains("West"))
        val encoded = ChartWidget.make("encoded")
            .series(listOf(ChartSeries("A \"b\\c\n<d", listOf(1))))
            .options(mapOf("legend" to true, "hidden" to false, "cutout" to 40, "empty" to null, "note" to "ok"))
        val encodedHtml = encoded.render()
        assertTrue(encodedHtml.contains("\\u003c"))
        assertTrue(encodedHtml.contains("true"))
        assertTrue(encodedHtml.contains("false"))
        assertTrue(encodedHtml.contains("null"))

        val mapped = ChartWidget.make("map")
            .type("area")
            .engine(ChartEngine.CHART_JS)
            .categories(emptyList())
            .series(emptyList())
            .filters(mapOf("east" to "East", "west" to "West"))
            .activeFilter("east")
            .extraInputAttributes(mapOf("data-input" to "lit"))
        val mappedHtml = mapped.render()
        assertTrue(mappedHtml.contains("data-ff-library-type=\"line\""))
        assertTrue(mappedHtml.contains("chart.umd.js"))
        assertFalse(mappedHtml.contains("apexcharts.min.js"))
        assertTrue(mappedHtml.contains("selected"))
        assertTrue(mappedHtml.contains("value=\"west\""))
        assertTrue(mappedHtml.contains("data-input=\"lit\""))

        val doughnut = ChartWidget.make("ring").type("donut").engine(ChartEngine.CHART_JS)
        assertTrue(doughnut.render().contains("data-ff-library-type=\"doughnut\""))
        val apexDonut = ChartWidget.make("ad").type("donut").engine(ChartEngine.APEX_CHARTS)
        assertTrue(apexDonut.render().contains("data-ff-library-type=\"donut\""))
        val bars = ChartWidget.make("bars").type("bar")
        assertTrue(bars.render().contains("data-ff-engine=\"chartjs\""))
        assertTrue(bars.render().contains("data-ff-library-type=\"bar\""))
        assertFalse(bars.render().contains("data-ff-max-height"))
        assertFalse(bars.render().contains("ff-chart-filter"))

        assertThrows<FireflyException> { ChartWidget.make("bad").type("radar") }
        assertThrows<FireflyException> { ChartWidget.make("bad").type { "radar" }.render() }
        assertThrows<FireflyException> { ChartWidget.make("bad").activeFilter(" ") }
        assertThrows<FireflyException> { ChartWidget.make("bad").activeFilter { " " }.render() }
        assertThrows<FireflyException> {
            ChartWidget.make("bad").series("east", listOf(ChartSeries("E", listOf(1)))).activeFilter("west").render()
        }
        assertThrows<FireflyException> {
            ChartWidget.make("bad").options(mapOf("bad" to Any())).render()
        }
        val badKey = linkedMapOf<Any, Any?>()
        badKey[1] = "a"
        @Suppress("UNCHECKED_CAST")
        assertThrows<FireflyException> {
            ChartWidget.make("bad").options(badKey as Map<String, Any?>).render()
        }
    }

    @Test
    fun chartConfigureUsing() {
        ChartWidget.configureUsing { it.type("pie") }
        assertTrue(ChartWidget.make("pie").render().contains("data-ff-type=\"pie\""))
        ChartWidget.configureUsing(null)
        assertTrue(ChartWidget.make("line").render().contains("data-ff-type=\"line\""))
    }

    @Test
    fun statsTableAccountAndEmptyPage() {
        val literal = Stat().label("Orders").value("3").description("today").color("success").icon("bag").chart(listOf(1, 2))
        val closed = Stat()
            .label { "Users" }
            .value { get -> get.get("/users").toString() }
            .description { "" }
            .color { "" }
            .icon { "" }
            .chart { listOf(4) }
        val blankDescription = Stat().label(" ").value("1")
        assertThrows<FireflyException> { blankDescription.render(WidgetBridge.open(emptyMap())) }
        val blankValue = Stat().label("Ok").value(" ")
        assertThrows<FireflyException> { blankValue.render(WidgetBridge.open(emptyMap())) }
        val missing = Stat()
        assertThrows<FireflyException> { missing.render(WidgetBridge.open(emptyMap())) }
        assertThrows<FireflyException> { Stat().label("Ok").render(WidgetBridge.open(emptyMap())) }
        val stats = StatsOverviewWidget.make("overview").extraAttributes(mapOf("data-s" to "1")).stats(literal, closed)
        stats.columnSpan(2)
        val statsHtml = stats.render(mapOf("users" to "9"))
        assertTrue(statsHtml.contains("Orders"))
        assertTrue(statsHtml.contains("ff-stat-description"))
        assertTrue(statsHtml.contains("data-ff-color=\"success\""))
        assertTrue(statsHtml.contains("ff-stat-icon"))
        assertTrue(statsHtml.contains("data-ff-chart=\"1,2\""))
        assertTrue(statsHtml.contains("Users"))
        assertTrue(statsHtml.contains(">9<"))
        assertThrows<FireflyException> { StatsOverviewWidget.make("empty").render() }

        val bare = Stat().label("Only").value("1")
        assertFalse(bare.render(WidgetBridge.open(emptyMap())).contains("ff-stat-description"))
        assertFalse(bare.render(WidgetBridge.open(emptyMap())).contains("data-ff-color"))
        assertFalse(bare.render(WidgetBridge.open(emptyMap())).contains("ff-stat-icon"))

        assertThrows<FireflyException> { TableWidget.make("rows").render() }
        val table = TableWidget.make("rows").extraAttributes(mapOf("data-t" to "1")).table(
            Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")),
        )
        table.columnSpan(1)
        assertTrue(table.render().contains("Ada"))

        assertThrows<FireflyException> { AccountWidget.make("me").render() }
        assertThrows<FireflyException> { AccountWidget.make("me").accountName(" ").render() }
        val account = AccountWidget.make("me")
            .extraAttributes(mapOf("data-a" to "1"))
            .accountName("Ada")
            .accountName { "Bea" }
            .email("")
            .email { "bea@example.com" }
            .avatar("")
            .avatar { "https://cdn.example/a.png" }
        account.columnSpan(1)
        val accountHtml = account.render()
        assertTrue(accountHtml.contains("Bea"))
        assertTrue(accountHtml.contains("bea@example.com"))
        assertTrue(accountHtml.contains("ff-account-avatar"))
        val blanks = AccountWidget.make("blank").accountName("Ada").email("").avatar("")
        assertFalse(blanks.render().contains("ff-account-email"))
        val namedOnly = AccountWidget.make("me").accountName("Ada")
        val namedHtml = namedOnly.render()
        assertTrue(namedHtml.contains("Ada"))
        assertFalse(namedHtml.contains("ff-account-email"))
        assertFalse(namedHtml.contains("ff-account-avatar"))

        val empty = Page().render()
        assertTrue(empty.contains("ff-page"))
        assertFalse(empty.contains("FormBody"))
        assertFalse(empty.contains("InfoBody"))
        val plainForm = Page().form(Schema().components(Text.make("FormBody"))).render()
        assertTrue(plainForm.contains("FormBody"))
        assertTrue(plainForm.contains("""id="ff-form""""))
        assertTrue(plainForm.contains("""method="post""""))
        assertFalse(plainForm.contains(" action="))
        val csrfForm = Page().form(Schema().components(Text.make("FormBody"))).render(
            mapOf(
                "csrfField" to """<input type="hidden" name="_token" value="tok" />""",
                "formAction" to "/admin/orders/create",
            ),
        )
        assertTrue(csrfForm.contains("""name="_token""""))
        assertTrue(csrfForm.contains("""action="/admin/orders/create""""))
        val blankAction = Page().form(Schema().components(Text.make("FormBody"))).render(
            mapOf("formAction" to "  ", "csrfField" to ""),
        )
        assertFalse(blankAction.contains(" action="))
        assertTrue(Page().infolistHtml("<p>InfoBody</p>").render().contains("InfoBody"))
        val related = Page().infolistHtml("<p>Info</p>").relationsHtml("""<section class="ff-relation">Rel</section>""").render()
        assertTrue(related.contains("""<p>Info</p></div><section class="ff-relation">Rel</section>""") || related.indexOf("Rel") > related.indexOf("Info"), related)
        assertFalse(related.contains("""<div class="ff-infolist"><section class="ff-relation">"""))
        assertTrue(Page().infolistHtml("<p>InfoBody</p>").render().contains("ff-infolist"))
        assertTrue(Page().infolistHtml("""<div class="ff-infolist"><p>Already</p></div>""").render().contains("Already"))
        // Relation-only markup (edit page) must not nest inside an .ff-infolist card (#271).
        val relationOnly =
            """<section class="ff-relation" data-ff-relation="lines"><div class="ff-table-wrap">rows</div></section>"""
        val relationHtml = Page().infolistHtml(relationOnly).render()
        assertTrue(relationHtml.contains("ff-relation"))
        assertTrue(relationHtml.contains("ff-table-wrap"))
        assertFalse(relationHtml.contains("ff-infolist"))
        // Schema + relation (view page) still gets the .ff-infolist host.
        val viewMix =
            """<div class="ff-schema"><div class="ff-entry">Ada</div></div>$relationOnly"""
        assertTrue(Page().infolistHtml(viewMix).render().contains("ff-infolist"))
        assertTrue(empty.contains(">Page<"))
        assertFalse(empty.contains("ff-dashboard"))
        assertTrue(Page().title("Static").render().contains(">Static<"))
        val titled = Page().title("Static").title { get -> get.get("/heading").toString() }
        assertTrue(titled.render(mapOf("heading" to "Hello & \"go\" <b>")).contains("Hello &amp; &quot;go&quot; &lt;b&gt;"))
        assertTrue(Dashboard().render().contains(">Dashboard<"))
        assertTrue(Dashboard().render().contains("ff-dashboard"))

        val crumbs = Page()
            .title("Edit Orders")
            .breadcrumbs(
                Breadcrumb.make("Dashboard", "/admin"),
                Breadcrumb.make("Orders", "/admin/orders/list"),
                Breadcrumb.make("Edit Orders"),
            )
            .render()
        assertTrue(crumbs.contains("ff-breadcrumbs"))
        assertTrue(crumbs.contains("""href="/admin""""))
        assertTrue(crumbs.contains("ff-breadcrumb-current"))
        assertTrue(crumbs.contains("Edit Orders"))
        // Crumbs above the toolbar; heading shares the toolbar row with actions (#359).
        assertTrue(crumbs.contains("ff-page-toolbar"))
        assertTrue(crumbs.indexOf("ff-breadcrumbs") < crumbs.indexOf("ff-page-toolbar"))
        assertTrue(crumbs.indexOf("ff-page-heading") > crumbs.indexOf("ff-page-toolbar"))
        assertTrue(crumbs.indexOf("</h1>") > crumbs.indexOf("ff-page-toolbar"))
        assertTrue(crumbs.indexOf("ff-page-header-actions") > crumbs.indexOf("ff-page-toolbar"))
        assertEquals("Orders", Breadcrumb.make("Orders", "/x").label)
        assertEquals("Solo", Breadcrumb.make("Solo").label)
        assertNull(Breadcrumb.make("Solo").href)
        assertTrue(
            Page().breadcrumbs(listOf(Breadcrumb.make("Only"))).render().contains("ff-breadcrumb-current"),
        )
        // Non-link middle crumbs (null or blank href) still render as plain text.
        val blankHref = Page().breadcrumbs(
            Breadcrumb.make("Home", "/"),
            Breadcrumb.make("Gap"),
            Breadcrumb("Spaced", "  "),
            Breadcrumb.make("Here"),
        ).render()
        assertTrue(blankHref.contains("Gap"))
        assertTrue(blankHref.contains("Spaced"))
        assertFalse(blankHref.contains("""href="  """))
        assertEquals("", Page().infolistHtml("").render().let { html ->
            // Empty infolist markup adds nothing beyond the page chrome.
            if (html.contains("ff-infolist")) "has" else ""
        })
    }

    @Test
    fun discoversWidgetsFromAPanelDirectory() {
        val root = Files.createTempDirectory("firefly-widgets")
        assertEquals(emptyList<Widget>(), WidgetDiscovery.discover(root, javaClass.classLoader))
        val blocked = root.resolve("blocked")
        Files.createDirectories(blocked)
        Files.writeString(blocked.resolve("widgets"), "nope")
        assertThrows<FireflyException> { WidgetDiscovery.discover(blocked, javaClass.classLoader) }

        val panel = root.resolve("panel")
        val widgets = panel.resolve("widgets")
        Files.createDirectories(widgets.resolve("nested"))
        Files.writeString(widgets.resolve("README.txt"), "skip")
        Files.writeString(widgets.resolve("Loose.kt"), "class Loose\n")
        assertThrows<FireflyException> { WidgetDiscovery.discover(panel, javaClass.classLoader) }
        Files.delete(widgets.resolve("Loose.kt"))
        Files.writeString(widgets.resolve("NoClass.kt"), "package app.widgets\nval ready = true\n")
        assertThrows<FireflyException> { WidgetDiscovery.discover(panel, javaClass.classLoader) }
        Files.delete(widgets.resolve("NoClass.kt"))
        Files.writeString(widgets.resolve("Wrong.kt"), "package app\nclass Other\n")
        assertThrows<FireflyException> { WidgetDiscovery.discover(panel, javaClass.classLoader) }
        Files.delete(widgets.resolve("Wrong.kt"))
        writeWidget(widgets.resolve("Ghost.kt"), "org.missing", "Ghost")
        assertThrows<FireflyException> { WidgetDiscovery.discover(panel, javaClass.classLoader) }
        Files.delete(widgets.resolve("Ghost.kt"))
        writeWidget(widgets.resolve("NeedsArg.kt"), "org.konaframework.firefly.widgets.discovered", "NeedsArg")
        assertThrows<FireflyException> { WidgetDiscovery.discover(panel, javaClass.classLoader) }
        Files.delete(widgets.resolve("NeedsArg.kt"))
        writeWidget(widgets.resolve("BoomWidget.kt"), "org.konaframework.firefly.widgets.discovered", "BoomWidget")
        assertThrows<FireflyException> { WidgetDiscovery.discover(panel, javaClass.classLoader) }
        Files.delete(widgets.resolve("BoomWidget.kt"))
        writeWidget(widgets.resolve("SkipWidget.kt"), "org.konaframework.firefly.widgets.discovered", "abstract class SkipWidget")
        writeWidget(widgets.resolve("NotAWidget.kt"), "org.konaframework.firefly.widgets.discovered", "NotAWidget")
        writeWidget(widgets.resolve("nested/FoundWidget.kt"), "org.konaframework.firefly.widgets.discovered", "FoundWidget")
        writeWidget(widgets.resolve("JavaFound.java"), "org.konaframework.firefly.widgets.discovered", "JavaFound")
        writeWidget(widgets.resolve("RevenueChart.kt"), "org.konaframework.firefly.widgets", "RevenueChart")
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = FoundWidget::class.java.classLoader
        try {
            val found = WidgetDiscovery.discover(panel)
            assertEquals(3, found.size)
            assertTrue(found.any { it is FoundWidget })
            assertTrue(found.any { it is JavaFound })
            assertTrue(found.any { it is RevenueChart })
            val page = Page().discoverWidgets(panel).widgets(OrdersChart())
            assertTrue(page.render().contains("ff-page-widgets"))
            val discovered = Page().discoverWidgets(panel)
            assertTrue(discovered.render().contains("data-ff-engine=\"chartjs\""))
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
        NeedsArg::class.java
        NotAWidget::class.java
        BoomWidget::class.java
    }

    @Test
    fun assetSources() {
        assertTrue(WidgetScripts.source(WidgetScripts.WIDGET).contains("ffWidget"))
        assertTrue(WidgetScripts.source(WidgetScripts.WIDGET).contains("ffDashboardWidget"))
        assertTrue(WidgetScripts.source(WidgetScripts.CHART).contains("mountFireflyChart"))
        assertTrue(WidgetScripts.source(WidgetScripts.CHART_JS).contains("Chart.js v4.4.6"))
        assertTrue(WidgetScripts.source(WidgetScripts.APEX).contains("ApexCharts"))
        assertThrows<FireflyException> { WidgetScripts.source("/_firefly/widgets/missing.js") }
    }

    @Test
    fun dashboardOrganizationAndNewWidgetKinds() {
        assertEquals(5000, parsePollingIntervalMs("5s"))
        assertEquals(500, parsePollingIntervalMs("500ms"))
        assertNull(parsePollingIntervalMs(null))
        assertNull(parsePollingIntervalMs("nope"))
        assertEquals("12.8M", formatCompactNumber(12_847_320))
        assertEquals("1K", formatCompactNumber(1000))
        assertEquals("-2B", formatCompactNumber(-2_000_000_000))
        assertEquals(3, autoStatsOverviewColumns(2))
        assertEquals(4, autoStatsOverviewColumns(4))
        assertEquals("heroicon-o-home", NavIcons.resolve("home"))
        assertEquals(NavIcons.DEFAULT, NavIcons.resolve(null))
        assertEquals(NavIcons.HOME, NavIcons.defaultForDashboard(true))

        val list = ListWidget.make("recent").apply {
            sort(40)
            heading("Recent")
            description("Latest")
            columns(ListWidgetColumn("Name", "name"), ListWidgetColumn("Sku", "meta.sku"))
            limit(2)
            records(
                listOf(
                    mapOf("name" to "Ada", "meta" to mapOf("sku" to "A1"), "url" to "/a"),
                    mapOf("name" to "Bea", "meta" to mapOf("sku" to "B1")),
                    mapOf("name" to "Cara"),
                ),
            )
        }
        val listHtml = list.render()
        assertTrue(listHtml.contains("Ada"))
        assertTrue(listHtml.contains("ff-list-widget-link"))
        assertTrue(listHtml.contains("A1"))
        assertFalse(listHtml.contains("Cara"))

        val card = CardWidget.make("tip").apply {
            html("<p>Hello</p>")
            heading("Tip")
        }
        assertTrue(card.render().contains("Hello"))
        assertTrue(CardWidget.make("empty").render().contains("No card content"))

        val nav = NavigationCardsWidget.make("nav")
            .cards(listOf(NavigationCard("Projects", "/admin/project", "folder")))
        val navHtml = nav.render()
        assertTrue(navHtml.contains("Projects"))
        assertTrue(navHtml.contains("ff-nav-card"))
        assertTrue(navHtml.contains("ff-nav-icon"))
        assertTrue(NavigationCardsWidget.make("empty-nav").render().contains("No resources"))

        val stats = StatsOverviewWidget.make("kpis").apply {
            lazy(false)
            pollingInterval(null)
            columns(5)
            stats(Stat().label("N").value("1").url("/x").openUrlInNewTab())
        }
        val statsHtml = stats.render()
        assertTrue(statsHtml.contains("ff-stat-link"))
        assertTrue(statsHtml.contains("--ff-stats-cols-sm: 5"))
        assertTrue(statsHtml.contains("""href="/x""""))

        val deferred = StatsOverviewWidget.make("lazy").apply {
            refreshUrl("/admin/widgets/lazy")
            stats(Stat().label("Hidden").value("9"))
        }
        assertTrue(deferred.render().contains("Loading"))
        assertTrue(
            deferred.render(mapOf("hydrateWidget" to "lazy")).contains("Hidden"),
        )

        val hidden = Widget.make("secret").canView(false).heading("Nope")
        val shown = Widget.make("open").sort(1).heading("Yes")
        val dash = Dashboard()
            .columns(2)
            .filtersForm(Schema().components(Text.make("Period")))
            .widgets(hidden, shown)
            .render(mapOf("panelHome" to "/admin"))
        assertTrue(dash.contains("data-ff-dashboard-columns=\"2\""))
        assertTrue(dash.contains("ff-dashboard-filters"))
        assertTrue(dash.contains("Yes"))
        assertFalse(dash.contains("Nope"))
        assertThrows<FireflyException> { Dashboard().columns(0) }
        assertTrue(Dashboard().render().contains("No dashboard widgets configured"))
        assertEquals(listOf(shown), Dashboard().widgets(shown).mountedWidgets())
        assertSame(shown, Dashboard().widgets(shown).findWidget("open"))
    }

    @Test
    fun dashboardWidgetCoverageEdges() {
        assertNull(parsePollingIntervalMs(""))
        assertNull(parsePollingIntervalMs("-1s"))
        assertEquals(0, parsePollingIntervalMs("0s"))
        assertEquals(1500, parsePollingIntervalMs("1.5s"))
        assertEquals("999", formatCompactNumber(999))
        assertEquals("1B", formatCompactNumber(1_000_000_000))
        assertEquals("1.5K", formatCompactNumber(1500))
        assertEquals(3, autoStatsOverviewColumns(0))
        assertEquals(3, autoStatsOverviewColumns(3))
        assertEquals(3, autoStatsOverviewColumns(5))
        assertEquals(4, autoStatsOverviewColumns(7))
        assertEquals(1, clampStatsOverviewColumns(0))
        assertEquals(12, clampStatsOverviewColumns(99))
        assertEquals(mapOf("default" to 1, "sm" to 3), resolveStatsOverviewColumns(null, 2))
        assertEquals(mapOf("default" to 1, "sm" to 2), resolveStatsOverviewColumns(2, 0))
        assertTrue(resolveStatsOverviewColumns(mapOf("lg" to 4)).containsKey("default"))
        assertEquals(mapOf("default" to 1, "sm" to 3), resolveStatsOverviewColumns(mapOf(1 to 2), 2))
        assertEquals(mapOf("default" to 1, "sm" to 3), resolveStatsOverviewColumns("nope", 1))
        assertTrue(statsOverviewGridStyle(mapOf("default" to 1, "md" to 2)).contains("--ff-stats-cols-md"))
        assertTrue(statsOverviewGridStyle(resolveStatsOverviewColumns(5, 4)).contains("--ff-stats-cols-sm: 5"))
        assertEquals("heroicon-o-folder", NavIcons.resolve("heroicon-o-folder"))
        assertEquals(NavIcons.DEFAULT, NavIcons.defaultForDashboard(false))

        val w = Widget.make("meta").apply {
            canView { true }
            pollingInterval(null)
            pollingInterval("2s")
            refreshUrl("/r")
            lazy(true)
        }
        assertEquals("2s", w.pollingInterval())
        assertEquals(2000, w.pollingIntervalMs())
        assertEquals("/r", w.refreshUrl())
        assertTrue(w.isLazy())
        assertTrue(w.visibleFor(WidgetBridge.open(emptyMap())))
        w.applyDashboardDefaults(false, null)
        assertTrue(w.isLazy())
        val fresh = Widget.make("fresh")
        fresh.applyDashboardDefaults(true, "5s")
        assertTrue(fresh.isLazy())
        assertEquals("5s", fresh.pollingInterval())
        assertTrue(
            Widget.make("hydrated").apply { lazy(true); refreshUrl("/x") }
                .render(mapOf("hydrateWidgets" to true))
                .contains("ff-dashboard-widget"),
        )
        assertTrue(
            Widget.make("poll-blank").apply { pollingInterval("  ") }.pollingInterval().isNullOrBlank(),
        )

        val list = ListWidget.make("rows").apply {
            columns(listOf(ListWidgetColumn("Name", "name"), ListWidgetColumn("Deep", "a.b")))
            records { listOf(mapOf("name" to "", "a" to "not-map"), mapOf("name" to null, "a" to mapOf("b" to mapOf("c" to 1)))) }
            limit(0)
            extraAttributes(mapOf("data-l" to "1"))
        }
        assertTrue(list.render().contains("No records to display"))
        list.limit(5)
        list.records(emptyList())
        assertTrue(list.render().contains("No records to display"))
        list.records(listOf(mapOf("name" to "Z", "a" to mapOf("b" to "deep"))))
        assertTrue(list.render().contains("deep"))
        list.refreshUrl("/admin/widgets/rows")
        list.lazy(true)
        assertTrue(list.render().contains("Loading") || list.render().contains("ff-widget-placeholder"))

        val card = CardWidget.make("c").apply {
            html { "from-get" }
            extraAttributes(mapOf("data-c" to "1"))
            refreshUrl("/admin/widgets/c")
            lazy(true)
        }
        assertTrue(card.render(mapOf("hydrateWidget" to "c")).contains("from-get"))
        assertTrue(card.render().contains("Loading") || card.render().contains("ff-widget-placeholder"))

        val nav = NavigationCardsWidget.make("n").apply {
            emptyMessage("None yet")
            cards { listOf(NavigationCard("A", "/a", "not-a-real-icon-zzzz")) }
            extraAttributes(mapOf("data-n" to "1"))
        }
        assertTrue(nav.render().contains("Open A"))
        nav.cards(emptyList())
        assertTrue(
            nav.render(mapOf("navigationCards" to listOf(mapOf("label" to "B", "href" to "/b", "icon" to "users"))))
                .contains("B"),
        )
        assertTrue(
            nav.render(mapOf("navigationCards" to listOf(NavigationCard("C", "/c"), mapOf("x" to 1), "skip")))
                .contains("C"),
        )
        assertTrue(nav.render(mapOf("navigationCards" to emptyList<Any>())).contains("None yet"))

        val linked = Stat().label("L").value("1").url { "/dyn" }.openUrlInNewTab(false)
        assertTrue(linked.render(WidgetBridge.open(emptyMap())).contains("""href="/dyn""""))
        assertFalse(linked.render(WidgetBridge.open(emptyMap())).contains("noopener"))

        val mapped = StatsOverviewWidget.make("map").apply {
            lazy(false)
            pollingInterval(null)
            columns(mapOf("sm" to 2, "lg" to 4))
            stats(Stat().label("S").value("1"))
        }
        assertTrue(mapped.render().contains("--ff-stats-cols-lg: 4"))
        assertTrue(mapped.render().contains("--ff-stats-cols-sm: 2"))

        val dash = Dashboard().filtersForm(Schema())
        assertNotNull(dash.mountedFiltersForm())
        assertEquals(3, dash.columns())
        dash.columns(4)
        assertEquals(4, dash.columns())
        dash.render() // empty filters schema still renders without throwing
        assertTrue(Dashboard().filtersForm(Schema().components(Text.make("Period"))).render().contains("Apply"))
        assertTrue(Page().form(Schema().components(Text.make("F"))).mountedForm() != null)
    }

    @Test
    fun classicWidgetScriptsHaveNoModuleExport() {
        // Served as plain <script src> (not type=module); top-level export throws in Chromium (#386).
        for (path in listOf(WidgetScripts.WIDGET, WidgetScripts.CHART)) {
            val source = WidgetScripts.source(path)
            assertFalse(source.lines().any { it.trimStart().startsWith("export ") }, path)
            assertTrue(source.trimStart().startsWith("(function"), path)
        }
        assertTrue(Widget.make("box").render().contains("""<script src="/_firefly/widgets/ff-widget.js"></script>"""))
    }

    private fun writeWidget(file: java.nio.file.Path, packageName: String, declaration: String) {
        val source = if (declaration.startsWith("abstract") || declaration.startsWith("class") || declaration.contains(" class ")) {
            "package $packageName\n$declaration\n"
        } else {
            "package $packageName\nclass $declaration\n"
        }
        Files.writeString(file, source)
    }
}
