package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

open class Entry<T : Entry<T>> protected constructor(name: String) : EntryHost<T>(name) {
    internal val placeholderValue = TextSetting()
    internal val colorValue = TextSetting()
    internal val iconValue = TextSetting()
    internal val iconColorValue = TextSetting()
    internal val iconPositionValue = TextSetting()
    internal val tooltipValue = TextSetting()
    internal val urlValue = TextSetting()
    internal val copyMessageValue = TextSetting()
    internal val copyStateValue = TextSetting()
    internal val helperValue = TextSetting()
    internal val hintValue = TextSetting()
    internal val hintColorValue = TextSetting()
    internal val hintIconValue = TextSetting()
    internal val prefixValue = TextSetting()
    internal val suffixValue = TextSetting()
    internal val alignmentValue = TextSetting()
    internal val openNewTab = BoolSetting(false)
    internal val copyableFlag = BoolSetting(false)
    /** Default on so long values wrap inside modals/containers (#368). */
    internal val wrapFlag = BoolSetting(true)
    internal val inlineFlag = BoolSetting(false)
    internal val copyDuration = IntSetting()
    internal val stateValue = AnySetting()
    private var above = emptyList<Component>()
    private var below = emptyList<Component>()

    override fun kind(): String = "entry"

    override fun label(value: String): T = self().also { super.label(value) }

    override fun label(value: Function<Get, String>): T = self().also { super.label(value) }

    override fun heading(value: String): T = self().also { super.heading(value) }

    override fun heading(value: Function<Get, String>): T = self().also { super.heading(value) }

    override fun description(value: String): T = self().also { super.description(value) }

    override fun description(value: Function<Get, String>): T = self().also { super.description(value) }

    override fun extraAttributes(values: Map<String, String>): T = self().also { super.extraAttributes(values) }

    override fun extraAttributes(provider: Function<Get, Map<String, String>>): T =
        self().also { super.extraAttributes(provider) }

    fun placeholder(value: String): T = self().also { placeholderValue.set(value) }

    fun placeholder(value: Function<Get, String>): T = self().also { placeholderValue.set(value) }

    fun color(value: String): T = self().also { colorValue.set(value) }

    fun color(value: Function<Get, String>): T = self().also { colorValue.set(value) }

    fun icon(value: String): T = self().also { iconValue.set(value) }

    fun icon(value: Function<Get, String>): T = self().also { iconValue.set(value) }

    fun iconColor(value: String): T = self().also { iconColorValue.set(value) }

    fun iconColor(value: Function<Get, String>): T = self().also { iconColorValue.set(value) }

    fun iconPosition(value: String): T = self().also { iconPositionValue.set(iconPositionOf(value)) }

    fun iconPosition(value: Function<Get, String>): T = self().also {
        iconPositionValue.set(Function { get -> iconPositionOf(value.apply(get)) })
    }

    fun tooltip(value: String): T = self().also { tooltipValue.set(value) }

    fun tooltip(value: Function<Get, String>): T = self().also { tooltipValue.set(value) }

    fun url(value: String): T = self().also { urlValue.set(value) }

    fun url(value: Function<Get, String>): T = self().also { urlValue.set(value) }

    fun openUrlInNewTab(): T = openUrlInNewTab(true)

    fun openUrlInNewTab(on: Boolean): T = self().also { openNewTab.set(on) }

    fun openUrlInNewTab(predicate: Predicate<Get>): T = self().also { openNewTab.set(predicate) }

    fun copyable(): T = copyable(true)

    fun copyable(on: Boolean): T = self().also { copyableFlag.set(on) }

    fun copyable(predicate: Predicate<Get>): T = self().also { copyableFlag.set(predicate) }

    fun copyMessage(value: String): T = self().also { copyMessageValue.set(value) }

    fun copyMessage(value: Function<Get, String>): T = self().also { copyMessageValue.set(value) }

    fun copyMessageDuration(milliseconds: Int): T = self().also { copyDuration.set(milliseconds) }

    fun copyMessageDuration(provider: Function<Get, Int>): T = self().also { copyDuration.set(provider) }

    fun copyableState(value: String): T = self().also { copyStateValue.set(value) }

    fun copyableState(value: Function<Get, String>): T = self().also { copyStateValue.set(value) }

    fun helperText(value: String): T = self().also { helperValue.set(value) }

    fun helperText(value: Function<Get, String>): T = self().also { helperValue.set(value) }

    fun hint(value: String): T = self().also { hintValue.set(value) }

    fun hint(value: Function<Get, String>): T = self().also { hintValue.set(value) }

    fun hintColor(value: String): T = self().also { hintColorValue.set(value) }

    fun hintColor(value: Function<Get, String>): T = self().also { hintColorValue.set(value) }

    fun hintIcon(value: String): T = self().also { hintIconValue.set(value) }

    fun hintIcon(value: Function<Get, String>): T = self().also { hintIconValue.set(value) }

    fun prefix(value: String): T = self().also { prefixValue.set(value) }

    fun prefix(value: Function<Get, String>): T = self().also { prefixValue.set(value) }

    fun suffix(value: String): T = self().also { suffixValue.set(value) }

    fun suffix(value: Function<Get, String>): T = self().also { suffixValue.set(value) }

    fun alignment(value: String): T = self().also { alignmentValue.set(alignmentOf(value)) }

    fun alignment(value: Function<Get, String>): T = self().also {
        alignmentValue.set(Function { get -> alignmentOf(value.apply(get)) })
    }

    fun alignStart(): T = alignment("start")

    fun alignCenter(): T = alignment("center")

    fun alignEnd(): T = alignment("end")

    fun alignJustify(): T = alignment("justify")

    fun wrap(): T = wrap(true)

    fun wrap(on: Boolean): T = self().also { wrapFlag.set(on) }

    fun wrap(predicate: Predicate<Get>): T = self().also { wrapFlag.set(predicate) }

    fun inlineLabel(): T = inlineLabel(true)

    fun inlineLabel(on: Boolean): T = self().also { inlineFlag.set(on) }

    fun inlineLabel(predicate: Predicate<Get>): T = self().also { inlineFlag.set(predicate) }

    fun state(value: Any?): T = self().also { stateValue.set(value) }

    fun state(provider: Function<Get, Any?>): T = self().also { stateValue.set(provider) }

    fun aboveContent(vararg components: Component): T = self().also {
        above = components.toList()
        attachOwned(above)
    }

    fun belowContent(vararg components: Component): T = self().also {
        below = components.toList()
        attachOwned(below)
    }

    override fun slotChildren(): List<Component> {
        val slots = ArrayList<Component>()
        slots.addAll(above)
        slots.addAll(below)
        return slots
    }

    override fun renderBody(schema: Schema): String {
        if (meta("ff-view") != null) return super.renderBody(schema)
        val get = reader(schema)
        val inner = renderEntry(schema, get)
        return """<div class="ff-entry" x-data="ffEntry"${entryAttrs(get)}>${labelHtml(schema)}${headingHtml(schema)}${descriptionHtml(schema)}${slot("above", above, schema)}${hintHtml(get)}${inner}${helperHtml(get)}${slot("below", below, schema)}</div>"""
    }

    internal open fun renderEntry(schema: Schema, get: Get): String = ""

    internal fun reader(schema: Schema): Get {
        val frame = ItemFrame.current()
        if (frame == null) return SchemaBridge.open(schema, this)
        return SchemaBridge.overlay(schema, this) { path -> itemLookup(frame, path) }
    }

    internal fun resolvedState(schema: Schema, get: Get): Any? {
        val provided = stateValue.get(get)
        if (provided.first) return provided.second
        val frame = ItemFrame.current()
        if (frame != null) return digName(frame, name)
        val path = absolutePath()
        if (path == null) return null
        val stored = schema.get(path)
        if (stored != null) return stored
        // Dotted paths: dig the Cadence/map record when the store was filled without nested relations (#361).
        if (name.contains('.')) {
            val dug = digName(schema.record(), name)
            if (dug != null) return dug
        }
        return stored
    }

    internal fun entryAttrs(get: Get): String {
        // Default justify so long prose fills the column evenly (#368).
        val align = alignmentValue.get(get) ?: "justify"
        val duration = copyDuration.get(get)
        return attr("data-ff-color", colorValue.get(get)) +
            attr("data-ff-align", align) +
            attr("data-ff-tooltip", tooltipValue.get(get)) +
            flag("data-ff-copyable", copyableFlag.get(get)) +
            attr("data-ff-copy-message", copyMessageValue.get(get)) +
            attr("data-ff-copy-state", copyStateValue.get(get)) +
            attr("data-ff-copy-duration", if (duration == null) null else duration.toString()) +
            attr("data-ff-wrap", if (wrapFlag.get(get)) "true" else "false") +
            flag("data-ff-inline-label", inlineFlag.get(get))
    }

    internal fun link(get: Get, inner: String): String {
        val href = urlValue.get(get)
        if (href == null || href.isEmpty()) return inner
        val target = if (openNewTab.get(get)) """ target="_blank" rel="noopener"""" else ""
        return """<a href="${escapeHtml(href)}"$target>$inner</a>"""
    }

    internal fun affixes(get: Get, inner: String): String {
        val icon = iconHtml(iconValue.get(get))
        val at = iconPositionValue.get(get) ?: "before"
        val before = if (at == "after") "" else icon
        val after = if (at == "after") icon else ""
        return wrapSpan("ff-prefix", escapeHtml(prefixValue.get(get) ?: "")) +
            wrapSpan("ff-icon-before", before) +
            inner +
            wrapSpan("ff-icon-after", after) +
            wrapSpan("ff-suffix", escapeHtml(suffixValue.get(get) ?: ""))
    }

    private fun hintHtml(get: Get): String {
        val text = hintValue.get(get)
        if (text == null) return ""
        val color = hintColorValue.get(get)
        val icon = iconHtml(hintIconValue.get(get))
        return """<span class="ff-hint"${attr("data-ff-hint-color", color)}>$icon${escapeHtml(text)}</span>"""
    }

    private fun helperHtml(get: Get): String {
        val text = helperValue.get(get)
        if (text == null) return ""
        return """<p class="ff-helper">${escapeHtml(text)}</p>"""
    }

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T
}

internal fun <T : Component> configure(type: Class<T>, block: Consumer<T>?) {
    if (block == null) {
        ComponentConfig.set(type, null)
        return
    }
    ComponentConfig.set(type, Consumer { component ->
        @Suppress("UNCHECKED_CAST")
        (block as Consumer<Component>).accept(component)
    })
}

internal fun finish(component: Component) {
    ComponentConfig.apply(component)
}

private fun slot(name: String, components: List<Component>, schema: Schema): String {
    if (components.isEmpty()) return ""
    val body = components.joinToString("") { it.render(schema) }
    return """<div class="ff-slot ff-slot-$name">$body</div>"""
}
