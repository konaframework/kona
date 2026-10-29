package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.support.FireflyException

class TableFiltersTest {
    private val rows = listOf(
        mapOf("id" to "1", "name" to "Ada", "status" to "open", "score" to 10, "active" to true, "joined" to "2024-01-15", "team" to mapOf("id" to "t1", "name" to "Core")),
        mapOf("id" to "2", "name" to "Bea", "status" to "draft", "score" to 250, "active" to false, "joined" to "2024-03-02", "team" to mapOf("id" to "t2", "name" to "Docs")),
        mapOf("id" to "3", "name" to "Cy", "status" to "open", "score" to 40, "active" to null, "joined" to null, "team_id" to "t1"),
        mapOf("id" to "4", "name" to "Dee", "status" to "shut", "score" to 5, "active" to true, "joined" to "2023-07-09", "tags" to listOf(mapOf("id" to "x", "name" to "Urgent"))),
    )

    private fun names(table: Table<Map<String, Any?>, MemoryQuery>): List<String> =
        table.load().records.map { it["name"].toString() }

    private fun people(vararg filters: Filter): Table<Map<String, Any?>, MemoryQuery> =
        Table.memory(rows).columns(TextColumn.make("name").searchable().sortable()).perPage(50).filters(*filters)

    @Test
    fun memoryQueryGroupsAndOperators() {
        fun q() = MemoryQuery(rows)
        assertEquals(3, q().where("name", "Ada").orWhere("name", "Bea").orWhere("score", ">", 30).get().size)
        assertEquals(2, q().where("status", "open").whereNested { it.where("score", "<", 20).orWhere("name", "Cy") }.get().size)
        assertEquals(3, q().where("status", "draft").orWhereNested { it.where("status", "open") }.get().size)
        assertEquals(4, q().whereNested { }.get().size)
        assertEquals(listOf("Cy"), q().whereNull("joined").get().map { it["name"] })
        assertEquals(3, q().whereNotNull("joined").get().size)
        assertEquals(3, q().whereIn("status", listOf("open", "shut")).get().size)
        assertEquals(1, q().whereNotIn("status", listOf("open", "shut")).get().size)
        assertEquals(2, q().where("name", "Dee").orWhereIn("status", listOf("draft")).get().size)
        assertEquals(1, q().where("status", "in", "draft").get().size)
        assertEquals(2, q().whereBetween("score", 6, 40).get().size)
        assertEquals(listOf("Ada"), q().whereDate("joined", "=", "2024-01-15T10:00:00").get().map { it["name"] })
        assertEquals(2, q().whereYear("joined", "=", 2024).get().size)
        assertEquals(listOf("Bea"), q().whereMonth("joined", "=", 3).get().map { it["name"] })
        assertEquals(listOf("Dee"), q().whereDay("joined", "=", "2023-01-09").get().map { it["name"] })
        assertEquals(0, q().whereDate("joined", "=", "soon").get().size)
        assertEquals(0, q().whereYear("joined", "=", "never").get().size)
        assertThrows(FireflyException::class.java) { q().whereDate("joined", "like", "2024") }
        assertEquals(2, q().whereRow { it["score"] as Int > 30 }.get().size)
        assertEquals(3, q().whereRow { it["score"] as Int > 30 }.orWhereRow { it["name"] == "Ada" }.get().size)
        assertEquals(listOf("Ada"), q().where("name", "like", "ad%").get().map { it["name"] })
        assertEquals(listOf("Bea"), q().where("name", "like", "%ea").get().map { it["name"] })
        assertEquals(listOf("Cy"), q().where("name", "like", "%Y%").get().map { it["name"] })
        assertEquals(1, q().where("score", ">", "200").get().size)
        assertEquals(2, q().where("active", "=", "1").get().size)
        assertEquals(1, q().where("active", "=", "false").get().size)
        assertEquals(0, q().where("active", "=", "maybe").get().size)
        assertEquals(1, q().where("team.name", "Core").get().size)
        assertThrows(FireflyException::class.java) { q().where("name", "regexp", "x") }
    }

    @Test
    fun memoryStoreRelationshipsDatesAndTrash() {
        val store = MemoryRecordStore(rows)
        fun count(block: (MemoryQuery) -> Unit): Int = store.query().also(block).get().size
        assertEquals(2, count { store.constrainRelationship(it, "team", listOf("t1"), false) })
        assertEquals(2, count { store.constrainRelationship(it, "team", listOf("t1"), true) })
        assertEquals(3, count { store.constrainRelationship(it, "team", null, false) })
        assertEquals(1, count { store.constrainRelationship(it, "tags", null, false) })
        assertEquals(1, count { store.constrainRelationship(it, "team", null, true) })
        assertEquals(1, count { store.constrainDate(it, "joined", "date", ">", "2024-02-01") })
        assertEquals(1, count { store.constrainDate(it, "joined", "day", "=", 9) })
        assertEquals(1, count { store.constrainDate(it, "joined", "month", "=", 7) })
        assertEquals(1, count { store.constrainDate(it, "joined", "year", "<", 2024) })
        assertThrows(FireflyException::class.java) { store.constrainDate(store.query(), "joined", "week", "=", 1) }
        assertEquals(3, count { store.group(it, false) { inner -> inner.where("status", "open").orWhere("name", "Dee") } })
        assertEquals(mapOf("t1" to "Core", "t2" to "Docs"), store.relationshipOptions("team", "name", null, null, null, null))
        assertEquals(mapOf("t2" to "Docs"), store.relationshipOptions("team", "name", null, "do", 5, null))
        assertEquals(mapOf("t1" to "Core"), store.relationshipOptions("team", "name", null, null, 1, null))
        assertEquals(mapOf("t2" to "Docs"), store.relationshipOptions("team", "name", null, null, null, listOf("t2")))
        assertEquals(emptyMap<String, String>(), store.relationshipOptions("team", "title", null, null, null, null))
        val untitled = MemoryRecordStore(listOf(mapOf("id" to "1", "owner" to mapOf("name" to "Solo"))))
        assertEquals(mapOf("Solo" to "Solo"), untitled.relationshipOptions("owner", "name", null, null, null, null))

        val soft = MemoryRecordStore(listOf(mapOf("id" to "1", "name" to "Kept"), mapOf("id" to "2", "name" to "Gone", "deleted_at" to "now")), softDeletes = true)
        val table = Table(soft).columns(TextColumn.make("name")).filters(TrashedFilter.make())
        assertEquals(listOf("Kept"), names(table))
        table.filter("trashed", "with")
        assertEquals(2, names(table).size)
        table.filter("trashed", "only")
        assertEquals(listOf("Gone"), names(table))
    }

    @Test
    fun customStoresMustOptInToRichConstraints() {
        val store = object : RecordStore<String, StringBuilder> by StubStore() {}
        val query = StringBuilder()
        store.group(query, true) { it.append("grouped") }
        assertEquals("grouped", query.toString())
        assertThrows(FireflyException::class.java) { store.constrainDate(query, "at", "date", "=", "x") }
        assertThrows(FireflyException::class.java) { store.constrainRelationship(query, "team", null, false) }
        assertThrows(FireflyException::class.java) { store.relationshipOptions("team", "name", null, null, null, null) }
    }

    @Test
    fun checkboxToggleTextAndSelectFilters() {
        val table = people(
            Filter.make("active").attribute("active").default(),
            Filter.make("open").attribute("status").toggle().queryUsing { query, _ -> (query as MemoryQuery).where("status", "open") },
            Filter.make("name").attribute("name").operator("like").textInput(),
            SelectFilter.make("status").options(mapOf("open" to "Open", "draft" to "Draft", "shut" to "Shut")).placeholder("Any status"),
        )
        assertEquals(listOf("Ada", "Dee"), names(table))
        assertEquals(listOf("Active"), table.filterIndicators().map { it.label })
        assertTrue(table.applyFilterQuery(mapOf("filters" to "1", "filters.open" to "1")))
        assertEquals(listOf("Ada", "Cy"), names(table))
        table.applyFilterQuery(mapOf("filters" to "1", "filters.open" to "0", "filters.name" to "e"))
        assertEquals(listOf("Bea", "Dee"), names(table))
        assertEquals(listOf("Name: e"), table.filterIndicators().map { it.label })
        table.applyFilterQuery(mapOf("filters" to "1", "filters.status" to "shut", "filters.active" to "1"))
        assertEquals(listOf("Dee"), names(table))
        assertEquals(mapOf("filters" to "1", "filters.active" to "1", "filters.status" to "shut"), table.filterQuery())
        assertEquals(listOf("Active", "Status: Shut"), table.filterIndicators().map { it.label })
        assertTrue(table.applyFilterQuery(mapOf("filters" to "reset")))
        assertEquals(listOf("Ada", "Dee"), names(table))
        assertTrue(table.filterQuery().isEmpty())
        assertFalse(table.applyFilterQuery(mapOf("search" to "x")))

        val html = table.render()
        assertTrue(html.contains("""data-ff-filter="active" data-ff-filter-kind="checkbox""""), html)
        assertTrue(html.contains("""data-ff-filter-kind="toggle""""))
        assertTrue(html.contains("""data-ff-filter-kind="text""""))
        assertTrue(html.contains("""data-ff-filter-kind="select""""))
        assertTrue(html.contains(">Any status<"))
        assertTrue(html.contains("""name="filters.active" value="1" checked"""))
        assertTrue(html.contains("data-ff-toggle"))
    }

    @Test
    fun selectFilterOptionsSearchMultipleAndRelationships() {
        val status = SelectFilter.make("status").options(mapOf("open" to "Open", "draft" to "Draft", "shut" to "Shut"))
            .multiple().searchable().optionsLimit(2)
        val table = people(status)
        assertEquals(mapOf("draft" to "Draft"), table.filterOptions("status", "dr"))
        // optionsLimit caps relationship lookups; static options are already all in memory.
        assertEquals(3, table.filterOptions("status", "").size)
        assertTrue(table.filterOptions("missing", "").isEmpty())
        table.filter("status", "open,shut")
        assertEquals(listOf("Ada", "Cy", "Dee"), names(table))
        assertEquals("Status: Open, Shut", table.filterIndicators().single().label)
        val html = table.render()
        assertTrue(html.contains("ff-filter-combobox"), html)
        assertTrue(html.contains("""data-ff-multiple="true""""))
        assertTrue(html.contains("""data-ff-state="open,shut""""))
        assertFalse(html.contains("data-ff-relationship"))
        assertTrue(status.isMultiple() && status.isSearchable())
        assertNull(status.relationshipName())
        assertThrows(FireflyException::class.java) { status.optionsLimit(0) }

        val team = SelectFilter.make("team").relationship("team", "name").hasEmptyOption()
        val teams = people(team)
        assertEquals("team", team.relationshipName())
        assertEquals(listOf("Core", "Docs", "None"), teams.filterOptions("team", "").values.sorted())
        teams.filter("team", "t1")
        assertEquals(listOf("Ada", "Cy"), names(teams))
        assertEquals("Team: Core", teams.filterIndicators().single().label)
        teams.filter("team", SelectFilter.EMPTY)
        assertEquals(listOf("Dee"), names(teams))
        assertEquals("Team: None", teams.filterIndicators().single().label)

        val native = SelectFilter.make("state").attribute("status").native().multiple()
            .options { mapOf("open" to "Open", "draft" to "Draft") }.selectablePlaceholder(false)
        val nativeHtml = people(native).filter("state", listOf("open", "draft")).render()
        assertTrue(nativeHtml.contains("""<select class="ff-input" name="filters.state" data-ff-filter-input multiple"""), nativeHtml)
        assertTrue(nativeHtml.contains("""value="draft" selected"""))
        val single = SelectFilter.make("solo").attribute("status").options(mapOf("open" to "Open")).searchable()
            .selectablePlaceholder(true).modifyFormFieldUsing { it.placeholder("Pick").label("Solo state") }
        val soloHtml = people(single).render()
        assertTrue(soloHtml.contains("""placeholder="Pick""""), soloHtml)
        assertTrue(soloHtml.contains("Solo state"))
        assertEquals(listOf("Ada", "Cy"), names(people(single).filter("solo", "open")))

        val custom = SelectFilter.make("bucket").options(mapOf("low" to "Low", "high" to "High"))
            .queryUsing { query, data -> (query as MemoryQuery).where("score", if (data.string("value") == "low") "<" else ">=", 20) }
        assertEquals(listOf("Ada", "Dee"), names(people(custom).filter("bucket", "low")))
        val legacy = SelectFilter.make("legacy").options(mapOf("Ada" to "Ada")).query { query, value -> (query as MemoryQuery).where("name", value) }
        assertEquals(listOf("Ada"), names(people(legacy).filter("legacy", "Ada")))
    }

    @Test
    fun ternaryFilterLabelsNullableAndCustomQueries() {
        val active = TernaryFilter.make("active").placeholder("Everyone").trueLabel("Active").falseLabel("Inactive")
        val table = people(active)
        val html = table.render()
        assertTrue(html.contains(">Everyone<") && html.contains(">Active<") && html.contains(">Inactive<"), html)
        assertTrue(html.contains("""value="" checked"""))
        table.filter("active", "true")
        assertEquals(listOf("Ada", "Dee"), names(table))
        table.filter("active", true)
        assertEquals(listOf("Ada", "Dee"), names(table))
        table.filter("active", "0")
        assertEquals(listOf("Bea"), names(table))
        assertEquals("Active: Inactive", table.filterIndicators().single().label)
        table.filter("active", "neither")
        assertEquals(4, names(table).size)

        val joined = people(TernaryFilter.make("joined").nullable().trueLabel("Joined").falseLabel("Pending"))
        joined.filter("joined", "false")
        assertEquals(listOf("Cy"), names(joined))
        val queried = people(
            TernaryFilter.make("big").queries({ q, _ -> (q as MemoryQuery).where("score", ">", 30) }, { q, _ -> (q as MemoryQuery).where("score", "<=", 30) }),
        )
        queried.filter("big", "false")
        assertEquals(listOf("Ada", "Dee"), names(queried))
        queried.filter("big", "true")
        assertEquals(listOf("Bea", "Cy"), names(queried))
        assertEquals(4, names(people(TernaryFilter.make("big").queries({ _, _ -> }, { _, _ -> }))).size)
        val hooked = people(TernaryFilter.make("active").queryUsing { q, data -> if (data.bool("value") == true) (q as MemoryQuery).where("name", "Cy") })
        hooked.filter("active", "true")
        assertEquals(listOf("Cy"), names(hooked))
    }

    @Test
    fun schemaFiltersIndicatorsAndRemoval() {
        val range = Filter.make("score").schema(FilterProbe("min"), FilterProbe("max")).columns(2)
            .queryUsing { query, data ->
                val q = query as MemoryQuery
                data.string("min")?.let { q.where("score", ">=", it) }
                data.string("max")?.let { q.where("score", "<=", it) }
            }
            .indicateUsing { data ->
                listOfNotNull(
                    data.string("min")?.let { FilterIndicator.make("Min $it").removeField("min").color("success") },
                    data.string("max")?.let { FilterIndicator.make("Max $it").removeFields("max") },
                )
            }
            .default(mapOf("min" to "6"))
        val locked = Filter.make("open").attribute("status").default("open").indicateUsing { FilterIndicator.make("Open only").removable(false) }
        val plain = Filter.make("status").attribute("status").indicator("Custom")
        val table = people(range, locked, plain).selectable(true)
        assertEquals(listOf("Ada", "Cy"), names(table))
        assertEquals(listOf("Min 6", "Open only"), table.filterIndicators().map { it.label })
        table.select("1")
        table.removeFilterFields("score", "min")
        assertTrue(table.selected().isEmpty())
        assertEquals(listOf("Ada", "Cy"), names(table))
        table.filterState("score", mapOf("max" to "20"))
        assertEquals(listOf("Ada"), names(table))
        table.call("removeFilter", "open")
        assertEquals(listOf("Ada", "Dee"), names(table))
        assertEquals(listOf("Max 20"), table.filterIndicators().map { it.label })
        table.call("removeAllFilters")
        assertEquals(4, names(table).size)
        table.call("resetFilters")
        assertEquals(listOf("Ada", "Cy"), names(table))
        table.filter("status", "shut")
        assertEquals(listOf("Custom"), table.filterIndicators().filter { it.label == "Custom" }.map { it.label })

        val view = table.render()
        assertTrue(view.contains("ff-filter-indicators"), view)
        assertTrue(view.contains("""data-ff-color="success""""))
        assertTrue(view.contains("ff-filter-indicator-remove"))
        assertTrue(view.contains("filters.score.min=6"))
        assertFalse(view.contains("Remove filter Open only"))
        assertTrue(view.contains("ff-filter-indicators-remove-all"))
        assertTrue(view.contains("""name="filters.score.min" value="6""""), view)
        assertTrue(view.contains("data-ff-columns=\"2\""), view)
        val keep = people(range).selectable(true).deselectAllRecordsWhenFiltered(false)
        keep.select("1")
        keep.removeAllFilters()
        assertEquals(listOf("1"), keep.selected())
        assertThrows(FireflyException::class.java) { keep.filterState("missing", emptyMap()) }
    }

    @Test
    fun layoutsActionsAndFormChrome() {
        fun table(): Table<Map<String, Any?>, MemoryQuery> = people(
            SelectFilter.make("status").options(mapOf("open" to "Open")).default("open"),
            Filter.make("active").attribute("active").columnSpanFull(),
            Filter.make("name").attribute("name").textInput().columnSpan(2).modifyFormFieldUsing { it.helperText("Partial match").hiddenLabel() },
            Filter.make("secret").attribute("name").hidden(),
        )
        val dropdown = table().render()
        assertTrue(dropdown.contains("data-ff-filters-trigger"), dropdown)
        assertTrue(dropdown.contains("""aria-haspopup="true""""))
        assertTrue(dropdown.contains("""data-ff-filters-count>1<"""))
        assertTrue(dropdown.contains("""data-ff-filters-layout="dropdown""""))
        assertTrue(dropdown.contains("--ff-filters-width: 20rem"))
        assertTrue(dropdown.contains("Apply filters"))
        assertTrue(dropdown.contains("""data-ff-filters-live="false""""))
        assertTrue(dropdown.contains("data-ff-deselect-on-filter"))
        assertTrue(dropdown.contains("data-ff-filters-reset"))
        assertTrue(dropdown.contains("filters=reset"))
        assertTrue(dropdown.contains("Partial match"))
        assertTrue(dropdown.contains("""data-ff-span="full""""))
        assertTrue(dropdown.contains("--ff-span: 2"))
        assertFalse(dropdown.contains("""data-ff-filter="secret""""))

        val modal = table().filtersLayout(FiltersLayout.MODAL).filtersFormWidth("4xl").filtersFormHeading("Refine").render()
        assertTrue(modal.contains("""data-ff-filters-layout="modal""""), modal)
        assertTrue(modal.contains("""aria-haspopup="dialog""""))
        assertTrue(modal.contains("Refine"))
        assertTrue(modal.contains("data-ff-dialog-mount"))
        for ((width, dialog) in listOf("sm" to "sm", "lg" to "md", "2xl" to "lg")) {
            assertTrue(table().filters(FiltersLayout.MODAL, Filter.make("a")).filtersFormWidth(width).render().contains("ff-dialog-$dialog"))
        }

        val above = table().filtersLayout(FiltersLayout.ABOVE_CONTENT).filtersFormColumns(3).deferFilters(false).render()
        assertTrue(above.contains("""data-ff-filters-layout="above""""), above)
        assertFalse(above.contains("data-ff-filters-trigger"))
        assertFalse(above.contains("Apply filters"))
        assertTrue(above.contains("""data-ff-filters-live="true""""))
        assertTrue(above.contains("data-ff-columns=\"3\""))
        val collapsible = table().filtersLayout(FiltersLayout.ABOVE_CONTENT_COLLAPSIBLE).render()
        assertTrue(collapsible.contains("""data-ff-filters-layout="collapsible" hidden"""), collapsible)
        assertTrue(collapsible.contains("data-ff-filters-trigger"))
        val below = table().filtersLayout(FiltersLayout.BELOW_CONTENT).filtersResetActionPosition(FiltersResetActionPosition.FOOTER).render()
        assertTrue(below.contains("""data-ff-filters-layout="below""""), below)
        assertTrue(below.indexOf("ff-filters-below") > below.indexOf("ff-table-body") || below.indexOf("ff-filters-below") > below.indexOf("<table"))
        assertTrue(below.contains("""<div class="ff-filters-footer"><button"""))

        val custom = table()
            .filtersTriggerAction { it.label("Refine").button().icon("check").color("primary").tooltip("Refine list").badge(false).extraAttributes(mapOf("data-test" to "go")) }
            .filtersApplyAction { it.label("Go").link() }
            .filtersResetAction { it.hidden() }
            .filtersRemoveAllAction { it.label("Clear").color("danger") }
            .filtersFormMaxHeight("20rem")
            .hiddenFilterIndicators()
        val customHtml = custom.render()
        assertTrue(customHtml.contains("ff-filters-action--button"), customHtml)
        assertTrue(customHtml.contains("""data-ff-icon="check""""))
        assertTrue(customHtml.contains(org.konaframework.firefly.support.Icon.CHECK.svg()))
        assertTrue(table().filtersApplyAction { it.button().icon("trash") }.render().contains(org.konaframework.firefly.support.Icon.TRASH.svg()))
        assertThrows(FireflyException::class.java) { table().filtersResetAction { it.icon("nope") }.render() }
        assertTrue(customHtml.contains("""title="Refine list""""))
        assertTrue(customHtml.contains("""data-test="go""""))
        assertFalse(customHtml.contains("data-ff-filters-count"))
        assertTrue(customHtml.contains("<span>Go</span>"))
        assertFalse(customHtml.contains("data-ff-filters-reset"))
        assertFalse(customHtml.contains("ff-filter-indicators"))
        assertTrue(customHtml.contains("max-height: 20rem"))
        val clear = table().filtersRemoveAllAction { it.label("Clear").color("danger") }.render()
        assertTrue(clear.contains("""aria-label="Clear" data-ff-color="danger""""), clear)
        assertFalse(table().filtersTriggerAction { it.hidden() }.render().contains("data-ff-filters-trigger"))
        assertFalse(table().filtersApplyAction { it.visible(false) }.render().contains("Apply filters"))
        assertTrue(table().filtersRemoveAllAction { it.hidden() }.render().let { !it.contains("ff-filter-indicators-remove-all") })
        assertThrows(FireflyException::class.java) { FilterAction("x", "X").extraAttributes(mapOf("class" to "x")) }
        assertThrows(FireflyException::class.java) { FilterAction("x", "X").extraAttributes(mapOf("data-ff-filters-panel" to "x")) }
        val action = FilterAction("x", "X").iconButton()
        assertEquals("X", action.label())
        assertEquals(FilterAction.Style.ICON_BUTTON, action.style())
        assertTrue(action.hasBadge())

        assertThrows(FireflyException::class.java) { table().filtersFormColumns(0) }
        assertThrows(FireflyException::class.java) { table().filtersFormWidth("huge") }
        assertThrows(FireflyException::class.java) { table().filtersFormMaxHeight("tall") }
        for ((columns, width) in listOf(2 to "42rem", 3 to "56rem", 4 to "72rem")) {
            assertTrue(table().filtersFormColumns(columns).render().contains("--ff-filters-width: $width"))
        }
        assertEquals("", Table.memory(rows).render().let { if (it.contains("data-ff-filters")) "has" else "" })
    }

    @Test
    fun sectionsSessionFlagsAndHrefs() {
        val table = people(
            SelectFilter.make("status").options(mapOf("open" to "Open", "shut" to "Shut")),
            Filter.make("active").attribute("active"),
            Filter.make("name").attribute("name").textInput(),
        ).filtersFormSchema(
            FilterSection.make("Workflow").filters("status", "active").columns(2).description("Where it stands").columnSpanFull(),
            FilterSection.make().filters("ghost"),
        ).persistFiltersInSession().filtersSessionKey("people.filters")
        assertTrue(table.filterSet().persistsInSession())
        assertEquals("people.filters", table.filterSet().sessionKey())
        assertTrue(table.filterSet().isDeferred())
        assertEquals(FiltersLayout.DROPDOWN, table.filterSet().layout())
        assertFalse(table.filterSet().isExplicit())
        val html = table.render()
        assertTrue(html.contains("<legend class=\"ff-filters-section-heading\">Workflow</legend>"), html)
        assertTrue(html.contains("Where it stands"))
        assertTrue(html.indexOf("data-ff-filter=\"name\"") > html.indexOf("Workflow"))
        assertEquals(listOf("Workflow"), listOfNotNull(FilterSection.make("Workflow").heading()))
        assertEquals(listOf("a"), FilterSection.make().filters("a").filterNames())
        assertThrows(FireflyException::class.java) { FilterSection.make().columns(0) }

        table.search("a").sort("name", "desc").perPage(25)
        table.applyFilterQuery(mapOf("filters" to "1", "filters.status" to "open"))
        val nav = table.render()
        assertTrue(nav.contains("sort=name&amp;direction=asc&amp;perPage=25&amp;filters=1&amp;filters.status=open") || nav.contains("filters.status=open"), nav)
        assertTrue(nav.contains("""<input type="hidden" name="filters.status" value="open" data-ff-filter-state />"""))
        assertTrue(nav.contains("""<input type="hidden" name="search" value="a" />"""))
        assertTrue(nav.contains("""name="direction" value="desc""""))
        assertTrue(nav.contains("""name="perPage" value="25""""))
        val paged = people(Filter.make("name").attribute("name").textInput()).perPage(1)
        paged.applyFilterQuery(mapOf("filters" to "1", "filters.name" to "a"))
        assertTrue(paged.render().contains("filters.name=a"))
        val columns = Table.memory(rows).columns(TextColumn.make("name").toggleable(), TextColumn.make("status").toggleable())
            .filters(Filter.make("x")).applyToggleableVisibility(listOf("name"))
        assertTrue(columns.render().contains("""name="columns" value="name""""))
        table.filters(SelectFilter.make("status").options(mapOf("open" to "Open")))
        assertEquals(mapOf("filters" to "1", "filters.status" to "open"), table.filterQuery())
    }

    @Test
    fun filterDataAndDefaults() {
        val data = FilterData.of(mapOf("value" to "a,b", "flag" to "on", "off" to "no", "list" to listOf("x", 2), "blank" to " ", "none" to null))
        assertEquals(listOf("a", "b"), data.values())
        assertEquals(listOf("x", "2"), data.list("list"))
        assertEquals(true, data.bool("flag"))
        assertEquals(false, data.bool("off"))
        assertNull(data.bool("value"))
        assertNull(data.string("blank"))
        assertFalse(data.filled("none"))
        assertFalse(data.isEmpty())
        assertTrue(FilterData.of(mapOf("blank" to "")).isEmpty())
        assertEquals(FilterData.single("a"), FilterData.of(mapOf("value" to "a", "empty" to "")))
        assertEquals(FilterData.single("a").hashCode(), FilterData.of(mapOf("value" to "a", "empty" to null)).hashCode())
        assertTrue(FilterData.single(null).isEmpty())
        assertFalse(FilterData.single("a").equals("a"))
        assertEquals(setOf("value"), FilterData.single(1).keys())
        assertEquals(mapOf("flag" to "on"), data.without(listOf("value", "off", "list", "blank", "none")).toMap())

        val filter = Filter.make("created_at")
        assertEquals("Created at", filter.heading(Cell { null }))
        assertEquals(FilterControl.CHECKBOX, filter.control(Cell { null }))
        assertEquals(FilterControl.SELECT, Filter.make("x").options(mapOf("a" to "A")).control(Cell { null }))
        assertFalse(filter.hasSchema())
        assertNull(filter.columnSpan())
        assertTrue(Filter.make("x").checkbox().visible(true).let { !it.isHidden(Cell { null }) })
        assertTrue(Filter.make("x").hidden { true }.isHidden(Cell { null }))
        assertThrows(FireflyException::class.java) { Filter.make("x").columns(0) }
        Filter.configureUsing(SelectFilter::class.java) { it.label("Picked") }
        assertEquals("Picked", SelectFilter.make("x").heading(Cell { null }))
        assertEquals("X", Filter.make("x").heading(Cell { null }))
        Filter.configureUsing(SelectFilter::class.java, null)
        assertEquals("X", SelectFilter.make("x").heading(Cell { null }))
        val bound = Filter.make("x").extraInputAttributes(mapOf("data-x" to "1")).modifyFormFieldUsing { it.extraInputAttributes(mapOf("data-y" to "2")).inline(true) }
        val markup = bound.render(Cell { null }, true)
        assertTrue(markup.contains("data-x=\"1\"") && markup.contains("data-y=\"2\""), markup)
        val guarded = FilterField("x").extraInputAttributes(mapOf("name" to "y", "" to "z", "type" to "t", "value" to "v", "data-ok" to "1"))
        assertEquals(mapOf("data-ok" to "1"), guarded.attributes)
    }

    @Test
    fun queryBuilderInMemory() {
        val builder = QueryBuilderFilter.make("query").label("Advanced").constraintPickerColumns(2).constraints(
            TextConstraint.make("name"),
            NumberConstraint.make("score"),
            DateConstraint.make("joined"),
            BooleanConstraint.make("active"),
            SelectConstraint.make("state").attribute("status").label("State").options(mapOf("open" to "Open", "shut" to "Shut")),
            RelationshipConstraint.make("team").titleAttribute("name").multiple(false).optionsLimit(5).modifyOptionsQueryUsing { },
        )
        assertEquals(6, builder.constraints().size)
        assertThrows(FireflyException::class.java) { builder.constraintPickerColumns(0) }
        assertThrows(FireflyException::class.java) { RelationshipConstraint.make("team").optionsLimit(0) }
        val table = people(builder)
        val empty = table.render()
        assertTrue(empty.contains("data-ff-qb-picker-columns=\"2\""), empty)
        assertTrue(empty.contains("data-ff-qb-group-template"))
        assertTrue(empty.contains("""<option value="state">State</option>"""))
        assertTrue(empty.contains("""data-ff-qb-novalue="true""""))
        fun run(rules: String): List<String> {
            table.applyFilterQuery(mapOf("filters" to "1", "filters.query" to rules))
            return names(table)
        }
        assertEquals(listOf("Bea", "Dee"), run("name:contains:e"))
        assertEquals(listOf("Bea", "Cy"), run("score:isMin:40"))
        assertEquals(listOf("Ada", "Bea"), run("joined:isYear:2024"))
        assertEquals(listOf("Ada", "Dee"), run("active:isTrue:"))
        assertEquals(listOf("Ada", "Cy", "Dee"), run("state:is:open,shut"))
        assertEquals(listOf("Ada", "Cy"), run("team:isRelatedTo:t1"))
        assertEquals(listOf("Cy"), run("name:!isFilled:|name:equals:Cy"))
        assertEquals(listOf("Ada", "Dee"), run("active:isTrue:;score:isMax:10"))
        val encoded = QueryBuilderFilter.encode(
            listOf(listOf(QueryRule("name", "contains", false, listOf("a b,c"))), listOf(QueryRule("active", "isTrue", true, emptyList()))),
        )
        assertEquals("name:contains:a+b%2Cc|active:!isTrue:", encoded)
        assertEquals(listOf("Bea"), run(encoded))
        val html = table.render()
        assertTrue(html.contains("""data-ff-qb-constraint="name""""), html)
        assertTrue(html.contains("""value="a b,c""""))
        assertTrue(html.contains("""<option value="!isTrue" data-ff-qb-novalue="true" selected>is false</option>"""))
        assertEquals("Advanced: Name contains a b,c or Active is false", table.filterIndicators().single().let { "Advanced: " + it.label })
        assertEquals("a b,c", QueryRule("name", "contains", false, listOf("a b,c")).value())
        assertNull(QueryRule("active", "isTrue", false, emptyList()).value())
        val overridden = people(QueryBuilderFilter.make("q").constraints(TextConstraint.make("name")).indicator("Custom rules"))
        overridden.applyFilterQuery(mapOf("filters" to "1", "filters.q" to "name:contains:a"))
        assertEquals("Custom rules", overridden.filterIndicators().single().label)
        val described = SelectConstraint.make("status").options(mapOf("open" to "Open"))
        assertEquals("Status is Open, x", described.describe(null, QueryRule("status", "is", false, listOf("open", "x"))))
        assertEquals("Status", described.describe(null, QueryRule("status", "nope", false, emptyList())))
        assertEquals("Team is not t1", RelationshipConstraint.make("team").describe(null, QueryRule("team", "isRelatedTo", true, listOf("t1"))))
        assertTrue(RelationshipConstraint.make("team").inputHtml(null, emptyList()).contains("<select"))
        assertTrue(TextConstraint.make("name").inputHtml(null, listOf("x")).contains("value=\"x\""))
    }

    @Test
    fun resolvingRecordsAndCalls() {
        val table = people(
            SelectFilter.make("status").options(mapOf("open" to "Open")).default("open").excludeWhenResolvingRecord(),
            Filter.make("visible").baseQuery { query, _ -> (query as MemoryQuery).where("name", "!=", "Dee") },
        )
        assertNotNull(table.resolveRecord("2"))
        assertNull(table.resolveRecord("4"))
        assertNull(table.resolveRecord("9"))
        table.call("applyFilter", "status", "draft")
        assertEquals(listOf("Bea"), names(table))
        assertEquals(FilterData.single("draft"), table.filterData("status"))
    }
}

private class StubStore : RecordStore<String, StringBuilder> {
    override fun query(): StringBuilder = StringBuilder()
    override fun find(key: String): String? = null
    override fun keyOf(record: String): String = record
    override fun keyName(): String = "id"
    override fun newRecord(): String = ""
    override fun fill(record: String, attributes: Map<String, Any?>): String = record
    override fun save(record: String): String = record
    override fun delete(record: String) = Unit
    override fun supportsSoftDeletes(): Boolean = false
    override fun <R> transaction(block: java.util.function.Function<RecordStore<String, StringBuilder>, R>): R = block.apply(this)
    override fun constrain(query: StringBuilder, column: String, operator: String, value: Any?) = Unit
    override fun search(query: StringBuilder, term: String, columns: List<String>) = Unit
    override fun sort(query: StringBuilder, column: String, direction: String) = Unit
    override fun paginate(query: StringBuilder, page: Int, perPage: Int): TablePage<String> = TablePage(emptyList(), 0, perPage, page)
    override fun list(query: StringBuilder): List<String> = emptyList()
    override fun summarize(query: StringBuilder, column: String, operation: Summary): Any? = null
    override fun trashed(query: StringBuilder, mode: String) = Unit
    override fun set(key: String, column: String, value: Any?) = Unit
    override fun reorder(column: String, orderedKeys: List<String>) = Unit
    override fun attributes(record: String): Map<String, Any?> = emptyMap()
}
