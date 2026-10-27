package org.konaframework.firefly.schemas

import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer

class Grid private constructor() : Component("") {
    override fun kind(): String = "grid"

    fun schema(vararg components: Component): Grid = apply { replaceChildren(components.toList()) }

    companion object {
        @JvmStatic
        fun make(columns: Int): Grid = Component.finish(Grid().also { it.columns(columns) })

        @JvmStatic
        fun configureUsing(block: Consumer<Grid>?) = configure(Grid::class.java, block)
    }
}

class Flex private constructor() : Component("") {
    override fun kind(): String = "flex"

    fun schema(vararg components: Component): Flex = apply { replaceChildren(components.toList()) }

    companion object {
        @JvmStatic
        fun make(): Flex = Component.finish(Flex())

        @JvmStatic
        fun configureUsing(block: Consumer<Flex>?) = configure(Flex::class.java, block)
    }
}

class Fieldset private constructor(private val legend: String) : Component("") {
    private var requiredFlag: Boolean = false

    override fun kind(): String = "fieldset"

    fun required(on: Boolean): Fieldset = apply { requiredFlag = on }

    fun isRequired(): Boolean = requiredFlag

    fun schema(vararg components: Component): Fieldset = apply { replaceChildren(components.toList()) }

    override fun renderBody(schema: Schema): String {
        val mark = if (requiredFlag) """ data-ff-required="true"""" else ""
        return """<fieldset class="ff-fieldset"$mark><legend>${escapeHtml(legend)}</legend>${childHtml(schema)}</fieldset>"""
    }

    companion object {
        @JvmStatic
        fun make(legend: String): Fieldset = Component.finish(Fieldset(legend))

        @JvmStatic
        fun configureUsing(block: Consumer<Fieldset>?) = configure(Fieldset::class.java, block)
    }
}

class Section private constructor() : Component("") {
    private var asideFlag: Boolean = false
    private var formBeforeFlag: Boolean = false
    private var collapsibleFlag: Boolean = false
    private var collapsedFlag: Boolean = false
    private var containedFlag: Boolean = true
    private var afterHeader = emptyList<Component>()
    private var footer = emptyList<Component>()
    private var beforeLabel = emptyList<Component>()
    private var afterLabel = emptyList<Component>()
    private var aboveContent = emptyList<Component>()
    private var belowContent = emptyList<Component>()

    override fun kind(): String = "section"

    fun aside(on: Boolean): Section = apply { asideFlag = on }

    fun isAside(): Boolean = asideFlag

    fun formBefore(on: Boolean): Section = apply { formBeforeFlag = on }

    fun isFormBefore(): Boolean = formBeforeFlag

    /** Allow the user to collapse the section body (details/summary). */
    fun collapsible(on: Boolean = true): Section = apply { collapsibleFlag = on }

    fun isCollapsible(): Boolean = collapsibleFlag

    /** Start collapsed when [collapsible] is on. */
    fun collapsed(on: Boolean = true): Section = apply {
        collapsedFlag = on
        if (on) collapsibleFlag = true
    }

    fun isCollapsed(): Boolean = collapsedFlag

    /** When false, skip the card chrome (useful inside an already-carded host). */
    fun contained(on: Boolean): Section = apply { containedFlag = on }

    fun isContained(): Boolean = containedFlag

    fun card(on: Boolean): Section = contained(on)

    override fun columns(count: Int): Section {
        super.columns(count)
        return this
    }

    override fun columns(provider: java.util.function.Function<Get, Int>): Section {
        super.columns(provider)
        return this
    }

    override fun heading(value: String): Section {
        super.heading(value)
        return this
    }

    override fun heading(value: java.util.function.Function<Get, String>): Section {
        super.heading(value)
        return this
    }

    override fun description(value: String): Section {
        super.description(value)
        return this
    }

    override fun description(value: java.util.function.Function<Get, String>): Section {
        super.description(value)
        return this
    }

    fun schema(vararg components: Component): Section = apply { replaceChildren(components.toList()) }

    fun afterHeader(vararg components: Component): Section = apply {
        afterHeader = components.toList()
        attachOwned(afterHeader)
    }

    fun footer(vararg components: Component): Section = apply {
        footer = components.toList()
        attachOwned(footer)
    }

    fun beforeLabel(vararg components: Component): Section = apply {
        beforeLabel = components.toList()
        attachOwned(beforeLabel)
    }

    fun afterLabel(vararg components: Component): Section = apply {
        afterLabel = components.toList()
        attachOwned(afterLabel)
    }

    fun aboveContent(vararg components: Component): Section = apply {
        aboveContent = components.toList()
        attachOwned(aboveContent)
    }

    fun belowContent(vararg components: Component): Section = apply {
        belowContent = components.toList()
        attachOwned(belowContent)
    }

    override fun allChildren(): List<Component> {
        val all = ArrayList<Component>()
        all.addAll(super.allChildren())
        all.addAll(beforeLabel)
        all.addAll(afterLabel)
        all.addAll(afterHeader)
        all.addAll(aboveContent)
        all.addAll(belowContent)
        all.addAll(footer)
        return all
    }

    override fun renderBody(schema: Schema): String {
        val aside = if (asideFlag) """ data-ff-aside="true"""" else ""
        val form = if (formBeforeFlag) """ data-ff-form-before="true"""" else ""
        val contained = if (containedFlag) "" else """ data-ff-contained="false""""
        val head = headingHtml(schema)
        val desc = descriptionHtml(schema)
        val headerBits =
            "$head$desc${slot("after-header", afterHeader, schema)}${slot("before-label", beforeLabel, schema)}${labelHtml(schema)}${slot("after-label", afterLabel, schema)}${slot("above", aboveContent, schema)}"
        val body = """<div class="ff-section-body">${childHtml(schema)}</div>${slot("below", belowContent, schema)}${slot("footer", footer, schema)}"""
        // Outer Component.render already uses `.ff-component.ff-section` for the card — do not nest another `.ff-section`.
        if (collapsibleFlag) {
            val open = if (collapsedFlag) "" else " open"
            val summary = if (head.isEmpty() && desc.isEmpty()) {
                """<summary class="ff-section-summary">Section</summary>"""
            } else {
                """<summary class="ff-section-summary">$head$desc</summary>"""
            }
            val rest = if (head.isEmpty() && desc.isEmpty()) {
                headerBits + body
            } else {
                """${slot("after-header", afterHeader, schema)}${slot("before-label", beforeLabel, schema)}${labelHtml(schema)}${slot("after-label", afterLabel, schema)}${slot("above", aboveContent, schema)}$body"""
            }
            return """<details class="ff-section-inner"$aside$form$contained$open>$summary$rest</details>"""
        }
        return """<div class="ff-section-inner"$aside$form$contained>$headerBits$body</div>"""
    }

    companion object {
        @JvmStatic
        fun make(): Section = make("")

        @JvmStatic
        fun make(heading: String): Section {
            val section = Section()
            if (heading.isNotEmpty()) {
                section.heading(heading)
            }
            return finish(section)
        }

        @JvmStatic
        fun configureUsing(block: Consumer<Section>?) = configure(Section::class.java, block)
    }
}

class Tab private constructor(private val tabLabel: String) : Component("") {
    private var badgeText: String? = null
    private var badgeIconName: String? = null
    private var badgeIconAt: String = "before"
    private var badgeDeferred: Boolean = false

    override fun kind(): String = "tab"

    fun schema(vararg components: Component): Tab = apply { replaceChildren(components.toList()) }

    fun badge(text: String): Tab = apply { badgeText = text }

    fun badgeIcon(name: String): Tab = apply { badgeIconName = name }

    fun badgeIconPosition(position: String): Tab = apply {
        if (position != "before" && position != "after") {
            throw FireflyException("Unknown badge icon position [$position]")
        }
        badgeIconAt = position
    }

    fun deferBadge(): Tab = apply { badgeDeferred = true }

    fun isBadgeDeferred(): Boolean = badgeDeferred

    fun title(): String = tabLabel

    override fun renderBody(schema: Schema): String {
        val badge = badgeText
        val badgeHtml = if (badge == null) "" else """<span class="ff-badge">${escapeHtml(badge)}</span>"""
        val icon = badgeIconName
        val iconHtml = if (icon == null) "" else """<span data-ff-badge-icon="${escapeHtml(icon)}" data-ff-badge-icon-at="${escapeHtml(badgeIconAt)}"></span>"""
        val deferred = if (badgeDeferred) """ data-ff-badge-deferred="true"""" else ""
        return """<div class="ff-tab-panel" id="ff-tab-${escapeHtml(tabLabel)}"$deferred>$iconHtml$badgeHtml${childHtml(schema)}</div>"""
    }

    companion object {
        @JvmStatic
        fun make(label: String): Tab = Component.finish(Tab(label))
    }
}

class Tabs private constructor() : Component("") {
    private var active: Int = 0
    private var persistQuery: Boolean = false
    private var scroll: Boolean = false
    private var verticalFlag: Boolean = false
    private var panels: Boolean = true
    private var livewirePropertyName: String? = null
    private val startHooks = ArrayList<String>()
    private val endHooks = ArrayList<String>()

    override fun kind(): String = "tabs"

    fun tabs(vararg tabs: Tab): Tabs = apply { replaceChildren(tabs.toList()) }

    fun activeTab(index: Int): Tabs = apply { active = index }

    fun getActiveTab(): Int = active

    fun persistTabInQueryString(on: Boolean): Tabs = apply { persistQuery = on }

    fun isTabPersistedInQueryString(): Boolean = persistQuery

    fun scrollable(on: Boolean): Tabs = apply { scroll = on }

    fun isScrollable(): Boolean = scroll

    fun vertical(on: Boolean): Tabs = apply { verticalFlag = on }

    fun isVertical(): Boolean = verticalFlag

    fun tabPanels(on: Boolean): Tabs = apply { panels = on }

    fun hasTabPanels(): Boolean = panels

    fun livewireProperty(name: String): Tabs = apply { livewirePropertyName = name }

    fun getLivewireProperty(): String? = livewirePropertyName

    fun startRenderHook(name: String): Tabs = apply { startHooks.add(name) }

    fun endRenderHook(name: String): Tabs = apply { endHooks.add(name) }

    override fun renderBody(schema: Schema): String {
        val tabs = allChildren().filterIsInstance<Tab>()
        val buttons = StringBuilder()
        var index = 0
        while (index < tabs.size) {
            val current = if (index == active) """ aria-current="true"""" else ""
            buttons.append("""<button type="button" class="ff-tab"$current>${escapeHtml(tabs[index].title())}</button>""")
            index += 1
        }
        val query = if (persistQuery) """ data-ff-tab-query="true"""" else ""
        val scrollAttr = if (scroll) """ data-ff-scrollable="true"""" else ""
        val verticalAttr = if (verticalFlag) """ data-ff-vertical="true"""" else ""
        val property = livewirePropertyName
        val propertyAttr = if (property == null) "" else """ data-ff-livewire="${escapeHtml(property)}""""
        val panelHtml = if (panels) childHtml(schema) else ""
        return """<div class="ff-tabs"$query$scrollAttr$verticalAttr$propertyAttr>${hooks(startHooks)}<div class="ff-tab-list">$buttons</div>$panelHtml${hooks(endHooks)}</div>"""
    }

    companion object {
        @JvmStatic
        fun make(): Tabs = Component.finish(Tabs())

        @JvmStatic
        fun configureUsing(block: Consumer<Tabs>?) = configure(Tabs::class.java, block)
    }
}

class WizardStep private constructor() : Component("") {
    private var stepIcon: String? = null
    private var completed: String? = null
    private var wrapper: Boolean = true
    private var afterValidationNote: String? = null
    private var beforeValidationNote: String? = null

    override fun kind(): String = "wizard-step"

    fun schema(vararg components: Component): WizardStep = apply { replaceChildren(components.toList()) }

    override fun description(value: String): WizardStep {
        super.description(value)
        return this
    }

    override fun description(value: java.util.function.Function<Get, String>): WizardStep {
        super.description(value)
        return this
    }

    fun icon(name: String): WizardStep = apply { stepIcon = name }

    fun completedIcon(name: String): WizardStep = apply { completed = name }

    fun formWrapper(on: Boolean): WizardStep = apply { wrapper = on }

    fun hasFormWrapper(): Boolean = wrapper

    fun afterValidation(note: String): WizardStep = apply { afterValidationNote = note }

    fun beforeValidation(note: String): WizardStep = apply { beforeValidationNote = note }

    fun validationNotes(): Pair<String?, String?> = afterValidationNote to beforeValidationNote

    override fun renderBody(schema: Schema): String {
        val iconHtml = Markup.optionalSpan("data-ff-icon", stepIcon)
        val doneHtml = Markup.optionalSpan("data-ff-completed-icon", completed)
        val wrap = if (wrapper) """<div class="ff-wizard-form">${childHtml(schema)}</div>""" else childHtml(schema)
        return """<div class="ff-wizard-step">${headingHtml(schema)}$iconHtml$doneHtml${descriptionHtml(schema)}$wrap</div>"""
    }

    companion object {
        @JvmStatic
        fun make(label: String): WizardStep = Component.finish(WizardStep().also { it.heading(label) })
    }
}

class Wizard private constructor() : Component("") {
    private var index: Int = 0
    private var skip: Boolean = false
    private var persistQuery: Boolean = false
    private var headerHidden: Boolean = false
    private var nextLabel: String = "Next"
    private var previousLabel: String = "Back"
    private var cancelLabel: String? = null
    private var submitLabel: String? = null
    private var submitHandler: String? = null

    override fun kind(): String = "wizard"

    fun steps(vararg steps: WizardStep): Wizard = apply { replaceChildren(steps.toList()) }

    fun startOnStep(step: Int): Wizard = apply { index = step - 1 }

    fun skippable(on: Boolean): Wizard = apply { skip = on }

    fun isSkippable(): Boolean = skip

    fun persistStepInQueryString(on: Boolean): Wizard = apply { persistQuery = on }

    fun isStepPersistedInQueryString(): Boolean = persistQuery

    fun hiddenHeader(on: Boolean): Wizard = apply { headerHidden = on }

    fun isHeaderHidden(): Boolean = headerHidden

    fun nextAction(label: String): Wizard = apply { nextLabel = label }

    fun previousAction(label: String): Wizard = apply { previousLabel = label }

    fun cancelAction(label: String): Wizard = apply { cancelLabel = label }

    fun submitAction(label: String): Wizard = apply { submitLabel = label }

    fun alpineSubmitHandler(handler: String): Wizard = apply { submitHandler = handler }

    fun getCurrentStepIndex(): Int = index

    fun nextStep(): Wizard = apply {
        if (index < allChildren().size - 1) index += 1
    }

    fun previousStep(): Wizard = apply {
        if (index > 0) index -= 1
    }

    fun goToStep(step: Int): Wizard = apply {
        val target = step - 1
        val distance = if (target > index) target - index else index - target
        if (!skip && distance > 1) throw FireflyException("Wizard step [$step] is not adjacent")
        if (target < 0 || target >= allChildren().size) throw FireflyException("Wizard step [$step] is out of range")
        index = target
    }

    override fun renderBody(schema: Schema): String {
        val steps = allChildren()
        val current = if (index >= 0 && index < steps.size) steps[index].render(schema) else ""
        val header = if (headerHidden) "" else """<div class="ff-wizard-header">$index</div>"""
        val cancel = cancelLabel
        val submit = submitLabel
        val cancelHtml = if (cancel == null) "" else """<button type="button" data-ff-action="cancel">${escapeHtml(cancel)}</button>"""
        val submitHtml = if (submit == null) "" else """<button type="button" data-ff-action="submit">${escapeHtml(submit)}</button>"""
        val handler = submitHandler
        val handlerAttr = if (handler == null) "" else """ data-ff-submit="${escapeHtml(handler)}""""
        val query = if (persistQuery) """ data-ff-step-query="true"""" else ""
        return """<div class="ff-wizard"$query$handlerAttr>$header$current<div class="ff-wizard-actions">$cancelHtml<button type="button" data-ff-action="previous">${escapeHtml(previousLabel)}</button><button type="button" data-ff-action="next">${escapeHtml(nextLabel)}</button>$submitHtml</div></div>"""
    }

    companion object {
        @JvmStatic
        fun make(): Wizard = Component.finish(Wizard())

        @JvmStatic
        fun configureUsing(block: Consumer<Wizard>?) = configure(Wizard::class.java, block)
    }
}

class Callout private constructor() : Component("") {
    private var statusName: String = "info"
    private var colorName: String? = null
    private val actionLabels = ArrayList<String>()
    private val controlLabels = ArrayList<String>()
    private var footer = emptyList<Component>()
    private var controls = emptyList<Component>()

    override fun kind(): String = "callout"

    fun status(name: String): Callout = apply {
        if (name != "danger" && name != "info" && name != "success" && name != "warning") {
            throw FireflyException("Unknown callout status [$name]")
        }
        statusName = name
    }

    fun getStatus(): String = statusName

    fun danger(): Callout = status("danger")

    fun info(): Callout = status("info")

    fun success(): Callout = status("success")

    fun warning(): Callout = status("warning")

    fun color(name: String): Callout = apply { colorName = name }

    fun getColor(): String = colorName ?: statusName

    fun actions(vararg labels: String): Callout = apply {
        actionLabels.clear()
        actionLabels.addAll(labels)
    }

    fun controlActions(vararg labels: String): Callout = apply {
        controlLabels.clear()
        controlLabels.addAll(labels)
    }

    fun footer(vararg components: Component): Callout = apply {
        footer = components.toList()
        attachOwned(footer)
    }

    fun controls(vararg components: Component): Callout = apply {
        controls = components.toList()
        attachOwned(controls)
    }

    override fun allChildren(): List<Component> {
        val all = ArrayList<Component>()
        all.addAll(super.allChildren())
        all.addAll(footer)
        all.addAll(controls)
        return all
    }

    override fun renderBody(schema: Schema): String {
        val color = getColor()
        val actions = actionLabels.joinToString("") { """<button type="button">${escapeHtml(it)}</button>""" }
        val controlsHtml = controlLabels.joinToString("") { """<span class="ff-control">${escapeHtml(it)}</span>""" }
        return """<div class="ff-callout" data-ff-status="${escapeHtml(statusName)}" data-ff-color="${escapeHtml(color)}">${headingHtml(schema)}${descriptionHtml(schema)}$actions$controlsHtml${childHtml(schema)}${slot("footer", footer, schema)}${slot("controls", controls, schema)}</div>"""
    }

    companion object {
        @JvmStatic
        fun make(heading: String): Callout = Component.finish(Callout().also { it.heading(heading) })

        @JvmStatic
        fun configureUsing(block: Consumer<Callout>?) = configure(Callout::class.java, block)
    }
}

class EmptyState private constructor() : Component("") {
    private var footer = emptyList<Component>()

    override fun kind(): String = "empty"

    fun footer(vararg components: Component): EmptyState = apply {
        footer = components.toList()
        attachOwned(footer)
    }

    override fun allChildren(): List<Component> {
        val all = ArrayList<Component>()
        all.addAll(super.allChildren())
        all.addAll(footer)
        return all
    }

    override fun renderBody(schema: Schema): String =
        """<div class="ff-empty">${headingHtml(schema)}${descriptionHtml(schema)}${childHtml(schema)}${slot("footer", footer, schema)}</div>"""

    companion object {
        @JvmStatic
        fun make(heading: String): EmptyState = Component.finish(EmptyState().also { it.heading(heading) })
    }
}

private fun configure(type: Class<out Component>, block: Consumer<out Component>?) {
    if (block == null) {
        ComponentConfig.set(type, null)
        return
    }
    ComponentConfig.set(type, Consumer { component ->
        @Suppress("UNCHECKED_CAST")
        (block as Consumer<Component>).accept(component)
    })
}

private fun slot(name: String, components: List<Component>, schema: Schema): String {
    if (components.isEmpty()) return ""
    val body = components.joinToString("") { it.render(schema) }
    return """<div class="ff-slot ff-slot-$name">$body</div>"""
}

private fun hooks(names: List<String>): String =
    names.joinToString("") { """<div data-ff-hook="${escapeHtml(it)}"></div>""" }
