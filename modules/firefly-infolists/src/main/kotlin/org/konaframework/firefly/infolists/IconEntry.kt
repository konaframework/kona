package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

class IconEntry private constructor(name: String) : Entry<IconEntry>(name) {
    private val booleanFlag = BoolSetting(false)
    private val breakFlag = BoolSetting(false)
    private val hideTrue = BoolSetting(false)
    private val hideFalse = BoolSetting(false)
    private val trueIconValue = TextSetting()
    private val falseIconValue = TextSetting()
    private val trueColorValue = TextSetting()
    private val falseColorValue = TextSetting()
    private val sizeValue = TextSetting()

    override fun kind(): String = "icon"

    fun bool(on: Boolean): IconEntry = apply { booleanFlag.set(on) }

    fun bool(predicate: Predicate<Get>): IconEntry = apply { booleanFlag.set(predicate) }

    /**
     * Treats the state as a boolean: truthy → true icon/color, falsey → false icon/color.
     * Kotlin alias for [bool]`(true)`. Java callers use [bool]`(true)` (`boolean` is a keyword).
     */
    fun boolean(): IconEntry = bool(true)

    fun whenTrue(icon: String, color: String): IconEntry = apply {
        booleanFlag.set(true)
        trueIconValue.set(icon)
        trueColorValue.set(color)
    }

    fun whenFalse(icon: String, color: String): IconEntry = apply {
        booleanFlag.set(true)
        falseIconValue.set(icon)
        falseColorValue.set(color)
    }

    fun trueIcon(icon: String): IconEntry = apply {
        booleanFlag.set(true)
        trueIconValue.set(icon)
    }

    fun trueIcon(icon: Function<Get, String>): IconEntry = apply {
        booleanFlag.set(true)
        trueIconValue.set(icon)
    }

    fun falseIcon(icon: String): IconEntry = apply {
        booleanFlag.set(true)
        falseIconValue.set(icon)
    }

    fun falseIcon(icon: Function<Get, String>): IconEntry = apply {
        booleanFlag.set(true)
        falseIconValue.set(icon)
    }

    fun trueColor(color: String): IconEntry = apply {
        booleanFlag.set(true)
        trueColorValue.set(color)
    }

    fun trueColor(color: Function<Get, String>): IconEntry = apply {
        booleanFlag.set(true)
        trueColorValue.set(color)
    }

    fun falseColor(color: String): IconEntry = apply {
        booleanFlag.set(true)
        falseColorValue.set(color)
    }

    fun falseColor(color: Function<Get, String>): IconEntry = apply {
        booleanFlag.set(true)
        falseColorValue.set(color)
    }

    fun hideTrueIcon(): IconEntry = apply {
        booleanFlag.set(true)
        hideTrue.set(true)
    }

    fun hideFalseIcon(): IconEntry = apply {
        booleanFlag.set(true)
        hideFalse.set(true)
    }

    fun size(value: String): IconEntry = apply { sizeValue.set(sizeOf(value)) }

    fun size(value: Function<Get, String>): IconEntry = apply {
        sizeValue.set(Function { get -> sizeOf(value.apply(get)) })
    }

    fun listWithLineBreaks(on: Boolean): IconEntry = apply { breakFlag.set(on) }

    fun listWithLineBreaks(predicate: Predicate<Get>): IconEntry = apply { breakFlag.set(predicate) }

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        if (isBlank(state)) return """<span class="ff-placeholder">${escapeHtml(placeholderValue.get(get) ?: "")}</span>"""
        val items = if (state is Collection<*>) state.map { it } else listOf(state)
        val breaks = breakFlag.get(get)
        val asBool = booleanFlag.get(get)
        val body = items.joinToString("") { item ->
            val icon = iconFor(item, get)
            val color = colorFor(item, get)
            val mark = if (asBool && item != null && !icon.isNullOrEmpty()) {
                val cls = if (truthy(item)) "is-true" else "is-false"
                val custom = if (icon == "check" || icon == "x") "" else " ff-boolean--custom"
                """<span class="ff-boolean$custom ff-boolean--icon-only $cls"><span class="ff-boolean__mark" aria-hidden="true">${iconHtml(icon)}</span></span>"""
            } else if (asBool && item != null) {
                val cls = if (truthy(item)) "is-true" else "is-false"
                """<span class="ff-boolean ff-boolean--icon-only $cls"></span>"""
            } else {
                iconHtml(icon)
            }
            val piece = """<span class="ff-icon-item"${attr("data-ff-color", color)}>$mark${accessText(item, get)}</span>"""
            if (breaks) """<div class="ff-icon-row">$piece</div>""" else piece
        }
        return """<div class="ff-icons"${attr("data-ff-size", sizeValue.get(get))}${flag("data-ff-line-breaks", breaks)}>$body</div>"""
    }

    private fun iconFor(state: Any?, get: Get): String? {
        val explicit = iconValue.get(get)
        if (explicit != null) return explicit
        if (!booleanFlag.get(get)) {
            if (state is String) return state
            return null
        }
        if (state == null) return null
        if (truthy(state)) {
            if (hideTrue.get(get)) return null
            return trueIconValue.get(get) ?: "check"
        }
        if (hideFalse.get(get)) return null
        return falseIconValue.get(get) ?: "x"
    }

    private fun colorFor(state: Any?, get: Get): String? {
        val explicit = colorValue.get(get)
        if (explicit != null) return explicit
        val painted = iconColorValue.get(get)
        if (painted != null) return painted
        if (!booleanFlag.get(get) || state == null) return null
        if (truthy(state)) return trueColorValue.get(get) ?: "success"
        // Soft false (Shamar is-false), not danger red.
        return falseColorValue.get(get) ?: "gray"
    }

    private fun accessText(state: Any?, get: Get): String {
        val tip = tooltipValue.get(get)
        if (tip != null) return """<span class="ff-sr">${escapeHtml(tip)}</span>"""
        if (booleanFlag.get(get)) {
            val word = if (truthy(state)) "true" else "false"
            return """<span class="ff-sr">$word</span>"""
        }
        if (state is String) return """<span class="ff-sr">${escapeHtml(state)}</span>"""
        return ""
    }

    companion object {
        @JvmStatic
        fun make(name: String): IconEntry {
            val entry = IconEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<IconEntry>?) = configure(IconEntry::class.java, block)
    }
}

internal fun truthy(state: Any?): Boolean {
    return when (state) {
        null -> false
        is Boolean -> state
        is Number -> state.toDouble() != 0.0
        is String -> state == "1" || state.equals("true", true) || state.equals("yes", true)
        else -> true
    }
}
