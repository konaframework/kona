package org.konaframework.firefly.tables

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.firefly.schemas.EmptyState
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer

enum class TableLayout {
    ROWS,
    STACK,
    GRID,
}

open class TableGroup(val column: String) {
    private val labelValue = TextSetting()

    fun label(value: String): TableGroup = apply { labelValue.set(value) }

    fun label(value: java.util.function.Function<Cell, String>): TableGroup = apply { labelValue.set(value) }

    fun heading(cell: Cell, fallback: String): String = labelValue.get(cell, fallback)

    companion object {
        @JvmStatic
        fun make(column: String): TableGroup = TableGroup(column)
    }
}

class LoadedTable<T>(
    val records: List<T>,
    val total: Long,
    val page: Int,
    val lastPage: Int,
    val summaries: Map<String, Any?>,
    val groups: List<Pair<String, List<T>>>,
) {
    fun isEmpty(): Boolean = records.isEmpty()

    fun hasMorePages(): Boolean = page < lastPage
}

open class Table<T, Q>(private val store: RecordStore<T, Q>) {
    private val columnList = ArrayList<Column>()
    private val filterList = ArrayList<Filter>()
    private val headerButtonList = ArrayList<TableButton>()
    private val rowButtonList = ArrayList<TableButton>()
    private val groupList = ArrayList<TableGroup>()
    private val hooks = ArrayList<Consumer<Q>>()
    private val filterValues = LinkedHashMap<String, Any?>()
    private val selectedKeys = LinkedHashSet<String>()
    private val hiddenByUser = LinkedHashSet<String>()
    private val emptyHeading = TextSetting()
    private val emptyDescription = TextSetting()
    private var searchTerm = ""
    private var sortColumn: String? = null
    private var sortDirection = "asc"
    private var pageNumber = 1
    private var pageSize = 10
    private var paginate = true
    private var pageSizes = emptyList<Int>()
    private var layout = TableLayout.ROWS
    private var selectable = false
    private var groupColumn: String? = null
    private var reorderColumn: String? = null
    private var pollInterval: String? = null

    fun columns(vararg columns: Column): Table<T, Q> = apply {
        columnList.clear()
        columnList.addAll(columns)
    }

    fun filters(vararg filters: Filter): Table<T, Q> = apply {
        filterList.clear()
        filterList.addAll(filters)
    }

    fun headerButtons(vararg buttons: TableButton): Table<T, Q> = apply {
        headerButtonList.clear()
        headerButtonList.addAll(buttons)
    }

    fun rowButtons(vararg buttons: TableButton): Table<T, Q> = apply {
        rowButtonList.clear()
        rowButtonList.addAll(buttons)
    }

    fun groups(vararg groups: TableGroup): Table<T, Q> = apply {
        groupList.clear()
        groupList.addAll(groups)
        if (groups.isNotEmpty() && groupColumn == null) groupColumn = groups.first().column
    }

    fun groupedBy(column: String): Table<T, Q> = apply {
        if (groupList.none { it.column == column }) throw FireflyException("Unknown group [$column]")
        groupColumn = column
    }

    fun modifyQueryUsing(hook: Consumer<Q>): Table<T, Q> = apply { hooks.add(hook) }

    fun search(term: String): Table<T, Q> = apply { searchTerm = term }

    fun sort(column: String, direction: String = "asc"): Table<T, Q> = apply {
        val target = this.column(column)
        if (!target.isSortable(tableCell())) throw FireflyException("Column [$column] is not sortable")
        val normalized = direction.lowercase()
        if (normalized != "asc" && normalized != "desc") {
            throw FireflyException("Sort direction must be asc or desc")
        }
        sortColumn = column
        sortDirection = normalized
    }

    fun page(number: Int): Table<T, Q> = apply {
        if (number < 1) throw FireflyException("Page must be at least 1")
        pageNumber = number
    }

    fun perPage(size: Int): Table<T, Q> = apply {
        if (size < 1) throw FireflyException("Page size must be at least 1")
        pageSize = size
    }

    fun paginated(on: Boolean): Table<T, Q> = apply { paginate = on }

    fun pageOptions(vararg options: Int): Table<T, Q> = apply { pageSizes = options.toList() }

    fun layout(value: TableLayout): Table<T, Q> = apply { layout = value }

    fun selectable(on: Boolean): Table<T, Q> = apply { selectable = on }

    fun reorderable(column: String): Table<T, Q> = apply { reorderColumn = column }

    fun empty(heading: String): Table<T, Q> = apply { emptyHeading.set(heading) }

    fun empty(heading: java.util.function.Function<Cell, String>): Table<T, Q> = apply { emptyHeading.set(heading) }

    fun emptyDescription(value: String): Table<T, Q> = apply { emptyDescription.set(value) }

    fun emptyDescription(value: java.util.function.Function<Cell, String>): Table<T, Q> = apply {
        emptyDescription.set(value)
    }

    fun poll(interval: String): Table<T, Q> = apply {
        if (!POLL.matches(interval)) throw FireflyException("Poll interval must look like 10s or 500ms")
        pollInterval = interval
    }

    fun filter(name: String, value: Any?): Table<T, Q> = apply {
        if (filterList.none { it.name == name }) throw FireflyException("Unknown filter [$name]")
        if (value == null) filterValues.remove(name) else filterValues[name] = value
    }

    fun select(key: String): Table<T, Q> = apply {
        if (!selectedKeys.remove(key)) selectedKeys.add(key)
    }

    fun deselect(key: String): Table<T, Q> = apply { selectedKeys.remove(key) }

    fun selected(): List<String> = selectedKeys.toList()

    fun <R : Cadence<R>> relationship(model: Class<R>): ModelBuilder<R> = CadenceRecordStore(model).query()

    fun <R : Cadence<R>> relationship(model: Class<R>, hook: Consumer<ModelBuilder<R>>): ModelBuilder<R> =
        relationship(model).also { hook.accept(it) }

    fun load(): LoadedTable<T> {
        val query = prepare(store.query())
        val page = if (paginate) {
            store.paginate(query, pageNumber, pageSize)
        } else {
            val records = store.list(query)
            TablePage(records, records.size.toLong(), records.size.coerceAtLeast(1), 1)
        }
        val summaries = if (columnList.any { it.summaries().isNotEmpty() }) {
            summarize(prepare(store.query()))
        } else {
            emptyMap()
        }
        return LoadedTable(page.records, page.total, page.page, page.lastPage, summaries, groups(page.records))
    }

    fun render(): String {
        val loaded = load()
        val cell = tableCell()
        val pollAttr = if (pollInterval == null) "" else """ wire:poll.$pollInterval"""
        val visible = columnList.filter { shown(it, cell) }
        return """
            <div class="ff-table-wrap" data-ff-layout="${layout.name.lowercase()}"$pollAttr>
              ${toolbar(cell)}
              ${body(loaded, visible)}
              ${footer(loaded)}
              ${emptyState(loaded, cell)}
              ${pager(loaded)}
            </div>
        """.trimIndent()
    }

    fun call(name: String, vararg args: String): Table<T, Q> = apply {
        when (name) {
            "selectRecord" -> select(arg(args, 0, name))
            "selectPage" -> selectPage()
            "gotoPage" -> page(number(arg(args, 0, name), name))
            "search" -> search(arg(args, 0, name))
            "sort" -> sort(arg(args, 0, name), if (args.size > 1) args[1] else "asc")
            "perPage" -> perPage(number(arg(args, 0, name), name))
            "applyFilter" -> filter(arg(args, 0, name), blankToNull(arg(args, 1, name)))
            "toggleColumn" -> setValue(arg(args, 0, name), arg(args, 1, name), arg(args, 2, name))
            "setColumn" -> setValue(arg(args, 0, name), arg(args, 1, name), arg(args, 2, name))
            "moveRecord" -> move(arg(args, 0, name), number(arg(args, 1, name), name))
            "toggleColumnVisibility" -> toggleColumn(arg(args, 0, name), arg(args, 1, name) != "false")
            else -> throw FireflyException("Unknown table call [$name]")
        }
    }

    fun toggleColumn(name: String, visible: Boolean): Table<T, Q> = apply {
        val column = column(name)
        if (!column.isToggleable(tableCell())) throw FireflyException("Column [$name] is not toggleable")
        if (visible) hiddenByUser.remove(name) else hiddenByUser.add(name)
    }

    fun move(key: String, position: Int): Table<T, Q> = apply {
        val column = reorderColumn ?: throw FireflyException("This table is not reorderable")
        val keys = store.list(baseQuery()).map { store.keyOf(it) }.toMutableList()
        if (!keys.remove(key)) throw FireflyException("Unknown record [$key]")
        keys.add(position.coerceIn(0, keys.size), key)
        store.reorder(column, keys)
    }

    private fun selectPage() {
        val keys = load().records.map { store.keyOf(it) }
        if (keys.all { it in selectedKeys }) {
            keys.forEach { selectedKeys.remove(it) }
        } else {
            keys.forEach { selectedKeys.add(it) }
        }
    }

    private fun setValue(key: String, column: String, raw: String) {
        val col = column(column)
        val record = store.find(key) ?: throw FireflyException("Unknown record [$key]")
        val values = store.attributes(record)
        val cell = rowCell(values, col.name)
        if (col.isDisabled(cell)) throw FireflyException("Column [$column] is not editable for record [$key]")
        if (col is CheckboxColumn && !col.isEditable(cell)) {
            throw FireflyException("Column [$column] is not editable for record [$key]")
        }
        val value: Any = when (raw) {
            "true" -> true
            "false" -> false
            else -> raw
        }
        store.set(key, column, value)
    }

    private fun prepare(query: Q): Q {
        val cell = tableCell()
        hooks.forEach { it.accept(query) }
        for (filter in filterList) {
            filter.apply(query as Any, store, filterValue(filter, cell))
        }
        val searchable = columnList.filter { it.isSearchable(cell) }.map { it.name }
        if (searchTerm.isNotEmpty()) store.search(query, searchTerm, searchable)
        val grouped = groupColumn
        if (grouped != null) store.sort(query, grouped, "asc")
        val sorting = sortColumn
        if (sorting != null) store.sort(query, sorting, sortDirection)
        return query
    }

    private fun baseQuery(): Q {
        val query = store.query()
        hooks.forEach { it.accept(query) }
        return query
    }

    private fun summarize(query: Q): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (column in columnList) {
            for (operation in column.summaries()) {
                out["${column.name}:${operation.name}"] = store.summarize(query, column.name, operation)
            }
        }
        return out
    }

    private fun groups(records: List<T>): List<Pair<String, List<T>>> {
        val column = groupColumn ?: return emptyList()
        val definition = groupList.firstOrNull { it.column == column } ?: return emptyList()
        val ordered = LinkedHashMap<String, MutableList<T>>()
        for (record in records) {
            val raw = store.attributes(record)[column]
            val key = if (raw == null) "—" else raw.toString()
            val label = definition.heading(Cell { path -> if (path == column || path == "/$column") raw else null }, key)
            ordered.getOrPut(label) { ArrayList() }.add(record)
        }
        return ordered.map { it.key to it.value.toList() }
    }

    private fun shown(column: Column, cell: Cell): Boolean =
        !column.isHidden(cell) && column.name !in hiddenByUser

    private fun toolbar(cell: Cell): String {
        val searchable = columnList.any { it.isSearchable(cell) }
        val search = if (!searchable) {
            ""
        } else {
            """
            <div class="ff-table-search-wrap">
              <svg class="ff-table-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 15.803 7.5 7.5 0 0015.803 15.803z"/></svg>
              <input class="ff-table-search" type="search" name="search" value="${escapeHtml(searchTerm)}" placeholder="Search…" aria-label="Search">
            </div>
            """.trimIndent()
        }
        val filters = if (filterList.isEmpty()) {
            ""
        } else {
            """<div class="ff-table-filters">${filterList.joinToString("") { it.render(cell, filterValue(it, cell)) }}</div>"""
        }
        val buttons = headerButtonList.joinToString("") { it.render(cell, selectedKeys.isEmpty()) }
        val tools = if (filterList.isEmpty() && buttons.isEmpty()) {
            ""
        } else {
            """<div class="ff-table-toolbar-actions">$filters$buttons</div>"""
        }
        if (!searchable && filterList.isEmpty() && headerButtonList.isEmpty()) {
            return """<div class="ff-table-toolbar ff-table-toolbar--empty" hidden></div>"""
        }
        // Query-string toolbar — chrome soft-nav swaps the wrap without a full document reload.
        return """<div class="ff-table-toolbar"><form class="ff-table-toolbar-form" method="get" data-ff-table-nav>$search$tools</form></div>"""
    }

    private fun body(loaded: LoadedTable<T>, visible: List<Column>): String = when (layout) {
        TableLayout.ROWS -> rows(loaded, visible)
        TableLayout.STACK -> cards(loaded, visible, "ff-table-stack")
        TableLayout.GRID -> cards(loaded, visible, "ff-table-grid")
    }

    private fun rows(loaded: LoadedTable<T>, visible: List<Column>): String {
        val cell = tableCell()
        val headers = visible.joinToString("") { columnHeader(it, cell) }
        val selectHead = if (selectable) """<th class="ff-table-select"><button type="button" x-on:click="${'$'}wire.${'$'}call('selectPage')" aria-label="Select page"></button></th>""" else ""
        val actionHead = if (rowButtonList.isEmpty()) "" else """<th class="ff-table-actions-head" data-ff-align="end">Actions</th>"""
        val span = visible.size + (if (selectable) 1 else 0) + (if (rowButtonList.isEmpty()) 0 else 1)
        val body = when {
            loaded.isEmpty() -> {
                """<tr class="ff-table-empty-row"><td colspan="$span">${emptyStateInner(cell)}</td></tr>"""
            }
            loaded.groups.isEmpty() -> loaded.records.joinToString("") { recordRow(it, visible) }
            else -> loaded.groups.joinToString("") { (label, records) ->
                """<tr class="ff-group"><th colspan="$span">${escapeHtml(label)}</th></tr>""" +
                    records.joinToString("") { recordRow(it, visible) }
            }
        }
        return """<div class="ff-table-scroll"><table class="ff-table"><thead><tr>$selectHead$headers$actionHead</tr></thead><tbody>$body</tbody></table></div>"""
    }

    private fun columnHeader(column: Column, cell: Cell): String {
        val label = escapeHtml(column.heading(cell))
        val align = column.resolvedAlign(cell).name.lowercase()
        val alignAttr = """ data-ff-align="$align""""
        if (!column.isSortable(cell)) {
            return """<th scope="col"$alignAttr>$label</th>"""
        }
        val active = sortColumn == column.name
        val next = if (active && sortDirection == "asc") "desc" else "asc"
        val state = when {
            !active -> "idle"
            sortDirection == "desc" -> "desc"
            else -> "asc"
        }
        val href = sortHref(column.name, next)
        // Query-string sort works without a Wire host; chrome soft-nav swaps .ff-table-wrap in place.
        return """<th scope="col" class="ff-table-sortable"$alignAttr data-ff-sort="$state"><a class="ff-table-sort" href="${escapeHtml(href)}" data-ff-table-nav data-ff-sort-column="${escapeHtml(column.name)}" data-ff-sort-direction="$next">$label<span class="ff-table-sort-indicator" aria-hidden="true"></span></a></th>"""
    }

    private fun sortHref(column: String, direction: String): String {
        val parts = ArrayList<String>()
        if (searchTerm.isNotEmpty()) parts.add("search=${encodeQuery(searchTerm)}")
        parts.add("sort=${encodeQuery(column)}")
        parts.add("direction=$direction")
        if (pageNumber > 1) parts.add("page=$pageNumber")
        if (pageSize != 10) parts.add("perPage=$pageSize")
        return "?" + parts.joinToString("&")
    }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun recordRow(record: T, visible: List<Column>): String {
        val values = store.attributes(record)
        val key = store.keyOf(record)
        val select = if (selectable) selectionCell(key) else ""
        val cells = visible.joinToString("") { """<td>${it.render(values, key, rowCell(values, it.name))}</td>""" }
        val actions = rowActions(values, key)
        return """<tr data-ff-record="${escapeHtml(key)}">$select$cells$actions</tr>"""
    }

    private fun cards(loaded: LoadedTable<T>, visible: List<Column>, className: String): String {
        val content = if (loaded.groups.isEmpty()) {
            loaded.records.joinToString("") { card(it, visible) }
        } else {
            loaded.groups.joinToString("") { (label, records) ->
                """<h3 class="ff-group">${escapeHtml(label)}</h3>""" +
                    records.joinToString("") { card(it, visible) }
            }
        }
        return """<div class="$className">$content</div>"""
    }

    private fun card(record: T, visible: List<Column>): String {
        val values = store.attributes(record)
        val key = store.keyOf(record)
        val select = if (selectable) selectionCell(key) else ""
        val fields = visible.joinToString("") { column ->
            """<div class="ff-stack-field"><span>${escapeHtml(column.heading(tableCell()))}</span>${column.render(values, key, rowCell(values, column.name))}</div>"""
        }
        return """<article class="ff-table-card" data-ff-record="${escapeHtml(key)}">$select$fields${rowActions(values, key)}</article>"""
    }

    private fun selectionCell(key: String): String {
        val checked = if (key in selectedKeys) """ checked""" else ""
        return """<td><input type="checkbox"$checked x-on:click="${'$'}wire.${'$'}call('selectRecord', '${escapeHtml(key)}')"></td>"""
    }

    private fun rowActions(values: Map<String, Any?>, key: String): String {
        if (rowButtonList.isEmpty()) return ""
        val cell = Cell { path ->
            when (path) {
                "key", "recordKey" -> key
                else -> values[path.removePrefix("/")]
            }
        }
        val html = rowButtonList.joinToString("") { it.render(cell, selectedKeys.isEmpty()) }
        return """<td class="ff-row-actions">$html</td>"""
    }

    private fun footer(loaded: LoadedTable<T>): String {
        if (loaded.summaries.isEmpty()) return ""
        val cells = loaded.summaries.entries.joinToString("") { (name, value) ->
            """<span data-ff-summary="$name">${escapeHtml(value?.toString().orEmpty())}</span>"""
        }
        return """<div class="ff-table-summaries">$cells</div>"""
    }

    private fun emptyState(loaded: LoadedTable<T>, cell: Cell): String {
        // Empty copy lives inside the table body (Shamar list). Keep a hidden sentinel for tests.
        if (!loaded.isEmpty()) return ""
        return """<div class="ff-table-empty" hidden>${emptyStateInner(cell)}</div>"""
    }

    private fun emptyStateInner(cell: Cell): String {
        val heading = emptyHeading.get(cell, "No records found.")
        val state = EmptyState.make(heading)
        val description = emptyDescription.get(cell, "")
        if (description.isNotEmpty()) state.description(description)
        return state.render(Schema.make())
    }

    private fun pager(loaded: LoadedTable<T>): String {
        if (!paginate) return ""
        val meta = """<p class="ff-pagination-meta">Page ${loaded.page} of ${loaded.lastPage} · ${loaded.total} records</p>"""
        val previous = if (loaded.page > 1) {
            """<a class="ff-pagination-link" href="${escapeHtml(pageHref(loaded.page - 1))}" data-ff-table-nav data-ff-page="prev">Previous</a>"""
        } else {
            """<span class="ff-pagination-link is-disabled" aria-disabled="true">Previous</span>"""
        }
        val next = if (loaded.hasMorePages()) {
            """<a class="ff-pagination-link" href="${escapeHtml(pageHref(loaded.page + 1))}" data-ff-table-nav data-ff-page="next">Next</a>"""
        } else {
            """<span class="ff-pagination-link is-disabled" aria-disabled="true">Next</span>"""
        }
        val links = pageLinks(loaded)
        val sizes = if (pageSizes.isEmpty()) {
            ""
        } else {
            val options = pageSizes.joinToString("") { size ->
                val selected = if (size == pageSize) " selected" else ""
                """<option value="$size"$selected>$size</option>"""
            }
            """<label class="ff-page-size-label">Rows <select class="ff-page-size" name="perPage" data-ff-table-nav-select>$options</select></label>"""
        }
        val controls = """<div class="ff-pagination-controls">$previous$links$next$sizes</div>"""
        return """<nav class="ff-pagination" data-ff-pagination aria-label="Pagination">$meta$controls</nav>"""
    }

    /** Shamar-style numbered page links with ellipsis windows. */
    private fun pageLinks(loaded: LoadedTable<T>): String {
        if (loaded.lastPage <= 1) return ""
        val out = StringBuilder()
        for (token in paginationWindow(loaded.page, loaded.lastPage)) {
            if (token == null) {
                out.append("""<span class="ff-pagination-ellipsis" aria-hidden="true">…</span>""")
                continue
            }
            val current = if (token == loaded.page) """ aria-current="page"""" else ""
            val active = if (token == loaded.page) " is-active" else ""
            out.append(
                """<a class="ff-pagination-link$active"$current href="${escapeHtml(pageHref(token))}" data-ff-table-nav data-ff-page="$token">$token</a>""",
            )
        }
        return out.toString()
    }

    private fun pageHref(page: Int): String {
        val parts = ArrayList<String>()
        if (searchTerm.isNotEmpty()) parts.add("search=${encodeQuery(searchTerm)}")
        val sorting = sortColumn
        if (sorting != null) {
            parts.add("sort=${encodeQuery(sorting)}")
            parts.add("direction=$sortDirection")
        }
        parts.add("page=$page")
        if (pageSize != 10) parts.add("perPage=$pageSize")
        return "?" + parts.joinToString("&")
    }

    private fun paginationWindow(current: Int, last: Int): List<Int?> {
        if (last <= 7) return (1..last).map { it }
        val pages = LinkedHashSet<Int>()
        pages.add(1)
        pages.add(last)
        pages.add(current.coerceIn(1, last))
        pages.add((current - 1).coerceIn(1, last))
        pages.add((current + 1).coerceIn(1, last))
        pages.add(2.coerceAtMost(last))
        pages.add((last - 1).coerceAtLeast(1))
        val sorted = pages.sorted()
        val out = ArrayList<Int?>()
        var prev = 0
        for (p in sorted) {
            if (prev > 0 && p - prev > 1) out.add(null)
            out.add(p)
            prev = p
        }
        return out
    }

    private fun filterValue(filter: Filter, cell: Cell): Any? =
        if (filterValues.containsKey(filter.name)) filterValues[filter.name] else filter.defaultValue(cell)

    private fun column(name: String): Column =
        columnList.firstOrNull { it.name == name } ?: throw FireflyException("Unknown column [$name]")

    private fun tableCell(): Cell = Cell { path ->
        when (path) {
            "search" -> searchTerm
            "page" -> pageNumber
            "sort" -> sortColumn
            else -> filterValues[path]
        }
    }

    private fun rowCell(values: Map<String, Any?>, column: String): Cell = Cell { path ->
        when (path) {
            ".", "" -> values[column]
            else -> values[path.removePrefix("/")]
        }
    }

    private fun arg(args: Array<out String>, index: Int, method: String): String {
        if (index >= args.size) throw FireflyException("Table call [$method] is missing an argument")
        return args[index]
    }

    private fun number(raw: String, method: String): Int =
        raw.toIntOrNull() ?: throw FireflyException("Table call [$method] expected a number")

    private fun blankToNull(raw: String): String? = if (raw.isEmpty()) null else raw

    companion object {
        private val POLL = Regex("\\d+(ms|s)")
        private var configurer: Consumer<Table<*, *>>? = null

        @JvmStatic
        fun configureUsing(block: Consumer<Table<*, *>>?) {
            configurer = block
        }

        @JvmStatic
        fun <T : Cadence<T>> cadence(model: Class<T>): Table<T, ModelBuilder<T>> = finish(Table(CadenceRecordStore(model)))

        @JvmStatic
        fun memory(rows: List<Map<String, Any?>>): Table<Map<String, Any?>, MemoryQuery> =
            finish(Table(MemoryRecordStore(rows)))

        @JvmStatic
        fun memory(vararg rows: Map<String, Any?>): Table<Map<String, Any?>, MemoryQuery> = memory(rows.toList())

        /** Open a Cadence table for [model], or throw when the model has no record store. */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun forModel(model: Class<*>): Table<*, *> {
            val store = RecordStores.open(model) as RecordStore<Any?, Any?>
            return finish(Table(store))
        }

        private fun <T, Q> finish(table: Table<T, Q>): Table<T, Q> {
            @Suppress("UNCHECKED_CAST")
            configurer?.accept(table as Table<*, *>)
            return table
        }
    }
}
