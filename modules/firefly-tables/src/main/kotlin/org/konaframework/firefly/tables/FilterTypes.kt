package org.konaframework.firefly.tables

import org.konaframework.firefly.support.FireflyException
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

/**
 * Dropdown filter (Filament `SelectFilter`): static [options] or a [relationship],
 * single or [multiple], native or searchable. Constrains [attribute] (default: the filter name)
 * with `=` / `in`, or the relationship with `whereHas` / `whereIn` on the foreign key.
 */
open class SelectFilter protected constructor(name: String) : Filter(name) {
    private var multipleFlag = false
    private var searchableFlag = false
    private var preloadFlag = false
    private var nativeFlag: Boolean? = null
    private var placeholderText: String? = null
    private var selectablePlaceholderFlag = true
    private var optionsLimitCount: Int? = null
    private var relationName: String? = null
    private var relationTitle = "name"
    private var relationModify: Consumer<Any>? = null
    private var emptyOptionFlag = false
    private var emptyOptionLabel = "None"
    private var searchResultsUsing: java.util.function.BiFunction<String, Cell, Map<String, String>>? = null
    private var optionLabelUsing: java.util.function.BiFunction<String, Cell, String?>? = null

    @JvmOverloads
    fun multiple(on: Boolean = true): SelectFilter = apply { multipleFlag = on }

    /** Type-ahead search in the dropdown; relationship filters search the related table. */
    @JvmOverloads
    fun searchable(on: Boolean = true): SelectFilter = apply { searchableFlag = on }

    /** Relationship filters: embed the first [optionsLimit] options instead of waiting for a search. */
    @JvmOverloads
    fun preload(on: Boolean = true): SelectFilter = apply { preloadFlag = on }

    /** Plain `<select>` (default unless [searchable] or [multiple]). */
    @JvmName("useNative")
    @JvmOverloads
    fun native(on: Boolean = true): SelectFilter = apply { nativeFlag = on }

    fun placeholder(value: String): SelectFilter = apply { placeholderText = value }

    /** When false, the single-select has no "All" option (a value is always chosen). */
    fun selectablePlaceholder(on: Boolean): SelectFilter = apply { selectablePlaceholderFlag = on }

    fun optionsLimit(count: Int): SelectFilter = apply {
        if (count < 1) throw FireflyException("Options limit must be at least 1")
        optionsLimitCount = count
    }

    fun relationship(name: String, titleAttribute: String): SelectFilter = relationship(name, titleAttribute, null)

    /** [modify] receives the related query (`ModelBuilder` / ignored for memory tables). */
    fun relationship(name: String, titleAttribute: String, modify: Consumer<Any>?): SelectFilter = apply {
        relationName = name
        relationTitle = titleAttribute
        relationModify = modify
    }

    /** Add a "None" choice matching rows with no value / no related record. */
    @JvmOverloads
    fun hasEmptyOption(on: Boolean = true): SelectFilter = apply { emptyOptionFlag = on }

    fun emptyRelationshipOptionLabel(label: String): SelectFilter = apply {
        emptyOptionLabel = label
        emptyOptionFlag = true
    }

    /**
     * Options from neither a static map nor a [relationship] (Filament `getSearchResultsUsing`).
     * Receives the search string and table [Cell]; pair with [searchable] so the combobox
     * hits `form-options`. [getOptionLabelUsing] labels selected keys that are not in the search page.
     */
    fun getSearchResultsUsing(hook: java.util.function.BiFunction<String, Cell, Map<String, String>>): SelectFilter =
        apply { searchResultsUsing = hook }

    /** Label for a selected option key when it is not in the current search results. */
    fun getOptionLabelUsing(hook: java.util.function.BiFunction<String, Cell, String?>): SelectFilter =
        apply { optionLabelUsing = hook }

    // Covariant setters so `SelectFilter.make(...).label(...).<SelectFilter setter>()` chains.
    override fun label(value: String): SelectFilter = apply { super.label(value) }

    override fun label(value: Function<Cell, String>): SelectFilter = apply { super.label(value) }

    override fun indicator(value: String): SelectFilter = apply { super.indicator(value) }

    override fun default(value: Any?): SelectFilter = apply { super.default(value) }

    override fun attribute(name: String): SelectFilter = apply { super.attribute(name) }

    override fun query(hook: BiConsumer<Any, Any?>): SelectFilter = apply { super.query(hook) }

    override fun queryUsing(hook: FilterQuery): SelectFilter = apply { super.queryUsing(hook) }

    override fun baseQuery(hook: FilterQuery): SelectFilter = apply { super.baseQuery(hook) }

    override fun indicateUsing(provider: Function<FilterData, Any?>): SelectFilter = apply { super.indicateUsing(provider) }

    override fun modifyFormFieldUsing(modifier: Consumer<FilterField>): SelectFilter = apply { super.modifyFormFieldUsing(modifier) }

    override fun columnSpan(count: Int): SelectFilter = apply { super.columnSpan(count) }

    override fun columnSpanFull(): SelectFilter = apply { super.columnSpanFull() }

    override fun visible(on: Boolean): SelectFilter = apply { super.visible(on) }

    override fun hidden(on: Boolean): SelectFilter = apply { super.hidden(on) }

    override fun indicator(value: Function<Cell, String>): SelectFilter = apply { super.indicator(value) }

    override fun default(): SelectFilter = apply { super.default() }

    override fun defaultState(value: Any?): SelectFilter = apply { super.defaultState(value) }

    override fun defaultState(): SelectFilter = apply { super.defaultState() }

    override fun defaultUsing(value: Function<Cell, Any?>): SelectFilter = apply { super.defaultUsing(value) }

    override fun operator(value: String): SelectFilter = apply { super.operator(value) }

    override fun options(values: Map<String, String>): SelectFilter = apply { super.options(values) }

    override fun options(provider: Function<Cell, Map<String, String>>): SelectFilter = apply { super.options(provider) }

    override fun extraInputAttributes(attributes: Map<String, String>): SelectFilter = apply { super.extraInputAttributes(attributes) }

    override fun extraInputAttributes(provider: Function<Cell, Map<String, String>>): SelectFilter = apply { super.extraInputAttributes(provider) }

    override fun hidden(predicate: java.util.function.Predicate<Cell>): SelectFilter = apply { super.hidden(predicate) }

    override fun excludeWhenResolvingRecord(on: Boolean): SelectFilter = apply { super.excludeWhenResolvingRecord(on) }

    fun isMultiple(): Boolean = multipleFlag

    fun isSearchable(): Boolean = searchableFlag

    fun relationshipName(): String? = relationName

    override fun control(cell: Cell): FilterControl = FilterControl.SELECT

    override fun isActive(data: FilterData): Boolean = data.values().isNotEmpty()

    override fun applyToQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        if (hasCustomQuery()) {
            super.applyToQuery(query, store, data)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val typed = store as RecordStore<Any?, Any?>
        val values = data.values()
        val wantsEmpty = emptyOptionFlag && EMPTY in values
        val keys = values.filter { it != EMPTY }
        val relation = relationName
        if (keys.isNotEmpty()) {
            when {
                relation != null -> typed.constrainRelationship(query, relation, keys, false)
                multipleFlag || keys.size > 1 -> typed.constrain(query, column(), "in", keys)
                else -> typed.constrain(query, column(), operatorName(), keys.first())
            }
        }
        if (!wantsEmpty) return
        typed.group(query, keys.isNotEmpty()) { inner ->
            if (relation != null) typed.constrainRelationship(inner, relation, null, true) else typed.constrain(inner, column(), "null", null)
        }
    }

    /** Options shown in the dropdown (static, relationship, or [getSearchResultsUsing]). */
    @JvmOverloads
    fun options(cell: Cell, store: RecordStore<*, *>?, search: String? = null, keys: Collection<String>? = null): Map<String, String> {
        val searchHook = searchResultsUsing
        if (searchHook != null) {
            return customOptions(cell, search.orEmpty(), keys)
        }
        val relation = relationName
        val base: Map<String, String> = if (relation == null || store == null) {
            val all = resolvedOptions(cell)
            val needle = search?.lowercase().orEmpty()
            val matched = if (needle.isEmpty()) all else all.filter { (key, label) ->
                label.lowercase().contains(needle) || key.lowercase().contains(needle)
            }
            if (keys != null) matched.filterKeys { it in keys } else matched
        } else {
            store.relationshipOptions(relation, relationTitle, relationModify, search, if (keys == null) limit() else null, keys)
        }
        if (!emptyOptionFlag || keys != null) return base
        val out = LinkedHashMap<String, String>()
        out[EMPTY] = emptyOptionLabel
        out.putAll(base)
        return out
    }

    private fun customOptions(cell: Cell, search: String, keys: Collection<String>?): Map<String, String> {
        val found = LinkedHashMap(searchResultsUsing!!.apply(search, cell))
        if (keys != null) {
            val labels = optionLabelUsing
            val out = LinkedHashMap<String, String>()
            for (key in keys) {
                if (key == EMPTY) {
                    out[EMPTY] = emptyOptionLabel
                    continue
                }
                out[key] = found[key] ?: labels?.apply(key, cell) ?: key
            }
            return out
        }
        if (!emptyOptionFlag) return found
        val out = LinkedHashMap<String, String>()
        out[EMPTY] = emptyOptionLabel
        out.putAll(found)
        return out
    }

    override fun indicators(context: FilterRenderContext): List<FilterIndicator> {
        if (hasIndicatorOverride() || !isActive(context.data)) return super.indicators(context)
        val selected = context.data.values()
        val keys = selected.filter { it != EMPTY }
        val labels = if (keys.isEmpty()) emptyMap() else options(context.cell, context.store, keys = keys)
        val text = selected.map { if (it == EMPTY) emptyOptionLabel else labels[it] ?: it }
        return listOf(FilterIndicator.make("${heading(context.cell)}: ${text.joinToString(", ")}"))
    }

    override fun renderControl(context: FilterRenderContext, field: FilterField): String {
        val cell = context.cell
        val label = fieldLabel(field.labelText ?: heading(cell), field)
        val selected = context.data.values().toSet()
        val placeholder = field.placeholderText ?: placeholderText ?: if (multipleFlag) "Select options" else "All"
        val extra = inputExtrasMarkup(cell) + extraAttributes(field)
        val name = escapeHtml(context.inputName())
        val customRemote = searchResultsUsing != null && searchableFlag
        val relationRemote = relationName != null && searchableFlag && context.store != null
        val remote = customRemote || relationRemote
        val lazyChoices = (relationName != null || searchResultsUsing != null) && !preloadFlag && searchableFlag
        val choices = if (lazyChoices) {
            if (selected.isEmpty()) emptyMap() else options(cell, context.store, keys = selected - EMPTY)
        } else {
            options(cell, context.store)
        }
        val native = nativeFlag ?: !(searchableFlag || multipleFlag)
        if (native) {
            return label + FilterSelectMarkup.native(name, choices, selected, placeholder, selectablePlaceholderFlag && !multipleFlag, extra, multipleFlag)
        }
        return label + FilterSelectMarkup.combobox(
            name,
            choices,
            selected,
            placeholder,
            multiple = multipleFlag,
            searchable = searchableFlag,
            allowClear = selectablePlaceholderFlag && !multipleFlag,
            remoteRelation = if (relationRemote) relationName else null,
            remote = remote,
            extra = extra,
        )
    }

    private fun column(): String = attributeName() ?: name

    private fun limit(): Int = optionsLimitCount ?: DEFAULT_LIMIT

    companion object {
        /** Value of the [hasEmptyOption] choice. */
        const val EMPTY = "__empty"
        private const val DEFAULT_LIMIT = 50

        @JvmStatic
        fun make(name: String): SelectFilter = finish(SelectFilter(name))

        @JvmStatic
        fun configureUsing(block: Consumer<SelectFilter>?) {
            @Suppress("UNCHECKED_CAST")
            Filter.configureUsing(SelectFilter::class.java, block as Consumer<Filter>?)
        }
    }
}

/**
 * Three-state filter (Filament `TernaryFilter`): placeholder (no constraint), true, false.
 * Default query compares [attribute] (the filter name) to true / false; [nullable] switches to
 * `not null` / `null`; [queries] replaces both with callbacks.
 */
open class TernaryFilter protected constructor(name: String) : Filter(name) {
    private var placeholderText: String? = null
    private var trueText: String? = null
    private var falseText: String? = null
    private var nullableFlag = false
    private var trueQuery: FilterQuery? = null
    private var falseQuery: FilterQuery? = null
    private var blankQuery: FilterQuery? = null

    fun placeholder(value: String): TernaryFilter = apply { placeholderText = value }

    fun trueLabel(value: String): TernaryFilter = apply { trueText = value }

    fun falseLabel(value: String): TernaryFilter = apply { falseText = value }

    /** True → `attribute is not null`, false → `attribute is null` (e.g. `email_verified_at`). */
    @JvmOverloads
    fun nullable(on: Boolean = true): TernaryFilter = apply { nullableFlag = on }

    fun queries(whenTrue: FilterQuery, whenFalse: FilterQuery): TernaryFilter = queries(whenTrue, whenFalse, null)

    /** [whenBlank] runs when the placeholder is chosen (no selection). */
    fun queries(whenTrue: FilterQuery, whenFalse: FilterQuery, whenBlank: FilterQuery?): TernaryFilter = apply {
        trueQuery = whenTrue
        falseQuery = whenFalse
        blankQuery = whenBlank
    }

    // Covariant setters so `TernaryFilter.make(...).label(...).<TernaryFilter setter>()` chains.
    override fun label(value: String): TernaryFilter = apply { super.label(value) }

    override fun label(value: Function<Cell, String>): TernaryFilter = apply { super.label(value) }

    override fun indicator(value: String): TernaryFilter = apply { super.indicator(value) }

    override fun default(value: Any?): TernaryFilter = apply { super.default(value) }

    override fun attribute(name: String): TernaryFilter = apply { super.attribute(name) }

    override fun query(hook: BiConsumer<Any, Any?>): TernaryFilter = apply { super.query(hook) }

    override fun queryUsing(hook: FilterQuery): TernaryFilter = apply { super.queryUsing(hook) }

    override fun baseQuery(hook: FilterQuery): TernaryFilter = apply { super.baseQuery(hook) }

    override fun indicateUsing(provider: Function<FilterData, Any?>): TernaryFilter = apply { super.indicateUsing(provider) }

    override fun modifyFormFieldUsing(modifier: Consumer<FilterField>): TernaryFilter = apply { super.modifyFormFieldUsing(modifier) }

    override fun columnSpan(count: Int): TernaryFilter = apply { super.columnSpan(count) }

    override fun columnSpanFull(): TernaryFilter = apply { super.columnSpanFull() }

    override fun visible(on: Boolean): TernaryFilter = apply { super.visible(on) }

    override fun hidden(on: Boolean): TernaryFilter = apply { super.hidden(on) }

    override fun indicator(value: Function<Cell, String>): TernaryFilter = apply { super.indicator(value) }

    override fun default(): TernaryFilter = apply { super.default() }

    override fun defaultState(value: Any?): TernaryFilter = apply { super.defaultState(value) }

    override fun defaultState(): TernaryFilter = apply { super.defaultState() }

    override fun defaultUsing(value: Function<Cell, Any?>): TernaryFilter = apply { super.defaultUsing(value) }

    override fun operator(value: String): TernaryFilter = apply { super.operator(value) }

    override fun options(values: Map<String, String>): TernaryFilter = apply { super.options(values) }

    override fun options(provider: Function<Cell, Map<String, String>>): TernaryFilter = apply { super.options(provider) }

    override fun extraInputAttributes(attributes: Map<String, String>): TernaryFilter = apply { super.extraInputAttributes(attributes) }

    override fun extraInputAttributes(provider: Function<Cell, Map<String, String>>): TernaryFilter = apply { super.extraInputAttributes(provider) }

    override fun hidden(predicate: java.util.function.Predicate<Cell>): TernaryFilter = apply { super.hidden(predicate) }

    override fun excludeWhenResolvingRecord(on: Boolean): TernaryFilter = apply { super.excludeWhenResolvingRecord(on) }

    protected open fun placeholderLabel(): String = placeholderText ?: "All"

    protected open fun trueLabelText(): String = trueText ?: "Yes"

    protected open fun falseLabelText(): String = falseText ?: "No"

    protected open fun trueToken(): String = "true"

    protected open fun falseToken(): String = "false"

    /** true / false / null (placeholder) from a submitted value. */
    open fun state(data: FilterData): Boolean? {
        val raw = data.string(FilterData.VALUE) ?: return null
        if (raw == trueToken()) return true
        if (raw == falseToken()) return false
        return data.bool(FilterData.VALUE)
    }

    override fun control(cell: Cell): FilterControl = FilterControl.SELECT

    override fun kind(cell: Cell): String = "ternary"

    override fun isActive(data: FilterData): Boolean = state(data) != null

    override fun applyToQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        if (hasCustomQuery()) {
            super.applyToQuery(query, store, data)
            return
        }
        val on = state(data) == true
        val callback = if (on) trueQuery else falseQuery
        if (callback != null) {
            callback.apply(query, data)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val typed = store as RecordStore<Any?, Any?>
        val column = attributeName() ?: name
        when {
            nullableFlag && on -> typed.constrain(query, column, "not-null", null)
            nullableFlag -> typed.constrain(query, column, "null", null)
            else -> typed.constrain(query, column, "=", on)
        }
    }

    override fun applyToBaseQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        super.applyToBaseQuery(query, store, data)
        val blank = blankQuery ?: return
        if (isActive(data)) return
        @Suppress("UNCHECKED_CAST")
        (store as RecordStore<Any?, Any?>).group(query, false) { inner -> blank.apply(inner as Any, data) }
    }

    override fun defaultIndicatorLabel(cell: Cell, data: FilterData): String {
        val text = if (state(data) == true) trueLabelText() else falseLabelText()
        return "${heading(cell)}: $text"
    }

    override fun renderControl(context: FilterRenderContext, field: FilterField): String {
        val label = field.labelText ?: heading(context.cell)
        val name = escapeHtml(context.inputName())
        val current = state(context.data)
        val extra = inputExtrasMarkup(context.cell) + extraAttributes(field)
        val options = listOf(
            Triple("", placeholderLabel(), current == null),
            Triple(trueToken(), trueLabelText(), current == true),
            Triple(falseToken(), falseLabelText(), current == false),
        )
        val buttons = options.joinToString("") { (value, text, checked) ->
            val mark = if (checked) " checked" else ""
            """<label class="ff-filter-ternary-option"><input type="radio" name="$name" value="${escapeHtml(value)}"$mark$extra /><span>${escapeHtml(text)}</span></label>"""
        }
        return fieldLabel(label, field) +
            """<div class="ff-filter-ternary" role="radiogroup" aria-label="${escapeHtml(label)}">$buttons</div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): TernaryFilter = finish(TernaryFilter(name))
    }
}

/**
 * Soft-deleted rows (Filament `TrashedFilter`): without (default), with, or only trashed.
 * Runs on the outer query, so Cadence `withTrashed()` / `onlyTrashed()` reach the model scope.
 */
open class TrashedFilter protected constructor() : TernaryFilter("trashed") {
    init {
        label("Deleted records")
    }

    override fun placeholderLabel(): String = "Without deleted records"

    override fun trueLabelText(): String = "With deleted records"

    override fun falseLabelText(): String = "Only deleted records"

    override fun trueToken(): String = "with"

    override fun falseToken(): String = "only"

    override fun state(data: FilterData): Boolean? {
        if (data.string(FilterData.VALUE) == "without") return null
        return super.state(data)
    }

    override fun applyToQuery(query: Any, store: RecordStore<*, *>, data: FilterData) = Unit

    override fun applyToBaseQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        super.applyToBaseQuery(query, store, data)
        val mode = when (state(data)) {
            true -> "with"
            false -> "only"
            null -> "without"
        }
        @Suppress("UNCHECKED_CAST")
        (store as RecordStore<Any?, Any?>).trashed(query, mode)
    }

    companion object {
        @JvmStatic
        fun make(): TrashedFilter = finish(TrashedFilter())
    }
}

/** Shared `<select>` / combobox markup for select-shaped filters (matches Firefly form chrome). */
internal object FilterSelectMarkup {
    private val OPTION_CHECK: String =
        org.konaframework.icons.Icons.svg("heroicon-o-check").className("ff-icon").toHtml()

    fun native(
        name: String,
        choices: Map<String, String>,
        selected: Set<String>,
        placeholder: String,
        withPlaceholder: Boolean,
        extra: String,
        multiple: Boolean = false,
    ): String = buildString {
        append("""<select class="ff-input" name="$name" data-ff-filter-input${if (multiple) " multiple" else ""}$extra>""")
        if (withPlaceholder) append("""<option value="">${escapeHtml(placeholder)}</option>""")
        for ((value, label) in choices) {
            val mark = if (value in selected) " selected" else ""
            append("""<option value="${escapeHtml(value)}"$mark>${escapeHtml(label)}</option>""")
        }
        append("</select>")
    }

    /** Same shell as the form `Select` combobox so chrome opens, filters, and chips it without Alpine. */
    fun combobox(
        name: String,
        choices: Map<String, String>,
        selected: Set<String>,
        placeholder: String,
        multiple: Boolean,
        searchable: Boolean,
        allowClear: Boolean,
        remoteRelation: String?,
        remote: Boolean,
        extra: String,
    ): String = buildString {
        val stored = selected.joinToString(",")
        val display = if (multiple) "" else selected.firstOrNull()?.let { choices[it] ?: it }.orEmpty()
        val remoteAttr = when {
            remoteRelation != null -> """ data-ff-relationship="${escapeHtml(remoteRelation)}""""
            remote -> """ data-ff-remote="true""""
            else -> ""
        }
        val state = if (stored.isEmpty()) "" else """ data-ff-state="${escapeHtml(stored)}""""
        append(
            """<div class="ff-combobox ff-filter-combobox" data-ff-combobox data-ff-path="$name" data-ff-searchable="$searchable" data-ff-multiple="$multiple" data-ff-preload="true" data-ff-empty="No results found" data-ff-searching="Searching" data-ff-debounce="300"$remoteAttr$state>""",
        )
        append("""<div class="ff-combobox-field" data-ff-combobox-open>""")
        if (multiple) {
            append("""<div class="ff-combobox-chips" data-ff-combobox-chips>""")
            for (value in selected) {
                val label = escapeHtml(choices[value] ?: value)
                append(
                    """<span class="ff-combobox-chip" data-ff-value="${escapeHtml(value)}" data-ff-label="$label"><span class="ff-combobox-chip-label">$label</span><button type="button" class="ff-combobox-chip-remove" data-ff-combobox-remove aria-label="Remove $label">&times;</button></span>""",
                )
            }
            append("</div>")
        }
        val multiClass = if (multiple) " ff-combobox-display--multi" else ""
        val readonly = if (searchable) "" else " readonly"
        append(
            """<input class="ff-input ff-combobox-display$multiClass" type="text" role="combobox" aria-expanded="false" aria-autocomplete="list" autocomplete="off" placeholder="${escapeHtml(placeholder)}" value="${escapeHtml(display)}" data-ff-combobox-display$readonly$extra />""",
        )
        append("""<span class="ff-combobox-caret" aria-hidden="true"></span></div>""")
        append("""<input type="hidden" name="$name" value="${escapeHtml(stored)}" data-ff-combobox-value data-ff-filter-input />""")
        append("""<div class="ff-combobox-panel" data-ff-combobox-panel hidden><ul class="ff-options" data-ff-combobox-options role="listbox">""")
        if (allowClear) {
            append(option("", placeholder, selected.isEmpty()))
        }
        for ((value, label) in choices) append(option(value, label, value in selected))
        append("</ul>")
        val emptyHidden = if (choices.isEmpty() && !allowClear) "" else " hidden"
        append("""<div class="ff-combobox-empty" data-ff-combobox-empty$emptyHidden>No results found</div></div></div>""")
    }

    private fun option(value: String, label: String, on: Boolean): String {
        val css = if (on) "ff-option ff-selected" else "ff-option"
        return """<li class="$css" role="option" aria-selected="$on" data-ff-value="${escapeHtml(value)}" data-ff-label="${escapeHtml(label)}"><span class="ff-option-text">${escapeHtml(label)}</span><span class="ff-option-check" aria-hidden="true">$OPTION_CHECK</span></li>"""
    }
}
