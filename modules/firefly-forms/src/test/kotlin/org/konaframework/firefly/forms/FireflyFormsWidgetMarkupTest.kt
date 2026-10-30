package org.konaframework.firefly.forms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema

class FireflyFormsWidgetMarkupTest {
    private fun html(component: Component, values: Map<String, Any?> = emptyMap()): String =
        Schema.make().components(component).fill(values).render()

    @Test
    fun monthAndYearPickersRenderTheirOwnGrids() {
        val month = html(MonthPicker.make("billing"), mapOf("billing" to "2026-03"))
        assertTrue(month.contains("ff-picker-grid-months"))
        assertTrue(month.contains("""class="ff-day ff-day-selected" data-ff-month="3""""))
        assertTrue(month.contains("""class="ff-day" data-ff-month="4""""))
        assertTrue(month.contains("""data-ff-picker-weekdays style="display:none""""))
        val otherYear = html(MonthPicker.make("billing").defaultFocusedDate("2025-01-01"), mapOf("billing" to "2026-03"))
        assertFalse(otherYear.contains("ff-day-selected"))
        val empty = html(MonthPicker.make("billing"))
        assertFalse(empty.contains("ff-day-selected"))

        val year = html(YearPicker.make("fiscal"), mapOf("fiscal" to "2026"))
        assertTrue(year.contains("ff-picker-grid-years"))
        assertTrue(year.contains("""class="ff-day ff-day-selected" data-ff-year="2026""""))
        assertTrue(year.contains("2016 – 2027"))
        assertFalse(html(YearPicker.make("fiscal")).contains("ff-day-selected\" data-ff-year"))

        val badMonth = html(MonthPicker.make("billing"), mapOf("billing" to "2026-13"))
        assertTrue(badMonth.contains("ff-picker-grid-months"))
        assertTrue(html(MonthPicker.make("billing"), mapOf("billing" to "2026-00")).contains("ff-picker-grid-months"))
    }

    @Test
    fun weekPickerRendersIsoWeekGrid() {
        val week = html(WeekPicker.make("sprint"), mapOf("sprint" to "2026-W14"))
        assertTrue(week.contains("data-ff-picker-kind=\"week\""))
        assertTrue(week.contains("ff-picker-grid-weeks"))
        assertTrue(week.contains("ff-picker-weekdays-weeks"))
        assertTrue(week.contains("""data-ff-week="2026-W14""""))
        assertTrue(week.contains("ff-week-selected"))
        assertTrue(week.contains("ff-picker-weeknum"))
        assertTrue(week.contains("Select week"))
        assertTrue(week.contains("""value="Week 14, 2026""""))
        // Lead spacer before weekday labels so headers align with weeknum + 7 days (#427).
        assertTrue(
            week.contains("""ff-picker-weekdays-weeks" data-ff-picker-weekdays><span aria-hidden="true"></span>"""),
            week,
        )
        // Full Mon–Sun row highlighted, not a single day (#422).
        assertEquals(7, Regex("""ff-week-selected ff-day-selected""").findAll(week).count())
        assertTrue(week.contains("ff-week-start"))
        assertTrue(week.contains("ff-week-end"))
        assertFalse(html(WeekPicker.make("sprint")).contains("ff-week-selected"))
        assertTrue(html(WeekPicker.make("sprint").native()).contains("""type="week""""))
    }

    @Test
    fun weekPickerSundayStartAlignsRowsAndHighlightsFullWeek() {
        val week = html(
            WeekPicker.make("sprint").weekStartsOnSunday(),
            mapOf("sprint" to "2026-W14"),
        )
        assertTrue(week.contains("""data-ff-first-day="0""""))
        // Sunday-based W14 starts 2026-03-29; focus derives from the stored week token.
        assertTrue(week.contains("""data-ff-focused="2026-03-29""""))
        assertTrue(week.contains("""data-ff-day="2026-03-29" data-ff-week="2026-W14""""))
        assertTrue(week.contains("""data-ff-day="2026-04-04" data-ff-week="2026-W14""""))
        assertEquals(7, Regex("""ff-week-selected ff-day-selected""").findAll(week).count())
        val grid = week.substringAfter("ff-picker-grid-weeks").substringBefore("</div>")
        assertTrue(grid.indexOf("""data-ff-day="2026-03-29"""") < grid.indexOf("""data-ff-day="2026-03-30""""))
    }

    @Test
    fun weekPickerDropsInvalidIsoWeekFiftyThreeWhenFocusing() {
        // 2024 has no ISO week 53 — parse fails and focus falls back without crashing.
        assertTrue(html(WeekPicker.make("sprint"), mapOf("sprint" to "2024-W53")).contains("ff-picker-grid-weeks"))
    }

    @Test
    fun colorPickerKeepsSwatchAndValueInSync() {
        val hex = html(ColorPicker.make("color"), mapOf("color" to "#DF8723"))
        assertTrue(hex.contains("""value="#df8723" aria-label="Pick color""""))
        assertTrue(hex.contains("""placeholder="#000000""""))
        val rgb = html(ColorPicker.make("color").rgb(), mapOf("color" to "rgb(1, 2, 3)"))
        assertTrue(rgb.contains("""value="#000000" aria-label="Pick color""""))
        assertTrue(rgb.contains("""placeholder="rgb(0, 0, 0)""""))
        assertTrue(html(ColorPicker.make("color").hsl()).contains("""placeholder="hsl(0, 0%, 0%)""""))
        val custom = html(ColorPicker.make("color").placeholder("Pick one"))
        assertTrue(custom.contains("Pick one"))
        assertFalse(custom.contains("""placeholder="#000000""""))
    }

    @Test
    fun sliderFillHandlesAnEmptyRange() {
        val flat = html(Slider.make("score").minValue(5.0).maxValue(5.0), mapOf("score" to 5))
        assertTrue(flat.contains("--ff-slider-fill: 0%"))
        val half = html(Slider.make("score").minValue(0.0).maxValue(100.0), mapOf("score" to 50))
        assertTrue(half.contains("--ff-slider-fill: 50%"))
    }

    @Test
    fun markdownToolbarFallsBackForCustomTools() {
        val markdown = html(MarkdownEditor.make("notes").toolbarButtons("bold", "mention", "italic").disableToolbarButtons("italic"))
        assertTrue(markdown.contains("""data-ff-tool="bold" aria-label="Bold""""))
        assertTrue(markdown.contains("""data-ff-tool="mention" aria-label="mention" title="mention">mention</button>"""))
        assertFalse(markdown.contains("""data-ff-tool="italic""""))
        assertTrue(markdown.contains("Markdown is supported"))
    }

    @Test
    fun formJsonDecodesOnlyJsonText() {
        assertEquals(listOf("a"), FormJson.decode("""["a"]"""))
        assertEquals(mapOf("k" to "v"), FormJson.decode(""" {"k":"v"} """))
        assertEquals("plain", FormJson.decode("plain"))
        assertEquals("[broken", FormJson.decode("[broken"))
        assertEquals(3, FormJson.decode(3))
        assertEquals("""{"a":1}""", FormJson.encode(mapOf("a" to 1)))
    }

    @Test
    fun selectedValuesAcceptNullsAndJson() {
        assertEquals(setOf("", "a"), selectedValues(listOf(null, "a")))
        assertEquals(setOf("x", "y"), selectedValues("""["x","y"]"""))
        assertEquals(setOf("solo"), selectedValues("solo"))
        assertEquals(emptySet<String>(), selectedValues(null))
    }

    @Test
    fun checkboxListToolsRenderIndependently() {
        val bulkOnly = html(CheckboxList.make("roles").options(mapOf("a" to "A")).bulkToggleable())
        assertTrue(bulkOnly.contains("data-ff-checkbox-list-bulk"))
        assertFalse(bulkOnly.contains("data-ff-checkbox-list-search"))
        assertFalse(bulkOnly.contains("data-ff-checkbox-list-empty"))
        val searchOnly = html(CheckboxList.make("roles").options(mapOf("a" to "A")).searchable())
        assertTrue(searchOnly.contains("data-ff-checkbox-list-search"))
        assertTrue(searchOnly.contains("data-ff-checkbox-list-empty"))
        assertFalse(searchOnly.contains("data-ff-checkbox-list-bulk"))
        val plain = html(CheckboxList.make("roles").options(mapOf("a" to "A")), mapOf("roles" to """["a"]"""))
        assertFalse(plain.contains("ff-checkbox-list-tools"))
        assertTrue(plain.contains("checked"))
    }

    @Test
    fun repeaterItemsCarryJsonStateAndHeaderControls() {
        val notes = Repeater.make("notes").simple(TextInput.make("body")).minItems(1).maxItems(3).grid(2)
            .cloneable(true).collapsible(true).collapsed(true)
        val out = html(notes, mapOf("notes" to """[{"body":"First"},"skip"]"""))
        assertTrue(out.contains("""data-ff-min="1""""))
        assertTrue(out.contains("""data-ff-max="3""""))
        assertTrue(out.contains("""style="--ff-item-cols: 2""""))
        assertTrue(out.contains("""data-ff-collapsed="true""""))
        assertTrue(out.contains("data-ff-item-clone"))
        assertTrue(out.contains("data-ff-item-collapse"))
        assertTrue(out.contains("""data-ff-name="notes.0.body""""))
        assertTrue(out.contains("notes.__INDEX__.body"))
        assertTrue(out.contains("""value="[{&quot;body&quot;:&quot;First&quot;}"""))
        val mixed = Repeater.make("notes").simple(TextInput.make("body"))
        assertEquals(listOf(mapOf("body" to "First"), emptyMap<String, Any?>()), mixed.items(Schema.make().components(mixed).fill(mapOf("notes" to """[{"body":"First"},"skip"]"""))))

        val open = html(Repeater.make("notes").simple(TextInput.make("body")).collapsible(true), mapOf("notes" to listOf(mapOf("body" to "x"))))
        assertFalse(open.contains("""data-ff-collapsed="true""""))
        assertFalse(open.contains("--ff-item-cols"))
        assertFalse(open.contains("data-ff-min="))
        val stiff = html(Repeater.make("notes").simple(TextInput.make("body")).collapsed(true), mapOf("notes" to listOf(mapOf("body" to "x"))))
        assertFalse(stiff.contains("""data-ff-index="0" data-ff-collapsed"""))
        assertEquals(emptyList<Map<String, Any?>>(), Repeater.make("notes").items(Schema.make().fill(mapOf("notes" to "nope"))))
    }

    @Test
    fun itemHelpersKeepOnlyStringKeys() {
        assertEquals(mapOf("a" to 1), stringKeys(mapOf("a" to 1, 2 to "b")))
        val flags = ItemFlags(reorder = false, clone = false, delete = false, collapse = false, collapsed = false)
        val head = itemHead("Label", flags)
        assertFalse(head.contains("data-ff-item-up"))
        assertTrue(head.contains("""data-ff-item-label>Label</span>"""))
        assertEquals("""<li class="x" data-m data-ff-index="0">""", itemOpen("x", "data-m", "0", flags))
    }
}
