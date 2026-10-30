package org.konaframework.firefly.schemas

import org.konaframework.firefly.support.FireflyException
import java.util.function.Function
import java.util.function.Predicate

open class Component protected constructor(val name: String) {
    private var owner: Schema? = null
    private var parent: Component? = null
    private val childList = ArrayList<Component>()
    private var explicitStatePath: String? = null
    private var componentKey: String? = null
    private var componentId: String? = null
    private val labelValue = TextValue()
    private val headingValue = TextValue()
    private val descriptionValue = TextValue()
    private var hiddenFlag: Boolean = false
    private var hiddenUsing: Predicate<Get>? = null
    private var visibleFlag: Boolean = true
    private var visibleUsing: Predicate<Get>? = null
    private var hiddenWhen: Predicate<Get>? = null
    private var visibleWhen: Predicate<Get>? = null
    private val hiddenOperations = ArrayList<String>()
    private val visibleOperations = ArrayList<String>()
    private val truthyPaths = ArrayList<String>()
    private val falsyPaths = ArrayList<String>()
    private var hiddenJsExpr: String? = null
    private var visibleJsExpr: String? = null
    private var live: Boolean? = null
    private var liveOnBlur: Boolean = false
    private var liveDebounceMs: Int? = null
    private var disabledFlag: Boolean = false
    private var disabledUsing: Predicate<Get>? = null
    private var columnSpanValue: String? = null
    private var columnSpanUsing: Function<Get, String>? = null
    private var columnLayout: ColumnLayout? = null
    private var columnsUsing: Function<Get, Int>? = null
    private var maxWidthValue: String? = null
    private var maxWidthUsing: Function<Get, String>? = null
    private var pollInterval: String? = null
    private var viewName: String? = null
    private var dehydratedFlag: Boolean = true
    private var dehydratedWhenHiddenFlag: Boolean = false
    private var defaultLiteral: Any? = null
    private var defaultSet: Boolean = false
    private var defaultUsing: Function<Get, Any?>? = null
    private var updated: StateHook? = null
    private var updatedJs: String? = null
    private var hydrated: HydratedHook? = null
    private var beforeDehydrated: HydratedHook? = null
    private var skipRenderFlag: Boolean = false
    private var skipRenderAfterUpdateFlag: Boolean = false
    private var partialSelf: Boolean = false
    private val partialComponents = ArrayList<String>()
    private val metaMap = LinkedHashMap<String, String>()
    private val extraBag = AttributeBag()

    fun statePath(path: String): Component = apply { explicitStatePath = path }

    fun key(value: String): Component = apply { componentKey = value }

    fun key(): String? = componentKey

    fun id(value: String): Component = apply { componentId = value }

    open fun label(value: String): Component = apply { labelValue.setLiteral(value) }

    open fun label(value: Function<Get, String>): Component = apply { labelValue.setUsing(value) }

    open fun heading(value: String): Component = apply { headingValue.setLiteral(value) }

    open fun heading(value: Function<Get, String>): Component = apply { headingValue.setUsing(value) }

    open fun description(value: String): Component = apply { descriptionValue.setLiteral(value) }

    open fun description(value: Function<Get, String>): Component = apply { descriptionValue.setUsing(value) }

    fun hidden(): Component = hidden(true)

    fun hidden(on: Boolean): Component = apply {
        hiddenFlag = on
        hiddenUsing = null
    }

    fun hidden(predicate: Predicate<Get>): Component = apply { hiddenUsing = predicate }

    fun visible(): Component = visible(true)

    fun visible(on: Boolean): Component = apply {
        visibleFlag = on
        visibleUsing = null
    }

    fun visible(predicate: Predicate<Get>): Component = apply { visibleUsing = predicate }

    fun hiddenWhen(predicate: Predicate<Get>): Component = apply { hiddenWhen = predicate }

    fun visibleWhen(predicate: Predicate<Get>): Component = apply { visibleWhen = predicate }

    fun hiddenOn(vararg operations: String): Component = apply {
        hiddenOperations.clear()
        hiddenOperations.addAll(operations)
    }

    fun visibleOn(vararg operations: String): Component = apply {
        visibleOperations.clear()
        visibleOperations.addAll(operations)
    }

    fun whenTruthy(vararg paths: String): Component = apply {
        truthyPaths.clear()
        truthyPaths.addAll(paths)
    }

    fun whenFalsy(vararg paths: String): Component = apply {
        falsyPaths.clear()
        falsyPaths.addAll(paths)
    }

    fun hiddenJs(expression: String): Component = apply { hiddenJsExpr = expression }

    fun visibleJs(expression: String): Component = apply { visibleJsExpr = expression }

    fun live(): Component = apply {
        live = true
        liveOnBlur = false
        liveDebounceMs = null
        partialSelf = true
    }

    fun liveOnBlur(): Component = apply {
        live = true
        liveOnBlur = true
        liveDebounceMs = null
        partialSelf = true
    }

    fun liveDebounce(milliseconds: Int): Component = apply {
        live = true
        liveOnBlur = false
        liveDebounceMs = milliseconds
        partialSelf = true
    }

    fun liveWhen(condition: Boolean): Component = apply { live = condition }

    fun reactive(): Component = live()

    fun lazy(): Component = liveOnBlur()

    fun debounce(milliseconds: Int): Component = liveDebounce(milliseconds)

    fun disabled(): Component = disabled(true)

    fun disabled(on: Boolean): Component = apply {
        disabledFlag = on
        disabledUsing = null
    }

    fun disabled(predicate: Predicate<Get>): Component = apply { disabledUsing = predicate }

    fun columnSpan(span: Int): Component = apply {
        columnSpanValue = span.toString()
        columnSpanUsing = null
    }

    fun columnSpan(span: String): Component = apply {
        columnSpanValue = span
        columnSpanUsing = null
    }

    fun columnSpan(provider: Function<Get, String>): Component = apply { columnSpanUsing = provider }

    /** Same as [columnSpan] `"full"` — occupy every column of the parent grid. */
    fun columnSpanFull(): Component = columnSpan("full")

    /** No-arg → **2** columns from `lg` up (mobile stays 1). */
    open fun columns(): Component = apply {
        columnLayout = ColumnLayout.two()
        columnsUsing = null
    }

    /** Integer columns from `lg` up; below `lg` the grid stays 1 column. */
    open fun columns(count: Int): Component = apply {
        columnLayout = ColumnLayout.of(count)
        columnsUsing = null
    }

    /** Per-breakpoint counts (`default`, `sm`, `md`, `lg`, `xl`, `2xl`). */
    open fun columns(breakpoints: Map<String, Int>): Component = apply {
        columnLayout = ColumnLayout.from(breakpoints)
        columnsUsing = null
    }

    open fun columns(layout: ColumnLayout): Component = apply {
        columnLayout = layout
        columnsUsing = null
    }

    /** Dynamic integer columns (applied as `lg`, same as [columns] `(Int)`). */
    open fun columns(provider: Function<Get, Int>): Component = apply {
        columnsUsing = provider
        columnLayout = null
    }

    fun maxWidth(width: String): Component = apply {
        maxWidthValue = width
        maxWidthUsing = null
    }

    fun maxWidth(provider: Function<Get, String>): Component = apply { maxWidthUsing = provider }

    fun poll(interval: String): Component = apply { pollInterval = interval }

    fun view(name: String): Component = apply { viewName = name }

    fun dehydrated(): Component = dehydrated(true)

    fun dehydrated(on: Boolean): Component = apply { dehydratedFlag = on }

    fun dehydratedWhenHidden(): Component = dehydratedWhenHidden(true)

    fun dehydratedWhenHidden(on: Boolean): Component = apply { dehydratedWhenHiddenFlag = on }

    fun defaultValue(value: Any?): Component = apply {
        defaultLiteral = value
        defaultSet = true
        defaultUsing = null
    }

    fun defaultUsing(provider: Function<Get, Any?>): Component = apply {
        defaultUsing = provider
        defaultSet = true
    }

    fun afterStateUpdated(hook: StateHook): Component = apply { updated = hook }

    fun afterStateUpdatedJs(expression: String): Component = apply { updatedJs = expression }

    fun afterStateHydrated(hook: HydratedHook): Component = apply { hydrated = hook }

    fun beforeStateDehydrated(hook: HydratedHook): Component = apply { beforeDehydrated = hook }

    fun skipRender(): Component = apply { skipRenderFlag = true }

    fun skipRenderAfterStateUpdated(): Component = apply { skipRenderAfterUpdateFlag = true }

    fun partiallyRenderAfterStateUpdated(): Component = apply { partialSelf = true }

    fun partiallyRenderComponentsAfterStateUpdated(vararg keys: String): Component = apply {
        partialComponents.clear()
        partialComponents.addAll(keys)
    }

    open fun extraAttributes(values: Map<String, String>): Component = apply { extraBag.set(values) }

    open fun extraAttributes(provider: Function<Get, Map<String, String>>): Component = apply { extraBag.set(provider) }

    fun meta(key: String, value: String): Component = apply { metaMap[key] = value }

    fun meta(key: String): String? = metaMap[key]

    open fun isHidden(schema: Schema): Boolean {
        val get = schema.getFor(this)
        val hiddenClosure = hiddenUsing
        if (hiddenClosure != null) {
            if (hiddenClosure.test(get)) return true
        } else if (hiddenFlag) {
            return true
        }
        val visibleClosure = visibleUsing
        if (visibleClosure != null) {
            if (!visibleClosure.test(get)) return true
        } else if (!visibleFlag) {
            return true
        }
        val hiddenPredicate = hiddenWhen
        if (hiddenPredicate != null && hiddenPredicate.test(get)) return true
        val visiblePredicate = visibleWhen
        if (visiblePredicate != null && !visiblePredicate.test(get)) return true
        if (hiddenOperations.contains(schema.operation())) return true
        if (visibleOperations.isNotEmpty() && !visibleOperations.contains(schema.operation())) return true
        for (path in truthyPaths) {
            if (!isTruthy(get.get(path))) return true
        }
        for (path in falsyPaths) {
            if (isTruthy(get.get(path))) return true
        }
        return false
    }

    fun isDehydrated(): Boolean = dehydratedFlag

    fun isDehydratedWhenHidden(): Boolean = dehydratedWhenHiddenFlag

    fun absolutePath(): String? {
        if (explicitStatePath != null) return explicitStatePath
        val own = if (name.isEmpty()) null else name
        val prefix = parentPrefix()
        if (own == null) return prefix
        if (prefix.isNullOrEmpty()) return own
        return "$prefix.$own"
    }

    fun containerPath(): String {
        val parentPath = parent?.absolutePath()
        if (parentPath != null) return parentPath
        val prefix = owner?.statePrefix()
        if (prefix == null) return ""
        return prefix
    }

    internal fun bind(schema: Schema?, parent: Component?) {
        owner = schema
        this.parent = parent
    }

    internal fun updatedHook(): StateHook? = updated

    internal fun hydratedHook(): HydratedHook? = hydrated

    internal fun skipsRenderAfterUpdate(): Boolean = skipRenderAfterUpdateFlag

    internal fun partialAfterUpdate(path: String): List<String> {
        if (partialComponents.isNotEmpty()) return partialComponents.toList()
        if (!partialSelf) return emptyList()
        val key = componentKey
        if (key != null) return listOf(key)
        return listOf(path)
    }

    internal fun provideDefault(get: Get): Pair<Boolean, Any?> {
        val provider = defaultUsing
        if (provider != null) return true to provider.apply(get)
        if (defaultSet) return true to defaultLiteral
        return false to null
    }

    internal fun beforeDehydrated(get: Get, set: Set) {
        Markup.hydrated(beforeDehydrated, Markup.stateAt(owner, absolutePath()), get, set)
    }

    internal open fun allChildren(): List<Component> = childList.toList()

    /** Direct schema-body children (not Section/Callout slots). */
    fun children(): List<Component> = childList.toList()

    /** Replace schema-body children (used when remapping a form layout into an infolist). */
    fun children(vararg components: Component): Component = apply { replaceChildren(components.toList()) }

    /**
     * Copies column span, columns grid, max width, and label / heading / description onto [target]
     * so a derived infolist entry keeps the form field’s layout chrome.
     */
    fun copyPresentationOnto(target: Component): Component {
        val spanUsing = columnSpanUsing
        if (spanUsing != null) {
            target.columnSpan(spanUsing)
        } else {
            val span = columnSpanValue
            if (span != null) target.columnSpan(span)
        }
        val colsUsing = columnsUsing
        if (colsUsing != null) {
            target.columns(colsUsing)
        } else {
            val layout = columnLayout
            if (layout != null) target.columns(layout)
        }
        val widthUsing = maxWidthUsing
        if (widthUsing != null) {
            target.maxWidth(widthUsing)
        } else {
            val width = maxWidthValue
            if (width != null) target.maxWidth(width)
        }
        labelValue.copyOnto(target.labelValue)
        headingValue.copyOnto(target.headingValue)
        descriptionValue.copyOnto(target.descriptionValue)
        return target
    }

    protected fun attachOwned(next: List<Component>) {
        val schema = owner
        if (schema == null) return
        for (child in next) schema.attach(child, this)
    }

    protected fun replaceChildren(next: List<Component>) {
        childList.clear()
        childList.addAll(next)
        attachOwned(next)
    }

    fun render(schema: Schema): String {
        if (skipRenderFlag) return ""
        val path = absolutePath()
        val classes = StringBuilder("ff-component ff-")
        classes.append(kind())
        if (isHidden(schema)) classes.append(" ff-hidden")
        if (isDisabled(schema)) classes.append(" ff-disabled")
        val attrs = StringBuilder()
        appendAttr(attrs, "data-ff-path", path)
        appendAttr(attrs, "id", componentId)
        appendAttr(attrs, "wire:key", componentKey)
        val span = resolvedSpan(schema)
        appendAttr(attrs, "data-ff-span", span)
        val layout = resolvedColumnLayout(schema)
        if (layout != null) {
            appendAttr(attrs, "data-ff-columns", layout.dataValue())
        }
        val styleBits = ArrayList<String>()
        if (layout != null) styleBits.add(layout.styleAttribute())
        val spanCols = span?.toIntOrNull()
        if (spanCols != null && spanCols > 0) styleBits.add("--ff-span: $spanCols")
        if (styleBits.isNotEmpty()) attrs.append(""" style="${styleBits.joinToString("; ")}"""")
        appendAttr(attrs, "data-ff-max-width", resolvedMaxWidth(schema))
        appendAttr(attrs, "data-ff-hidden-js", hiddenJsExpr)
        appendAttr(attrs, "data-ff-visible-js", visibleJsExpr)
        appendAttr(attrs, "data-ff-updated-js", updatedJs)
        val poll = pollInterval
        if (poll != null) attrs.append(" wire:poll.${escapeHtml(poll)}=\"partial\"")
        if (path != null && live == true) attrs.append(" ${binding(path)}")
        attrs.append(htmlAttributes(extraBag.resolve(schema.getFor(this))))
        val body = renderBody(schema)
        return """<div class="$classes"$attrs>$body</div>"""
    }

    protected open fun kind(): String = "component"

    protected open fun renderBody(schema: Schema): String {
        val namedView = viewName
        if (namedView != null) {
            val rendered = schema.renderView(namedView, viewData(schema))
            if (rendered != null) return rendered
            return """<div data-ff-view="${escapeHtml(namedView)}"></div>"""
        }
        val chrome = labelHtml(schema) + headingHtml(schema) + descriptionHtml(schema)
        return chrome + childList.joinToString("") { it.render(schema) }
    }

    protected fun childHtml(schema: Schema): String = childList.joinToString("") { it.render(schema) }

    private fun viewData(schema: Schema): Map<String, Any?> {
        val path = absolutePath() ?: ""
        return mapOf("path" to path, "content" to childHtml(schema), "label" to (labelValue.resolve(schema.getFor(this)) ?: ""))
    }

    private fun binding(path: String): String {
        val entangle = """data-ff-entangle="@entangle(${escapeHtml(path)})""""
        if (liveOnBlur) return """wire:model.blur="${escapeHtml(path)}" $entangle"""
        val debounce = liveDebounceMs
        if (debounce != null) return """wire:model.live.debounce.${debounce}ms="${escapeHtml(path)}" $entangle"""
        return """wire:model.live="${escapeHtml(path)}" $entangle"""
    }

    private fun parentPrefix(): String? {
        val fromParent = parent?.absolutePath()
        if (fromParent != null) return fromParent
        val prefix = owner?.statePrefix()
        if (prefix.isNullOrEmpty()) return null
        return prefix
    }

    protected fun labelHtml(schema: Schema): String {
        val text = resolvedLabel(schema) ?: return ""
        return """<span class="ff-label">${escapeHtml(text)}</span>"""
    }

    /** Explicit label, or a humanized field name when the component has a name. */
    protected fun resolvedLabel(schema: Schema): String? {
        if (labelValue.isSet()) return labelValue.resolve(schema.getFor(this))
        if (name.isBlank()) return null
        return humanizeName(name)
    }

    protected fun headingHtml(schema: Schema): String {
        val text = headingValue.resolve(schema.getFor(this))
        if (text == null) return ""
        val level = headingLevel(schema)
        return """<h$level class="ff-heading">${escapeHtml(text)}</h$level>"""
    }

    protected fun descriptionHtml(schema: Schema): String {
        val text = descriptionValue.resolve(schema.getFor(this))
        if (text == null) return ""
        return """<p class="ff-description">${escapeHtml(text)}</p>"""
    }

    private fun isDisabled(schema: Schema): Boolean {
        val predicate = disabledUsing
        if (predicate != null) return predicate.test(schema.getFor(this))
        return disabledFlag
    }

    private fun resolvedSpan(schema: Schema): String? {
        val provider = columnSpanUsing
        if (provider != null) return provider.apply(schema.getFor(this))
        return columnSpanValue
    }

    private fun resolvedColumnLayout(schema: Schema): ColumnLayout? {
        val provider = columnsUsing
        if (provider != null) return ColumnLayout.of(provider.apply(schema.getFor(this)))
        return columnLayout
    }

    private fun resolvedMaxWidth(schema: Schema): String? {
        val provider = maxWidthUsing
        if (provider != null) return provider.apply(schema.getFor(this))
        return maxWidthValue
    }

    private fun headingLevel(schema: Schema): Int {
        var level = schema.headingLevel()
        var cursor = parent
        while (cursor != null) {
            if (cursor.headingValue.isSet()) level += 1
            cursor = cursor.parent
        }
        if (level > 6) return 6
        return level
    }

    companion object {
        internal fun <T : Component> finish(component: T): T {
            ComponentConfig.apply(component)
            return component
        }
    }
}

internal class AttributeBag {
    private var literal: Map<String, String> = emptyMap()
    private var using: Function<Get, Map<String, String>>? = null

    fun set(values: Map<String, String>) {
        literal = LinkedHashMap(values)
        using = null
    }

    fun set(provider: Function<Get, Map<String, String>>) {
        using = provider
        literal = emptyMap()
    }

    fun resolve(get: Get): Map<String, String> {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class TextValue {
    private var literal: String? = null
    private var using: Function<Get, String>? = null

    fun setLiteral(value: String) {
        literal = value
        using = null
    }

    fun setUsing(provider: Function<Get, String>) {
        using = provider
        literal = null
    }

    fun isSet(): Boolean = using != null || literal != null

    fun resolve(get: Get): String? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }

    fun copyOnto(other: TextValue) {
        val provider = using
        if (provider != null) {
            other.setUsing(provider)
            return
        }
        val text = literal
        if (text != null) other.setLiteral(text)
    }
}

internal fun appendAttr(out: StringBuilder, name: String, value: String?) {
    if (value == null) return
    out.append(" ")
    out.append(name)
    out.append("=\"")
    out.append(escapeHtml(value))
    out.append("\"")
}
