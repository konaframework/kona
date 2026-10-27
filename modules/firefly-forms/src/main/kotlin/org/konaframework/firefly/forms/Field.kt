package org.konaframework.firefly.forms

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function
import java.util.function.Predicate

/**
 * Shared form field. Visibility, live updates, and dehydration stay on [Component].
 * Chainable text and flags accept a fixed value or a closure that receives [Get].
 */
open class Field<T : Field<T>> protected constructor(name: String) : Component(name) {
    private val helperText = Phrase()
    private val hintText = Phrase()
    private val hintColorText = Phrase()
    private val hintIconText = Phrase()
    private val placeholderText = Phrase()
    private val prefixText = Phrase()
    private val suffixText = Phrase()
    private val prefixIconText = Phrase()
    private val suffixIconText = Phrase()
    private val prefixIconColorText = Phrase()
    private val suffixIconColorText = Phrase()
    private val aboveText = Phrase()
    private val belowText = Phrase()
    private var prefixAction: Action? = null
    private var suffixAction: Action? = null
    private var hintAction: Action? = null
    private val validationAttributeText = Phrase()
    private val requiredSwitch = Switch()
    private var markRequired: Boolean = false
    private var nullableFlag: Boolean = false
    private var readOnlyFlag: Boolean = false
    private var autofocusFlag: Boolean = false
    private var inlinePrefixFlag: Boolean = false
    private var distinctFlag: Boolean = false
    private val extraRules = ArrayList<String>()
    private val validationMessages = LinkedHashMap<String, String>()
    private var inputLiteral: Map<String, String> = emptyMap()
    private var inputUsing: Function<Get, Map<String, String>>? = null
    private var autocompleteText: String? = null
    private var autocapitalizeText: String? = null
    private var inputModeText: String? = null
    private var formatter: Function<Any?, Any?>? = null
    private var dehydrator: Function<Any?, Any?>? = null

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T

    override fun label(value: String): T {
        super.label(value)
        return self()
    }

    override fun label(value: Function<Get, String>): T {
        super.label(value)
        return self()
    }

    override fun heading(value: String): T {
        super.heading(value)
        return self()
    }

    override fun heading(value: Function<Get, String>): T {
        super.heading(value)
        return self()
    }

    override fun description(value: String): T {
        super.description(value)
        return self()
    }

    override fun description(value: Function<Get, String>): T {
        super.description(value)
        return self()
    }

    override fun extraAttributes(values: Map<String, String>): T {
        super.extraAttributes(values)
        return self()
    }

    override fun extraAttributes(provider: Function<Get, Map<String, String>>): T {
        super.extraAttributes(provider)
        return self()
    }

    fun helperText(value: String): T = self().apply { helperText.set(value) }

    fun helperText(value: Function<Get, String>): T = self().apply { helperText.set(value) }

    fun hint(value: String): T = self().apply { hintText.set(value) }

    fun hint(value: Function<Get, String>): T = self().apply { hintText.set(value) }

    fun hintColor(value: String): T = self().apply { hintColorText.set(value) }

    fun hintColor(value: Function<Get, String>): T = self().apply { hintColorText.set(value) }

    fun hintIcon(value: String): T = self().apply { hintIconText.set(value) }

    fun hintIcon(value: Function<Get, String>): T = self().apply { hintIconText.set(value) }

    fun placeholder(value: String): T = self().apply { placeholderText.set(value) }

    fun placeholder(value: Function<Get, String>): T = self().apply { placeholderText.set(value) }

    fun prefix(value: String): T = self().apply { prefixText.set(value) }

    fun prefix(value: Function<Get, String>): T = self().apply { prefixText.set(value) }

    fun suffix(value: String): T = self().apply { suffixText.set(value) }

    fun suffix(value: Function<Get, String>): T = self().apply { suffixText.set(value) }

    fun prefixIcon(value: String): T = self().apply { prefixIconText.set(value) }

    fun prefixIcon(value: Function<Get, String>): T = self().apply { prefixIconText.set(value) }

    fun suffixIcon(value: String): T = self().apply { suffixIconText.set(value) }

    fun suffixIcon(value: Function<Get, String>): T = self().apply { suffixIconText.set(value) }

    fun prefixIconColor(value: String): T = self().apply { prefixIconColorText.set(value) }

    fun prefixIconColor(value: Function<Get, String>): T = self().apply { prefixIconColorText.set(value) }

    fun suffixIconColor(value: String): T = self().apply { suffixIconColorText.set(value) }

    fun suffixIconColor(value: Function<Get, String>): T = self().apply { suffixIconColorText.set(value) }

    fun inlinePrefix(on: Boolean): T = self().apply { inlinePrefixFlag = on }

    fun aboveContent(value: String): T = self().apply { aboveText.set(value) }

    fun aboveContent(value: Function<Get, String>): T = self().apply { aboveText.set(value) }

    fun belowContent(value: String): T = self().apply { belowText.set(value) }

    fun prefixAction(action: Action): T = self().apply { prefixAction = action }

    fun suffixAction(action: Action): T = self().apply { suffixAction = action }

    fun hintAction(action: Action): T = self().apply { hintAction = action }

    fun belowContent(value: Function<Get, String>): T = self().apply { belowText.set(value) }

    fun required(): T = required(true)

    fun required(on: Boolean): T = self().apply {
        requiredSwitch.set(on)
        if (on) nullableFlag = false
    }

    fun required(predicate: Predicate<Get>): T = self().apply {
        requiredSwitch.set(predicate)
        nullableFlag = false
    }

    fun markAsRequired(on: Boolean): T = self().apply { markRequired = on }

    fun nullable(): T = self().apply {
        nullableFlag = true
        requiredSwitch.set(false)
    }

    fun readOnly(on: Boolean): T = self().apply { readOnlyFlag = on }

    fun autofocus(on: Boolean): T = self().apply { autofocusFlag = on }

    fun distinct(): T = self().apply { distinctFlag = true }

    fun isDistinct(): Boolean = distinctFlag

    fun autocomplete(value: String): T = self().apply { autocompleteText = value }

    fun autocapitalize(value: String): T = self().apply { autocapitalizeText = value }

    fun inputMode(value: String): T = self().apply { inputModeText = value }

    fun extraInputAttributes(attributes: Map<String, String>): T = self().apply {
        inputLiteral = LinkedHashMap(attributes)
        inputUsing = null
    }

    fun extraInputAttributes(provider: Function<Get, Map<String, String>>): T = self().apply {
        inputUsing = provider
        inputLiteral = emptyMap()
    }

    fun rule(rule: String): T = self().apply {
        if (rule.isBlank()) throw FireflyException("Validation rule is empty")
        extraRules.add(rule)
    }

    fun rules(vararg rules: String): T = self().apply {
        for (item in rules) rule(item)
    }

    fun validationAttribute(value: String): T = self().apply { validationAttributeText.set(value) }

    fun validationAttribute(value: Function<Get, String>): T = self().apply { validationAttributeText.set(value) }

    fun validationMessages(messages: Map<String, String>): T = self().apply {
        validationMessages.clear()
        validationMessages.putAll(messages)
    }

    fun saved(on: Boolean): T = self().apply { dehydrated(on) }

    fun formatStateUsing(formatter: Function<Any?, Any?>): T = self().apply {
        this.formatter = formatter
        installTransform()
    }

    fun dehydrateStateUsing(dehydrator: Function<Any?, Any?>): T = self().apply {
        this.dehydrator = dehydrator
        installTransform()
    }

    fun isRequired(schema: Schema): Boolean = requiredSwitch.get(Reads.of(schema, this))

    fun isMarkedRequired(): Boolean = markRequired

    fun isNullable(): Boolean = nullableFlag

    fun placeholderValue(schema: Schema): String? = placeholderText.get(Reads.of(schema, this))

    fun helperValue(schema: Schema): String? = helperText.get(Reads.of(schema, this))

    fun hintValue(schema: Schema): String? = hintText.get(Reads.of(schema, this))

    fun messageFor(rule: String): String? = validationMessages[rule]

    open fun validationRules(schema: Schema): List<String> {
        val out = ArrayList<String>()
        if (isRequired(schema)) out.add("required")
        out.addAll(collectedRules())
        return out
    }

    internal open fun collectedRules(): List<String> {
        val out = ArrayList<String>()
        if (nullableFlag) out.add("nullable")
        out.addAll(extraRules)
        return out
    }

    internal open fun extraErrors(schema: Schema): Map<String, List<String>> = emptyMap()

    internal fun attributeLabel(schema: Schema): String? {
        val explicit = validationAttributeText.get(Reads.of(schema, this))
        if (explicit != null) return explicit
        return resolvedLabel(schema)
    }

    internal fun stateValue(schema: Schema, path: String): Any? = schema.get(path)

    override fun kind(): String = "field"

    override fun renderBody(schema: Schema): String {
        val path = fieldPath()
        val get = Reads.of(schema, this)
        val value = schema.get(path)
        return chrome(schema, get, path, value)
    }

    internal open fun renderAt(schema: Schema, path: String, value: Any?): String {
        val get = Reads.of(schema, this)
        return control(schema, get, path, value)
    }

    protected fun fieldPath(): String {
        val absolute = absolutePath()
        if (!absolute.isNullOrEmpty()) return absolute
        if (name.isNotEmpty()) return name
        return "field"
    }

    protected fun chrome(schema: Schema, get: Get, path: String, value: Any?): String {
        val required = requiredSwitch.get(get) || markRequired
        return buildString {
            append(textTag("div", "ff-above", aboveText.get(get)))
            append(labelHtml(schema))
            if (required) append("""<span class="ff-required-mark">*</span>""")
            append(textTag("p", "ff-hint", hintText.get(get)))
            val hintButton = hintAction
            if (hintButton != null) append(hintButton.render(schema))
            val hintColor = hintColorText.get(get)
            val hintIcon = hintIconText.get(get)
            if (hintColor != null) append("""<span class="ff-hint-color" data-ff-hint-color="${esc(hintColor)}"></span>""")
            if (hintIcon != null) append("""<span class="ff-hint-icon" data-ff-hint-icon="${esc(hintIcon)}"></span>""")
            append("""<div class="ff-control${if (inlinePrefixFlag) " ff-inline-prefix" else ""}">""")
            append(entangle(path))
            val prefixButton = prefixAction
            if (prefixButton != null) append(prefixButton.render(schema))
            append(textTag("span", "ff-prefix", prefixText.get(get)))
            val prefixIcon = prefixIconText.get(get)
            if (prefixIcon != null) {
                append("""<span class="ff-prefix-icon" data-ff-icon="${esc(prefixIcon)}"${attr("data-ff-icon-color", prefixIconColorText.get(get))}></span>""")
            }
            append(control(schema, get, path, value))
            append(textTag("span", "ff-suffix", suffixText.get(get)))
            val suffixButton = suffixAction
            if (suffixButton != null) append(suffixButton.render(schema))
            val suffixIcon = suffixIconText.get(get)
            if (suffixIcon != null) {
                append("""<span class="ff-suffix-icon" data-ff-icon="${esc(suffixIcon)}"${attr("data-ff-icon-color", suffixIconColorText.get(get))}></span>""")
            }
            append("</div>")
            append(textTag("p", "ff-helper", helperText.get(get)))
            append(textTag("div", "ff-below", belowText.get(get)))
        }
    }

    internal open fun control(schema: Schema, get: Get, path: String, value: Any?): String = ""

    protected fun inputAttrs(get: Get, type: String, path: String, value: String): String {
        val builder = StringBuilder()
        builder.append(""" type="${esc(type)}" name="${esc(path)}" value="${esc(value)}"""")
        builder.append(attr("placeholder", placeholderText.get(get)))
        builder.append(attr("autocomplete", autocompleteText))
        builder.append(attr("autocapitalize", autocapitalizeText))
        builder.append(attr("inputmode", inputModeText))
        if (readOnlyFlag) builder.append(" readonly")
        if (autofocusFlag) builder.append(" autofocus")
        builder.append(extraInputMarkup(get))
        return builder.toString()
    }

    internal fun extraInputMarkup(get: Get): String {
        val provider = inputUsing
        val source = if (provider != null) provider.apply(get) else inputLiteral
        if (source.isEmpty()) return ""
        val filtered = LinkedHashMap<String, String>()
        for ((key, value) in source) {
            if (key.isEmpty() || key in RESERVED_INPUT) continue
            filtered[key] = value
        }
        return htmlAttributes(filtered)
    }

    private fun installTransform() {
        beforeStateDehydrated { state, _, set ->
            var next = state
            val format = formatter
            if (format != null) next = format.apply(next)
            val dry = dehydrator
            if (dry != null) next = dry.apply(next)
            set.set(name, next, false)
        }
    }

    companion object {
        private val RESERVED_INPUT = setOf("type", "name", "value")
    }
}
