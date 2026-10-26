package org.konaframework.firefly.forms

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

data class FieldOption(val value: String, val label: String, val enabled: Boolean, val description: String?)

data class RelationBinding(val name: String, val titleAttribute: String, val modify: Consumer<Any?>?)

internal class OptionSource {
    private var literal: LinkedHashMap<String, String> = linkedMapOf()
    private var using: Function<Get, Map<String, String>>? = null

    fun set(map: Map<String, String>) {
        literal = LinkedHashMap(map)
        using = null
    }

    fun set(provider: Function<Get, Map<String, String>>) {
        using = provider
        literal = linkedMapOf()
    }

    fun get(get: Get): Map<String, String> {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }

    fun isClosure(): Boolean = using != null
}

open class Select protected constructor(name: String) : Field<Select>(name) {
    private val options = OptionSource()
    private val descriptions = LinkedHashMap<String, String>()
    private var searchableFlag: Boolean = true
    private var multipleFlag: Boolean = false
    private var preloadFlag: Boolean = true
    private var nativeFlag: Boolean = false
    private var allowHtmlFlag: Boolean = false
    private var selectablePlaceholderFlag: Boolean = true
    private var optionsLimitCount: Int? = null
    private var maxItemsCount: Int? = null
    private var searchDebounceMs: Int = 300
    private var positionText: String = "bottom"
    private var loadingMessageText: String = "Loading"
    private var searchingMessageText: String = "Searching"
    private var emptyMessageText: String = "No results"
    private var searchPromptText: String = "Search"
    private var labelUsing: Function<String, String>? = null
    private var searchUsing: Function<String, Map<String, String>>? = null
    private var disableWhen: Predicate<String>? = null
    private var relation: RelationBinding? = null
    private var relationNameUsing: Function<Get, String>? = null
    private var relationTitleUsing: Function<Get, String>? = null
    private var recordLabel: Function<Any?, String>? = null
    private var createForm: Schema? = null
    private var createAction: Action? = null
    private var editForm: Schema? = null
    private var createUsing: Function<Map<String, Any?>, String>? = null

    fun options(map: Map<String, String>): Select = apply { options.set(map) }

    fun options(provider: Function<Get, Map<String, String>>): Select = apply { options.set(provider) }

    fun optionsFrom(records: List<Map<String, Any?>>, title: String, key: String): Select = apply {
        val map = LinkedHashMap<String, String>()
        for (record in records) {
            val id = record[key] ?: continue
            val label = record[title] ?: continue
            map[id.toString()] = label.toString()
        }
        options.set(map)
    }

    fun descriptions(map: Map<String, String>): Select = apply {
        descriptions.clear()
        descriptions.putAll(map)
    }

    fun searchable(on: Boolean): Select = apply { searchableFlag = on }

    fun multiple(on: Boolean): Select = apply { multipleFlag = on }

    fun preload(on: Boolean): Select = apply { preloadFlag = on }

    @JvmName("useNative")
    fun native(): Select = native(true)

    @JvmName("useNative")
    fun native(on: Boolean): Select = apply { nativeFlag = on }

    fun isNative(): Boolean = nativeFlag

    fun allowHtml(on: Boolean): Select = apply { allowHtmlFlag = on }

    fun selectablePlaceholder(on: Boolean): Select = apply { selectablePlaceholderFlag = on }

    fun optionsLimit(count: Int): Select = apply {
        if (count < 1) throw FireflyException("Options limit must be at least 1")
        optionsLimitCount = count
    }

    fun maxItems(count: Int): Select = apply {
        if (count < 1) throw FireflyException("Max items must be at least 1")
        maxItemsCount = count
    }

    fun searchDebounce(milliseconds: Int): Select = apply { searchDebounceMs = milliseconds }

    fun position(value: String): Select = apply { positionText = value }

    fun loadingMessage(value: String): Select = apply { loadingMessageText = value }

    fun searchingMessage(value: String): Select = apply { searchingMessageText = value }

    fun noSearchResultsMessage(value: String): Select = apply { emptyMessageText = value }

    fun searchPrompt(value: String): Select = apply { searchPromptText = value }

    fun getOptionLabelUsing(provider: Function<String, String>): Select = apply { labelUsing = provider }

    fun getSearchResultsUsing(provider: Function<String, Map<String, String>>): Select = apply { searchUsing = provider }

    fun disableOptionWhen(predicate: Predicate<String>): Select = apply { disableWhen = predicate }

    fun boolean(): Select = options(mapOf("true" to "Yes", "false" to "No"))

    fun relationship(name: String, titleAttribute: String): Select = relationship(name, titleAttribute, null)

    fun relationship(name: String, titleAttribute: String, modify: Consumer<Any?>?): Select = apply {
        relationNameUsing = null
        relationTitleUsing = null
        relation = RelationBinding(name, titleAttribute, modify)
        searchableFlag = true
        preloadFlag = true
    }

    fun relationship(name: Function<Get, String>, titleAttribute: Function<Get, String>): Select =
        relationship(name, titleAttribute, null)

    fun relationship(
        name: Function<Get, String>,
        titleAttribute: Function<Get, String>,
        modify: Consumer<Any?>?,
    ): Select = apply {
        relationNameUsing = name
        relationTitleUsing = titleAttribute
        relation = RelationBinding("", "", modify)
        searchableFlag = true
        preloadFlag = true
    }

    fun modifyQueryUsing(modify: Consumer<Any?>): Select = apply {
        val binding = relation ?: throw FireflyException("Select relationship is not set")
        relation = binding.copy(modify = modify)
    }

    fun getOptionLabelFromRecordUsing(provider: Function<Any?, String>): Select = apply { recordLabel = provider }

    fun createOptionAction(action: Action): Select = apply { createAction = action }

    fun relation(): RelationBinding? = relation

    fun prepareRelationship(query: Any?) {
        val binding = relation ?: return
        val modify = binding.modify
        if (modify != null) modify.accept(query)
    }

    fun createOptionForm(schema: Schema): Select = apply { createForm = schema }

    fun editOptionForm(schema: Schema): Select = apply { editForm = schema }

    fun createOptionUsing(provider: Function<Map<String, Any?>, String>): Select = apply { createUsing = provider }

    fun createOption(data: Map<String, Any?>): String? {
        val provider = createUsing ?: return null
        return provider.apply(data)
    }

    fun resolvedOptions(schema: Schema): List<FieldOption> {
        val binding = relation
        val owner = schema.record()
        if (binding != null && owner != null) {
            val get = Reads.of(schema, this)
            val name = relationNameUsing?.apply(get) ?: binding.name
            val title = relationTitleUsing?.apply(get) ?: binding.titleAttribute
            if (relationNameUsing != null) {
                relation = RelationBinding(name, title, binding.modify)
            }
            val opened = RelationshipOptions.open(owner, name, binding.modify)
            return optionsFromRecords(opened.records, title)
        }
        val get = Reads.of(schema, this)
        return optionsFromPairs(options.get(get).entries.toList())
    }

    private fun optionsFromRecords(records: List<Any?>, title: String): List<FieldOption> {
        var rows = records
        val limit = optionsLimitCount
        if (limit != null && rows.size > limit) rows = rows.take(limit)
        val disable = disableWhen
        val labels = labelUsing
        val fromRecord = recordLabel
        return rows.map { record ->
            val value = recordKey(record)
            val base = if (fromRecord != null) {
                (fromRecord.apply(record) as String?).orEmpty()
            } else {
                val raw = recordAttribute(record, title)
                if (raw == null) "" else raw.toString()
            }
            val text = if (labels == null) base else labels.apply(value)
            val enabled = if (disable == null) true else !disable.test(value)
            FieldOption(value, text, enabled, descriptions[value])
        }
    }

    private fun optionsFromPairs(entries: List<Map.Entry<String, String>>): List<FieldOption> {
        var rows = entries
        val limit = optionsLimitCount
        if (limit != null && rows.size > limit) rows = rows.take(limit)
        val disable = disableWhen
        val labels = labelUsing
        return rows.map { (value, label) ->
            val text = if (labels == null) label else labels.apply(value)
            val enabled = if (disable == null) true else !disable.test(value)
            FieldOption(value, text, enabled, descriptions[value])
        }
    }

    fun search(schema: Schema, query: String): List<FieldOption> {
        val provider = searchUsing
        if (provider != null) {
            return provider.apply(query).map { (value, label) -> FieldOption(value, label, true, descriptions[value]) }
        }
        val needle = query.lowercase()
        return resolvedOptions(schema).filter { it.label.lowercase().contains(needle) || it.value.lowercase().contains(needle) }
    }

    override fun kind(): String = "select"

    override fun validationRules(schema: Schema): List<String> {
        val out = ArrayList(super.validationRules(schema))
        if (!options.isClosure() && !multipleFlag) {
            val values = resolvedOptions(schema).map { it.value }
            if (values.isNotEmpty() && values.none { it.contains(',') || it.contains('|') }) {
                out.add("in:" + values.joinToString(","))
            }
        }
        return out
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val choices = resolvedOptions(schema)
        val selected = selected(value)
        val binding = relation
        val body = if (nativeFlag) {
            nativeSelect(path, choices, selected, extraInputMarkup(get))
        } else {
            combobox(path, schema, get, choices, selected, binding)
        }
        val action = createAction
        if (action == null) return body
        return body + action.render(schema)
    }

    private fun combobox(
        path: String,
        schema: Schema,
        get: Get,
        choices: List<FieldOption>,
        selected: Set<String>,
        binding: RelationBinding?,
    ): String = buildString {
            val inputExtra = extraInputMarkup(get)
            append("""<div class="ff-combobox" x-data="ffCombobox" data-ff-path="${esc(path)}"${boolAttr("data-ff-searchable", searchableFlag)}${boolAttr("data-ff-multiple", multipleFlag)}${boolAttr("data-ff-preload", preloadFlag)}${attr("data-ff-position", positionText)}${attr("data-ff-loading", loadingMessageText)}${attr("data-ff-searching", searchingMessageText)}${attr("data-ff-empty", emptyMessageText)}${attr("data-ff-prompt", searchPromptText)} data-ff-debounce="$searchDebounceMs"${attr("data-ff-max-items", maxItemsCount?.toString())}${attr("data-ff-relationship", binding?.name)}${attr("data-ff-title", binding?.titleAttribute)}${if (searchableFlag) "" else inputExtra}>""")
            if (searchableFlag) append("""<input class="ff-input" type="text" role="combobox"${attr("placeholder", placeholderValue(schema))}$inputExtra />""")
            append("""<ul class="ff-options">""")
            for (option in choices) {
                val css = if (option.value in selected) "ff-option ff-selected" else "ff-option"
                val label = if (allowHtmlFlag) option.label else esc(option.label)
                append("""<li class="$css" data-ff-value="${esc(option.value)}"${boolAttr("data-ff-enabled", option.enabled)}>${label}${textTag("small", "ff-option-description", option.description)}</li>""")
            }
            append("</ul>")
            append(optionDialog("Create option", createForm))
            append(optionDialog("Edit option", editForm))
            append("</div>")
    }

    private fun nativeSelect(path: String, choices: List<FieldOption>, selected: Set<String>, inputExtra: String): String = buildString {
        append("""<select class="ff-input" name="${esc(path)}"${if (multipleFlag) " multiple" else ""}$inputExtra>""")
        if (selectablePlaceholderFlag && !multipleFlag) append("""<option value="">${esc(placeholderValueOrEmpty())}</option>""")
        for (option in choices) {
            val mark = if (option.value in selected) " selected" else ""
            val disabled = if (option.enabled) "" else " disabled"
            append("""<option value="${esc(option.value)}"$mark$disabled>${esc(option.label)}</option>""")
        }
        append("</select>")
    }

    private fun placeholderValueOrEmpty(): String = ""

    private fun optionDialog(title: String, form: Schema?): String {
        if (form == null) return ""
        return Dialog.make(title).body(form.render()).render()
    }

    private fun selected(value: Any?): Set<String> {
        if (value == null) return emptySet()
        if (value is Collection<*>) {
            val out = LinkedHashSet<String>()
            for (item in value) {
                if (item == null) out.add("") else out.add(item.toString())
            }
            return out
        }
        if (value is String) return setOf(value)
        return setOf(value.toString())
    }

    companion object {
        private val maker = Maker { name -> Select(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Select>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Select = maker.make(name)
    }
}

open class Checkbox protected constructor(name: String) : Field<Checkbox>(name) {
    private var inlineFlag: Boolean = false
    private var acceptedFlag: Boolean = false

    fun inline(on: Boolean): Checkbox = apply { inlineFlag = on }

    fun accepted(): Checkbox = apply { acceptedFlag = true }

    override fun kind(): String = "checkbox"

    override fun validationRules(schema: Schema): List<String> {
        val out = ArrayList(super.validationRules(schema))
        if (acceptedFlag) out.add("accepted")
        return out
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val checked = value == true || value == "1" || value == "true" || value == "on" || value == "yes"
        return """<input class="ff-input ff-checkbox" type="checkbox" name="${esc(path)}" value="1"${if (checked) " checked" else ""}${boolAttr("data-ff-inline", inlineFlag)}${extraInputMarkup(get)} />"""
    }

    companion object {
        private val maker = Maker { name -> Checkbox(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Checkbox>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Checkbox = maker.make(name)
    }
}

open class Toggle protected constructor(name: String) : Field<Toggle>(name) {
    private var inlineFlag: Boolean = false
    private var onColorText: String? = null
    private var offColorText: String? = null
    private var onIconText: String? = null
    private var offIconText: String? = null

    fun inline(on: Boolean): Toggle = apply { inlineFlag = on }

    fun onColor(value: String): Toggle = apply { onColorText = value }

    fun offColor(value: String): Toggle = apply { offColorText = value }

    fun onIcon(value: String): Toggle = apply { onIconText = value }

    fun offIcon(value: String): Toggle = apply { offIconText = value }

    override fun kind(): String = "toggle"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val checked = value == true || value == "1" || value == "true"
        return """<button type="button" class="ff-toggle" x-data="ffField" data-ff-path="${esc(path)}"${boolAttr("aria-pressed", checked)}${boolAttr("data-ff-inline", inlineFlag)}${attr("data-ff-on-color", onColorText)}${attr("data-ff-off-color", offColorText)}${attr("data-ff-on-icon", onIconText)}${attr("data-ff-off-icon", offIconText)}></button>"""
    }

    companion object {
        private val maker = Maker { name -> Toggle(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Toggle>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Toggle = maker.make(name)
    }
}

open class CheckboxList protected constructor(name: String) : Field<CheckboxList>(name) {
    private val options = OptionSource()
    private val descriptions = LinkedHashMap<String, String>()
    private var searchableFlag: Boolean = false
    private var bulkFlag: Boolean = false
    private var optionColumnCount: Int = 1
    private var directionText: String = "row"
    private var emptyMessageText: String = "No results"
    private var searchPromptText: String = "Search"
    private var disableWhen: Predicate<String>? = null
    private var relation: RelationBinding? = null

    fun options(map: Map<String, String>): CheckboxList = apply { options.set(map) }

    fun options(provider: Function<Get, Map<String, String>>): CheckboxList = apply { options.set(provider) }

    fun descriptions(map: Map<String, String>): CheckboxList = apply {
        descriptions.clear()
        descriptions.putAll(map)
    }

    fun searchable(on: Boolean): CheckboxList = apply { searchableFlag = on }

    fun bulkToggleable(on: Boolean): CheckboxList = apply { bulkFlag = on }

    fun optionColumns(count: Int): CheckboxList = apply {
        if (count < 1) throw FireflyException("Option columns must be at least 1")
        optionColumnCount = count
    }

    fun gridDirection(direction: String): CheckboxList = apply { directionText = direction }

    fun noSearchResultsMessage(value: String): CheckboxList = apply { emptyMessageText = value }

    fun searchPrompt(value: String): CheckboxList = apply { searchPromptText = value }

    fun disableOptionWhen(predicate: Predicate<String>): CheckboxList = apply { disableWhen = predicate }

    fun relationship(name: String, titleAttribute: String): CheckboxList = apply {
        relation = RelationBinding(name, titleAttribute, null)
    }

    fun relation(): RelationBinding? = relation

    override fun kind(): String = "checkbox-list"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val selected = when (value) {
            is Collection<*> -> {
                val out = LinkedHashSet<String>()
                for (item in value) {
                    if (item == null) out.add("") else out.add(item.toString())
                }
                out
            }
            null -> emptySet()
            else -> setOf(value.toString())
        }
        val disable = disableWhen
        return buildString {
            append("""<div class="ff-checkbox-list" data-ff-columns="$optionColumnCount"${attr("data-ff-direction", directionText)}${boolAttr("data-ff-searchable", searchableFlag)}${attr("data-ff-empty", emptyMessageText)}${attr("data-ff-prompt", searchPromptText)}${attr("data-ff-relationship", relation?.name)}>""")
            if (searchableFlag) append("""<input class="ff-input" type="text"${attr("placeholder", searchPromptText)}${extraInputMarkup(get)} />""")
            if (bulkFlag) append("""<button type="button" class="ff-bulk-toggle">Select all</button>""")
            for ((optionValue, label) in options.get(get)) {
                val enabled = if (disable == null) true else !disable.test(optionValue)
                val checked = if (optionValue in selected) " checked" else ""
                append("""<label class="ff-check"><input type="checkbox" name="${esc(path)}" value="${esc(optionValue)}"$checked${if (enabled) "" else " disabled"}${extraInputMarkup(get)} />${esc(label)}${textTag("small", "ff-option-description", descriptions[optionValue])}</label>""")
            }
            append("</div>")
        }
    }

    companion object {
        private val maker = Maker { name -> CheckboxList(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<CheckboxList>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): CheckboxList = maker.make(name)
    }
}

open class Radio protected constructor(name: String) : Field<Radio>(name) {
    private val options = OptionSource()
    private val descriptions = LinkedHashMap<String, String>()
    private var inlineFlag: Boolean = false
    private var disableWhen: Predicate<String>? = null

    fun options(map: Map<String, String>): Radio = apply { options.set(map) }

    fun options(provider: Function<Get, Map<String, String>>): Radio = apply { options.set(provider) }

    fun descriptions(map: Map<String, String>): Radio = apply {
        descriptions.clear()
        descriptions.putAll(map)
    }

    fun inline(on: Boolean): Radio = apply { inlineFlag = on }

    fun boolean(): Radio = options(mapOf("true" to "Yes", "false" to "No"))

    fun disableOptionWhen(predicate: Predicate<String>): Radio = apply { disableWhen = predicate }

    override fun kind(): String = "radio"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val current = value?.toString()
        val disable = disableWhen
        return buildString {
            append("""<div class="ff-radio"${boolAttr("data-ff-inline", inlineFlag)}>""")
            for ((optionValue, label) in options.get(get)) {
                val enabled = if (disable == null) true else !disable.test(optionValue)
                val checked = if (optionValue == current) " checked" else ""
                append("""<label class="ff-check"><input type="radio" name="${esc(path)}" value="${esc(optionValue)}"$checked${if (enabled) "" else " disabled"}${extraInputMarkup(get)} />${esc(label)}${textTag("small", "ff-option-description", descriptions[optionValue])}</label>""")
            }
            append("</div>")
        }
    }

    companion object {
        private val maker = Maker { name -> Radio(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<Radio>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Radio = maker.make(name)
    }
}

open class ToggleButtons protected constructor(name: String) : Field<ToggleButtons>(name) {
    private val options = OptionSource()
    private val colors = LinkedHashMap<String, String>()
    private val icons = LinkedHashMap<String, String>()
    private var inlineFlag: Boolean = true
    private var groupedFlag: Boolean = true
    private var multipleFlag: Boolean = false

    fun options(map: Map<String, String>): ToggleButtons = apply { options.set(map) }

    fun options(provider: Function<Get, Map<String, String>>): ToggleButtons = apply { options.set(provider) }

    fun colors(map: Map<String, String>): ToggleButtons = apply {
        colors.clear()
        colors.putAll(map)
    }

    fun icons(map: Map<String, String>): ToggleButtons = apply {
        icons.clear()
        icons.putAll(map)
    }

    fun inline(on: Boolean): ToggleButtons = apply { inlineFlag = on }

    fun grouped(on: Boolean): ToggleButtons = apply { groupedFlag = on }

    fun multiple(on: Boolean): ToggleButtons = apply { multipleFlag = on }

    fun boolean(): ToggleButtons = options(mapOf("true" to "Yes", "false" to "No"))

    override fun kind(): String = "toggle-buttons"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val selected = when (value) {
            is Collection<*> -> {
                val out = LinkedHashSet<String>()
                for (item in value) {
                    if (item == null) out.add("") else out.add(item.toString())
                }
                out
            }
            null -> emptySet()
            else -> setOf(value.toString())
        }
        return buildString {
            append("""<div class="ff-toggle-buttons"${boolAttr("data-ff-inline", inlineFlag)}${boolAttr("data-ff-grouped", groupedFlag)}${boolAttr("data-ff-multiple", multipleFlag)}>""")
            for ((optionValue, label) in options.get(get)) {
                val pressed = optionValue in selected
                append("""<button type="button" class="ff-toggle-button" data-ff-value="${esc(optionValue)}"${boolAttr("aria-pressed", pressed)}${attr("data-ff-color", colors[optionValue])}${attr("data-ff-icon", icons[optionValue])}>${esc(label)}</button>""")
            }
            append("</div>")
        }
    }

    companion object {
        private val maker = Maker { name -> ToggleButtons(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<ToggleButtons>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): ToggleButtons = maker.make(name)
    }
}
