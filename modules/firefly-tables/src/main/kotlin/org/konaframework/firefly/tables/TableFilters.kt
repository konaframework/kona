package org.konaframework.firefly.tables

import org.konaframework.firefly.schemas.ColumnLayout
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer

/**
 * Filter state + chrome for one [Table]: query-string / session serialization, per-filter
 * query groups, indicators, and the dropdown / modal / above / below layouts.
 *
 * Query-string shape: `{prefix}=1` marks explicit state (absent fields mean "cleared"),
 * `{prefix}=reset` restores defaults; single-control filters use `{prefix}.{name}`, schema
 * filters `{prefix}.{name}.{field}`. The default prefix is `filters`; [queryStringIdentifier]
 * namespaces it as `{id}Filters` so multiple tables on one page do not share keys (#388).
 * Without the sentinel, defaults (or the session copy) apply.
 */
class TableFilters internal constructor() {
    private val list = ArrayList<Filter>()
    private val explicit = LinkedHashMap<String, FilterData>()
    private var explicitMode = false
    private var layout = FiltersLayout.DROPDOWN
    private var formColumns = 1
    private var formWidth: String? = null
    private var formMaxHeight: String? = null
    private val sections = ArrayList<FilterSection>()
    private var deferred = true
    private var persist = false
    private var sessionKey: String? = null
    private var deselectOnFilter = true
    private var hideIndicators = false
    private var resetPosition = FiltersResetActionPosition.HEADER
    private val trigger = FilterAction("filter", "Filter").iconButton()
    private val applyAction = FilterAction("apply", "Apply filters").button()
    private val resetAction = FilterAction("reset", "Reset").link()
    private val removeAllAction = FilterAction("removeAll", "Remove all filters").iconButton()
    private var headingText = "Filters"
    private var queryIdentifier: String? = null

    internal fun set(filters: List<Filter>) {
        list.clear()
        list.addAll(filters)
        explicit.keys.retainAll(filters.map { it.name }.toSet())
    }

    fun filters(): List<Filter> = list.toList()

    fun isEmpty(): Boolean = list.isEmpty()

    /**
     * Namespace filter query keys for this table. Blank clears the namespace (back to `filters`).
     * Non-blank values become `{id}Filters` / `{id}Filters.{name}` (#388).
     */
    internal fun queryStringIdentifier(id: String?) {
        val trimmed = id?.trim().orEmpty()
        queryIdentifier = trimmed.ifEmpty { null }
    }

    fun queryStringIdentifier(): String? = queryIdentifier

    /** Query-string sentinel / prefix (`filters` or `{id}Filters`). */
    fun queryPrefix(): String {
        val id = queryIdentifier
        return if (id.isNullOrBlank()) DEFAULT_SENTINEL else "${id}Filters"
    }

    internal fun layout(value: FiltersLayout) {
        layout = value
    }

    fun layout(): FiltersLayout = layout

    internal fun formColumns(count: Int) {
        if (count < 1) throw FireflyException("Filters form columns must be at least 1")
        formColumns = count
    }

    internal fun formWidth(value: String) {
        if (value !in WIDTHS) throw FireflyException("Unknown filters form width [$value]")
        formWidth = value
    }

    internal fun formMaxHeight(value: String) {
        if (!CSS_LENGTH.matches(value)) throw FireflyException("Filters form max height must be a CSS length like 24rem")
        formMaxHeight = value
    }

    internal fun sections(values: List<FilterSection>) {
        sections.clear()
        sections.addAll(values)
    }

    internal fun defer(on: Boolean) {
        deferred = on
    }

    fun isDeferred(): Boolean = deferred

    internal fun persist(on: Boolean, key: String?) {
        persist = on
        if (key != null) sessionKey = key
    }

    fun persistsInSession(): Boolean = persist

    fun sessionKey(): String? = sessionKey

    internal fun deselectOnFilter(on: Boolean) {
        deselectOnFilter = on
    }

    fun deselectsOnFilter(): Boolean = deselectOnFilter

    internal fun hideIndicators(on: Boolean) {
        hideIndicators = on
    }

    internal fun resetPosition(value: FiltersResetActionPosition) {
        resetPosition = value
    }

    internal fun heading(value: String) {
        headingText = value
    }

    internal fun trigger(block: Consumer<FilterAction>) = block.accept(trigger)

    internal fun apply(block: Consumer<FilterAction>) = block.accept(applyAction)

    internal fun reset(block: Consumer<FilterAction>) = block.accept(resetAction)

    internal fun removeAll(block: Consumer<FilterAction>) = block.accept(removeAllAction)

    fun filter(name: String): Filter =
        list.firstOrNull { it.name == name } ?: throw FireflyException("Unknown filter [$name]")

    /** Current state: explicit value, else (unless state is explicit) the filter default. */
    fun data(filter: Filter, cell: Cell): FilterData {
        val stored = explicit[filter.name]
        if (stored != null) return stored
        if (explicitMode) return FilterData(emptyMap())
        return filter.defaultData(cell)
    }

    fun isExplicit(): Boolean = explicitMode

    internal fun snapshot(cell: Cell): Map<String, FilterData> = list.associate { it.name to data(it, cell) }

    /** Legacy single-value setter: null drops the explicit value so the default applies again. */
    internal fun setValue(name: String, value: Any?) {
        val filter = filter(name)
        if (value == null) explicit.remove(name) else explicit[name] = filter.toData(value)
    }

    internal fun setData(name: String, data: FilterData) {
        filter(name)
        explicit[name] = data
        explicitMode = true
    }

    internal fun remove(name: String, fields: Collection<String>, cell: Cell) {
        val filter = filter(name)
        val current = data(filter, cell)
        freeze(cell)
        explicit[name] = filter.applyIndicatorRemoval(current, fields.toList())
    }

    internal fun removeAll(cell: Cell) {
        freeze(cell)
        for (filter in list) explicit[filter.name] = FilterData(emptyMap())
    }

    internal fun resetAll() {
        explicit.clear()
        explicitMode = false
    }

    /** Pin every filter's current state so later removals don't resurrect defaults. */
    private fun freeze(cell: Cell) {
        if (explicitMode) return
        for (filter in list) {
            if (!explicit.containsKey(filter.name)) explicit[filter.name] = filter.defaultData(cell)
        }
        explicitMode = true
    }

    /**
     * Read `{prefix}` / `{prefix}.*` keys. Returns false when the input carries no filter state
     * for this table's [queryPrefix] (caller may restore a session copy).
     */
    internal fun applyInput(input: Map<String, Any?>): Boolean {
        val sentinel = queryPrefix()
        val raw = input[sentinel] ?: return false
        val mode = raw.toString()
        if (mode == RESET) {
            resetAll()
            return true
        }
        explicit.clear()
        explicitMode = true
        for (filter in list) {
            val single = input["$sentinel.${filter.name}"]
            val fieldPrefix = "$sentinel.${filter.name}."
            val fields = LinkedHashMap<String, Any?>()
            if (single != null) fields[FilterData.VALUE] = single
            for ((key, value) in input) {
                if (key.startsWith(fieldPrefix)) fields[key.removePrefix(fieldPrefix)] = value
            }
            explicit[filter.name] = if (filter.hasSchema()) {
                FilterData(fields.filterKeys { it != FilterData.VALUE })
            } else {
                FilterData(fields.filterKeys { it == FilterData.VALUE })
            }
        }
        return true
    }

    /** Query-string pairs for the current state; empty when state is implicit (defaults). */
    fun query(cell: Cell): List<Pair<String, String>> {
        if (!explicitMode) return emptyList()
        val sentinel = queryPrefix()
        val out = ArrayList<Pair<String, String>>()
        out.add(sentinel to "1")
        for (filter in list) out.addAll(pairs(filter, data(filter, cell)))
        return out
    }

    private fun pairs(filter: Filter, data: FilterData): List<Pair<String, String>> {
        val sentinel = queryPrefix()
        val out = ArrayList<Pair<String, String>>()
        for (key in data.keys()) {
            if (!data.filled(key)) continue
            val name = if (key == FilterData.VALUE) "$sentinel.${filter.name}" else "$sentinel.${filter.name}.$key"
            out.add(name to serialize(data.get(key)))
        }
        return out
    }

    private fun serialize(value: Any?): String = when (value) {
        is Collection<*> -> value.filterNotNull().joinToString(",") { serialize(it) }
        true -> "1"
        else -> value.toString()
    }

    /** Apply base queries and active filters, each active filter inside its own group. */
    internal fun <Q> applyTo(query: Q, store: RecordStore<*, Q>, cell: Cell, resolving: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val typed = store as RecordStore<Any?, Any?>
        for (filter in list) {
            if (resolving && filter.isExcludedWhenResolvingRecord()) continue
            val data = data(filter, cell)
            filter.applyToBaseQuery(query as Any, store, data)
            if (!filter.isActive(data)) continue
            typed.group(query, false) { inner -> filter.applyToQuery(inner as Any, store, data) }
        }
    }

    fun indicators(cell: Cell, store: RecordStore<*, *>?): List<Pair<Filter, FilterIndicator>> {
        val out = ArrayList<Pair<Filter, FilterIndicator>>()
        for (filter in list) {
            if (filter.isHidden(cell)) continue
            val data = data(filter, cell)
            if (!filter.isActive(data)) continue
            for (indicator in filter.indicators(FilterRenderContext(cell, data, store, filter.name, queryPrefix()))) {
                out.add(filter to indicator)
            }
        }
        return out
    }

    // ---- chrome ----

    /** Funnel trigger for the toolbar actions cluster ("" for always-visible layouts). */
    internal fun triggerHtml(view: View): String {
        if (list.isEmpty() || trigger.isHidden()) return ""
        if (layout == FiltersLayout.ABOVE_CONTENT || layout == FiltersLayout.BELOW_CONTENT) return ""
        val count = activeCount(view)
        val badge = if (count > 0 && trigger.hasBadge()) """<span class="ff-filters-badge" data-ff-filters-count>$count</span>""" else ""
        val popup = if (layout == FiltersLayout.DROPDOWN) "true" else "dialog"
        return """<button type="button" class="${trigger.classes("ff-filters-trigger")}" data-ff-filters-trigger aria-haspopup="$popup" aria-expanded="false" aria-label="${escapeHtml(trigger.label())}"${trigger.attributes()}>${trigger.inner(trigger.iconSvg(FUNNEL))}$badge</button>"""
    }

    /** Dropdown / modal / collapsible panel, rendered after the toolbar form (forms can't nest). */
    internal fun panelHtml(view: View): String {
        if (list.isEmpty()) return ""
        return when (layout) {
            FiltersLayout.DROPDOWN -> {
                val style = panelStyle(defaultWidth())
                """<div class="ff-filters-dropdown" data-ff-filters-panel data-ff-filters-layout="dropdown" role="dialog" aria-label="${escapeHtml(headingText)}" hidden$style>${form(view)}</div>"""
            }
            FiltersLayout.MODAL -> {
                val dialog = Dialog.make(headingText).width(modalWidth()).body(form(view, heading = false)).render(open = true)
                """<div class="ff-filters-modal-mount" data-ff-dialog-mount><div class="ff-table-button-dialog" data-ff-dialog-panel data-ff-filters-panel data-ff-filters-layout="modal" style="display:none" hidden aria-hidden="true">$dialog</div></div>"""
            }
            FiltersLayout.ABOVE_CONTENT_COLLAPSIBLE ->
                """<section class="ff-filters-above" data-ff-filters-panel data-ff-filters-layout="collapsible" hidden>${form(view)}</section>"""
            else -> ""
        }
    }

    internal fun aboveHtml(view: View): String {
        if (list.isEmpty() || layout != FiltersLayout.ABOVE_CONTENT) return ""
        return """<section class="ff-filters-above" data-ff-filters-panel data-ff-filters-layout="above">${form(view)}</section>"""
    }

    internal fun belowHtml(view: View): String {
        if (list.isEmpty() || layout != FiltersLayout.BELOW_CONTENT) return ""
        return """<section class="ff-filters-below" data-ff-filters-panel data-ff-filters-layout="below">${form(view)}</section>"""
    }

    /** Active-filter chips with per-chip remove and remove-all links (soft-nav). */
    internal fun indicatorsHtml(view: View): String {
        if (list.isEmpty() || hideIndicators) return ""
        val active = indicators(view.cell, view.store)
        if (active.isEmpty()) return ""
        val chips = active.joinToString("") { (filter, indicator) ->
            val color = indicator.color()?.let { """ data-ff-color="${escapeHtml(it)}"""" }.orEmpty()
            val remove = if (indicator.isRemovable()) {
                val href = view.href(removedQuery(view.cell, filter.name, indicator.fields()))
                """<a class="ff-filter-indicator-remove" href="${escapeHtml(href)}" data-ff-table-nav data-ff-filter-nav aria-label="Remove filter ${escapeHtml(indicator.label)}">$X</a>"""
            } else {
                ""
            }
            """<span class="ff-filter-indicator" data-ff-filter="${escapeHtml(filter.name)}"$color><span class="ff-filter-indicator-label">${escapeHtml(indicator.label)}</span>$remove</span>"""
        }
        val removeAll = if (removeAllAction.isHidden()) {
            ""
        } else {
            val href = view.href(listOf(queryPrefix() to "1"))
            """<a class="${removeAllAction.classes("ff-filter-indicators-remove-all")}" href="${escapeHtml(href)}" data-ff-table-nav data-ff-filter-nav aria-label="${escapeHtml(removeAllAction.label())}"${removeAllAction.attributes()}>${removeAllAction.inner(removeAllAction.iconSvg(X))}</a>"""
        }
        return """<div class="ff-filter-indicators" data-ff-filter-indicators><span class="ff-filter-indicators-label">Active filters</span><div class="ff-filter-indicators-list">$chips</div>$removeAll</div>"""
    }

    /** Hidden inputs so the toolbar search / per-page form keeps the filter state. */
    internal fun hiddenInputs(cell: Cell): String =
        query(cell).joinToString("") { (key, value) ->
            """<input type="hidden" name="${escapeHtml(key)}" value="${escapeHtml(value)}" data-ff-filter-state />"""
        }

    private fun removedQuery(cell: Cell, name: String, fields: List<String>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out.add(queryPrefix() to "1")
        for (filter in list) {
            val current = data(filter, cell)
            val next = if (filter.name != name) current else filter.applyIndicatorRemoval(current, fields)
            out.addAll(pairs(filter, next))
        }
        return out
    }

    private fun activeCount(view: View): Int = indicators(view.cell, view.store).size

    private fun form(view: View, heading: Boolean = true): String {
        val hidden = view.base.joinToString("") { (key, value) ->
            """<input type="hidden" name="${escapeHtml(key)}" value="${escapeHtml(value)}" />"""
        }
        val prefix = queryPrefix()
        val sentinel = """<input type="hidden" name="$prefix" value="1" data-ff-filters-sentinel />"""
        val reset = if (resetAction.isHidden()) {
            ""
        } else {
            val href = view.href(listOf(prefix to RESET))
            """<a href="${escapeHtml(href)}" class="${resetAction.classes("ff-filters-reset")}" data-ff-filters-reset${resetAction.attributes()}>${resetAction.inner(resetAction.iconSvg(""))}</a>"""
        }
        val headerReset = if (resetPosition == FiltersResetActionPosition.HEADER) reset else ""
        val footerReset = if (resetPosition == FiltersResetActionPosition.FOOTER) reset else ""
        val header = if (heading || headerReset.isNotEmpty()) {
            val title = if (heading) """<span class="ff-filters-heading">${escapeHtml(headingText)}</span>""" else ""
            """<div class="ff-filters-header">$title$headerReset</div>"""
        } else {
            ""
        }
        val apply = if (deferred && !applyAction.isHidden()) {
            """<button type="submit" class="${applyAction.classes("ff-filters-apply")}" data-ff-filters-apply${applyAction.attributes()}>${applyAction.inner(applyAction.iconSvg(""))}</button>"""
        } else {
            ""
        }
        val footer = if (apply.isEmpty() && footerReset.isEmpty()) "" else """<div class="ff-filters-footer">$apply$footerReset</div>"""
        val deselect = if (deselectOnFilter) """ data-ff-deselect-on-filter="true"""" else ""
        val maxHeight = formMaxHeight?.let { """ style="max-height: ${escapeHtml(it)}"""" }.orEmpty()
        val prefixAttr = """ data-ff-filters-prefix="${escapeHtml(prefix)}""""
        return """<form class="ff-filters-form" method="get" data-ff-table-nav data-ff-filters-form data-ff-filters-live="${!deferred}"$deselect$prefixAttr>$hidden$sentinel$header<div class="ff-filters-body"$maxHeight>${fields(view)}</div>$footer</form>"""
    }

    private fun fields(view: View): String {
        val rendered = LinkedHashMap<String, String>()
        val prefix = queryPrefix()
        for (filter in list) {
            if (filter.isHidden(view.cell)) continue
            val context = FilterRenderContext(view.cell, data(filter, view.cell), view.store, filter.name, prefix)
            rendered[filter.name] = filter.renderField(context)
        }
        val placed = HashSet<String>()
        val out = StringBuilder()
        for (section in sections) {
            val inner = section.filterNames().mapNotNull { name -> rendered[name]?.also { placed.add(name) } }
            if (inner.isEmpty()) continue
            val heading = section.heading()?.let { """<legend class="ff-filters-section-heading">${escapeHtml(it)}</legend>""" }.orEmpty()
            val description = section.description()?.let { """<p class="ff-filters-section-description">${escapeHtml(it)}</p>""" }.orEmpty()
            val span = if (section.spansFull()) """ data-ff-span="full"""" else ""
            out.append("""<fieldset class="ff-component ff-filters-section"$span>$heading$description${grid(section.columnCount() ?: 1, inner.joinToString(""))}</fieldset>""")
        }
        val rest = rendered.filterKeys { it !in placed }.values.joinToString("")
        if (sections.isEmpty()) return grid(formColumns, rest)
        return grid(formColumns, out.toString() + rest)
    }

    private fun grid(columns: Int, inner: String): String {
        val layout = ColumnLayout.of(columns)
        return """<div class="ff-schema ff-filters-grid" data-ff-columns="${layout.dataValue()}" style="${layout.styleAttribute()}">$inner</div>"""
    }

    private fun defaultWidth(): String = formWidth ?: when (formColumns) {
        1 -> "xs"
        2 -> "2xl"
        3 -> "4xl"
        else -> "6xl"
    }

    private fun modalWidth(): String = when (formWidth ?: defaultWidth()) {
        "xs", "sm" -> "sm"
        "md", "lg" -> "md"
        "xl", "2xl" -> "lg"
        else -> "xl"
    }

    private fun panelStyle(width: String): String {
        val bits = ArrayList<String>()
        bits.add("--ff-filters-width: ${WIDTH_REM.getValue(width)}")
        return """ style="${bits.joinToString("; ")}""""
    }

    /** Toolbar + table state the filters form and indicator links must carry along. */
    internal class View(
        val cell: Cell,
        val store: RecordStore<*, *>?,
        val base: List<Pair<String, String>>,
    ) {
        fun href(filterQuery: List<Pair<String, String>>): String {
            val all = base + filterQuery
            return "?" + all.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        }

        private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
    }

    companion object {
        /** Default query-string prefix when [queryStringIdentifier] is unset. */
        const val DEFAULT_SENTINEL = "filters"

        /** @see DEFAULT_SENTINEL */
        const val SENTINEL = DEFAULT_SENTINEL

        const val RESET = "reset"
        private val WIDTH_REM = linkedMapOf(
            "xs" to "20rem",
            "sm" to "24rem",
            "md" to "28rem",
            "lg" to "32rem",
            "xl" to "36rem",
            "2xl" to "42rem",
            "3xl" to "48rem",
            "4xl" to "56rem",
            "5xl" to "64rem",
            "6xl" to "72rem",
            "7xl" to "80rem",
        )
        private val WIDTHS = WIDTH_REM.keys
        private val CSS_LENGTH = Regex("\\d+(\\.\\d+)?(px|rem|em|vh|%)")
        private val FUNNEL: String =
            org.konaframework.icons.Icons.svg("heroicon-o-funnel").className("ff-filters-icon").toHtml()
        private val X: String =
            org.konaframework.icons.Icons.svg("heroicon-o-x-mark").className("ff-filters-icon").toHtml()
    }
}
