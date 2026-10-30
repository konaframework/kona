package org.konaframework.firefly.tables

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

/** Built-in form control of a plain [Filter]. */
enum class FilterControl {
    CHECKBOX,
    TOGGLE,
    TEXT,
    SELECT,
}

/** What a filter needs to render its form fields. */
class FilterRenderContext internal constructor(
    val cell: Cell,
    val data: FilterData,
    internal val store: RecordStore<*, *>?,
    private val filterName: String,
    private val queryPrefix: String = TableFilters.DEFAULT_SENTINEL,
) {
    /** Query-string prefix for this table (`filters` or `{id}Filters`). */
    fun queryPrefix(): String = queryPrefix

    /** Query-string name of [field]: `{prefix}.{filter}` for [FilterData.VALUE], else `{prefix}.{filter}.{field}`. */
    fun inputName(field: String = FilterData.VALUE): String =
        if (field == FilterData.VALUE) "$queryPrefix.$filterName" else "$queryPrefix.$filterName.$field"
}

/**
 * Table filter (Filament `Filter`). By default a checkbox: when checked, [query] runs.
 * [toggle] swaps the control, [schema] replaces it with any form fields, and
 * [attribute] constrains a column without a callback.
 *
 * Every filter's query runs inside its own `and (…)` group, so `orWhere` in one filter
 * never widens another. [baseQuery] runs outside that group (scope removal, trashed rows).
 */
open class Filter(val name: String) {
    private val labelValue = TextSetting()
    private val indicatorValue = TextSetting()
    private val defaultValue = ValueSetting()
    private val hiddenFlag = FlagSetting(false)
    private var attribute: String? = null
    private var operator = "="
    private var queryHook: BiConsumer<Any, Any?>? = null
    private var queryUsing: FilterQuery? = null
    private var baseQueryUsing: FilterQuery? = null
    private var options: Map<String, String> = emptyMap()
    private var optionsUsing: Function<Cell, Map<String, String>>? = null
    private val inputExtras = InputAttributes()
    private var control: FilterControl? = null
    private val schemaParts = ArrayList<Component>()
    private var schemaColumns: Int? = null
    private var span: String? = null
    private var excludeOnResolve = false
    private var indicateUsing: Function<FilterData, Any?>? = null
    private var fieldModifier: Consumer<FilterField>? = null

    open fun label(value: String): Filter = apply { labelValue.set(value) }

    open fun label(value: Function<Cell, String>): Filter = apply { labelValue.set(value) }

    /** Fixed indicator text when the filter is active (see [indicateUsing] for full control). */
    open fun indicator(value: String): Filter = apply { indicatorValue.set(value) }

    open fun indicator(value: Function<Cell, String>): Filter = apply { indicatorValue.set(value) }

    /**
     * Default state. Single-control filters take a value (`true`, `"open"`, a list for multiple selects);
     * [schema] filters take a `Map` of field → value.
     */
    open fun default(value: Any?): Filter = apply { defaultValue.set(value) }

    /** Checked by default (Filament `->default()` with no argument). */
    open fun default(): Filter = default(true)

    /** Java spelling of [default] (`default` is a Java keyword). */
    open fun defaultState(value: Any?): Filter = default(value)

    /** Java spelling of [default] with no value (checkbox / toggle on). */
    open fun defaultState(): Filter = default()

    open fun defaultUsing(value: Function<Cell, Any?>): Filter = apply { defaultValue.set(value) }

    open fun attribute(name: String): Filter = apply { attribute = name }

    open fun operator(value: String): Filter = apply { operator = value }

    /** Legacy hook: the second argument is the single value, or the field map of a [schema] filter. */
    open fun query(hook: BiConsumer<Any, Any?>): Filter = apply {
        queryHook = hook
        queryUsing = null
    }

    /** Runs when the filter is active, with the native query and the submitted [FilterData]. */
    open fun queryUsing(hook: FilterQuery): Filter = apply {
        queryUsing = hook
        queryHook = null
    }

    /**
     * Runs on every load, active or not, directly on the outer query (not inside the filter's
     * group) — e.g. `withoutGlobalScope(...)` (Filament `baseQuery`).
     */
    open fun baseQuery(hook: FilterQuery): Filter = apply { baseQueryUsing = hook }

    open fun options(values: Map<String, String>): Filter = apply {
        options = LinkedHashMap(values)
        optionsUsing = null
    }

    open fun options(provider: Function<Cell, Map<String, String>>): Filter = apply { optionsUsing = provider }

    open fun extraInputAttributes(attributes: Map<String, String>): Filter = apply { inputExtras.set(attributes) }

    open fun extraInputAttributes(provider: Function<Cell, Map<String, String>>): Filter = apply { inputExtras.set(provider) }

    /** Checkbox control (default for a plain filter). */
    fun checkbox(): Filter = apply { control = FilterControl.CHECKBOX }

    /** Toggle switch instead of a checkbox. */
    fun toggle(): Filter = apply { control = FilterControl.TOGGLE }

    /** Free-text input; the typed value is passed to [query] / compared to [attribute]. */
    fun textInput(): Filter = apply { control = FilterControl.TEXT }

    /** Tweak the built-in control (label, placeholder, inline, input attributes). */
    open fun modifyFormFieldUsing(modifier: Consumer<FilterField>): Filter = apply { fieldModifier = modifier }

    /**
     * Custom filter form. Field names become `filters.{name}.{field}` in the query string;
     * [queryUsing] receives them as [FilterData]. Accepts any Firefly form component.
     */
    fun schema(vararg components: Component): Filter = apply {
        schemaParts.clear()
        schemaParts.addAll(components)
    }

    /** Grid columns inside a [schema] filter form. */
    fun columns(count: Int): Filter = apply {
        if (count < 1) throw FireflyException("Filter columns must be at least 1")
        schemaColumns = count
    }

    /** Columns this filter spans in the filters form grid. */
    open fun columnSpan(count: Int): Filter = apply { span = count.toString() }

    open fun columnSpanFull(): Filter = apply { span = "full" }

    @JvmOverloads
    open fun hidden(on: Boolean = true): Filter = apply { hiddenFlag.set(on) }

    open fun hidden(predicate: java.util.function.Predicate<Cell>): Filter = apply { hiddenFlag.set(predicate) }

    open fun visible(on: Boolean): Filter = hidden(!on)

    /** Skip this filter when a single record is resolved for an action ([Table.resolveRecord]). */
    @JvmOverloads
    open fun excludeWhenResolvingRecord(on: Boolean = true): Filter = apply { excludeOnResolve = on }

    /**
     * Custom indicators. Return null / empty for none, a `String`, a [FilterIndicator],
     * or a collection of either.
     */
    open fun indicateUsing(provider: Function<FilterData, Any?>): Filter = apply { indicateUsing = provider }

    open fun heading(cell: Cell): String = labelValue.get(cell, humanize(name))

    fun defaultValue(cell: Cell): Any? = defaultValue.get(cell)

    fun isHidden(cell: Cell): Boolean = hiddenFlag.get(cell)

    fun isExcludedWhenResolvingRecord(): Boolean = excludeOnResolve

    fun columnSpan(): String? = span

    fun hasSchema(): Boolean = schemaParts.isNotEmpty()

    /** Effective control of a plain filter: explicit choice, else select with options, else checkbox. */
    open fun control(cell: Cell): FilterControl {
        val chosen = control
        if (chosen != null) return chosen
        return if (resolvedOptions(cell).isEmpty()) FilterControl.CHECKBOX else FilterControl.SELECT
    }

    /** Default state as [FilterData]. */
    fun defaultData(cell: Cell): FilterData = toData(defaultValue(cell))

    /** Normalize a programmatic value (single, list, or schema map) into [FilterData]. */
    open fun toData(value: Any?): FilterData {
        if (value is FilterData) return value
        if (value is Map<*, *> && hasSchema()) {
            return FilterData(value.entries.associate { (key, item) -> key.toString() to item })
        }
        return FilterData.single(value)
    }

    /**
     * Whether [data] narrows the query. Checkbox / toggle filters are inactive when unchecked
     * (`0`, `false`, `off`) or blank.
     */
    open fun isActive(data: FilterData): Boolean {
        if (hasSchema()) return !data.isEmpty()
        if (!data.filled(FilterData.VALUE)) return false
        return when (control(Cell { null })) {
            FilterControl.CHECKBOX, FilterControl.TOGGLE -> data.bool(FilterData.VALUE) != false
            else -> true
        }
    }

    /** Apply one value (or schema map) outside a table: [baseQuery], then the query when active. */
    open fun apply(query: Any, store: RecordStore<*, *>, value: Any?) {
        val data = toData(value)
        applyToBaseQuery(query, store, data)
        if (!isActive(data)) return
        applyToQuery(query, store, data)
    }

    /** Constrain [query] for active [data]; the table wraps this in the filter's own group. */
    open fun applyToQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        val using = queryUsing
        if (using != null) {
            using.apply(query, data)
            return
        }
        val hook = queryHook
        if (hook != null) {
            hook.accept(query, legacyValue(data))
            return
        }
        val column = attribute ?: return
        val value: Any? = when (control(Cell { null })) {
            FilterControl.CHECKBOX, FilterControl.TOGGLE -> if (data.bool(FilterData.VALUE) == true) true else data.value()
            else -> data.value()
        }
        @Suppress("UNCHECKED_CAST")
        (store as RecordStore<Any?, Any?>).constrain(query, column, operator, value)
    }

    /** Runs [baseQuery] on the outer query. */
    open fun applyToBaseQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        baseQueryUsing?.apply(query, data)
    }

    /** Chips with table context (store-backed labels); defaults to [indicators] `(cell, data)`. */
    open fun indicators(context: FilterRenderContext): List<FilterIndicator> = indicators(context.cell, context.data)

    /**
     * Apply an indicator × removal to [data]. Default drops the named schema fields (or the whole
     * filter when [fields] is empty). Query-builder chips pass rule coordinates here.
     */
    open fun applyIndicatorRemoval(data: FilterData, fields: List<String>): FilterData =
        if (fields.isEmpty()) FilterData(emptyMap()) else data.without(fields)

    /** Active-filter chips for [data]; empty when inactive. */
    open fun indicators(cell: Cell, data: FilterData): List<FilterIndicator> {
        val custom = indicateUsing
        if (custom != null) return normalizeIndicators(custom.apply(data))
        if (!isActive(data)) return emptyList()
        if (indicatorValue.isAssigned()) {
            return listOf(FilterIndicator.make(indicatorValue.get(cell, "")))
        }
        return listOf(FilterIndicator.make(defaultIndicatorLabel(cell, data)))
    }

    protected open fun defaultIndicatorLabel(cell: Cell, data: FilterData): String {
        if (hasSchema()) return heading(cell)
        return when (control(cell)) {
            FilterControl.CHECKBOX, FilterControl.TOGGLE -> heading(cell)
            FilterControl.SELECT -> {
                val choices = resolvedOptions(cell)
                val labels = data.values().map { choices[it] ?: it }
                "${heading(cell)}: ${labels.joinToString(", ")}"
            }
            FilterControl.TEXT -> "${heading(cell)}: ${data.string(FilterData.VALUE).orEmpty()}"
        }
    }

    /** Form field markup inside the filters form. */
    open fun renderField(context: FilterRenderContext): String {
        val field = FilterField(name)
        fieldModifier?.accept(field)
        val body = if (hasSchema()) renderSchema(context) else renderControl(context, field)
        val spanAttr = span?.let { """ data-ff-span="${escapeHtml(it)}"""" }.orEmpty()
        val spanStyle = span?.toIntOrNull()?.let { """ style="--ff-span: $it"""" }.orEmpty()
        val helper = field.helperText?.let { """<p class="ff-helper">${escapeHtml(it)}</p>""" }.orEmpty()
        return """<div class="ff-component ff-filter-field" data-ff-filter="${escapeHtml(name)}" data-ff-filter-kind="${kind(context.cell)}"$spanAttr$spanStyle>$body$helper</div>"""
    }

    /** Legacy: field markup for one value (no table context). */
    open fun render(cell: Cell, value: Any?): String =
        renderField(FilterRenderContext(cell, toData(value), null, name))

    protected open fun kind(cell: Cell): String =
        if (hasSchema()) "schema" else control(cell).name.lowercase()

    protected open fun renderControl(context: FilterRenderContext, field: FilterField): String {
        val cell = context.cell
        val label = field.labelText ?: heading(cell)
        val extra = inputExtras.markup(cell) + extraAttributes(field)
        val inputName = escapeHtml(context.inputName())
        return when (control(cell)) {
            FilterControl.CHECKBOX -> {
                val checked = if (context.data.bool(FilterData.VALUE) == true) " checked" else ""
                val labelHtml = if (field.hiddenLabel) "" else """<span class="ff-filter-check-label">${escapeHtml(label)}</span>"""
                """<label class="ff-checkbox-wrap ff-filter-check" data-ff-inline="${field.inline ?: true}"><input class="ff-checkbox" type="checkbox" name="$inputName" value="1"$checked$extra /><span class="ff-checkbox-box" aria-hidden="true"></span>$labelHtml</label>"""
            }
            FilterControl.TOGGLE -> {
                val on = context.data.bool(FilterData.VALUE) == true
                val stored = if (on) "1" else "0"
                val labelHtml = if (field.hiddenLabel) "" else """<span class="ff-toggle-label">${escapeHtml(label)}</span>"""
                """<span class="ff-toggle-wrap ff-filter-toggle" data-ff-inline="${field.inline ?: true}"><input type="hidden" class="ff-toggle-input" name="$inputName" value="$stored" data-ff-toggle-value /><button type="button" class="ff-toggle" data-ff-toggle role="switch" aria-pressed="$on" aria-checked="$on" aria-label="${escapeHtml(label)}"$extra><span class="ff-toggle-track"><span class="ff-toggle-thumb"></span></span></button>$labelHtml</span>"""
            }
            FilterControl.TEXT -> {
                val current = context.data.string(FilterData.VALUE).orEmpty()
                val placeholder = field.placeholderText?.let { """ placeholder="${escapeHtml(it)}"""" }.orEmpty()
                fieldLabel(label, field) +
                    """<input class="ff-input" type="text" name="$inputName" value="${escapeHtml(current)}" data-ff-filter-input$placeholder$extra />"""
            }
            FilterControl.SELECT -> fieldLabel(label, field) +
                FilterSelectMarkup.native(inputName, resolvedOptions(cell), context.data.values().toSet(), field.placeholderText ?: "All", true, extra)
        }
    }

    protected fun fieldLabel(label: String, field: FilterField): String =
        if (field.hiddenLabel) "" else """<label class="ff-label">${escapeHtml(label)}</label>"""

    protected fun extraAttributes(field: FilterField): String =
        field.attributes.entries.joinToString("") { (key, value) -> """ ${escapeHtml(key)}="${escapeHtml(value)}"""" }

    protected fun inputExtrasMarkup(cell: Cell): String = inputExtras.markup(cell)

    private fun renderSchema(context: FilterRenderContext): String {
        val prefix = context.queryPrefix()
        val schema = Schema.make().statePath("$prefix.$name")
        val columns = schemaColumns
        if (columns != null) schema.columns(columns)
        schema.components(*schemaParts.toTypedArray())
        schema.fill(context.data.toMap().mapKeys { (field, _) -> "$prefix.$name.$field" })
        val heading = """<p class="ff-filter-heading">${escapeHtml(heading(context.cell))}</p>"""
        return heading + schema.render()
    }

    protected fun resolvedOptions(cell: Cell): Map<String, String> {
        val provider = optionsUsing
        if (provider != null) return provider.apply(cell)
        return options
    }

    protected fun attributeName(): String? = attribute

    protected fun hasCustomQuery(): Boolean = queryUsing != null || queryHook != null

    protected fun hasIndicatorOverride(): Boolean = indicateUsing != null || indicatorValue.isAssigned()

    protected fun operatorName(): String = operator

    private fun legacyValue(data: FilterData): Any? {
        if (hasSchema()) return data.toMap()
        return data.value()
    }

    private fun normalizeIndicators(raw: Any?): List<FilterIndicator> = when (raw) {
        null -> emptyList()
        is FilterIndicator -> listOf(raw)
        is Collection<*> -> raw.flatMap { normalizeIndicators(it) }
        else -> raw.toString().let { if (it.isBlank()) emptyList() else listOf(FilterIndicator.make(it)) }
    }

    companion object {
        private val configurers = HashMap<Class<*>, Consumer<Filter>?>()

        @JvmStatic
        fun make(name: String): Filter = finish(Filter(name))

        @JvmStatic
        fun configureUsing(type: Class<out Filter>, block: Consumer<Filter>?) {
            configurers[type] = block
        }

        @JvmStatic
        fun configureUsing(block: Consumer<Filter>?) {
            configureUsing(Filter::class.java, block)
        }

        internal fun <T : Filter> finish(filter: T): T {
            configurers[filter.javaClass]?.accept(filter)
            return filter
        }

        internal fun humanize(name: String): String {
            val words = name.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim()
            if (words.isEmpty()) return name
            return words.substring(0, 1).uppercase() + words.substring(1)
        }
    }
}
