package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

class TextEntry private constructor(name: String) : Entry<TextEntry>(name) {
    private val badgeFlag = BoolSetting(false)
    private val bulletFlag = BoolSetting(false)
    private val breakFlag = BoolSetting(false)
    private val proseFlag = BoolSetting(false)
    private val htmlFlag = BoolSetting(false)
    private val markdownFlag = BoolSetting(false)
    private val expandableFlag = BoolSetting(false)
    private val sizeValue = TextSetting()
    private val weightValue = TextSetting()
    private val fontValue = TextSetting()
    private val patternValue = TextSetting()
    private val zoneValue = TextSetting()
    private val tooltipPattern = TextSetting()
    private val currencyValue = TextSetting()
    private val listLimit = IntSetting()
    private val charLimit = IntSetting()
    private val wordLimit = IntSetting()
    private val clampValue = IntSetting()
    private val decimalsValue = IntSetting()
    private var formatter: Function<Get, String>? = null
    private var mode = Mode.PLAIN

    override fun kind(): String = "text"

    fun badge(): TextEntry = badge(true)

    fun badge(on: Boolean): TextEntry = apply { badgeFlag.set(on) }

    fun badge(predicate: Predicate<Get>): TextEntry = apply { badgeFlag.set(predicate) }

    fun bulleted(on: Boolean): TextEntry = apply { bulletFlag.set(on) }

    fun bulleted(predicate: Predicate<Get>): TextEntry = apply { bulletFlag.set(predicate) }

    fun listWithLineBreaks(on: Boolean): TextEntry = apply { breakFlag.set(on) }

    fun listWithLineBreaks(predicate: Predicate<Get>): TextEntry = apply { breakFlag.set(predicate) }

    fun limitList(limit: Int): TextEntry = apply { listLimit.set(limit) }

    fun limitList(provider: Function<Get, Int>): TextEntry = apply { listLimit.set(provider) }

    fun expandableLimitedList(on: Boolean): TextEntry = apply { expandableFlag.set(on) }

    fun expandableLimitedList(predicate: Predicate<Get>): TextEntry = apply { expandableFlag.set(predicate) }

    fun prose(on: Boolean): TextEntry = apply { proseFlag.set(on) }

    fun prose(predicate: Predicate<Get>): TextEntry = apply { proseFlag.set(predicate) }

    fun size(value: String): TextEntry = apply { sizeValue.set(sizeOf(value)) }

    fun size(value: Function<Get, String>): TextEntry = apply {
        sizeValue.set(Function { get -> sizeOf(value.apply(get)) })
    }

    fun weight(value: String): TextEntry = apply { weightValue.set(weightOf(value)) }

    fun weight(value: Function<Get, String>): TextEntry = apply {
        weightValue.set(Function { get -> weightOf(value.apply(get)) })
    }

    fun fontFamily(value: String): TextEntry = apply { fontValue.set(fontOf(value)) }

    fun fontFamily(value: Function<Get, String>): TextEntry = apply {
        fontValue.set(Function { get -> fontOf(value.apply(get)) })
    }

    fun lineClamp(lines: Int): TextEntry = apply { clampValue.set(lines) }

    fun lineClamp(provider: Function<Get, Int>): TextEntry = apply { clampValue.set(provider) }

    fun limit(chars: Int): TextEntry = apply { charLimit.set(chars) }

    fun limit(provider: Function<Get, Int>): TextEntry = apply { charLimit.set(provider) }

    fun words(count: Int): TextEntry = apply { wordLimit.set(count) }

    fun words(provider: Function<Get, Int>): TextEntry = apply { wordLimit.set(provider) }

    /** Render the state as trusted HTML (same as [html] with `true`). */
    fun html(): TextEntry = html(true)

    fun html(on: Boolean): TextEntry = apply { htmlFlag.set(on) }

    fun html(predicate: Predicate<Get>): TextEntry = apply { htmlFlag.set(predicate) }

    fun markdown(on: Boolean): TextEntry = apply { markdownFlag.set(on) }

    fun markdown(predicate: Predicate<Get>): TextEntry = apply { markdownFlag.set(predicate) }

    fun date(): TextEntry = apply { mode = Mode.DATE }

    fun date(pattern: String): TextEntry = apply {
        mode = Mode.DATE
        patternValue.set(pattern)
    }

    fun date(pattern: Function<Get, String>): TextEntry = apply {
        mode = Mode.DATE
        patternValue.set(pattern)
    }

    fun dateTime(): TextEntry = apply { mode = Mode.DATE_TIME }

    fun dateTime(pattern: String): TextEntry = apply {
        mode = Mode.DATE_TIME
        patternValue.set(pattern)
    }

    fun dateTime(pattern: Function<Get, String>): TextEntry = apply {
        mode = Mode.DATE_TIME
        patternValue.set(pattern)
    }

    fun time(): TextEntry = apply { mode = Mode.TIME }

    fun time(pattern: String): TextEntry = apply {
        mode = Mode.TIME
        patternValue.set(pattern)
    }

    fun time(pattern: Function<Get, String>): TextEntry = apply {
        mode = Mode.TIME
        patternValue.set(pattern)
    }

    fun since(): TextEntry = apply { mode = Mode.SINCE }

    fun timezone(zone: String): TextEntry = apply { zoneValue.set(zone) }

    fun timezone(zone: Function<Get, String>): TextEntry = apply { zoneValue.set(zone) }

    fun dateTooltip(): TextEntry = dateTooltip("yyyy-MM-dd")

    fun dateTooltip(pattern: String): TextEntry = apply { tooltipPattern.set(pattern) }

    fun dateTooltip(pattern: Function<Get, String>): TextEntry = apply { tooltipPattern.set(pattern) }

    fun money(): TextEntry = apply { mode = Mode.MONEY }

    fun money(currency: String): TextEntry = apply {
        mode = Mode.MONEY
        currencyValue.set(currency)
    }

    fun money(currency: Function<Get, String>): TextEntry = apply {
        mode = Mode.MONEY
        currencyValue.set(currency)
    }

    fun numeric(): TextEntry = apply { mode = Mode.NUMERIC }

    fun numeric(decimals: Int): TextEntry = apply {
        mode = Mode.NUMERIC
        decimalsValue.set(decimals)
    }

    fun numeric(decimals: Function<Get, Int>): TextEntry = apply {
        mode = Mode.NUMERIC
        decimalsValue.set(decimals)
    }

    fun formatStateUsing(formatter: Function<Get, String>): TextEntry = apply { this.formatter = formatter }

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        if (isBlank(state)) return placeholderHtml(get)
        val items = if (state is Collection<*>) state.map { it } else listOf(state)
        return """<div class="ff-text"${textAttrs(get)}>${renderItems(items, get, state)}</div>"""
    }

    private fun renderItems(items: List<Any?>, get: Get, sample: Any?): String {
        val limit = listLimit.get(get)
        val extra = if (limit != null && items.size > limit) items.size - limit else 0
        val expandFlag = expandableFlag.get(get)
        val expandable = expandFlag && extra > 0
        val visible = if (limit != null && extra > 0 && !expandable) items.take(limit) else items
        val badge = badgeFlag.get(get)
        val bullets = bulletFlag.get(get)
        val breaks = breakFlag.get(get) || bullets
        val body = if (visible.size == 1 && !bullets && !breaks && !badge) {
            itemHtml(visible[0], get, false)
        } else if (bullets) {
            "<ul class=\"ff-bullets\">" + visible.mapIndexed { index, item ->
                """<li${extraAttr(expandFlag, index, limit)}>${itemHtml(item, get, badge)}</li>"""
            }.joinToString("") + "</ul>"
        } else if (breaks || badge) {
            visible.mapIndexed { index, item ->
                """<div class="ff-text-item"${extraAttr(expandFlag, index, limit)}>${itemHtml(item, get, badge)}</div>"""
            }.joinToString("")
        } else {
            itemHtml(visible.joinToString(", ") { plain(it, get) }, get, false, alreadyPlain = true)
        }
        val more = if (extra > 0 && !expandable) """<span class="ff-more">+$extra</span>""" else ""
        val tip = dateTip(sample, get)
        return """<div class="ff-text-items"$tip>$body$more</div>"""
    }

    private fun itemHtml(item: Any?, get: Get, badge: Boolean, alreadyPlain: Boolean = false): String {
        val text = if (alreadyPlain) escapeHtml(stringify(item)) else present(item, get)
        val badgeAttr = if (badge) """ data-ff-badge="true"""" else ""
        return link(get, affixes(get, """<span class="ff-value"$badgeAttr>$text</span>"""))
    }

    private fun present(item: Any?, get: Get): String {
        val custom = formatter
        if (custom != null) return escapeHtml(custom.apply(get))
        val plain = plain(item, get)
        val limited = limitText(plain, charLimit.get(get), wordLimit.get(get))
        if (markdownFlag.get(get)) return markdown(limited)
        if (htmlFlag.get(get)) return limited
        return escapeHtml(limited)
    }

    private fun plain(item: Any?, get: Get): String {
        val zone = zoneOf(zoneValue.get(get))
        val pattern = patternValue.get(get)
        return when (mode) {
            Mode.PLAIN -> stringify(item)
            Mode.DATE -> temporalText(item, pattern ?: "yyyy-MM-dd", zone)
            Mode.DATE_TIME -> temporalText(item, pattern ?: "yyyy-MM-dd HH:mm", zone)
            Mode.TIME -> temporalText(item, pattern ?: "HH:mm", zone)
            Mode.SINCE -> sinceText(item, zone)
            Mode.MONEY -> moneyText(item, currencyValue.get(get) ?: "USD")
            Mode.NUMERIC -> numericText(item, decimalsValue.get(get) ?: 0)
        }
    }

    private fun dateTip(state: Any?, get: Get): String {
        val pattern = tooltipPattern.get(get)
        if (pattern == null) return ""
        val zone = zoneOf(zoneValue.get(get))
        return attr("data-ff-date-tooltip", temporalText(state, pattern, zone))
    }

    private fun textAttrs(get: Get): String {
        val clamp = clampValue.get(get)
        return attr("data-ff-size", sizeValue.get(get)) +
            attr("data-ff-weight", weightValue.get(get)) +
            attr("data-ff-font", fontValue.get(get)) +
            flag("data-ff-prose", proseFlag.get(get)) +
            attr("data-ff-line-clamp", if (clamp == null) null else clamp.toString()) +
            attr("data-ff-icon-color", iconColorValue.get(get)) +
            attr("data-ff-list-limit", listLimit.get(get)?.toString())
    }

    private fun placeholderHtml(get: Get): String {
        val text = placeholderValue.get(get) ?: ""
        return """<span class="ff-placeholder">${escapeHtml(text)}</span>"""
    }

    private enum class Mode { PLAIN, DATE, DATE_TIME, TIME, SINCE, MONEY, NUMERIC }

    companion object {
        @JvmStatic
        fun make(name: String): TextEntry {
            val entry = TextEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<TextEntry>?) = configure(TextEntry::class.java, block)
    }
}

internal fun isBlank(state: Any?): Boolean {
    if (state == null) return true
    if (state is String) return state.isEmpty()
    if (state is Collection<*>) return state.isEmpty()
    return false
}

private fun extraAttr(expandable: Boolean, index: Int, limit: Int?): String {
    if (!expandable || limit == null || index < limit) return ""
    return """ data-ff-extra="true""""
}
