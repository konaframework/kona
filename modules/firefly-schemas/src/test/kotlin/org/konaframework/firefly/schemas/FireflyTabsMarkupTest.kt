package org.konaframework.firefly.schemas

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FireflyTabsMarkupTest {
    private fun tabs(): Tabs = Tabs.make().tabs(
        Tab.make("One").schema(Text.make("first")),
        Tab.make("Two").schema(Text.make("second")),
    )

    @Test
    fun activeTabShowsItsPaneAndHidesTheRest() {
        val html = Schema.make().components(tabs().activeTab(1)).render()
        assertTrue(html.contains("""role="tablist""""))
        assertTrue(html.contains("""data-ff-tab-trigger="1" aria-selected="true" aria-current="true">Two"""))
        assertTrue(html.contains("""data-ff-tab-trigger="0" aria-selected="false">One"""))
        assertTrue(html.contains("""data-ff-tab-pane="0" hidden>"""))
        assertTrue(html.contains("""data-ff-tab-pane="1">"""))
    }

    @Test
    fun outOfRangeActiveTabFallsBackToTheFirst() {
        val html = Schema.make().components(tabs().activeTab(9)).render()
        assertTrue(html.contains("""data-ff-tab-trigger="0" aria-selected="true""""))
        assertTrue(html.contains("""data-ff-tab-pane="1" hidden>"""))
        val negative = Schema.make().components(tabs().activeTab(-1)).render()
        assertTrue(negative.contains("""data-ff-tab-trigger="0" aria-selected="true""""))
    }

    @Test
    fun panelsOffRendersOnlyTheTabList() {
        val html = Schema.make().components(tabs().tabPanels(false)).render()
        assertTrue(html.contains("ff-tab-trigger"))
        assertFalse(html.contains("data-ff-tab-pane"))
        assertFalse(html.contains("second"))
    }
}
