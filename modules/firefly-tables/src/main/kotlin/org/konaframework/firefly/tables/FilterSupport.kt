package org.konaframework.firefly.tables

import org.konaframework.firefly.support.Icon
import org.konaframework.firefly.support.FireflyException

/**
 * Submitted state of one filter form, keyed by field name. Single-control filters
 * (checkbox, toggle, select, ternary, text) keep their value under [VALUE];
 * a [Filter.schema] filter keeps one entry per form field.
 */
class FilterData(values: Map<String, Any?>) {
    private val values = LinkedHashMap(values)

    fun get(key: String): Any? = values[key]

    /** Trimmed text, or null when the field is missing or blank. */
    fun string(key: String): String? {
        val raw = values[key] ?: return null
        if (raw is Collection<*>) return list(key).firstOrNull()
        return raw.toString().trim().ifEmpty { null }
    }

    /** Multi-value fields: a collection, or comma-separated text from the query string. */
    fun list(key: String): List<String> = split(values[key])

    /** `true`/`1`/`on`/`yes` → true, `false`/`0`/`off`/`no` → false, anything else → null. */
    fun bool(key: String): Boolean? {
        val raw = values[key] ?: return null
        if (raw is Boolean) return raw
        return when (raw.toString().trim().lowercase()) {
            "true", "1", "on", "yes" -> true
            "false", "0", "off", "no" -> false
            else -> null
        }
    }

    fun filled(key: String): Boolean = isFilled(values[key])

    fun value(): Any? = values[VALUE]

    fun values(): List<String> = list(VALUE)

    fun isEmpty(): Boolean = values.values.none { isFilled(it) }

    fun keys(): Set<String> = values.keys.toSet()

    fun toMap(): Map<String, Any?> = LinkedHashMap(values)

    /** Copy without [fields] — used when an indicator removes part of a filter. */
    fun without(fields: Collection<String>): FilterData = FilterData(values.filterKeys { it !in fields })

    override fun equals(other: Any?): Boolean = other is FilterData && normalized() == other.normalized()

    override fun hashCode(): Int = normalized().hashCode()

    private fun normalized(): Map<String, List<String>> =
        values.filterValues { isFilled(it) }.mapValues { (key, _) -> list(key) }

    private fun split(raw: Any?): List<String> = when (raw) {
        null -> emptyList()
        is Collection<*> -> raw.filterNotNull().map { it.toString().trim() }.filter { it.isNotEmpty() }
        else -> raw.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        const val VALUE = "value"

        @JvmStatic
        fun of(values: Map<String, Any?>): FilterData = FilterData(values)

        @JvmStatic
        fun single(value: Any?): FilterData = FilterData(if (value == null) emptyMap() else mapOf(VALUE to value))

        internal fun isFilled(value: Any?): Boolean = when (value) {
            null -> false
            is Collection<*> -> value.any { isFilled(it) }
            else -> value.toString().isNotBlank()
        }
    }
}

/** Query callback that receives the native query (`ModelBuilder` / `MemoryQuery`) and the form [FilterData]. */
fun interface FilterQuery {
    fun apply(query: Any, data: FilterData)
}

/** One active-filter chip. [removeField] limits what the chip's × clears (default: the whole filter). */
class FilterIndicator private constructor(val label: String) {
    private val fieldNames = LinkedHashSet<String>()
    private var removableFlag = true
    private var colorName: String? = null

    fun removeField(name: String): FilterIndicator = apply { fieldNames.add(name) }

    fun removeFields(vararg names: String): FilterIndicator = apply { fieldNames.addAll(names) }

    fun removable(on: Boolean): FilterIndicator = apply { removableFlag = on }

    fun color(value: String): FilterIndicator = apply { colorName = value }

    fun fields(): List<String> = fieldNames.toList()

    fun isRemovable(): Boolean = removableFlag

    fun color(): String? = colorName

    companion object {
        @JvmStatic
        fun make(label: String): FilterIndicator = FilterIndicator(label)
    }
}

/** Presentation of the filters form (Filament `FiltersLayout`). */
enum class FiltersLayout {
    /** Funnel trigger in the toolbar opens a popover (default). */
    DROPDOWN,

    /** Funnel trigger opens a dialog. */
    MODAL,

    /** Filters form sits between the toolbar and the records, always visible. */
    ABOVE_CONTENT,

    /** Above the records, collapsed behind the funnel trigger until opened. */
    ABOVE_CONTENT_COLLAPSIBLE,

    /** Filters form below the records and pagination. */
    BELOW_CONTENT,
}

enum class FiltersResetActionPosition {
    HEADER,
    FOOTER,
}

/** Chrome control for filter UI actions (trigger, apply, reset, remove-all). */
class FilterAction internal constructor(val name: String, defaultLabel: String) {
    private var labelText = defaultLabel
    private var iconName: String? = null
    private var colorName: String? = null
    private var style = Style.BUTTON
    private var hiddenFlag = false
    private var tooltipText: String? = null
    private var badgeFlag = true
    private val extra = LinkedHashMap<String, String>()

    enum class Style {
        BUTTON,
        LINK,
        ICON_BUTTON,
    }

    fun label(value: String): FilterAction = apply { labelText = value }

    fun icon(value: String): FilterAction = apply { iconName = value }

    fun color(value: String): FilterAction = apply { colorName = value }

    fun button(): FilterAction = apply { style = Style.BUTTON }

    fun link(): FilterAction = apply { style = Style.LINK }

    fun iconButton(): FilterAction = apply { style = Style.ICON_BUTTON }

    @JvmOverloads
    fun hidden(on: Boolean = true): FilterAction = apply { hiddenFlag = on }

    fun visible(on: Boolean): FilterAction = hidden(!on)

    fun tooltip(value: String): FilterAction = apply { tooltipText = value }

    /** Trigger only: show the active-filter count badge (default true). */
    fun badge(on: Boolean): FilterAction = apply { badgeFlag = on }

    fun extraAttributes(values: Map<String, String>): FilterAction = apply {
        for (key in values.keys) {
            if (key.isBlank() || key == "class" || key.startsWith("data-ff-filters")) {
                throw FireflyException("Filter action attribute [$key] is reserved")
            }
        }
        extra.putAll(values)
    }

    fun label(): String = labelText

    fun style(): Style = style

    fun isHidden(): Boolean = hiddenFlag

    fun hasBadge(): Boolean = badgeFlag

    internal fun classes(base: String): String {
        val variant = when (style) {
            Style.BUTTON -> "ff-filters-action--button"
            Style.LINK -> "ff-filters-action--link"
            Style.ICON_BUTTON -> "ff-filters-action--icon"
        }
        return "ff-filters-action $variant $base"
    }

    internal fun attributes(): String {
        val out = StringBuilder()
        val color = colorName
        if (color != null) out.append(""" data-ff-color="${escapeHtml(color)}"""")
        val icon = iconName
        if (icon != null) out.append(""" data-ff-icon="${escapeHtml(icon)}"""")
        val tip = tooltipText
        if (tip != null) out.append(""" title="${escapeHtml(tip)}"""")
        for ((key, value) in extra) out.append(""" ${escapeHtml(key)}="${escapeHtml(value)}"""")
        return out.toString()
    }

    /** [icon] resolved through Firefly's icon set, or [fallback] when no icon was chosen. */
    internal fun iconSvg(fallback: String): String {
        val name = iconName ?: return fallback
        return Icon.fromName(name).svg()
    }

    /** Visible text: icon buttons keep the label for screen readers only. */
    internal fun inner(iconSvg: String): String {
        val text = escapeHtml(labelText)
        return when (style) {
            Style.ICON_BUTTON -> """$iconSvg<span class="ff-sr-only">$text</span>"""
            Style.LINK -> """<span>$text</span>"""
            Style.BUTTON -> """$iconSvg<span>$text</span>"""
        }
    }
}

/**
 * Groups filters inside the filters form (Filament `filtersFormSchema` sections).
 * Filters not named by any section render after the sections.
 */
class FilterSection private constructor(private val headingText: String?) {
    private val names = ArrayList<String>()
    private var columnCount: Int? = null
    private var descriptionText: String? = null
    private var spanFull = false

    fun filters(vararg filterNames: String): FilterSection = apply {
        names.clear()
        names.addAll(filterNames)
    }

    fun columns(count: Int): FilterSection = apply {
        if (count < 1) throw FireflyException("Filter section columns must be at least 1")
        columnCount = count
    }

    fun description(value: String): FilterSection = apply { descriptionText = value }

    fun columnSpanFull(): FilterSection = apply { spanFull = true }

    fun heading(): String? = headingText

    fun filterNames(): List<String> = names.toList()

    internal fun columnCount(): Int? = columnCount

    internal fun description(): String? = descriptionText

    internal fun spansFull(): Boolean = spanFull

    companion object {
        @JvmStatic
        fun make(heading: String): FilterSection = FilterSection(heading)

        @JvmStatic
        fun make(): FilterSection = FilterSection(null)
    }
}

/** Built-in control tweaks for [Filter.modifyFormFieldUsing]. */
class FilterField internal constructor(val filterName: String) {
    internal var labelText: String? = null
    internal var placeholderText: String? = null
    internal var helperText: String? = null
    internal var hiddenLabel = false
    internal var inline: Boolean? = null
    internal val attributes = LinkedHashMap<String, String>()

    fun label(value: String): FilterField = apply { labelText = value }

    fun placeholder(value: String): FilterField = apply { placeholderText = value }

    fun helperText(value: String): FilterField = apply { helperText = value }

    @JvmOverloads
    fun hiddenLabel(on: Boolean = true): FilterField = apply { hiddenLabel = on }

    /** Checkbox / toggle: label beside the control (true) or above it (false). */
    fun inline(on: Boolean): FilterField = apply { inline = on }

    fun extraInputAttributes(values: Map<String, String>): FilterField = apply {
        for ((key, value) in values) {
            if (key.isEmpty() || key == "type" || key == "name" || key == "value") continue
            attributes[key] = value
        }
    }
}
