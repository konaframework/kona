package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.firefly.forms.DatePicker
import org.konaframework.firefly.tables.Filter
import org.konaframework.firefly.tables.MemoryQuery
import org.konaframework.firefly.tables.SelectFilter
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TernaryFilter
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Router

class FireflyTableFiltersTest {
    @Test
    fun listPagesApplyQueryStringFilters() {
        val router = mount(PeopleResource(persist = false))
        val all = send(router, "/admin/people/list")
        assertTrue(all.contains("""data-ff-record="1"""") && all.contains("""data-ff-record="2"""") && all.contains("""data-ff-record="3""""), all)
        assertTrue(all.contains("data-ff-filters-trigger"))
        val open = send(router, "/admin/people/list", mapOf("filters" to "1", "filters.status" to "open"))
        assertTrue(open.contains("""data-ff-record="1"""") && open.contains("""data-ff-record="3"""") && !open.contains("""data-ff-record="2""""), open)
        assertTrue(open.contains("Status: Open"))
        assertTrue(open.contains("filters.status=open"))
        val combined = send(
            router,
            "/admin/people/list",
            mapOf("filters" to "1", "filters.status" to "open", "filters.active" to "false", "search" to "c", "sort" to "name"),
        )
        assertTrue(combined.contains("""data-ff-record="3"""") && !combined.contains("""data-ff-record="1""""), combined)
        val dated = send(router, "/admin/people/list", mapOf("filters" to "1", "filters.joined.from" to "2024-02-01"))
        assertTrue(dated.contains("""data-ff-record="2"""") && !dated.contains("""data-ff-record="1""""), dated)
        assertTrue(dated.contains("""value="2024-02-01""""), dated)
        val reset = send(router, "/admin/people/list", mapOf("filters" to "reset"))
        assertTrue(reset.contains("""data-ff-record="2""""))
    }

    @Test
    fun manageAndCustomPagesShareTheSameState() {
        val router = mount(PeopleResource(persist = false, manage = true))
        val managed = send(router, "/admin/people/manage", mapOf("filters" to "1", "filters.status" to "draft"))
        assertTrue(managed.contains("""data-ff-record="2"""") && !managed.contains("""data-ff-record="1""""), managed)

        val table = people(persist = false)
        TableState.apply(table, mapOf("filterInput" to mapOf("filters" to "1", "filters.status" to "open")), "custom.filters")
        assertEquals(2, table.load().records.size)
        TableState.apply(people(persist = false), emptyMap(), "custom.filters")
    }

    @Test
    fun persistFiltersInSessionRestoresTheLastState() {
        val router = mount(PeopleResource(persist = true))
        val session = MemorySession()
        val first = send(router, "/admin/people/list", mapOf("filters" to "1", "filters.status" to "draft"), session)
        assertTrue(first.contains("""data-ff-record="2"""") && !first.contains("""data-ff-record="1""""))
        assertEquals(mapOf("filters" to "1", "filters.status" to "draft"), session.get("firefly.tables.people.filters"))
        val back = send(router, "/admin/people/list", emptyMap(), session)
        assertTrue(back.contains("""data-ff-record="2"""") && !back.contains("""data-ff-record="1""""), back)
        send(router, "/admin/people/list", mapOf("filters" to "reset"), session)
        assertEquals(emptyMap<String, String>(), session.get("firefly.tables.people.filters"))
        val fresh = send(router, "/admin/people/list", emptyMap(), session)
        assertTrue(fresh.contains("""data-ff-record="1"""") && fresh.contains("""data-ff-record="2""""))
        val keyed = people(persist = true).filtersSessionKey("my.key")
        val other = MemorySession()
        TableState.apply(keyed, mapOf("filterInput" to mapOf("filters" to "1", "filters.status" to "open"), "filterSession" to other), "ignored")
        assertEquals(mapOf("filters" to "1", "filters.status" to "open"), other.get("my.key"))
        val empty = MemorySession()
        TableState.apply(people(persist = true), mapOf("filterSession" to empty), "none")
        assertEquals(null, empty.get("none"))
        val noSession = send(mount(PeopleResource(persist = true)), "/admin/people/list")
        assertTrue(noSession.contains("""data-ff-record="1""""))
    }

    @Test
    fun formOptionsSearchesSelectFilters() {
        val router = mount(PeopleResource(persist = false))
        val match = router.find("GET", "/admin/people/form-options")!!
        val body = match.route.action.handle(
            Request("GET", "/admin/people/form-options", emptyMap(), mapOf("field" to "filters.status", "q" to "dra"), ""),
        ).body
        assertTrue(body.contains("\"draft\"") && !body.contains("\"open\""), body)
        val missing = match.route.action.handle(
            Request("GET", "/admin/people/form-options", emptyMap(), mapOf("field" to "filters.ghost", "q" to ""), ""),
        ).body
        assertEquals("[]", missing.trim())
    }

    private fun mount(resource: Resource): Router {
        val router = Router()
        Panel().path("admin").authenticate(false).resource(resource).mount(router)
        return router
    }

    private fun send(router: Router, path: String, query: Map<String, String> = emptyMap(), session: RequestSession? = null): String {
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        val request = Request("GET", path, emptyMap(), query, "")
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        val response = match.route.action.handle(request)
        assertEquals(200, response.status, response.body)
        return response.body
    }
}

private fun people(persist: Boolean): Table<Map<String, Any?>, MemoryQuery> =
    Table.memory(
        mapOf("id" to "1", "name" to "Ada", "status" to "open", "active" to true, "joined" to "2024-01-10"),
        mapOf("id" to "2", "name" to "Bea", "status" to "draft", "active" to false, "joined" to "2024-03-02"),
        mapOf("id" to "3", "name" to "Cy", "status" to "open", "active" to false, "joined" to "2024-01-20"),
    ).columns(TextColumn.make("name").searchable().sortable())
        .filters(
            SelectFilter.make("status").options(mapOf("open" to "Open", "draft" to "Draft")).searchable(),
            TernaryFilter.make("active"),
            Filter.make("joined").schema(DatePicker.make("from")).queryUsing { query, data ->
                data.string("from")?.let { (query as MemoryQuery).whereDate("joined", ">=", it) }
            },
        ).persistFiltersInSession(persist)

private class PeopleResource(private val persist: Boolean, manage: Boolean = false) : Resource("people") {
    init {
        label("People")
        if (manage) simple()
        model(FilterPerson::class.java)
    }

    override fun table(table: Table<*, *>): Table<*, *> = people(persist)
}

class FilterPerson : Cadence<FilterPerson>() {
    override val table = "ff_filter_people"
}

private class MemorySession : RequestSession {
    private val values = LinkedHashMap<String, Any?>()
    override fun get(key: String): Any? = values[key]
    override fun put(key: String, value: Any?): RequestSession = apply { values[key] = value }
    override fun flash(key: String, value: Any?): RequestSession = put(key, value)
    override fun flashInput(input: Map<String, String>): RequestSession = this
    override fun old(key: String, default: String?): String? = default
    override fun oldInput(): Map<String, String> = emptyMap()
    override fun previousUrl(): String? = null
    override fun setPreviousUrl(url: String): RequestSession = this
}
