package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterEdgeCasesTest {
    private val rows = listOf(
        mapOf("id" to "1", "name" to "Ada", "status" to "open", "score" to 2, "flag" to "1", "joined" to "2024-01-15"),
        mapOf("id" to "2", "name" to null, "status" to "shut", "score" to "x", "flag" to "0", "joined" to "2024-01-16"),
        mapOf("id" to "3", "name" to null, "status" to "open", "score" to 7, "flag" to "true", "joined" to "2023-05-01"),
    )

    private val none = Cell { null }

    private fun table(vararg filters: Filter): Table<Map<String, Any?>, MemoryQuery> =
        Table.memory(rows).columns(TextColumn.make("name")).perPage(50).filters(*filters)

    @Test
    fun memoryQueryComparisonsAndSorting() {
        fun q() = MemoryQuery(rows)
        assertEquals(1, q().whereDate("joined", "!=", "2024-01-15").whereDate("joined", "==", "2024-01-16").get().size)
        assertEquals(3, q().where("name", "like", "%").orWhere("name", "null", null).get().size)
        assertEquals(2, q().where("flag", "=", true).get().size)
        assertEquals(1, q().where("flag", "=", false).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">", null).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">", "abc").get().size)
        val sorted = q().orderBy("name").orderBy("score", "desc").get().map { it["id"] }
        assertEquals(listOf("2", "3", "1"), sorted)
        assertEquals(listOf("1", "3", "2"), q().orderBy("score").get().map { it["id"] })
    }

    @Test
    fun memoryRelationshipsWithoutKeys() {
        val store = MemoryRecordStore(listOf(mapOf("id" to "1", "owner" to mapOf("name" to "Solo")), mapOf("id" to "2", "owner" to mapOf("id" to "o1", "title" to null))))
        assertEquals(0, store.query().also { store.constrainRelationship(it, "owner", listOf("Solo"), false) }.get().size)
        assertEquals(mapOf("Solo" to "Solo"), store.relationshipOptions("owner", "name", null, "", null, null))
        assertEquals(emptyMap<String, String>(), store.relationshipOptions("owner", "title", null, null, null, null))
    }

    @Test
    fun stateTransitionsAndSerialization() {
        val score = Filter.make("score").schema(FilterProbe("min"), FilterProbe("max"))
        val status = SelectFilter.make("status").options(mapOf("open" to "Open", "shut" to "Shut"))
        val flag = Filter.make("flag").attribute("flag")
        val tableWithDefaults = table(score, status.default("open"), flag)
        assertTrue(tableWithDefaults.filterSet().filters().isNotEmpty() && !tableWithDefaults.filterSet().isEmpty())
        tableWithDefaults.filterState("score", mapOf("min" to "", "max" to "3"))
        assertTrue(tableWithDefaults.filterData("status").isEmpty())
        assertEquals(mapOf("filters" to "1", "filters.score.max" to "3"), tableWithDefaults.filterQuery())
        tableWithDefaults.filterState("status", mapOf("value" to listOf("open", null, "shut")))
        tableWithDefaults.filterState("flag", mapOf("value" to true))
        assertEquals("open,shut", tableWithDefaults.filterQuery()["filters.status"])
        assertEquals("1", tableWithDefaults.filterQuery()["filters.flag"])

        val legacy = table(status, flag)
        legacy.filter("flag", "1")
        legacy.removeFilter("status")
        assertEquals(FilterData.single("1"), legacy.filterData("flag"))
        assertFalse(legacy.applyFilterQuery(mapOf("filters" to null)))
        assertTrue(Table.memory(rows).filterSet().isEmpty())
    }

    @Test
    fun formChromeVariants() {
        fun base(): Table<Map<String, Any?>, MemoryQuery> = table(SelectFilter.make("status").options(mapOf("open" to "Open")), Filter.make("flag").attribute("flag"))
        val modalNoReset = base().filtersLayout(FiltersLayout.MODAL).filtersResetAction { it.hidden() }.render()
        assertFalse(modalNoReset.contains("ff-filters-header"), modalNoReset)
        val liveFooterReset = base().deferFilters(false).filtersResetActionPosition(FiltersResetActionPosition.FOOTER).deselectAllRecordsWhenFiltered(false).render()
        assertTrue(liveFooterReset.contains("""<div class="ff-filters-footer"><a"""), liveFooterReset)
        assertFalse(liveFooterReset.contains("data-ff-deselect-on-filter"))
        val liveHeaderReset = base().deferFilters(false).render()
        assertFalse(liveHeaderReset.contains("ff-filters-footer"))
        val plainSection = base().filtersFormSchema(FilterSection.make().filters("status")).render()
        assertTrue(plainSection.contains("""<fieldset class="ff-component ff-filters-section"><div class="ff-schema ff-filters-grid" data-ff-columns="1""""), plainSection)
        assertTrue(base().filtersFormWidth("md").render().contains("--ff-filters-width: 28rem"))
        for ((width, dialog) in listOf("xs" to "sm", "md" to "md", "xl" to "lg", "5xl" to "xl")) {
            assertTrue(base().filtersLayout(FiltersLayout.MODAL).filtersFormWidth(width).render().contains("ff-dialog-$dialog"), width)
        }
        assertTrue(base().filtersLayout(FiltersLayout.MODAL).render().contains("ff-dialog-sm"))
        val cells = Table.memory(emptyList<Map<String, Any?>>()).columns(TextColumn.make("name"))
            .filters(SelectFilter.make("status").options(mapOf("open" to "Open")).default("open"), Filter.make("range").schema(FilterProbe("min")).default(mapOf("min" to "4")))
            .empty { cell -> "Empty ${cell.get("status")} ${cell.get("range.min")} ${cell.get("ghost")}" }
        assertTrue(cells.render().contains("Empty open 4 null"), cells.render())
    }

    @Test
    fun baseFilterControlsAndIndicators() {
        assertEquals("filters.x.min", FilterRenderContext(none, FilterData.single(null), null, "x").inputName("min"))
        val data = FilterData.single("a")
        assertTrue(Filter.make("x").toData(data) === data)
        assertEquals(FilterData.single(mapOf("k" to "v")), Filter.make("x").toData(mapOf("k" to "v")))
        val toggled = table(Filter.make("flag").attribute("flag").toggle())
        toggled.filter("flag", "1")
        assertEquals(2, toggled.load().records.size)
        assertTrue(Filter.make("x").indicators(none, FilterData.single(null)).isEmpty())
        assertEquals("Range", Filter.make("range").schema(FilterProbe("min")).indicators(none, FilterData.of(mapOf("min" to "1"))).single().label)
        assertEquals("Flag", Filter.make("flag").toggle().indicators(none, FilterData.single("1")).single().label)
        assertEquals("Name: Ada, Zed", Filter.make("name").options(mapOf("ada" to "Ada")).indicators(none, FilterData.single("ada,Zed")).single().label)
        assertEquals("Name: x", Filter.make("name").textInput().indicators(none, FilterData.single("x")).single().label)
        assertEquals("Fixed", Filter.make("name").indicator("Fixed").indicators(none, FilterData.single("x")).single().label)
        fun indicated(raw: Any?): List<String> =
            Filter.make("n").indicateUsing { raw }.indicators(none, FilterData.single("x")).map { it.label }
        assertEquals(emptyList<String>(), indicated(null))
        assertEquals(emptyList<String>(), indicated(" "))
        assertEquals(listOf("A", "B"), indicated(listOf("A", FilterIndicator.make("B"))))
        assertEquals(listOf("C"), indicated("C"))

        val checkbox = Filter.make("flag").modifyFormFieldUsing { it.label("Flagged").hiddenLabel() }.render(none, "1")
        assertTrue(checkbox.contains("checked") && !checkbox.contains("Flagged"), checkbox)
        assertTrue(Filter.make("flag").modifyFormFieldUsing { it.label("Flagged") }.render(none, null).contains("Flagged"))
        val toggleOn = Filter.make("flag").toggle().modifyFormFieldUsing { it.inline(false).hiddenLabel() }.render(none, "1")
        assertTrue(toggleOn.contains("""value="1"""") && toggleOn.contains("""data-ff-inline="false"""") && !toggleOn.contains("ff-toggle-label"), toggleOn)
        val toggleOff = Filter.make("flag").toggle().render(none, "0")
        assertTrue(toggleOff.contains("""value="0"""") && toggleOff.contains("ff-toggle-label"), toggleOff)
        assertTrue(Filter.make("q").textInput().modifyFormFieldUsing { it.placeholder("Find") }.render(none, null).contains("""placeholder="Find""""))
        var seen: Any? = null
        val schemaHook = Filter.make("range").schema(FilterProbe("min")).query { _, value -> seen = value }
        schemaHook.apply(MemoryQuery(rows), MemoryRecordStore(rows), mapOf("min" to "2"))
        assertEquals(mapOf("min" to "2"), seen)
        assertEquals("_", Filter.humanize("_"))
        assertEquals("Created at", Filter.humanize("created_at"))
    }

    @Test
    fun filterDataEdges() {
        val data = FilterData.of(mapOf("list" to listOf(" a ", null, ""), "num" to 5, "yes" to "YES", "no" to "No", "off" to "off", "bool" to false))
        assertEquals("a", data.string("list"))
        assertEquals(listOf("5"), data.list("num"))
        assertEquals(true, data.bool("yes"))
        assertEquals(false, data.bool("no"))
        assertEquals(false, data.bool("off"))
        assertEquals(false, data.bool("bool"))
        assertNull(data.bool("missing"))
        for (yes in listOf("true", "1", "on", "yes")) assertEquals(true, FilterData.single(yes).bool("value"), yes)
        for (no in listOf("false", "0", "off", "no")) assertEquals(false, FilterData.single(no).bool("value"), no)
        assertNull(FilterData.single("maybe").bool("value"))
        assertEquals(listOf("a", "b"), FilterData.single("a,,b").values())
        assertTrue(runCatching { FilterAction("x", "X").extraAttributes(mapOf(" " to "x")) }.isFailure)
    }

    @Test
    fun selectFilterBranches() {
        val store = MemoryRecordStore(
            listOf(
                mapOf("id" to "1", "status" to "open", "team" to mapOf("id" to "t1", "name" to "Core")),
                mapOf("id" to "2", "status" to "shut", "team" to mapOf("id" to "t2", "name" to "Docs")),
                mapOf("id" to "3", "status" to null),
            ),
        )
        fun names(table: Table<Map<String, Any?>, MemoryQuery>) = table.load().records.map { it["id"] }
        val static = SelectFilter.make("status").options(mapOf("open" to "Open", "shut" to "Shut")).hasEmptyOption()
        val statics = Table(store).filters(static)
        statics.filter("status", "open,shut")
        assertEquals(listOf("1", "2"), names(statics))
        statics.filter("status", SelectFilter.EMPTY)
        assertEquals(listOf("3"), names(statics))
        assertEquals("Status: Gone", static.indicators(FilterRenderContext(none, FilterData.single("Gone"), store, "status")).single().label)
        assertTrue(static.indicators(FilterRenderContext(none, FilterData.single(null), store, "status")).isEmpty())
        assertEquals("Fixed", SelectFilter.make("s").indicator("Fixed").indicators(FilterRenderContext(none, FilterData.single("x"), store, "s")).single().label)

        val team = SelectFilter.make("team").relationship("team", "name").optionsLimit(1)
        assertTrue(team.options(none, null).isEmpty())
        assertEquals(mapOf("t1" to "Core"), team.options(none, store))
        assertEquals(mapOf("t2" to "Docs"), team.options(none, store, keys = listOf("t2")))
        fun render(filter: SelectFilter, value: Any?, withStore: Boolean = true): String =
            filter.renderField(FilterRenderContext(none, filter.toData(value), if (withStore) store else null, filter.name))
        val lazy = SelectFilter.make("team").relationship("team", "name").searchable()
        assertTrue(render(lazy, null).let { it.contains("data-ff-relationship") && !it.contains(">Core<") }, render(lazy, null))
        assertTrue(render(lazy, "t2").contains(">Docs<"))
        assertFalse(render(lazy, null, withStore = false).contains("data-ff-relationship"))
        val eager = SelectFilter.make("team").relationship("team", "name").searchable().preload()
        assertTrue(render(eager, null).contains(">Core<"))
        val fixed = SelectFilter.make("team").relationship("team", "name").multiple()
        val chips = render(fixed, listOf("t1", "ghost"))
        assertTrue(chips.contains("readonly") && chips.contains(">ghost<") && !chips.contains("data-ff-relationship"), chips)
        val clearMulti = SelectFilter.make("tags").multiple().selectablePlaceholder(true)
        assertTrue(render(clearMulti, null).contains("ff-combobox-empty\" data-ff-combobox-empty>"), render(clearMulti, null))
        val clearSingle = SelectFilter.make("tags").searchable().selectablePlaceholder(true)
        assertTrue(render(clearSingle, "ghost").let { it.contains("value=\"ghost\"") && it.contains("data-ff-combobox-empty hidden") }, render(clearSingle, "ghost"))
        val noClear = SelectFilter.make("tags").searchable().selectablePlaceholder(false)
        assertTrue(render(noClear, null).contains("ff-combobox-empty\" data-ff-combobox-empty>"), render(noClear, null))
        val indicateUsing = SelectFilter.make("s").options(mapOf("x" to "X")).indicateUsing { "Using" }
        assertEquals("Using", indicateUsing.indicators(FilterRenderContext(none, FilterData.single("x"), store, "s")).single().label)
        assertEquals("S: X", SelectFilter.make("s").options(mapOf("x" to "X")).indicators(FilterRenderContext(none, FilterData.single("x"), store, "s")).single().label)
        val withChoices = SelectFilter.make("tags").searchable().options(mapOf("a" to "A"))
        assertTrue(render(withChoices, null).contains("data-ff-combobox-empty hidden"))
    }

    @Test
    fun ternaryAndQueryBuilderBranches() {
        val ternary = TernaryFilter.make("active").modifyFormFieldUsing { it.label("Is active") }
        assertEquals(FilterControl.SELECT, ternary.control(none))
        assertTrue(ternary.render(none, null).contains("Is active"))

        val builder = QueryBuilderFilter.make("q").modifyFormFieldUsing { it.label("Rules") }.constraints(
            NumberConstraint.make("score"),
            DateConstraint.make("joined"),
            SelectConstraint.make("status").options(mapOf("open" to "Open", "shut" to "Shut")).multiple(),
            RelationshipConstraint.make("team").label("Team"),
            TextConstraint.make("name"),
        )
        assertEquals(FilterControl.TEXT, builder.control(none))
        assertTrue(builder.indicators(FilterRenderContext(none, FilterData.single(null), null, "q")).isEmpty())
        val html = builder.render(none, "score:isMin:3;joined:isMonth:2024-01-01;status:is:open;name:isFilled")
        assertTrue(html.contains("Rules"), html)
        assertTrue(html.contains("""type="number" step="any" data-ff-qb-input value="3""""))
        assertTrue(html.contains("""type="date" data-ff-qb-input value="2024-01-01""""))
        assertTrue(html.contains("""<option value="open" selected>Open</option>"""))
        assertTrue(html.contains("""data-ff-qb-constraint="name""""))
        assertTrue(html.contains("""type="number" step="any" data-ff-qb-input value="" aria-label"""))
        assertEquals(2, table(builder).also { it.filter("q", "joined:isMonth:2024-01-01") }.load().records.size)
    }
}
