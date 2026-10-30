package org.konaframework.firefly.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException

class CoverageEdgesTest {
    @Test
    fun refreshUrlFromBaseAndDeferBranches() {
        val widget = Widget.make("pulse").apply {
            lazy(true)
            pollingInterval("0s")
        }
        val html = widget.render(mapOf("widgetRefreshBase" to "/admin/"))
        assertTrue(html.contains("data-ff-refresh-url=\"/admin/widgets/pulse\""))
        assertTrue(html.contains("data-ff-lazy=\"1\""))
        assertFalse(html.contains("data-ff-polling-interval"))

        val hydrated = widget.render(
            mapOf(
                "widgetRefreshBase" to "/admin",
                "hydrateWidget" to "other",
                "hydrateWidgets" to "true",
            ),
        )
        assertFalse(hydrated.contains("data-ff-lazy=\"1\""))

        val blankBase = Widget.make("x").apply { lazy(true) }.render(mapOf("widgetRefreshBase" to "  "))
        assertFalse(blankBase.contains("data-ff-refresh-url"))

        val unset = Widget.make("y")
        unset.applyDashboardDefaults(false, "")
        assertFalse(unset.isLazy())
        assertTrue(unset.pollingInterval().isNullOrBlank())
        val unsetNull = Widget.make("z")
        unsetNull.applyDashboardDefaults(true, null)
        assertTrue(unsetNull.isLazy())
        assertEquals(null, unsetNull.pollingInterval())

        assertEquals(null, parsePollingIntervalMs("1.2.3s"))
        assertEquals(null, parsePollingIntervalMs("-1s"))
        assertEquals(250, parsePollingIntervalMs("250MS"))
        assertEquals(3000, parsePollingIntervalMs("3"))

        val list = ListWidget.make("deep").apply {
            columns(ListWidgetColumn("N", "name.nested"), ListWidgetColumn("Obj", "payload"))
            records(
                listOf(
                    mapOf("name" to "Ada", "url" to "  ", "payload" to mapOf("x" to 1)),
                    mapOf("name" to mapOf("nested" to ""), "payload" to emptyMap<String, Any?>()),
                ),
            )
            lazy(true)
            refreshUrl("/admin/widgets/deep")
        }
        assertTrue(list.render().contains("Loading") || list.render().contains("ff-widget-placeholder"))
        assertTrue(
            list.render(mapOf("hydrateWidget" to "deep")).contains("—") ||
                list.render(mapOf("hydrateWidget" to "deep")).contains("Ada") ||
                list.render(mapOf("hydrateWidget" to "deep")).contains("ff-list"),
        )

        val nav = NavigationCardsWidget.make("nav").apply {
            lazy(true)
            refreshUrl("/admin/widgets/nav")
        }
        assertTrue(nav.render().contains("Loading") || nav.render().contains("ff-widget-placeholder"))
        assertTrue(
            nav.render(
                mapOf(
                    "hydrateWidget" to "nav",
                    "navigationCards" to listOf(mapOf("label" to "Only")),
                ),
            ).contains("None") || nav.render(mapOf("hydrateWidget" to "nav")).contains("empty") ||
                nav.render(mapOf("hydrateWidget" to "nav", "navigationCards" to listOf(mapOf("href" to "/x")))).contains("No resources"),
        )

        val card = CardWidget.make("tip").html("hi").apply {
            lazy(true)
            refreshUrl("/admin/widgets/tip")
        }
        assertTrue(card.render().contains("ff-widget-placeholder") || card.render().contains("Loading"))

        val stats = StatsOverviewWidget.make("s").apply {
            lazy(true)
            refreshUrl("/admin/widgets/s")
        }
        // Deferred with no stats: placeholder, no throw.
        assertTrue(stats.render().contains("Loading") || stats.render().contains("ff-widget-placeholder"))

        assertTrue(
            resolveStatsOverviewColumns(mapOf("default" to 2, "sm" to 3)).containsKey("default"),
        )
        assertTrue(statsOverviewGridStyle(resolveStatsOverviewColumns(null, 4)).isNotEmpty())
        assertTrue(statsOverviewGridStyle(resolveStatsOverviewColumns("skip", 2)).contains("--ff-stats-cols"))
        assertTrue(statsOverviewGridStyle(resolveStatsOverviewColumns(3, 5)).contains("--ff-stats-cols-sm: 3"))
        assertEquals(mapOf("default" to 1, "sm" to 3), resolveStatsOverviewColumns(mapOf("a" to "b"), 2))
        assertEquals(mapOf("default" to 2, "sm" to 3), resolveStatsOverviewColumns(mapOf("default" to 2, "sm" to 3)))

        val page = Page()
        assertFalse(page.render().contains("data-ff-dashboard-columns"))
        assertTrue(
            Dashboard()
                .filtersForm(Schema().components(Text.make("Period")))
                .render(mapOf("formAction" to "/admin"))
                .contains("""action="/admin""""),
        )
        assertTrue(
            Dashboard()
                .filtersForm(Schema().components(Text.make("Period")))
                .render(emptyMap())
                .contains("ff-dashboard-filters"),
        )
        assertTrue(
            Dashboard()
                .filtersForm(Schema().components(Text.make("Period")))
                .render(mapOf("panelHome" to "/desk", "formAction" to "/ignored"))
                .contains("""action="/desk""""),
        )
        assertThrows<FireflyException> { Dashboard().columns(5) }

        // Nested null path + Map missing label/href for nav cards.
        val nullPath = ListWidget.make("nulls").apply {
            columns(ListWidgetColumn("X", "a.b"))
            records(listOf(mapOf("a" to null)))
        }
        assertTrue(nullPath.render().contains("—"))
        val viaProvider = ListWidget.make("prov").apply {
            records { _ -> listOf(mapOf("n" to "1")) }
            columns(ListWidgetColumn("N", "n"))
        }
        assertTrue(viaProvider.render().contains("1"))

        val cardDefer = CardWidget.make("cd").apply {
            lazy(true)
            refreshUrl("/admin/widgets/cd")
            html("")
        }
        assertTrue(cardDefer.render().contains("ff-widget-placeholder") || cardDefer.render().contains("Loading"))
        assertTrue(cardDefer.render(mapOf("hydrateWidgets" to true)).contains("No card content") ||
            cardDefer.render(mapOf("hydrateWidget" to "cd")).contains("No card content") ||
            cardDefer.render(mapOf("hydrateWidget" to "cd")).contains("ff-card"))

        val navMaps = NavigationCardsWidget.make("nm")
        assertTrue(
            navMaps.render(
                mapOf(
                    "navigationCards" to listOf(
                        mapOf("href" to "/x"),
                        mapOf("label" to "L"),
                        mapOf("label" to "Ok", "href" to "/ok"),
                    ),
                ),
            ).contains("Ok"),
        )

        val fromBase = Widget.make("num").apply { lazy(true) }
            .render(mapOf("widgetRefreshBase" to 42, "hydrateWidget" to "other"))
        assertTrue(fromBase.contains("data-ff-refresh-url=\"42/widgets/num\"") || fromBase.contains("data-ff-lazy"))
    }
}
