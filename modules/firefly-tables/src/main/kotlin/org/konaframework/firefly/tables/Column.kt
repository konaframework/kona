package org.konaframework.firefly.tables

import org.konaframework.cadence.Cadence
import org.konaframework.firefly.schemas.humanizeName
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Icon
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

enum class Align {
    START,
    CENTER,
    END,
}

enum class TextStyle {
    PLAIN,
    MONEY,
    DATE,
    DATE_TIME,
}

open class Column protected constructor(val name: String) {
    private val labelValue = TextSetting()
    private val descriptionValue = TextSetting()
    private val tooltipValue = TextSetting()
    private val placeholderValue = TextSetting()
    private val prefixValue = TextSetting()
    private val suffixValue = TextSetting()
    private val colorValue = TextSetting()
    private val iconValue = TextSetting()
    private val urlValue = TextSetting()
    private val widthValue = TextSetting()
    private val hiddenFlag = FlagSetting(false)
    private val visibleFlag = FlagSetting(true)
    private val disabledFlag = FlagSetting(false)
    private val searchableFlag = FlagSetting(false)
    private val sortableFlag = FlagSetting(false)
    private val toggleableFlag = FlagSetting(false)
    private var toggledHiddenByDefaultFlag = false
    private var alignValue = Align.START
    private var alignUsing: Function<Cell, Align>? = null
    private var extra: Map<String, String> = emptyMap()
    private var extraUsing: Function<Cell, Map<String, String>>? = null
    private var viewName: String? = null
    private var formatter: BiFunction<Any?, Cell, String>? = null
    private var html = false
    private var badge = false
    private var style = TextStyle.PLAIN
    private var limit: Int? = null
    private var newTab = false
    private var copyable = false
    private val summaryOps = ArrayList<Summary>()
    private var relationshipName: String? = null
    private var relationshipTitleAttribute: String? = null

    fun label(value: String): Column = apply { labelValue.set(value) }

    fun label(value: Function<Cell, String>): Column = apply { labelValue.set(value) }

    fun description(value: String): Column = apply { descriptionValue.set(value) }

    fun description(value: Function<Cell, String>): Column = apply { descriptionValue.set(value) }

    fun tooltip(value: String): Column = apply { tooltipValue.set(value) }

    fun tooltip(value: Function<Cell, String>): Column = apply { tooltipValue.set(value) }

    fun placeholder(value: String): Column = apply { placeholderValue.set(value) }

    fun placeholder(value: Function<Cell, String>): Column = apply { placeholderValue.set(value) }

    fun prefix(value: String): Column = apply { prefixValue.set(value) }

    fun prefix(value: Function<Cell, String>): Column = apply { prefixValue.set(value) }

    fun suffix(value: String): Column = apply { suffixValue.set(value) }

    fun suffix(value: Function<Cell, String>): Column = apply { suffixValue.set(value) }

    fun color(value: String): Column = apply { colorValue.set(value) }

    fun color(value: Function<Cell, String>): Column = apply { colorValue.set(value) }

    fun icon(value: String): Column = apply { iconValue.set(value) }

    fun icon(value: Function<Cell, String>): Column = apply { iconValue.set(value) }

    fun url(value: String): Column = apply { urlValue.set(value) }

    fun url(value: Function<Cell, String>): Column = apply { urlValue.set(value) }

    fun width(value: String): Column = apply { widthValue.set(value) }

    fun width(value: Function<Cell, String>): Column = apply { widthValue.set(value) }

    fun hidden(): Column = hidden(true)

    fun hidden(on: Boolean): Column = apply { hiddenFlag.set(on) }

    fun hidden(predicate: Predicate<Cell>): Column = apply { hiddenFlag.set(predicate) }

    fun visible(): Column = visible(true)

    fun visible(on: Boolean): Column = apply { visibleFlag.set(on) }

    fun visible(predicate: Predicate<Cell>): Column = apply { visibleFlag.set(predicate) }

    fun disabled(): Column = disabled(true)

    fun disabled(on: Boolean): Column = apply { disabledFlag.set(on) }

    fun disabled(predicate: Predicate<Cell>): Column = apply { disabledFlag.set(predicate) }

    fun searchable(): Column = searchable(true)

    fun searchable(on: Boolean): Column = apply { searchableFlag.set(on) }

    fun searchable(predicate: Predicate<Cell>): Column = apply { searchableFlag.set(predicate) }

    fun sortable(): Column = sortable(true)

    fun sortable(on: Boolean): Column = apply { sortableFlag.set(on) }

    fun sortable(predicate: Predicate<Cell>): Column = apply { sortableFlag.set(predicate) }

    fun toggleable(): Column = toggleable(true)

    fun toggleable(on: Boolean): Column = apply { toggleableFlag.set(on) }

    fun toggleable(predicate: Predicate<Cell>): Column = apply { toggleableFlag.set(predicate) }

    /**
     * Marks the column toggleable and initially hidden in the column picker
     * (Filament-style `toggleable(isToggledHiddenByDefault: true)`).
     */
    fun toggledHiddenByDefault(): Column = toggledHiddenByDefault(true)

    fun toggledHiddenByDefault(on: Boolean): Column = apply {
        toggledHiddenByDefaultFlag = on
        if (on) toggleableFlag.set(true)
    }

    fun isToggledHiddenByDefault(): Boolean = toggledHiddenByDefaultFlag

    fun align(value: Align): Column = apply {
        alignValue = value
        alignUsing = null
    }

    fun align(value: Function<Cell, Align>): Column = apply { alignUsing = value }

    fun extraAttributes(attributes: Map<String, String>): Column = apply {
        extra = LinkedHashMap(attributes)
        extraUsing = null
    }

    fun extraAttributes(provider: Function<Cell, Map<String, String>>): Column = apply { extraUsing = provider }

    fun view(name: String): Column = apply { viewName = name }

    fun formatStateUsing(formatter: BiFunction<Any?, Cell, String>): Column = apply { this.formatter = formatter }

    /**
     * Show a related record’s [titleAttribute] instead of the foreign-key value.
     * Prefer the Filament/Shamar dotted make path when possible:
     * `TextColumn.make("project.name")` (up to four levels, e.g. `a.b.c.d`).
     * The table eager-loads [name] on Cadence queries (`with("project")` /
     * `with("a.b.c")`). Defaults the heading to a humanized relation name.
     */
    fun relationship(name: String, titleAttribute: String): Column = apply {
        if (name.isBlank()) throw FireflyException("Column relationship name is blank")
        if (titleAttribute.isBlank()) throw FireflyException("Column relationship title attribute is blank")
        relationshipName = name
        relationshipTitleAttribute = titleAttribute
        if (!labelValue.isAssigned()) label(humanizeName(name.substringBefore('.')))
        formatStateUsing { state, cell -> relationshipTitle(cell, name, titleAttribute, state) }
    }

    fun relationshipName(): String? = relationshipName

    /**
     * When [name] is a dotted path (`project.name` … four segments max), bind
     * [relationship] automatically so make-time Filament/Shamar syntax works.
     */
    internal fun bindDottedRelationshipPath() {
        if (relationshipName != null) return
        if (!name.contains('.')) return
        val parts = name.split('.')
        if (parts.size > MAX_RELATION_DEPTH) {
            throw FireflyException(
                "Column path [$name] has ${parts.size} segments; maximum is $MAX_RELATION_DEPTH",
            )
        }
        if (parts.any { it.isBlank() }) {
            throw FireflyException("Column path [$name] has a blank segment")
        }
        relationship(parts.dropLast(1).joinToString("."), parts.last())
    }

    fun html(): Column = html(true)

    fun html(on: Boolean): Column = apply { html = on }

    fun badge(): Column = badge(true)

    fun badge(on: Boolean): Column = apply { badge = on }

    fun money(): Column = apply {
        style = TextStyle.MONEY
        if (alignUsing == null && alignValue == Align.START) alignValue = Align.END
    }

    /** Human date (`January 2, 2026` / PHP `F j, Y`); hover shows the raw value. */
    fun date(): Column = apply { style = TextStyle.DATE }

    /** Human date and time (`January 2, 2026, 3:45 PM`); hover shows the raw value. */
    fun dateTime(): Column = apply { style = TextStyle.DATE_TIME }

    fun limit(characters: Int): Column = apply { limit = characters }

    fun openUrlInNewTab(): Column = openUrlInNewTab(true)

    fun openUrlInNewTab(on: Boolean): Column = apply { newTab = on }

    fun copyable(): Column = copyable(true)

    fun copyable(on: Boolean): Column = apply { copyable = on }

    fun summarize(operation: Summary): Column = apply { summaryOps.add(operation) }

    fun summaries(): List<Summary> = summaryOps.toList()

    open fun isHidden(cell: Cell): Boolean = hiddenFlag.get(cell) || !visibleFlag.get(cell)

    fun isSearchable(cell: Cell): Boolean = searchableFlag.get(cell)

    fun isSortable(cell: Cell): Boolean = sortableFlag.get(cell)

    fun isToggleable(cell: Cell): Boolean = toggleableFlag.get(cell)

    fun isDisabled(cell: Cell): Boolean = disabledFlag.get(cell)

    fun heading(cell: Cell): String = labelValue.get(cell, humanizeName(name))

    open fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val state = digMap(row, name)
        val formatted = format(state, cell)
        val described = descriptionValue.get(cell, "")
        val description = if (described.isEmpty()) "" else """<p class="ff-column-description">${escapeHtml(described)}</p>"""
        val tip = tooltipValue.get(cell, "").ifEmpty { temporalRawTip(state) }
        val tooltip = if (tip.isEmpty()) "" else """ title="${escapeHtml(tip)}" data-ff-tooltip="${escapeHtml(tip)}""""
        val viewAttr = if (viewName == null) "" else """ data-ff-view="${escapeHtml(viewName!!)}""""
        val copy = if (copyable) """ data-ff-copyable="true"""" else ""
        val width = widthValue.get(cell, "")
        val widthAttr = if (width.isEmpty()) "" else """ style="width:${escapeHtml(width)}""""
        val align = resolvedAlign(cell)
        val extras = resolvedExtra(cell)
        val extraAttr = extras.entries.joinToString("") { (attr, value) ->
            """ ${escapeHtml(attr)}="${escapeHtml(value)}""""
        }
        val disabled = if (isDisabled(cell)) """ data-ff-disabled="true"""" else ""
        return """
            <div class="ff-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-path="${escapeHtml(name)}" data-ff-entangle="@entangle(${escapeHtml(name)})" data-ff-align="${align.name.lowercase()}"$tooltip$viewAttr$copy$widthAttr$disabled$extraAttr>
              $formatted
              $description
            </div>
        """.trimIndent()
    }

    protected fun format(state: Any?, cell: Cell): String {
        val custom = formatter
        val raw = if (custom != null) {
            custom.apply(state, cell)
        } else {
            when (effectiveStyle()) {
                TextStyle.MONEY -> money(state)
                TextStyle.DATE -> humanDate(state, withTime = false)
                TextStyle.DATE_TIME -> humanDate(state, withTime = true)
                TextStyle.PLAIN -> plain(state)
            }
        }
        val shown = if (raw.isEmpty()) placeholderValue.get(cell, "") else raw
        val limited = limit(shown)
        val escaped = if (html) limited else escapeHtml(limited)
        val prefixed = prefixValue.get(cell, "") + escaped + suffixValue.get(cell, "")
        val painted = paint(prefixed, cell)
        val linked = link(painted, cell)
        return if (badge) """<span class="ff-badge">$linked</span>""" else linked
    }

    private fun plain(state: Any?): String = state?.toString().orEmpty()

    private fun money(state: Any?): String = when (state) {
        null -> ""
        is Number -> "$" + String.format(Locale.US, "%.2f", state.toDouble())
        else -> state.toString()
    }

    /** PHP `F j, Y` (`January 2, 2026`) or date+time (`January 2, 2026, 3:45 PM`). */
    private fun humanDate(state: Any?, withTime: Boolean): String {
        if (state == null) return ""
        val zone = ZoneId.systemDefault()
        val formatter = if (withTime) HUMAN_DATE_TIME else HUMAN_DATE
        return when (state) {
            is LocalDate ->
                if (withTime) state.atStartOfDay(zone).format(formatter) else state.format(formatter)
            is LocalDateTime -> state.atZone(zone).format(formatter)
            is Instant -> state.atZone(zone).format(formatter)
            is java.sql.Timestamp -> state.toInstant().atZone(zone).format(formatter)
            is java.sql.Date -> {
                val local = state.toLocalDate()
                if (withTime) local.atStartOfDay(zone).format(formatter) else local.format(formatter)
            }
            is Date -> Instant.ofEpochMilli(state.time).atZone(zone).format(formatter)
            is String -> {
                val parsed = parseTemporal(state, zone) ?: return state
                parsed.format(formatter)
            }
            else -> state.toString()
        }
    }

    private fun effectiveStyle(): TextStyle {
        if (style != TextStyle.PLAIN) return style
        return inferTemporalStyle(name) ?: TextStyle.PLAIN
    }

    private fun temporalRawTip(state: Any?): String {
        val active = effectiveStyle()
        if (active != TextStyle.DATE && active != TextStyle.DATE_TIME) return ""
        if (state == null) return ""
        return state.toString()
    }

    private fun parseTemporal(text: String, zone: ZoneId): java.time.ZonedDateTime? {
        try {
            return Instant.parse(text).atZone(zone)
        } catch (_: Exception) {
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T')).atZone(zone)
        } catch (_: Exception) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay(zone)
        } catch (_: Exception) {
        }
        return null
    }

    private fun limit(text: String): String {
        val max = limit ?: return text
        if (text.length <= max) return text
        return text.take(max) + "…"
    }

    private fun paint(text: String, cell: Cell): String {
        val color = colorValue.get(cell, "")
        val icon = iconValue.get(cell, "")
        val iconHtml = if (icon.isEmpty()) "" else Icon.fromName(icon).svg()
        val body = if (color.isEmpty()) text else """<span class="ff-column-color" style="color:${escapeHtml(color)}">$text</span>"""
        return iconHtml + body
    }

    private fun link(text: String, cell: Cell): String {
        val href = urlValue.get(cell, "")
        if (href.isEmpty()) return text
        val target = if (newTab) """ target="_blank" rel="noreferrer"""" else ""
        return """<a class="ff-column-link" href="${escapeHtml(href)}"$target>$text</a>"""
    }

    internal fun resolvedAlign(cell: Cell): Align {
        val provider = alignUsing
        if (provider != null) return provider.apply(cell)
        return alignValue
    }

    private fun resolvedExtra(cell: Cell): Map<String, String> {
        val provider = extraUsing
        if (provider != null) return provider.apply(cell)
        return extra
    }

    companion object {
        private val HUMAN_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        private val HUMAN_DATE_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.ENGLISH)

        private val configurers = HashMap<Class<*>, Consumer<Column>?>()

        /** Name hints (`due_on`, `created_at`, `start_date`) so PLAIN columns still humanize. */
        internal fun inferTemporalStyle(columnName: String): TextStyle? {
            val leaf = columnName.substringAfterLast('.').lowercase()
            return when {
                leaf.endsWith("_at") || leaf.contains("datetime") -> TextStyle.DATE_TIME
                leaf.endsWith("_on") || leaf.endsWith("_date") || leaf == "date" -> TextStyle.DATE
                else -> null
            }
        }

        @JvmStatic
        fun configureUsing(type: Class<out Column>, block: Consumer<Column>?) {
            configurers[type] = block
        }

        /** Dotted relation + attribute paths support up to this many segments (`a.b.c.d`). */
        const val MAX_RELATION_DEPTH: Int = 4

        internal fun <T : Column> finish(column: T): T {
            configurers[column.javaClass]?.accept(column)
            return column
        }

        internal fun relationshipTitle(cell: Cell, relation: String, titleAttribute: String, fallback: Any?): String {
            val related = digCell(cell, relation)
            val title = attributeOf(related, titleAttribute)
            if (title != null) return title.toString()
            return fallback?.toString().orEmpty()
        }

        /** Resolve a dotted path against a row map (`project.name`, `a.b.c.d`). */
        internal fun digMap(row: Map<String, Any?>, path: String): Any? {
            if (path.isEmpty()) return null
            if (!path.contains('.')) return row[path]
            var current: Any? = row
            for (part in path.split('.')) {
                current = attributeOf(current, part) ?: return null
            }
            return current
        }

        private fun digCell(cell: Cell, path: String): Any? {
            val parts = path.split('.')
            var current: Any? = null
            for ((index, part) in parts.withIndex()) {
                current = if (index == 0) {
                    cell.get(part) ?: cell.get("/$part")
                } else {
                    attributeOf(current, part)
                }
                if (current == null) return null
            }
            return current
        }

        private fun attributeOf(related: Any?, name: String): Any? = when (related) {
            null -> null
            is Map<*, *> -> related[name]
            is Cadence<*> -> related.getRelations()[name] ?: related.getAttribute(name)
            else -> {
                val method = related.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                method?.invoke(related)
            }
        }
    }
}

open class TextColumn protected constructor(name: String) : Column(name) {
    companion object {
        @JvmStatic
        fun make(name: String): TextColumn {
            val column = finish(TextColumn(name))
            column.bindDottedRelationshipPath()
            return column
        }

        @JvmStatic
        fun configureUsing(block: Consumer<TextColumn>?) {
            if (block == null) {
                Column.configureUsing(TextColumn::class.java, null)
            } else {
                @Suppress("UNCHECKED_CAST")
                Column.configureUsing(TextColumn::class.java, block as Consumer<Column>?)
            }
        }
    }
}

/**
 * Text column that renders as a badge by default (`TextColumn.badge(true)`).
 * Prefer this when the column is always a status/chip; chain [color] for tone.
 */
open class BadgeColumn protected constructor(name: String) : TextColumn(name) {
    init {
        badge(true)
    }

    companion object {
        @JvmStatic
        fun make(name: String): BadgeColumn {
            val column = finish(BadgeColumn(name))
            column.bindDottedRelationshipPath()
            return column
        }
    }
}

open class IconColumn private constructor(name: String) : Column(name) {
    /** Shamar boolean true mark (check). */
    private var trueIcon = "check"
    /** Soft false mark (X), paired with [trueIcon]. */
    private var falseIcon = "x"
    private var booleanMode = false

    fun trueIcon(name: String): IconColumn = apply { trueIcon = name }

    fun falseIcon(name: String): IconColumn = apply { falseIcon = name }

    /**
     * Treats the cell as a boolean (Shamar check / cross chips). Truthy for [Boolean], numbers,
     * and `"1"` / `"true"` / `"yes"`. Kotlin: [boolean]; Java: [bool] (`boolean` is a keyword).
     */
    fun boolean(): IconColumn = apply { booleanMode = true }

    /** Java-friendly alias for [boolean]. */
    @JvmName("bool")
    fun bool(): IconColumn = boolean()

    override fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val state = row[name]
        val icon = if (booleanMode) {
            when {
                state == null -> ""
                truthy(state) -> trueIcon
                else -> falseIcon
            }
        } else {
            when (state) {
                null -> ""
                is Boolean -> if (state) trueIcon else falseIcon
                else -> state.toString()
            }
        }
        val html = when {
            icon.isEmpty() -> ""
            booleanMode -> {
                val on = truthy(state)
                val cls = if (on) "is-true" else "is-false"
                val word = if (on) "true" else "false"
                val custom = if (icon == "check" || icon == "x") "" else " ff-boolean--custom"
                """<span class="ff-boolean$custom ff-boolean--icon-only $cls"><span class="ff-boolean__mark" aria-hidden="true">${Icon.fromName(icon).svg()}</span><span class="ff-sr">$word</span></span>"""
            }
            else -> Icon.fromName(icon).svg()
        }
        return """<div class="ff-column ff-icon-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})">$html</div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): IconColumn = finish(IconColumn(name))
    }
}

open class ImageColumn private constructor(name: String) : Column(name) {
    private val altValue = TextSetting()
    private var height: Int? = null
    private var width: Int? = null

    fun alt(value: String): ImageColumn = apply { altValue.set(value) }

    fun alt(value: Function<Cell, String>): ImageColumn = apply { altValue.set(value) }

    fun imageHeight(pixels: Int): ImageColumn = apply { height = pixels }

    fun imageWidth(pixels: Int): ImageColumn = apply { width = pixels }

    override fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val src = row[name]?.toString().orEmpty()
        val alt = escapeHtml(altValue.get(cell, ""))
        if (src.isEmpty()) {
            return """<div class="ff-column ff-image-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})"></div>"""
        }
        val heightAttr = if (height == null) "" else """ height="$height""""
        val widthAttr = if (width == null) "" else """ width="$width""""
        return """<div class="ff-column ff-image-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})"><img class="ff-image" src="${escapeHtml(src)}" alt="$alt"$heightAttr$widthAttr></div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): ImageColumn = finish(ImageColumn(name))
    }
}

open class ColorColumn private constructor(name: String) : Column(name) {
    override fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val color = row[name]?.toString().orEmpty()
        val swatch = if (color.isEmpty()) "" else """<span class="ff-color" style="background:${escapeHtml(color)}"></span>"""
        return """<div class="ff-column ff-color-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})">$swatch</div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): ColorColumn = finish(ColorColumn(name))
    }
}

open class CheckboxColumn private constructor(name: String) : Column(name) {
    private val inputExtras = InputAttributes()
    private var editableUsing: Predicate<Cell>? = null

    fun extraInputAttributes(attributes: Map<String, String>): CheckboxColumn = apply { inputExtras.set(attributes) }

    fun extraInputAttributes(provider: Function<Cell, Map<String, String>>): CheckboxColumn =
        apply { inputExtras.set(provider) }

    /**
     * When false, the checkbox is readonly (no persist). Use for edit-permission gates:
     * `.editable { cell -> canUpdate(cell) }`.
     */
    fun editable(): CheckboxColumn = editable(true)

    fun editable(on: Boolean): CheckboxColumn = apply {
        editableUsing = Predicate { on }
    }

    fun editable(predicate: Predicate<Cell>): CheckboxColumn = apply { editableUsing = predicate }

    fun isEditable(cell: Cell): Boolean {
        if (isDisabled(cell)) return false
        val gate = editableUsing ?: return true
        return gate.test(cell)
    }

    override fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val checked = if (truthy(row[name])) """ checked""" else ""
        val canEdit = isEditable(cell)
        val disabled = if (!canEdit) """ disabled""" else ""
        val extra = inputExtras.markup(cell)
        val change = if (canEdit) {
            """ x-on:change="${'$'}wire.${'$'}call('toggleColumn', '${escapeHtml(key)}', '${escapeHtml(name)}', ${'$'}event.target.checked ? 'true' : 'false')""""
        } else {
            ""
        }
        return """<div class="ff-column ff-checkbox-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})"><label class="ff-checkbox-wrap"><input class="ff-checkbox" type="checkbox"$checked$disabled$extra$change><span class="ff-checkbox-box" aria-hidden="true"></span></label></div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): CheckboxColumn = finish(CheckboxColumn(name))
    }
}

open class ToggleColumn private constructor(name: String) : Column(name) {
    override fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val on = truthy(row[name])
        val next = if (on) "false" else "true"
        val pressed = if (on) "true" else "false"
        return """<div class="ff-column ff-toggle-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})"><button type="button" aria-pressed="$pressed" x-on:click="${'$'}wire.${'$'}call('toggleColumn', '${escapeHtml(key)}', '${escapeHtml(name)}', '$next')"></button></div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): ToggleColumn = finish(ToggleColumn(name))
    }
}

open class SelectColumn private constructor(name: String) : Column(name) {
    private var options: Map<String, String> = emptyMap()
    private var optionsUsing: Function<Cell, Map<String, String>>? = null
    private val inputExtras = InputAttributes()

    fun extraInputAttributes(attributes: Map<String, String>): SelectColumn = apply { inputExtras.set(attributes) }

    fun extraInputAttributes(provider: Function<Cell, Map<String, String>>): SelectColumn =
        apply { inputExtras.set(provider) }

    fun options(values: Map<String, String>): SelectColumn = apply {
        options = LinkedHashMap(values)
        optionsUsing = null
    }

    fun options(provider: Function<Cell, Map<String, String>>): SelectColumn = apply { optionsUsing = provider }

    override fun render(row: Map<String, Any?>, key: String, cell: Cell): String {
        val current = row[name]?.toString().orEmpty()
        val choices = resolved(cell)
        val tags = choices.entries.joinToString("") { (value, label) ->
            val selected = if (value == current) """ selected""" else ""
            """<option value="${escapeHtml(value)}"$selected>${escapeHtml(label)}</option>"""
        }
        val extra = inputExtras.markup(cell)
        return """<div class="ff-column ff-select-column" x-data="ffColumn('${escapeHtml(name)}', true)" data-ff-entangle="@entangle(${escapeHtml(name)})"><select$extra x-on:change="${'$'}wire.${'$'}call('setColumn', '${escapeHtml(key)}', '${escapeHtml(name)}', ${'$'}event.target.value)">$tags</select></div>"""
    }

    private fun resolved(cell: Cell): Map<String, String> {
        val provider = optionsUsing
        if (provider != null) return provider.apply(cell)
        return options
    }

    companion object {
        @JvmStatic
        fun make(name: String): SelectColumn = finish(SelectColumn(name))
    }
}
