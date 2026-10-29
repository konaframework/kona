package org.konaframework.firefly.forms

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function
import java.util.function.Predicate

open class TextInput protected constructor(name: String) : Field<TextInput>(name) {
    private var inputType: String = "text"
    private var revealableFlag: Boolean = false
    private var copyableFlag: Boolean = false
    private var maskText: String? = null
    private val minLengthValue = IntPhrase()
    private val maxLengthValue = IntPhrase()
    private val minNumber = NumberPhrase()
    private val maxNumber = NumberPhrase()
    private var stepText: String? = null
    private var regexText: String? = null
    private var notRegexText: String? = null
    private var stripLiteral: String? = null
    private var stripUsing: Function<String, String>? = null
    private val datalistValues = ArrayList<String>()
    private var telPattern: String? = null
    private var uniqueRule: String? = null
    private var existsRule: String? = null

    fun email(): TextInput = apply { inputType = "email" }

    fun numeric(): TextInput = apply {
        inputType = "number"
        inputMode("decimal")
    }

    fun integer(): TextInput = apply {
        inputType = "number"
        inputMode("numeric")
        stepText = "1"
    }

    fun password(): TextInput = apply { inputType = "password" }

    fun revealable(on: Boolean): TextInput = apply { revealableFlag = on }

    fun tel(): TextInput = apply {
        inputType = "tel"
        if (telPattern == null) telPattern = "^[0-9+().\\-\\s]+$"
    }

    fun telRegex(pattern: String): TextInput = apply { telPattern = pattern }

    fun url(): TextInput = apply { inputType = "url" }

    fun type(value: String): TextInput = apply { inputType = value }

    fun mask(value: String): TextInput = apply { maskText = value }

    fun copyable(): TextInput = copyable(true)

    fun copyable(on: Boolean): TextInput = apply { copyableFlag = on }

    fun step(value: String): TextInput = apply { stepText = value }

    fun minValue(value: Double): TextInput = apply { minNumber.set(value) }

    fun minValue(value: Function<Get, Double>): TextInput = apply { minNumber.set(value) }

    fun maxValue(value: Double): TextInput = apply { maxNumber.set(value) }

    fun maxValue(value: Function<Get, Double>): TextInput = apply { maxNumber.set(value) }

    fun minLength(value: Int): TextInput = apply { minLengthValue.set(value) }

    fun minLength(value: Function<Get, Int>): TextInput = apply { minLengthValue.set(value) }

    fun maxLength(value: Int): TextInput = apply { maxLengthValue.set(value) }

    fun maxLength(value: Function<Get, Int>): TextInput = apply { maxLengthValue.set(value) }

    fun length(value: Int): TextInput = apply {
        minLengthValue.set(value)
        maxLengthValue.set(value)
    }

    fun regex(pattern: String): TextInput = apply { regexText = pattern }

    fun notRegex(pattern: String): TextInput = apply { notRegexText = pattern }

    fun stripCharacters(characters: String): TextInput = apply {
        stripLiteral = characters
        stripUsing = null
    }

    fun stripCharacters(strip: Function<String, String>): TextInput = apply {
        stripUsing = strip
        stripLiteral = null
    }

    fun datalist(vararg values: String): TextInput = apply {
        datalistValues.clear()
        datalistValues.addAll(values)
    }

    fun unique(table: String, column: String): TextInput = apply { uniqueRule = "unique:$table,$column" }

    fun exists(table: String, column: String): TextInput = apply { existsRule = "exists:$table,$column" }

    fun inputType(): String = inputType

    fun normalizeText(raw: String): String {
        val custom = stripUsing
        if (custom != null) return custom.apply(raw)
        val chars = stripLiteral
        if (chars == null) return raw
        val skip = chars.toSet()
        return buildString {
            for (ch in raw) {
                if (ch !in skip) append(ch)
            }
        }
    }

    override fun kind(): String = "text-input"

    override fun validationRules(schema: Schema): List<String> {
        val get = Reads.of(schema, this)
        val out = ArrayList(super.validationRules(schema))
        if (inputType == "email") out.add("email")
        if (inputType == "url") out.add("url")
        if (inputType == "number" && stepText == "1") out.add("integer")
        if (inputType == "number" && stepText != "1") out.add("numeric")
        val min = minNumber.get(get)
        if (min != null) out.add("min:$min")
        val max = maxNumber.get(get)
        if (max != null) out.add("max:$max")
        val minLength = minLengthValue.get(get)
        if (minLength != null) out.add("min:$minLength")
        val maxLength = maxLengthValue.get(get)
        if (maxLength != null) out.add("max:$maxLength")
        val pattern = regexText ?: telPattern
        if (pattern != null) out.add("regex:$pattern")
        val blocked = notRegexText
        if (blocked != null) out.add("not_regex:$blocked")
        val unique = uniqueRule
        if (unique != null) out.add(unique)
        val exists = existsRule
        if (exists != null) out.add(exists)
        return out
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else normalizeText(value.toString())
        val listId = if (datalistValues.isEmpty()) null else "$path-list"
        val actions = revealableFlag || copyableFlag
        val builder = StringBuilder()
        if (actions) builder.append("""<div class="ff-input-group" data-ff-input-group>""")
        builder.append("""<input class="ff-input"${inputAttrs(get, inputType, path, shown)}""")
        builder.append(attr("list", listId))
        builder.append(attr("step", stepText))
        builder.append(attr("data-ff-mask", maskText))
        val min = minNumber.get(get)
        if (min != null) builder.append(attr("min", trimNumber(min)))
        val max = maxNumber.get(get)
        if (max != null) builder.append(attr("max", trimNumber(max)))
        val maxLength = maxLengthValue.get(get)
        if (maxLength != null) builder.append(attr("maxlength", maxLength.toString()))
        builder.append(" />")
        if (revealableFlag) {
            builder.append("""<button type="button" class="ff-input-action ff-reveal" data-ff-reveal="true" aria-label="Show password" aria-pressed="false" title="Show password">""")
            builder.append("""<span class="ff-reveal-show">${FormIcons.svg(FormIcons.EYE)}</span><span class="ff-reveal-hide">${FormIcons.svg(FormIcons.EYE_SLASH)}</span></button>""")
        }
        if (copyableFlag) {
            builder.append("""<button type="button" class="ff-input-action ff-copy" data-ff-copy="true" aria-label="Copy" title="Copy">""")
            builder.append("""<span class="ff-copy-idle">${FormIcons.svg(FormIcons.CLIPBOARD)}</span><span class="ff-copy-done">${FormIcons.svg(FormIcons.CHECK)}</span></button>""")
        }
        if (actions) builder.append("</div>")
        if (listId != null) {
            builder.append("""<datalist id="${esc(listId)}">""")
            for (item in datalistValues) builder.append("""<option value="${esc(item)}"></option>""")
            builder.append("</datalist>")
        }
        return builder.toString()
    }

    companion object {
        private val maker = Maker { name -> TextInput(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<TextInput>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): TextInput = maker.make(name)

        private fun trimNumber(value: Double): String {
            if (value == value.toLong().toDouble()) return value.toLong().toString()
            return value.toString()
        }
    }
}

open class Textarea protected constructor(name: String) : Field<Textarea>(name) {
    private var rowCount: Int = 3
    private var colCount: Int? = null
    private var autosizeFlag: Boolean = false
    private val minLengthValue = IntPhrase()
    private val maxLengthValue = IntPhrase()

    /** Same as [Field.required] — listed on Textarea for discoverability. */
    override fun required(): Textarea = super.required()

    /** Same as [Field.required] — listed on Textarea for discoverability. */
    override fun required(on: Boolean): Textarea = super.required(on)

    /** Same as [Field.required] — listed on Textarea for discoverability. */
    override fun required(predicate: Predicate<Get>): Textarea = super.required(predicate)

    fun rows(count: Int): Textarea = apply {
        if (count < 1) throw FireflyException("Textarea rows must be at least 1")
        rowCount = count
    }

    fun cols(count: Int): Textarea = apply {
        if (count < 1) throw FireflyException("Textarea cols must be at least 1")
        colCount = count
    }

    fun autosize(on: Boolean): Textarea = apply { autosizeFlag = on }

    fun minLength(value: Int): Textarea = apply { minLengthValue.set(value) }

    fun maxLength(value: Int): Textarea = apply { maxLengthValue.set(value) }

    override fun kind(): String = "textarea"

    override fun validationRules(schema: Schema): List<String> {
        val get = Reads.of(schema, this)
        val out = ArrayList(super.validationRules(schema))
        val minLength = minLengthValue.get(get)
        if (minLength != null) out.add("min:$minLength")
        val maxLength = maxLengthValue.get(get)
        if (maxLength != null) out.add("max:$maxLength")
        return out
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else value.toString()
        val cols = colCount
        return """<textarea class="ff-input" name="${esc(path)}" rows="$rowCount"${if (cols == null) "" else " cols=\"$cols\""}${boolAttr("data-ff-autosize", autosizeFlag)}${attr("placeholder", placeholderValue(schema))}${extraInputMarkup(get)}>${esc(shown)}</textarea>"""
    }

    companion object {
        private val maker = Maker { name -> Textarea(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<Textarea>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Textarea = maker.make(name)
    }
}

open class Hidden protected constructor(name: String) : Field<Hidden>(name) {
    override fun kind(): String = "hidden"

    override fun renderBody(schema: Schema): String {
        val path = fieldPath()
        val value = schema.get(path)
        val shown = if (value == null) "" else value.toString()
        return """${entangle(path)}<input class="ff-input" type="hidden" name="${esc(path)}" value="${esc(shown)}"${extraInputMarkup(Reads.of(schema, this))} />"""
    }

    companion object {
        private val maker = Maker { name -> Hidden(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<Hidden>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Hidden = maker.make(name)
    }
}

open class TagsInput protected constructor(name: String) : Field<TagsInput>(name) {
    private var separatorText: String = ","
    private val suggestions = ArrayList<String>()
    private val splitKeys = ArrayList<String>()
    private var reorderableFlag: Boolean = false
    private var colorText: String? = null

    fun separator(value: String): TagsInput = apply {
        if (value.isEmpty()) throw FireflyException("Tag separator is empty")
        separatorText = value
    }

    fun suggestions(vararg values: String): TagsInput = apply {
        suggestions.clear()
        suggestions.addAll(values)
    }

    fun splitKeys(vararg keys: String): TagsInput = apply {
        splitKeys.clear()
        splitKeys.addAll(keys)
    }

    fun reorderable(on: Boolean): TagsInput = apply { reorderableFlag = on }

    fun color(value: String): TagsInput = apply { colorText = value }

    fun tags(raw: Any?): List<String> {
        if (raw == null) return emptyList()
        if (raw is Collection<*>) {
            val out = ArrayList<String>()
            for (item in raw) {
                if (item == null) continue
                val text = item.toString().trim()
                if (text.isNotEmpty()) out.add(text)
            }
            return out
        }
        return raw.toString().split(separatorText).map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun kind(): String = "tags-input"

    private fun chip(tag: String): String =
        """<span class="ff-tag" data-ff-tag="${esc(tag)}"><span class="ff-tag-label">${esc(tag)}</span>${FormIcons.button("ff-tag-remove", "Remove", "data-ff-tag-remove", FormIcons.X_MARK)}</span>"""

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val tags = tags(value)
        val keys = if (splitKeys.isEmpty()) "Enter" else splitKeys.joinToString(",")
        val listId = if (suggestions.isEmpty()) null else "$path-suggestions"
        return buildString {
            append("""<div class="ff-tags" x-data="ffTags" data-ff-tags data-ff-path="${esc(path)}"${attr("data-ff-separator", separatorText)}${attr("data-ff-split", keys)}${boolAttr("data-ff-reorder", reorderableFlag)}${attr("data-ff-color", colorText)}>""")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(tags.joinToString(separatorText))}" data-ff-tags-value />""")
            append("""<div class="ff-tags-field">""")
            for (tag in tags) append(chip(tag))
            append("""<input class="ff-tags-input" type="text" data-ff-tags-input${attr("list", listId)}${attr("placeholder", placeholderValue(schema) ?: "Add tag…")}${extraInputMarkup(get)} />""")
            append("</div>")
            append("""<template data-ff-tags-template>${chip("")}</template>""")
            if (listId != null) {
                append("""<datalist id="${esc(listId)}" class="ff-suggestions">""")
                for (item in suggestions) append("""<option value="${esc(item)}"></option>""")
                append("</datalist>")
            }
            append("</div>")
        }
    }

    companion object {
        private val maker = Maker { name -> TagsInput(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<TagsInput>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): TagsInput = maker.make(name)
    }
}

open class ColorPicker protected constructor(name: String) : Field<ColorPicker>(name) {
    private var formatName: String = "hex"

    fun hex(): ColorPicker = apply { formatName = "hex" }

    fun rgb(): ColorPicker = apply { formatName = "rgb" }

    fun hsl(): ColorPicker = apply { formatName = "hsl" }

    fun format(): String = formatName

    fun normalize(raw: String?): String? {
        if (raw == null || raw.isBlank()) return null
        val text = raw.trim()
        val ok = when (formatName) {
            "rgb" -> RGB.matches(text)
            "hsl" -> HSL.matches(text)
            else -> HEX.matches(text)
        }
        if (!ok) return null
        return text
    }

    override fun kind(): String = "color-picker"

    override fun validationRules(schema: Schema): List<String> {
        val out = ArrayList(super.validationRules(schema))
        val pattern = when (formatName) {
            "rgb" -> "regex:^rgb\\(\\d{1,3}, ?\\d{1,3}, ?\\d{1,3}\\)$"
            "hsl" -> "regex:^hsl\\(\\d{1,3}, ?\\d{1,3}%, ?\\d{1,3}%\\)$"
            else -> "regex:^#[0-9A-Fa-f]{6}$"
        }
        out.add(pattern)
        return out
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else value.toString()
        val swatch = if (HEX.matches(shown)) shown.lowercase() else "#000000"
        val placeholder = when (formatName) {
            "rgb" -> "rgb(0, 0, 0)"
            "hsl" -> "hsl(0, 0%, 0%)"
            else -> "#000000"
        }
        return buildString {
            append("""<div class="ff-color" x-data="ffColor" data-ff-color data-ff-format="${esc(formatName)}">""")
            append("""<input type="color" class="ff-color-swatch" value="$swatch" aria-label="Pick color" data-ff-color-swatch />""")
            append("""<input class="ff-input ff-color-value" data-ff-color-value${inputAttrs(get, "text", path, shown)}""")
            if (placeholderValue(schema) == null) append(""" placeholder="$placeholder"""")
            append(" />")
            append("</div>")
        }
    }

    companion object {
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
        private val RGB = Regex("^rgb\\(\\d{1,3}, ?\\d{1,3}, ?\\d{1,3}\\)$")
        private val HSL = Regex("^hsl\\(\\d{1,3}, ?\\d{1,3}%, ?\\d{1,3}%\\)$")
        private val maker = Maker { name -> ColorPicker(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<ColorPicker>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): ColorPicker = maker.make(name)
    }
}

open class Slider protected constructor(name: String) : Field<Slider>(name) {
    private var minNumber: Double = 0.0
    private var maxNumber: Double = 100.0
    private var stepNumber: Double = 1.0
    private var places: Int? = null
    private var tooltipsFlag: Boolean = false

    fun minValue(value: Double): Slider = apply { minNumber = value }

    fun maxValue(value: Double): Slider = apply { maxNumber = value }

    fun step(value: Double): Slider = apply {
        if (value <= 0.0) throw FireflyException("Slider step must be positive")
        stepNumber = value
    }

    fun decimalPlaces(count: Int): Slider = apply {
        if (count < 0) throw FireflyException("Slider decimal places cannot be negative")
        places = count
    }

    fun tooltips(on: Boolean): Slider = apply { tooltipsFlag = on }

    fun normalize(raw: Double): Double {
        var value = raw
        if (value < minNumber) value = minNumber
        if (value > maxNumber) value = maxNumber
        val digits = places
        if (digits != null) {
            val factor = Math.pow(10.0, digits.toDouble())
            value = Math.round(value * factor) / factor
        }
        return value
    }

    override fun kind(): String = "slider"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val numeric = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
        val shown = if (numeric == null) minNumber else normalize(numeric)
        val text = display(shown)
        return buildString {
            append("""<div class="ff-slider-wrap" data-ff-slider>""")
            append("""<input class="ff-slider" x-data="ffSlider"${inputAttrs(get, "range", path, text)} min="${display(minNumber)}" max="${display(maxNumber)}" step="${display(stepNumber)}"${boolAttr("data-ff-tooltips", tooltipsFlag)} data-ff-slider-input style="--ff-slider-fill: ${fill(shown)}%" />""")
            append("""<output class="ff-slider-value" data-ff-slider-value>${esc(text)}</output>""")
            append("</div>")
        }
    }

    private fun display(value: Double): String {
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return value.toString()
    }

    private fun fill(value: Double): String {
        val span = maxNumber - minNumber
        if (span <= 0.0) return "0"
        return display(Math.round((value - minNumber) / span * 1000.0) / 10.0)
    }

    companion object {
        private val maker = Maker { name -> Slider(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<Slider>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Slider = maker.make(name)
    }
}

open class KeyValue protected constructor(name: String) : Field<KeyValue>(name) {
    private val keyLabelText = Phrase()
    private val valueLabelText = Phrase()
    private val keyPlaceholderText = Phrase()
    private val valuePlaceholderText = Phrase()
    private val addLabelText = Phrase()
    private var addableFlag: Boolean = true
    private var deletableFlag: Boolean = true
    private var editableKeysFlag: Boolean = true
    private var editableValuesFlag: Boolean = true
    private var reorderableFlag: Boolean = false

    fun keyLabel(value: String): KeyValue = apply { keyLabelText.set(value) }

    fun keyLabel(value: Function<Get, String>): KeyValue = apply { keyLabelText.set(value) }

    fun valueLabel(value: String): KeyValue = apply { valueLabelText.set(value) }

    fun valueLabel(value: Function<Get, String>): KeyValue = apply { valueLabelText.set(value) }

    fun keyPlaceholder(value: String): KeyValue = apply { keyPlaceholderText.set(value) }

    fun valuePlaceholder(value: String): KeyValue = apply { valuePlaceholderText.set(value) }

    fun addActionLabel(value: String): KeyValue = apply { addLabelText.set(value) }

    fun addActionLabel(value: Function<Get, String>): KeyValue = apply { addLabelText.set(value) }

    fun addable(on: Boolean): KeyValue = apply { addableFlag = on }

    fun deletable(on: Boolean): KeyValue = apply { deletableFlag = on }

    fun editableKeys(on: Boolean): KeyValue = apply { editableKeysFlag = on }

    fun editableValues(on: Boolean): KeyValue = apply { editableValuesFlag = on }

    fun reorderable(on: Boolean): KeyValue = apply { reorderableFlag = on }

    fun pairs(raw: Any?): List<Pair<String, String>> {
        val decoded = FormJson.decode(raw)
        if (decoded !is Map<*, *>) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for ((key, value) in decoded) {
            if (key is String) {
                val text = if (value == null) "" else value.toString()
                out.add(key to text)
            }
        }
        return out
    }

    override fun kind(): String = "key-value"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val rows = pairs(value)
        val keyLabel = keyLabelText.get(get) ?: "Key"
        val valueLabel = valueLabelText.get(get) ?: "Value"
        return buildString {
            append("""<div class="ff-key-value" x-data="ffKeyValue" data-ff-key-value data-ff-path="${esc(path)}"${boolAttr("data-ff-addable", addableFlag)}${boolAttr("data-ff-deletable", deletableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}>""")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(FormJson.encode(rows.associate { it }))}" data-ff-key-value-value />""")
            append("""<div class="ff-key-value-head"><span>${esc(keyLabel)}</span><span>${esc(valueLabel)}</span><span class="ff-key-value-head-actions"></span></div>""")
            append("""<div class="ff-key-value-rows" data-ff-key-value-rows>""")
            for ((key, cell) in rows) append(row(get, key, cell, keyLabel, valueLabel))
            append("</div>")
            append("""<template data-ff-key-value-template>${row(get, "", "", keyLabel, valueLabel)}</template>""")
            if (addableFlag) {
                append("""<button type="button" class="ff-action-outlined ff-key-value-add" data-ff-key-value-add>${FormIcons.svg(FormIcons.PLUS)}<span>${esc(addLabelText.get(get) ?: "Add row")}</span></button>""")
            }
            append("</div>")
        }
    }

    private fun row(get: Get, key: String, cell: String, keyLabel: String, valueLabel: String): String {
        val keyMode = if (editableKeysFlag) "" else " readonly"
        val valueMode = if (editableValuesFlag) "" else " readonly"
        val inputExtra = extraInputMarkup(get)
        return buildString {
            append("""<div class="ff-key-value-row" data-ff-key-value-row>""")
            append("""<input class="ff-input" type="text" value="${esc(key)}" data-ff-key-value-key$keyMode placeholder="${esc(keyPlaceholderText.get(get) ?: keyLabel)}"$inputExtra />""")
            append("""<input class="ff-input" type="text" value="${esc(cell)}" data-ff-key-value-cell$valueMode placeholder="${esc(valuePlaceholderText.get(get) ?: valueLabel)}"$inputExtra />""")
            append("""<div class="ff-key-value-row-actions">""")
            if (reorderableFlag) {
                append(FormIcons.button("ff-icon-button", "Move up", "data-ff-key-value-up", FormIcons.ARROW_UP))
                append(FormIcons.button("ff-icon-button", "Move down", "data-ff-key-value-down", FormIcons.ARROW_DOWN))
            }
            if (deletableFlag) append(FormIcons.button("ff-icon-button ff-icon-button-danger", "Remove", "data-ff-key-value-remove", FormIcons.TRASH))
            append("</div></div>")
        }
    }

    companion object {
        private val maker = Maker { name -> KeyValue(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<KeyValue>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): KeyValue = maker.make(name)
    }
}

open class Placeholder protected constructor(name: String) : Field<Placeholder>(name) {
    private val contentText = Phrase()

    fun content(value: String): Placeholder = apply { contentText.set(value) }

    fun content(value: Function<Get, String>): Placeholder = apply { contentText.set(value) }

    override fun kind(): String = "placeholder"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String =
        """<div class="ff-placeholder">${esc(contentText.get(get) ?: "")}</div>"""

    companion object {
        private val maker = Maker { name -> Placeholder(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<Placeholder>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): Placeholder {
            val field = Placeholder(name)
            field.saved(false)
            return maker.finish(field)
        }
    }
}

open class ViewField protected constructor(name: String) : Field<ViewField>(name) {
    private var viewKey: String? = null

    fun viewName(name: String): ViewField = apply {
        viewKey = name
        view(name)
    }

    fun viewKey(): String? = viewKey

    override fun kind(): String = "view-field"

    override fun renderBody(schema: Schema): String {
        val path = fieldPath()
        val key = viewKey
        val rendered = if (key == null) null else schema.renderView(key, mapOf("path" to path))
        val body = rendered ?: """<div data-ff-view="${esc(key ?: "")}"></div>"""
        return entangle(path) + body
    }

    companion object {
        private val maker = Maker { name -> ViewField(name) }

        @JvmStatic
        fun configureUsing(block: java.util.function.Consumer<ViewField>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): ViewField = maker.make(name)
    }
}
