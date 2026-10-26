package org.konaframework.firefly.forms

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

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
        val builder = StringBuilder()
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
        if (revealableFlag) builder.append("""<button type="button" class="ff-reveal" data-ff-reveal="true">Show</button>""")
        if (copyableFlag) builder.append("""<button type="button" class="ff-copy" data-ff-copy="true">Copy</button>""")
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
        return """<textarea class="ff-input" name="${esc(path)}" rows="$rowCount"${if (cols == null) "" else " cols=\"$cols\""}${boolAttr("data-ff-autosize", autosizeFlag)}${attr("placeholder", placeholderValue(schema))}>${esc(shown)}</textarea>"""
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
        return """${entangle(path)}<input class="ff-input" type="hidden" name="${esc(path)}" value="${esc(shown)}" />"""
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

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val tags = tags(value)
        val keys = if (splitKeys.isEmpty()) "Enter" else splitKeys.joinToString(",")
        return buildString {
            append("""<div class="ff-tags" x-data="ffTags" data-ff-path="${esc(path)}"${attr("data-ff-separator", separatorText)}${attr("data-ff-split", keys)}${boolAttr("data-ff-reorder", reorderableFlag)}${attr("data-ff-color", colorText)}>""")
            for (tag in tags) append("""<span class="ff-tag">${esc(tag)}</span>""")
            append("""<input class="ff-input" type="text"${attr("placeholder", placeholderValue(schema))} />""")
            if (suggestions.isNotEmpty()) {
                append("""<ul class="ff-suggestions">""")
                for (item in suggestions) append("""<li>${esc(item)}</li>""")
                append("</ul>")
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
        return """<input class="ff-input ff-color" x-data="ffColor"${inputAttrs(get, "text", path, shown)} data-ff-format="${esc(formatName)}" />"""
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
        return """<input class="ff-input ff-slider" x-data="ffSlider"${inputAttrs(get, "range", path, shown.toString())} min="$minNumber" max="$maxNumber" step="$stepNumber"${boolAttr("data-ff-tooltips", tooltipsFlag)} />"""
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
        if (raw !is Map<*, *>) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for ((key, value) in raw) {
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
        return buildString {
            append("""<div class="ff-key-value" x-data="ffKeyValue" data-ff-path="${esc(path)}"${boolAttr("data-ff-addable", addableFlag)}${boolAttr("data-ff-deletable", deletableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}>""")
            append("""<div class="ff-key-value-head"><span>${esc(keyLabelText.get(get) ?: "Key")}</span><span>${esc(valueLabelText.get(get) ?: "Value")}</span></div>""")
            for ((key, cell) in rows) {
                val keyMode = if (editableKeysFlag) "" else " readonly"
                val valueMode = if (editableValuesFlag) "" else " readonly"
                append("""<div class="ff-key-value-row"><input class="ff-input" value="${esc(key)}"$keyMode${attr("placeholder", keyPlaceholderText.get(get))} /><input class="ff-input" value="${esc(cell)}"$valueMode${attr("placeholder", valuePlaceholderText.get(get))} /></div>""")
            }
            if (addableFlag) append("""<button type="button" class="ff-key-value-add">${esc(addLabelText.get(get) ?: "Add row")}</button>""")
            append("</div>")
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
