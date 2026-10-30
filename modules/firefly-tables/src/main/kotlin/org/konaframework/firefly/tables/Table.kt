package org.konaframework.firefly.tables

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.firefly.schemas.ColumnLayout
import org.konaframework.firefly.schemas.EmptyState
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.layout.LayoutComponent
import org.konaframework.firefly.tables.layout.LayoutRenderContext
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

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
    private val filterSet = TableFilters()
    private val headerButtonList = ArrayList<TableButton>()
    private val rowButtonList = ArrayList<TableButton>()
    private val groupList = ArrayList<TableGroup>()
    private val hooks = ArrayList<Consumer<Q>>()
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
    private var groupRowActionsFlag = true
    private var recordClickDisabled = false
    private var recordUrlUsing: Function<Cell, String>? = null
    private var recordActionUsing: Function<Cell, String>? = null
    private var stackedOnMobileFlag = true
    private var contentGridLayout: ColumnLayout? = null
    /** Top-level column defs: plain [Column] and/or [LayoutComponent] (Split/Stack/Grid). */
    private val columnDefs = ArrayList<Any>()
    private val bulkEntries = ArrayList<BulkActionEntry>()
    private var bulkConfigured = false
    private var checkboxPosition = RecordCheckboxPosition.BEFORE_CELLS
    private var selectableUsing: Predicate<Cell>? = null
    private var maxSelectable: Int? = null
    private var currentPageOnly = false
    private var groupsOnly = false
    private val persistentQuery = LinkedHashMap<String, String>()

    /**
     * Attach columns and optional layout components ([org.konaframework.firefly.tables.layout.Split],
     * [org.konaframework.firefly.tables.layout.Stack], [org.konaframework.firefly.tables.layout.Grid]).
     */
    fun columns(vararg items: Any): Table<T, Q> = apply {
        columnList.clear()
        columnDefs.clear()
        for (item in items) {
            when (item) {
                is Column -> {
                    columnDefs.add(item)
                    columnList.add(item)
                }
                is LayoutComponent -> {
                    columnDefs.add(item)
                    columnList.addAll(item.flattenColumns())
                }
                else -> throw FireflyException(
                    "Table.columns expects Column or layout Split/Stack/Grid; got ${item.javaClass.name}",
                )
            }
        }
        // Seed Filament-style toggle-hidden defaults when columns are attached (#363).
        hiddenByUser.clear()
        for (column in columnList) {
            if (column.isToggledHiddenByDefault()) {
                hiddenByUser.add(column.name)
            }
        }
    }

    /**
     * Apply the `columns` query-string list of **visible** toggleable column names.
     * Null leaves [toggledHiddenByDefault] seeding alone. Used by resource list soft-nav (#363).
     */
    fun applyToggleableVisibility(visibleNames: Collection<String>?): Table<T, Q> = apply {
        if (visibleNames == null) return@apply
        val cell = tableCell()
        val wanted = visibleNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        for (column in columnList) {
            if (!column.isToggleable(cell)) continue
            if (column.name in wanted) hiddenByUser.remove(column.name) else hiddenByUser.add(column.name)
        }
    }

    fun filters(vararg filters: Filter): Table<T, Q> = apply { filterSet.set(filters.toList()) }

    /** Filters with a layout (Filament `filters([...], layout: FiltersLayout::Modal)`). */
    fun filters(layout: FiltersLayout, vararg filters: Filter): Table<T, Q> = apply {
        filterSet.set(filters.toList())
        filterSet.layout(layout)
    }

    fun filtersLayout(layout: FiltersLayout): Table<T, Q> = apply { filterSet.layout(layout) }

    /** Grid columns of the filters form (dropdown width grows with the count unless set). */
    fun filtersFormColumns(count: Int): Table<T, Q> = apply { filterSet.formColumns(count) }

    /** Dropdown width token: `xs`, `sm`, `md`, `lg`, `xl`, `2xl` … `7xl`. */
    fun filtersFormWidth(width: String): Table<T, Q> = apply { filterSet.formWidth(width) }

    /** Scroll the filters form past this height (`24rem`, `400px`). */
    fun filtersFormMaxHeight(height: String): Table<T, Q> = apply { filterSet.formMaxHeight(height) }

    /** Arrange filters in headed sections; unnamed filters follow the sections. */
    fun filtersFormSchema(vararg sections: FilterSection): Table<T, Q> = apply { filterSet.sections(sections.toList()) }

    fun filtersFormHeading(heading: String): Table<T, Q> = apply { filterSet.heading(heading) }

    /**
     * Deferred (default): changes apply with the Apply button. `false`: every change
     * re-queries immediately (soft-nav, the popover stays open).
     */
    @JvmOverloads
    fun deferFilters(on: Boolean = true): Table<T, Q> = apply { filterSet.defer(on) }

    /** Remember filter state in the session between visits ([filtersSessionKey] names the slot). */
    @JvmOverloads
    fun persistFiltersInSession(on: Boolean = true): Table<T, Q> = apply { filterSet.persist(on, null) }

    fun filtersSessionKey(key: String): Table<T, Q> = apply { filterSet.persist(filterSet.persistsInSession(), key) }

    /**
     * Namespace this table's filter query keys so multiple filtered tables on one page
     * do not share `filters` / `filters.*` (#388). Example: `queryStringIdentifier("orders")`
     * uses `ordersFilters=1` and `ordersFilters.status=…`. Blank clears the namespace.
     */
    fun queryStringIdentifier(id: String): Table<T, Q> = apply {
        val trimmed = id.trim()
        if (trimmed.isNotEmpty() && !QUERY_ID.matches(trimmed)) {
            throw FireflyException("queryStringIdentifier must be a letter followed by letters, digits, or underscores")
        }
        filterSet.queryStringIdentifier(trimmed.ifEmpty { null })
    }

    fun queryStringIdentifier(): String? = filterSet.queryStringIdentifier()

    /** Default true: searching or changing the filter state clears the record selection. */
    @JvmOverloads
    fun deselectAllRecordsWhenFiltered(on: Boolean = true): Table<T, Q> = apply { filterSet.deselectOnFilter(on) }

    /** Hide the active-filter chips bar. */
    @JvmOverloads
    fun hiddenFilterIndicators(on: Boolean = true): Table<T, Q> = apply { filterSet.hideIndicators(on) }

    fun filtersResetActionPosition(position: FiltersResetActionPosition): Table<T, Q> = apply {
        filterSet.resetPosition(position)
    }

    /** Customize the funnel trigger (label, icon, style, badge, hidden). */
    fun filtersTriggerAction(block: Consumer<FilterAction>): Table<T, Q> = apply { filterSet.trigger(block) }

    fun filtersApplyAction(block: Consumer<FilterAction>): Table<T, Q> = apply { filterSet.apply(block) }

    fun filtersResetAction(block: Consumer<FilterAction>): Table<T, Q> = apply { filterSet.reset(block) }

    /** The × that clears every active filter from the indicators bar. */
    fun filtersRemoveAllAction(block: Consumer<FilterAction>): Table<T, Q> = apply { filterSet.removeAll(block) }

    /** Filter configuration and state (layout, defer, session, indicators). */
    fun filterSet(): TableFilters = filterSet

    /** Set a filter's form state (schema filters: field → value). */
    fun filterState(name: String, data: Map<String, Any?>): Table<T, Q> = filtering {
        filterSet.setData(name, FilterData.of(data))
    }

    fun filterData(name: String): FilterData = filterSet.data(filterSet.filter(name), tableCell())

    /** Clear one filter (its default does not come back until [resetFilters]). */
    fun removeFilter(name: String): Table<T, Q> = filtering { filterSet.remove(name, emptyList(), tableCell()) }

    /** Clear only [fields] of a schema filter (indicator `removeField`). */
    fun removeFilterFields(name: String, vararg fields: String): Table<T, Q> = filtering {
        filterSet.remove(name, fields.toList(), tableCell())
    }

    fun removeAllFilters(): Table<T, Q> = filtering { filterSet.removeAll(tableCell()) }

    /** Back to each filter's default state. */
    fun resetFilters(): Table<T, Q> = filtering { filterSet.resetAll() }

    /**
     * Apply `filters` / `filters.*` query-string (or session) input. Returns false when the
     * input carries no filter state, so callers can fall back to a stored copy.
     */
    fun applyFilterQuery(input: Map<String, Any?>): Boolean {
        var applied = false
        filtering { applied = filterSet.applyInput(input) }
        return applied
    }

    /** Current explicit filter state as query-string pairs (empty while defaults apply). */
    fun filterQuery(): Map<String, String> = LinkedHashMap(filterSet.query(tableCell()).toMap())

    /** Options of a [SelectFilter] matching [search] (remote combobox search); empty for other filters. */
    fun filterOptions(name: String, search: String): Map<String, String> {
        val filter = filterSet.filters().firstOrNull { it.name == name } as? SelectFilter ?: return emptyMap()
        return filter.options(tableCell(), store, search.ifBlank { null })
    }

    fun filterIndicators(): List<FilterIndicator> = filterSet.indicators(tableCell(), store).map { it.second }

    /**
     * Find one record through the table query (hooks + filters), skipping filters marked
     * [Filter.excludeWhenResolvingRecord]. Null when filtered out or missing.
     */
    fun resolveRecord(key: String): T? {
        val query = store.query()
        hooks.forEach { it.accept(query) }
        filterSet.applyTo(query, store, tableCell(), resolving = true)
        store.constrain(query, store.keyName(), "=", key)
        return store.list(query).firstOrNull()
    }

    private fun filtering(block: () -> Unit): Table<T, Q> = apply {
        val cell = tableCell()
        val before = filterSet.snapshot(cell)
        block()
        if (filterSet.deselectsOnFilter() && before != filterSet.snapshot(cell)) selectedKeys.clear()
    }

    fun headerButtons(vararg buttons: TableButton): Table<T, Q> = apply {
        headerButtonList.clear()
        headerButtonList.addAll(buttons)
    }

    fun rowButtons(vararg buttons: TableButton): Table<T, Q> = apply {
        rowButtonList.clear()
        rowButtonList.addAll(buttons)
    }

    /**
     * When true (default), two or more [rowButtons] render inside a ⋯ dropdown.
     * Pass false to show each action as its own button.
     */
    fun groupRowActions(on: Boolean = true): Table<T, Q> = apply { groupRowActionsFlag = on }

    /**
     * Whole-row click navigates to this URL (Filament `recordUrl`). Clears [recordAction].
     * Pass blank to fall through to the default View-then-Edit behavior.
     */
    fun recordUrl(url: String): Table<T, Q> = apply {
        recordClickDisabled = false
        recordActionUsing = null
        recordUrlUsing = Function { url }
    }

    fun recordUrl(url: Function<Cell, String>): Table<T, Q> = apply {
        recordClickDisabled = false
        recordActionUsing = null
        recordUrlUsing = url
    }

    /**
     * Whole-row click runs the named row [TableButton] (Filament `recordAction`) —
     * opens its dialog or follows its link. Clears [recordUrl].
     */
    fun recordAction(name: String): Table<T, Q> = apply {
        recordClickDisabled = false
        recordUrlUsing = null
        recordActionUsing = Function { name }
    }

    fun recordAction(name: Function<Cell, String>): Table<T, Q> = apply {
        recordClickDisabled = false
        recordUrlUsing = null
        recordActionUsing = name
    }

    /**
     * When false, rows are not clickable even if View/Edit actions exist.
     * Default true: click View if present, otherwise Edit (#377).
     */
    fun recordClickable(on: Boolean = true): Table<T, Q> = apply {
        recordClickDisabled = !on
        if (!on) {
            recordUrlUsing = null
            recordActionUsing = null
        }
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

    /**
     * Query-string pairs every sort, page, search, and filter link keeps — for example the
     * `id` of the record a relation table belongs to. Replaces earlier values.
     */
    fun persistQueryParameters(values: Map<String, String>): Table<T, Q> = apply {
        persistentQuery.clear()
        persistentQuery.putAll(values)
    }

    fun persistedQueryParameters(): Map<String, String> = LinkedHashMap(persistentQuery)

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

    fun layout(value: TableLayout): Table<T, Q> = apply {
        layout = value
        if (value != TableLayout.GRID) contentGridLayout = null
    }

    /**
     * On small viewports, ROWS become card/stack (label above value). Default **true**
     * so manage/list tables stay readable on phones without an opt-in.
     */
    @JvmOverloads
    fun stackedOnMobile(on: Boolean = true): Table<T, Q> = apply { stackedOnMobileFlag = on }

    /**
     * Arrange records in a responsive card grid (1 column on the smallest size).
     * Example: `contentGrid(mapOf("md" to 2, "xl" to 3))`.
     */
    fun contentGrid(breakpoints: Map<String, Int>): Table<T, Q> = apply {
        contentGridLayout = ColumnLayout.from(breakpoints)
        layout = TableLayout.GRID
    }

    fun selectable(on: Boolean): Table<T, Q> = apply { selectable = on }

    /** Row checkboxes render when [selectable] is on or any bulk action is configured. */
    fun isSelectable(): Boolean = selectable || bulkEntries.isNotEmpty()

    /**
     * Toolbar bulk actions: each [BulkActionGroup] renders as a dropdown, each loose [BulkAction]
     * as a button beside it. Configuring bulk actions turns on row selection.
     * Calling with no entries clears them and opts out of resource defaults.
     */
    fun bulkActions(vararg entries: BulkActionEntry): Table<T, Q> = apply {
        bulkEntries.clear()
        bulkEntries.addAll(entries)
        bulkConfigured = true
    }

    /** Shorthand for `bulkActions(BulkActionGroup.make(...))`. */
    fun groupedBulkActions(vararg actions: BulkAction): Table<T, Q> = bulkActions(BulkActionGroup.make(*actions))

    fun bulkActionEntries(): List<BulkActionEntry> = bulkEntries.toList()

    /** True once [bulkActions] was called (even with no entries). */
    fun isBulkActionsConfigured(): Boolean = bulkConfigured

    fun bulkAction(name: String): BulkAction =
        bulkEntries.flatMap { it.bulkActions() }.firstOrNull { it.name == name }
            ?: throw FireflyException("Unknown bulk action [$name]")

    fun checkboxPosition(position: RecordCheckboxPosition): Table<T, Q> = apply { checkboxPosition = position }

    /** Rows failing [predicate] render a disabled checkbox and are skipped by bulk actions. */
    fun checkIfRecordIsSelectableUsing(predicate: Predicate<Cell>): Table<T, Q> = apply { selectableUsing = predicate }

    fun maxSelectableRecords(max: Int): Table<T, Q> = apply {
        if (max < 1) throw FireflyException("Max selectable records must be at least 1")
        maxSelectable = max
    }

    /** Hide the "Select all N" control so selection stays on the current page. */
    @JvmOverloads
    fun selectCurrentPageOnly(on: Boolean = true): Table<T, Q> = apply { currentPageOnly = on }

    /** Only one group's records may be selected at a time (grouped tables). */
    @JvmOverloads
    fun selectGroupsOnly(on: Boolean = true): Table<T, Q> = apply { groupsOnly = on }

    /**
     * Run bulk action [name] against [keys] (or every record matching the current search,
     * filters, and query hooks when [allMatching]). [data] carries the dialog's submitted fields.
     */
    @JvmOverloads
    fun runBulkAction(
        name: String,
        keys: List<String>,
        data: Map<String, Any?> = emptyMap(),
        allMatching: Boolean = false,
    ): BulkActionResult {
        val action = bulkAction(name)
        val cell = tableCell()
        if (action.isHidden(cell) || action.isDisabled(cell)) {
            throw FireflyException("Bulk action [$name] is not available")
        }
        val requested = if (allMatching) {
            store.list(prepare(store.query())).map { store.keyOf(it) }
        } else {
            keys.filter { it.isNotBlank() }.distinct()
        }
        if (requested.isEmpty()) throw FireflyException("Bulk action [$name] needs at least one selected record")
        val max = maxSelectable
        if (max != null && requested.size > max) throw FireflyException("Select at most $max records")
        val records = requested
            .map { key -> BulkRecord(key, scopedFind(key)?.let { store.attributes(it) }) }
            .filter { record -> record.attributes == null || isRecordSelectable(record.attributes, record.key) }
        val grouped = groupColumn
        if (groupsOnly && grouped != null) {
            val groups = records.mapNotNull { it.attributes }.map { it[grouped]?.toString() }.distinct()
            if (groups.size > 1) throw FireflyException("Select records from one group at a time")
        }
        return action.execute(
            records,
            data,
            { key -> deleteRecord(key) },
            { record -> rowActionCell(record.attributes.orEmpty(), record.key) },
        )
    }

    private fun deleteRecord(key: String): Boolean {
        val record = scopedFind(key) ?: return false
        store.delete(record)
        return true
    }

    /**
     * Selected keys resolve through [modifyQueryUsing] hooks, so a posted key outside the
     * table's query (another owner's related record) counts as missing.
     */
    private fun scopedFind(key: String): T? {
        val found = store.find(key) ?: return null
        if (hooks.isEmpty()) return found
        val query = baseQuery()
        store.constrain(query, store.keyName(), "=", store.attributes(found)[store.keyName()])
        return store.list(query).firstOrNull()
    }

    private fun isRecordSelectable(values: Map<String, Any?>, key: String): Boolean {
        val using = selectableUsing ?: return true
        return using.test(rowActionCell(values, key))
    }

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

    fun filter(name: String, value: Any?): Table<T, Q> = filtering { filterSet.setValue(name, value) }

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
        val stackedAttr =
            if (stackedOnMobileFlag) """ data-ff-stacked-mobile="true"""" else """ data-ff-stacked-mobile="false""""
        val composedAttr = if (usesComposedLayout()) """ data-ff-composed="true"""" else ""
        val gridAttr = if (contentGridLayout != null) """ data-ff-content-grid="true"""" else ""
        val filtersPrefix =
            if (filterSet.isEmpty()) {
                ""
            } else {
                """ data-ff-filters-prefix="${escapeHtml(filterSet.queryPrefix())}""""
            }
        return """
            <div class="ff-table-wrap" data-ff-layout="${layout.name.lowercase()}"$stackedAttr$composedAttr$gridAttr$pollAttr$filtersPrefix${selectionAttrs(loaded)}>
              ${toolbar(cell, loaded)}
              ${filterSet.aboveHtml(filterView(cell))}
              ${filterSet.indicatorsHtml(filterView(cell))}
              ${body(loaded, visible)}
              ${footer(loaded)}
              ${emptyState(loaded, cell)}
              ${pager(loaded)}
              ${filterSet.belowHtml(filterView(cell))}
            </div>
        """.trimIndent()
    }

    private fun usesComposedLayout(): Boolean = columnDefs.any { it is LayoutComponent }

    fun call(name: String, vararg args: String): Table<T, Q> = apply {
        when (name) {
            "selectRecord" -> select(arg(args, 0, name))
            "selectPage" -> selectPage()
            "gotoPage" -> page(number(arg(args, 0, name), name))
            "search" -> search(arg(args, 0, name))
            "sort" -> sort(arg(args, 0, name), if (args.size > 1) args[1] else "asc")
            "perPage" -> perPage(number(arg(args, 0, name), name))
            "applyFilter" -> filter(arg(args, 0, name), blankToNull(arg(args, 1, name)))
            "removeFilter" -> removeFilter(arg(args, 0, name))
            "removeAllFilters" -> removeAllFilters()
            "resetFilters" -> resetFilters()
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
        eagerLoadRelationships(query)
        val cell = tableCell()
        hooks.forEach { it.accept(query) }
        filterSet.applyTo(query, store, cell, resolving = false)
        val searchable = columnList.filter { it.isSearchable(cell) }.map { it.queryName() }
        if (searchTerm.isNotEmpty()) store.search(query, searchTerm, searchable)
        val grouped = groupColumn
        if (grouped != null) store.sort(query, grouped, "asc")
        val sorting = sortColumn
        if (sorting != null) {
            store.sort(query, column(sorting).queryName(), sortDirection)
        }
        return query
    }

    /** Cadence queries: `with()` every [Column.relationship] so titles resolve without N+1. */
    @Suppress("UNCHECKED_CAST")
    private fun eagerLoadRelationships(query: Q) {
        val names = columnList.mapNotNull { it.relationshipName() }.distinct()
        if (names.isEmpty()) return
        if (query is ModelBuilder<*>) {
            (query as ModelBuilder<Cadence<*>>).with(*names.toTypedArray())
        }
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

    /** Chrome reads these to keep the selection, enforce limits, and offer "Select all N". */
    private fun selectionAttrs(loaded: LoadedTable<T>): String {
        if (!isSelectable()) return ""
        val max = maxSelectable?.let { """ data-ff-max-selectable="$it"""" }.orEmpty()
        val groups = if (groupsOnly) """ data-ff-select-groups-only="true"""" else ""
        return """ data-ff-selectable data-ff-total="${loaded.total}" data-ff-deselect-on-filter="${filterSet.deselectsOnFilter()}"$max$groups"""
    }

    /** Filament-style bulk area: dropdown group trigger(s) and loose action buttons, plus hidden forms/dialogs. */
    private fun bulkHtml(cell: Cell): String {
        val triggers = StringBuilder()
        for (entry in bulkEntries) {
            if (entry is BulkActionGroup) {
                triggers.append(entry.render(cell))
            } else {
                entry.bulkActions().filter { !it.isHidden(cell) }.forEach { triggers.append(it.renderTrigger(cell, menuItem = false)) }
            }
        }
        if (triggers.isEmpty()) return ""
        val mounts = bulkEntries.flatMap { it.bulkActions() }.filter { !it.isHidden(cell) }.joinToString("") { it.renderMount(cell) }
        return """<div class="ff-table-bulk" data-ff-bulk><div class="ff-table-bulk-triggers">$triggers</div><div class="ff-table-bulk-mounts">$mounts</div></div>"""
    }

    /** "N records selected · Select all M · Deselect all" — shown by the chrome once rows are selected. */
    private fun selectionBar(loaded: LoadedTable<T>): String {
        if (!isSelectable()) return ""
        val withinMax = maxSelectable?.let { loaded.total <= it } ?: true
        val all = if (!currentPageOnly && !groupsOnly && withinMax) {
            """<button type="button" class="ff-table-selection-link" data-ff-select-all-matching hidden>Select all ${loaded.total}</button>"""
        } else {
            ""
        }
        return """<div class="ff-table-selection" data-ff-selection role="status" hidden><span class="ff-table-selection-count" data-ff-selection-count></span>$all<button type="button" class="ff-table-selection-link" data-ff-deselect-all>Deselect all</button></div>"""
    }

    private fun toolbar(cell: Cell, loaded: LoadedTable<T>): String {
        val bulk = bulkHtml(cell)
        val selection = selectionBar(loaded)
        val form = toolbarForm(cell)
        val toolbar = when {
            bulk.isEmpty() && form.isEmpty() -> """<div class="ff-table-toolbar ff-table-toolbar--empty" hidden></div>"""
            bulk.isEmpty() -> """<div class="ff-table-toolbar">$form</div>"""
            // Bulk forms are POST forms — keep them outside the GET toolbar form.
            else -> """<div class="ff-table-toolbar"><div class="ff-table-toolbar-row">$bulk$form</div></div>"""
        }
        return toolbar + selection
    }

    /** Search, column picker, filters, and header buttons — empty when the table has none. */
    private fun toolbarForm(cell: Cell): String {
        val searchable = columnList.any { it.isSearchable(cell) }
        val columnToggle = columnToggleHtml(cell)
        val mobileSort = mobileSortHtml(cell)
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
        val filterView = filterView(cell)
        val filters = filterSet.triggerHtml(filterView)
        val buttons = headerButtonList.joinToString("") { it.render(cell, selectedKeys.isEmpty()) }
        val tools = if (filters.isEmpty() && buttons.isEmpty() && columnToggle.isEmpty() && mobileSort.isEmpty()) {
            ""
        } else {
            """<div class="ff-table-toolbar-actions">$mobileSort$columnToggle$filters$buttons</div>"""
        }
        if (!searchable && filters.isEmpty() && headerButtonList.isEmpty() && columnToggle.isEmpty() && mobileSort.isEmpty()) return ""
        val columnsHidden = columnsQueryHiddenInput(cell)
        val filterHidden = filterSet.hiddenInputs(cell)
        val persistedHidden = persistentQuery.entries.joinToString("") { (key, value) ->
            """<input type="hidden" name="${escapeHtml(key)}" value="${escapeHtml(value)}" />"""
        }
        // Query-string toolbar — chrome soft-nav swaps the wrap without a full document reload.
        // Filter panels carry their own GET form, so they render after the toolbar form (forms can't nest).
        val prefixAttr =
            if (filterSet.isEmpty()) {
                ""
            } else {
                """ data-ff-filters-prefix="${escapeHtml(filterSet.queryPrefix())}""""
            }
        return """<form class="ff-table-toolbar-form" method="get" data-ff-table-nav$prefixAttr>$persistedHidden$search$columnsHidden$filterHidden$tools</form>${filterSet.panelHtml(filterView)}"""
    }

    /**
     * Sort dropdown for stacked / card layouts when header sort links are hidden on small screens.
     */
    private fun mobileSortHtml(cell: Cell): String {
        // contentGrid forces GRID layout, so layout != ROWS already covers it.
        val needsMobileSort =
            stackedOnMobileFlag || layout != TableLayout.ROWS || usesComposedLayout()
        if (!needsMobileSort) return ""
        val sortable = columnList.filter { shown(it, cell) && it.isSortable(cell) }
        if (sortable.isEmpty()) return ""
        val options = StringBuilder()
        options.append("""<option value="">Sort</option>""")
        for (column in sortable) {
            val label = escapeHtml(column.heading(cell))
            val ascSelected = if (sortColumn == column.name && sortDirection == "asc") " selected" else ""
            val descSelected = if (sortColumn == column.name && sortDirection == "desc") " selected" else ""
            options.append(
                """<option value="${escapeHtml(column.name)}:asc"$ascSelected>$label · Asc</option>""",
            )
            options.append(
                """<option value="${escapeHtml(column.name)}:desc"$descSelected>$label · Desc</option>""",
            )
        }
        return """
            <label class="ff-table-mobile-sort">
              <span class="ff-table-mobile-sort-label">Sort</span>
              <select class="ff-table-mobile-sort-select" data-ff-table-nav-select data-ff-mobile-sort aria-label="Sort records">$options</select>
            </label>
        """.trimIndent()
    }

    /** Columns picker for [Column.toggleable] / [Column.toggledHiddenByDefault] (#363). */
    private fun columnToggleHtml(cell: Cell): String {
        val toggleable = columnList.filter { it.isToggleable(cell) }
        if (toggleable.isEmpty()) return ""
        val options = toggleable.joinToString("") { column ->
            val checked = if (column.name !in hiddenByUser) " checked" else ""
            val label = escapeHtml(column.heading(cell))
            """<label class="ff-column-toggle-option"><input type="checkbox" data-ff-column-toggle="${escapeHtml(column.name)}"$checked /><span>$label</span></label>"""
        }
        return """
            <details class="ff-column-toggle">
              <summary class="ff-column-toggle-trigger" aria-label="Toggle columns">
                <svg class="ff-column-toggle-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M9 4.5v15m6-15v15m-10.875 0h15.75c.621 0 1.125-.504 1.125-1.125V5.625c0-.621-.504-1.125-1.125-1.125H4.125C3.504 4.5 3 5.004 3 5.625v12.75c0 .621.504 1.125 1.125 1.125z"/></svg>
                <span>Columns</span>
              </summary>
              <div class="ff-column-toggle-panel" role="group" aria-label="Toggle columns">$options</div>
            </details>
        """.trimIndent()
    }

    private fun columnsQueryHiddenInput(cell: Cell): String {
        val value = columnsQueryValue(cell) ?: return ""
        return """<input type="hidden" name="columns" value="${escapeHtml(value)}" data-ff-columns-state />"""
    }

    /** Comma-separated visible toggleable column names, or null when none are toggleable. */
    private fun columnsQueryValue(cell: Cell): String? {
        val toggleable = columnList.filter { it.isToggleable(cell) }
        if (toggleable.isEmpty()) return null
        return toggleable.filter { it.name !in hiddenByUser }.joinToString(",") { it.name }
    }

    private fun body(loaded: LoadedTable<T>, visible: List<Column>): String {
        if (usesComposedLayout() || contentGridLayout != null) {
            val className = when {
                contentGridLayout != null || layout == TableLayout.GRID -> "ff-table-grid"
                layout == TableLayout.STACK -> "ff-table-stack"
                else -> "ff-table-stack"
            }
            return cards(loaded, visible, className)
        }
        return when (layout) {
            TableLayout.ROWS -> rows(loaded, visible)
            TableLayout.STACK -> cards(loaded, visible, "ff-table-stack")
            TableLayout.GRID -> cards(loaded, visible, "ff-table-grid")
        }
    }

    private fun rows(loaded: LoadedTable<T>, visible: List<Column>): String {
        val cell = tableCell()
        val headers = visible.joinToString("") { columnHeader(it, cell) }
        val selecting = isSelectable()
        val selectHead = if (selecting) selectPageHead(loaded) else ""
        val actionHead = if (rowButtonList.isEmpty()) "" else """<th class="ff-table-actions-head" data-ff-align="end">Actions</th>"""
        val span = visible.size + (if (selecting) 1 else 0) + (if (rowButtonList.isEmpty()) 0 else 1)
        val body = when {
            loaded.isEmpty() -> {
                """<tr class="ff-table-empty-row"><td colspan="$span">${emptyStateInner(cell)}</td></tr>"""
            }
            loaded.groups.isEmpty() -> loaded.records.joinToString("") { recordRow(it, visible, null) }
            else -> loaded.groups.joinToString("") { (label, records) ->
                """<tr class="ff-group"><th colspan="$span">${groupSelect(label, records)}${escapeHtml(label)}</th></tr>""" +
                    records.joinToString("") { recordRow(it, visible, label) }
            }
        }
        val head = if (checkboxPosition == RecordCheckboxPosition.AFTER_CELLS) "$headers$selectHead" else "$selectHead$headers"
        return """<div class="ff-table-scroll"><table class="ff-table"><thead><tr>$head$actionHead</tr></thead><tbody>$body</tbody></table></div>"""
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
        val parts = persistedParts()
        if (searchTerm.isNotEmpty()) parts.add("search=${encodeQuery(searchTerm)}")
        parts.add("sort=${encodeQuery(column)}")
        parts.add("direction=$direction")
        if (pageNumber > 1) parts.add("page=$pageNumber")
        if (pageSize != 10) parts.add("perPage=$pageSize")
        appendColumnsQuery(parts)
        appendFilterQuery(parts)
        return "?" + parts.joinToString("&")
    }

    private fun appendFilterQuery(parts: ArrayList<String>) {
        for ((key, value) in filterSet.query(tableCell())) parts.add("${encodeQuery(key)}=${encodeQuery(value)}")
    }

    /** Search / sort / page size / columns the filters form and indicator links carry (page resets to 1). */
    private fun persistedParts(): ArrayList<String> =
        persistentQuery.entries.mapTo(ArrayList()) { (key, value) -> "${encodeQuery(key)}=${encodeQuery(value)}" }

    private fun filterView(cell: Cell): TableFilters.View {
        val base = persistentQuery.entries.mapTo(ArrayList()) { it.key to it.value }
        if (searchTerm.isNotEmpty()) base.add("search" to searchTerm)
        val sorting = sortColumn
        if (sorting != null) {
            base.add("sort" to sorting)
            base.add("direction" to sortDirection)
        }
        if (pageSize != 10) base.add("perPage" to pageSize.toString())
        val cols = columnsQueryValue(cell)
        if (cols != null) base.add("columns" to cols)
        return TableFilters.View(cell, store, base)
    }

    private fun appendColumnsQuery(parts: ArrayList<String>) {
        val cols = columnsQueryValue(tableCell()) ?: return
        parts.add("columns=${encodeQuery(cols)}")
    }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun recordRow(record: T, visible: List<Column>, group: String?): String {
        val values = store.attributes(record)
        val key = store.keyOf(record)
        val select = if (isSelectable()) """<td class="ff-table-select" data-label="">${selectionBox(values, key)}</td>""" else ""
        val cells = visible.joinToString("") { column ->
            val label = escapeHtml(column.heading(tableCell()))
            """<td data-label="$label">${column.render(values, key, rowCell(values, column.name))}</td>"""
        }
        val actions = rowActions(values, key, asTableCell = true)
        val click = recordClickAttrs(rowActionCell(values, key))
        val selected = if (select.isNotEmpty() && key in selectedKeys) """ class="ff-selected"""" else ""
        val groupAttr = group?.let { """ data-ff-group="${escapeHtml(it)}"""" }.orEmpty()
        val row = if (checkboxPosition == RecordCheckboxPosition.AFTER_CELLS) "$cells$select" else "$select$cells"
        return """<tr data-ff-record="${escapeHtml(key)}"$selected$groupAttr$click>$row$actions</tr>"""
    }

    /** Header checkbox: toggles every selectable row on this page (indeterminate set by the chrome). */
    private fun selectPageHead(loaded: LoadedTable<T>): String {
        // Group-only selection uses each group header's checkbox instead.
        if (groupsOnly) return """<th class="ff-table-select" scope="col"></th>"""
        return """<th class="ff-table-select" scope="col">${selectPageBox(loaded)}</th>"""
    }

    private fun selectPageBox(loaded: LoadedTable<T>): String {
        val keys = loaded.records
            .map { store.attributes(it) to store.keyOf(it) }
            .filter { (values, key) -> isRecordSelectable(values, key) }
            .map { it.second }
        val checked = if (keys.isNotEmpty() && keys.all { it in selectedKeys }) " checked" else ""
        val disabled = if (keys.isEmpty()) " disabled" else ""
        return checkbox("data-ff-select-page$checked$disabled", "Select all on this page")
    }

    private fun groupSelect(label: String, records: List<T>): String {
        if (!isSelectable()) return ""
        val selectableCount = records.count { isRecordSelectable(store.attributes(it), store.keyOf(it)) }
        val disabled = if (selectableCount == 0) " disabled" else ""
        return checkbox("""data-ff-select-group="${escapeHtml(label)}"$disabled""", "Select group $label")
    }

    private fun selectionBox(values: Map<String, Any?>, key: String): String {
        val allowed = isRecordSelectable(values, key)
        val checked = if (allowed && key in selectedKeys) " checked" else ""
        val disabled = if (allowed) "" else " disabled"
        return checkbox("""data-ff-select-record value="${escapeHtml(key)}"$checked$disabled""", "Select row")
    }

    private fun checkbox(attrs: String, label: String): String =
        """<label class="ff-checkbox-wrap ff-table-checkbox"><input type="checkbox" class="ff-checkbox" $attrs aria-label="${escapeHtml(label)}"><span class="ff-checkbox-box" aria-hidden="true"></span></label>"""

    private fun cards(loaded: LoadedTable<T>, visible: List<Column>, className: String): String {
        val cell = tableCell()
        val content = when {
            loaded.isEmpty() -> {
                """<div class="ff-table-card ff-table-card--empty">${emptyStateInner(cell)}</div>"""
            }
            loaded.groups.isEmpty() -> loaded.records.joinToString("") { card(it, visible, null) }
            else -> loaded.groups.joinToString("") { (label, records) ->
                """<h3 class="ff-group">${groupSelect(label, records)}${escapeHtml(label)}</h3>""" +
                    records.joinToString("") { card(it, visible, label) }
            }
        }
        val grid = contentGridLayout
        val style = if (grid == null) "" else """ style="${grid.styleAttribute()}""""
        val gridData =
            if (grid == null) {
                ""
            } else {
                """ data-ff-content-grid="true" data-ff-columns="${grid.dataValue()}""""
            }
        val selectPage =
            if (!isSelectable() || loaded.isEmpty() || groupsOnly) {
                ""
            } else {
                """<div class="ff-table-card-select-page">${selectPageBox(loaded)}<span>Select all on this page</span></div>"""
            }
        return """<div class="$className"$gridData$style>$selectPage$content</div>"""
    }

    private fun card(record: T, visible: List<Column>, group: String?): String {
        val values = store.attributes(record)
        val key = store.keyOf(record)
        val select = if (isSelectable()) """<div class="ff-table-select ff-table-card-select">${selectionBox(values, key)}</div>""" else ""
        val selected = if (select.isNotEmpty() && key in selectedKeys) " ff-selected" else ""
        val groupAttr = group?.let { """ data-ff-group="${escapeHtml(it)}"""" }.orEmpty()
        val fields =
            if (usesComposedLayout()) {
                composedFields(values, key)
            } else {
                visible.joinToString("") { column ->
                    """<div class="ff-stack-field"><span class="ff-stack-label">${escapeHtml(column.heading(tableCell()))}</span><div class="ff-stack-value">${column.render(values, key, rowCell(values, column.name))}</div></div>"""
                }
            }
        val actions = rowActions(values, key, asTableCell = false)
        val click = recordClickAttrs(rowActionCell(values, key))
        return """<article class="ff-table-card$selected" data-ff-record="${escapeHtml(key)}"$groupAttr$click><div class="ff-table-card-main">$select<div class="ff-table-card-body">$fields</div></div>$actions</article>"""
    }

    private fun composedFields(values: Map<String, Any?>, key: String): String {
        val tableCell = tableCell()
        val ctx = LayoutRenderContext(
            values = values,
            key = key,
            tableCell = tableCell,
            rowCellFor = { name -> rowCell(values, name) },
            shown = { column -> shown(column, tableCell) },
        )
        return columnDefs.joinToString("") { def ->
            when (def) {
                is LayoutComponent -> def.render(ctx)
                is Column -> {
                    if (!shown(def, tableCell)) {
                        ""
                    } else {
                        """<div class="ff-stack-field"><span class="ff-stack-label">${escapeHtml(def.heading(tableCell))}</span><div class="ff-stack-value">${def.render(values, key, rowCell(values, def.name))}</div></div>"""
                    }
                }
                else -> throw FireflyException("Unexpected table column def ${def.javaClass.name}")
            }
        }
    }

    private fun rowActionCell(values: Map<String, Any?>, key: String): Cell =
        Cell { path ->
            when (path) {
                "key", "recordKey" -> key
                else -> values[path.removePrefix("/")]
            }
        }

    /**
     * Filament-shaped row click: explicit [recordUrl] / [recordAction], else View then Edit (#377).
     */
    private fun recordClickAttrs(cell: Cell): String {
        if (recordClickDisabled) return ""
        val urlUsing = recordUrlUsing
        if (urlUsing != null) {
            val url = urlUsing.apply(cell).trim()
            if (url.isEmpty()) return ""
            return """ data-ff-record-url="${escapeHtml(url)}" tabindex="0""""
        }
        val actionUsing = recordActionUsing
        if (actionUsing != null) {
            val action = actionUsing.apply(cell).trim()
            if (action.isEmpty()) return ""
            return """ data-ff-record-action="${escapeHtml(action)}" tabindex="0""""
        }
        val target = defaultRecordClick(cell) ?: return ""
        val url = target.url
        if (url != null) return """ data-ff-record-url="${escapeHtml(url)}" tabindex="0""""
        return """ data-ff-record-action="${escapeHtml(target.action)}" tabindex="0""""
    }

    private data class RecordClick(val url: String? = null, val action: String = "")

    /** Prefer View, then Edit — URL buttons yield [RecordClick.url], dialogs yield [RecordClick.action]. */
    private fun defaultRecordClick(cell: Cell): RecordClick? {
        for (name in listOf("view", "edit")) {
            val button = rowButtonList.firstOrNull { it.name == name } ?: continue
            if (button.isHidden(cell)) continue
            val href = button.urlFor(cell)
            if (href != null) return RecordClick(url = href)
            return RecordClick(action = name)
        }
        return null
    }

    private fun rowActions(values: Map<String, Any?>, key: String, asTableCell: Boolean): String {
        if (rowButtonList.isEmpty()) return ""
        val cell = rowActionCell(values, key)
        val emptySelection = selectedKeys.isEmpty()
        val buttons =
            if (groupRowActionsFlag && rowButtonList.size > 1) {
                listOf(TableButton.group(*rowButtonList.toTypedArray()))
            } else {
                rowButtonList
            }
        val html = buttons.joinToString("") { it.render(cell, emptySelection) }
        val inner = """<div class="ff-row-actions-inner">$html</div>"""
        // Keep flex on an inner wrapper — display:flex on the <td> breaks collapsed
        // row borders (disjoint rules vs other columns on narrow/scroll viewports) (#365).
        return if (asTableCell) {
            """<td class="ff-row-actions" data-label="Actions">$inner</td>"""
        } else {
            """<div class="ff-row-actions">$inner</div>"""
        }
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
        val parts = persistedParts()
        if (searchTerm.isNotEmpty()) parts.add("search=${encodeQuery(searchTerm)}")
        val sorting = sortColumn
        if (sorting != null) {
            parts.add("sort=${encodeQuery(sorting)}")
            parts.add("direction=$sortDirection")
        }
        parts.add("page=$page")
        if (pageSize != 10) parts.add("perPage=$pageSize")
        appendColumnsQuery(parts)
        appendFilterQuery(parts)
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

    private fun column(name: String): Column =
        columnList.firstOrNull { it.name == name } ?: throw FireflyException("Unknown column [$name]")

    private fun tableCell(): Cell = Cell { path ->
        when (path) {
            "search" -> searchTerm
            "page" -> pageNumber
            "sort" -> sortColumn
            else -> filterStateAt(path)
        }
    }

    /** Table closures read a filter's value by name (`filters.x` fields as `x.field`). */
    private fun filterStateAt(path: String): Any? {
        val name = path.substringBefore('.')
        val filter = filterSet.filters().firstOrNull { it.name == name } ?: return null
        val field = if (path.contains('.')) path.substringAfter('.') else FilterData.VALUE
        val stored = filterSet.data(filter, Cell { null })
        return stored.get(field)
    }

    private fun rowCell(values: Map<String, Any?>, column: String): Cell = Cell { path ->
        when (path) {
            ".", "" -> Column.digMap(values, column)
            else -> Column.digMap(values, path.removePrefix("/"))
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
        private val QUERY_ID = Regex("^[A-Za-z][A-Za-z0-9_]*$")
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
