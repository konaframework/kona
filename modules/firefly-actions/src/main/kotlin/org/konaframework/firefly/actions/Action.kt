package org.konaframework.firefly.actions

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Icon
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * A button, link, or confirmed dialog action.
 *
 * Modal, sidebar, and fullscreen presentation call [Dialog]. Schema components passed to [schema]
 * are the dialog body. This type does not add form fields.
 */
open class Action protected constructor(name: String) : Component(name) {
    private val authorization = Authorization()
    private val iconSetting = TextSetting()
    private val colorSetting = TextSetting()
    private val tooltipSetting = TextSetting()
    private val badgeSetting = TextSetting()
    private val badgeColorSetting = TextSetting()
    private val urlSetting = TextSetting()
    private val sizeSetting = TextSetting()
    private val iconPositionSetting = TextSetting()
    private val modalHeadingSetting = TextSetting()
    private val modalDescriptionSetting = TextSetting()
    private val modalSubmitSetting = TextSetting()
    private val modalCancelSetting = TextSetting()
    private val modalIconSetting = TextSetting()
    private val modalIconColorSetting = TextSetting()
    private val modalWidthSetting = TextSetting()
    private val successRedirectSetting = TextSetting()
    private val failureRedirectSetting = TextSetting()
    private val confirm = Flag(false)
    private val openInNewTab = Flag(false)
    private var style: String = "button"
    private var placementToken: ActionPlacement = ActionPlacement.SCHEMA
    private var mode: String = "modal"
    private var mount: Boolean = false
    private var suppress: Boolean = false
    private var modalHiddenFlag: Boolean = false
    private var closeAway: Boolean = true
    private var closeEscape: Boolean = true
    /** Official Dialog header close (`ff-dialog-close`). Default on. */
    private var showClose: Boolean = true
    private var autofocus: Boolean = true
    private var draggable: Boolean = true
    private var modalComponents: List<Component> = emptyList()
    /** When set (via [schema] with a [Schema]), modal body keeps that schema’s record for relationship Selects. */
    private var modalForm: Schema? = null
    private var submitAction: Action? = null
    private var cancelAction: Action? = null
    private var footerActions: List<Action> = emptyList()
    private var callback: ActionCallback? = null
    private var beforeCallback: ActionCallback? = null
    private var afterCallback: ActionCallback? = null
    private var arguments: Map<String, Any?> = emptyMap()
    /** When set, the trigger is a native submit button associated with that form id (Shamar save). */
    private var submitFormId: String? = null
    /** When set, the dialog confirm control POSTs to this URL (delete / destructive confirms). */
    private val postUrlSetting = TextSetting()
    private var formExtrasHtml: String = ""

    init {
        requireActionName(name)
    }

    override fun kind(): String = "action"

    override fun label(value: String): Action {
        super.label(value)
        return this
    }

    override fun label(value: Function<Get, String>): Action {
        super.label(value)
        return this
    }

    override fun extraAttributes(values: Map<String, String>): Action {
        checkAttributes(values)
        super.extraAttributes(values)
        return this
    }

    override fun extraAttributes(provider: Function<Get, Map<String, String>>): Action {
        super.extraAttributes(Function { get ->
            val values = provider.apply(get)
            checkAttributes(values)
            values
        })
        return this
    }

    override fun isHidden(schema: Schema): Boolean {
        if (!isAuthorized(schema)) return true
        return super.isHidden(schema)
    }

    fun isAuthorized(schema: Schema): Boolean = authorization.permits(schema, this)

    fun icon(value: String): Action = apply { iconSetting.setLiteral(value) }

    fun icon(value: Function<Get, String>): Action = apply { iconSetting.setUsing(value) }

    fun color(value: String): Action = apply { colorSetting.setLiteral(value) }

    fun color(value: Function<Get, String>): Action = apply { colorSetting.setUsing(value) }

    fun tooltip(value: String): Action = apply { tooltipSetting.setLiteral(value) }

    fun tooltip(value: Function<Get, String>): Action = apply { tooltipSetting.setUsing(value) }

    fun badge(value: String): Action = apply { badgeSetting.setLiteral(value) }

    fun badge(value: Function<Get, String>): Action = apply { badgeSetting.setUsing(value) }

    fun badgeColor(value: String): Action = apply { badgeColorSetting.setLiteral(value) }

    fun badgeColor(value: Function<Get, String>): Action = apply { badgeColorSetting.setUsing(value) }

    fun size(value: String): Action = apply { sizeSetting.setLiteral(value) }

    fun size(value: Function<Get, String>): Action = apply { sizeSetting.setUsing(value) }

    fun iconPosition(value: String): Action = apply { iconPositionSetting.setLiteral(value) }

    fun iconPosition(value: Function<Get, String>): Action = apply { iconPositionSetting.setUsing(value) }

    fun button(): Action = apply { style = "button" }

    fun link(): Action = apply { style = "link" }

    fun iconButton(): Action = apply { style = "icon-button" }

    fun outlined(): Action = apply { style = "outlined" }

    fun grouped(): Action = apply { style = "grouped" }

    /**
     * Render a native `<button type="submit" form="…">` so header actions can save a page form
     * that lives outside the header (Shamar create/edit). Clears any URL so the button posts.
     */
    @JvmOverloads
    fun submitForm(formId: String = "ff-form"): Action = apply {
        if (formId.isBlank()) throw FireflyException("Submit form id is blank")
        submitFormId = formId
        urlSetting.clear()
    }

    /**
     * Confirm button becomes a native POST form (CSRF + hidden fields in [extrasHtml]).
     * Used for delete when Wire/Alpine are not mounted on the panel chrome.
     */
    @JvmOverloads
    fun postForm(actionUrl: String, extrasHtml: String = ""): Action = apply {
        if (actionUrl.isBlank()) throw FireflyException("Post form action is blank")
        postUrlSetting.setLiteral(actionUrl)
        formExtrasHtml = extrasHtml
        submitFormId = null
        requiresConfirmation(true)
        modal(true)
    }

    fun placement(placement: ActionPlacement): Action = apply { placementToken = placement }

    fun placement(): ActionPlacement = placementToken

    fun authorize(on: Boolean): Action = apply { authorization.allow(on) }

    fun authorize(predicate: Predicate<Get>): Action = apply { authorization.allow(predicate) }

    fun authorize(ability: String): Action = apply { authorization.ability(ability) }

    fun url(value: String): Action = apply { urlSetting.setLiteral(value) }

    fun url(value: Function<Get, String>): Action = apply { urlSetting.setUsing(value) }

    fun openUrlInNewTab(on: Boolean): Action = apply { openInNewTab.setLiteral(on) }

    fun openUrlInNewTab(predicate: Predicate<Get>): Action = apply { openInNewTab.setUsing(predicate) }

    fun requiresConfirmation(): Action = requiresConfirmation(true)

    fun requiresConfirmation(on: Boolean): Action = apply { confirm.setLiteral(on) }

    fun requiresConfirmation(predicate: Predicate<Get>): Action = apply { confirm.setUsing(predicate) }

    fun modal(): Action = apply {
        mode = "modal"
        mount = true
        suppress = false
    }

    fun modal(on: Boolean): Action = apply {
        if (on) {
            mode = "modal"
            mount = true
            suppress = false
        } else {
            suppress = true
            mount = false
        }
    }

    fun slideOver(): Action = apply {
        mode = "sidebar-end"
        mount = true
        suppress = false
    }

    fun slideOverStart(): Action = apply {
        mode = "sidebar-start"
        mount = true
        suppress = false
    }

    fun fullscreen(): Action = apply {
        mode = "fullscreen"
        mount = true
        suppress = false
    }

    fun modalWidth(value: String): Action = apply { modalWidthSetting.setLiteral(value) }

    fun modalWidth(value: Function<Get, String>): Action = apply { modalWidthSetting.setUsing(value) }

    fun modalDraggable(on: Boolean): Action = apply { draggable = on }

    fun modalHeading(value: String): Action = apply { modalHeadingSetting.setLiteral(value) }

    fun modalHeading(value: Function<Get, String>): Action = apply { modalHeadingSetting.setUsing(value) }

    fun modalDescription(value: String): Action = apply { modalDescriptionSetting.setLiteral(value) }

    fun modalDescription(value: Function<Get, String>): Action = apply { modalDescriptionSetting.setUsing(value) }

    fun modalSubmitActionLabel(value: String): Action = apply { modalSubmitSetting.setLiteral(value) }

    fun modalSubmitActionLabel(value: Function<Get, String>): Action = apply { modalSubmitSetting.setUsing(value) }

    fun modalCancelActionLabel(value: String): Action = apply { modalCancelSetting.setLiteral(value) }

    fun modalCancelActionLabel(value: Function<Get, String>): Action = apply { modalCancelSetting.setUsing(value) }

    fun modalIcon(value: String): Action = apply { modalIconSetting.setLiteral(value) }

    fun modalIcon(value: Function<Get, String>): Action = apply { modalIconSetting.setUsing(value) }

    fun modalIconColor(value: String): Action = apply { modalIconColorSetting.setLiteral(value) }

    fun modalIconColor(value: Function<Get, String>): Action = apply { modalIconColorSetting.setUsing(value) }

    fun modalHidden(on: Boolean): Action = apply { modalHiddenFlag = on }

    fun closeModalByClickingAway(on: Boolean): Action = apply { closeAway = on }

    fun closeModalByEscaping(on: Boolean): Action = apply { closeEscape = on }

    fun modalCloseButton(on: Boolean): Action = apply { showClose = on }

    fun modalAutofocus(on: Boolean): Action = apply { autofocus = on }

    fun schema(vararg components: Component): Action = apply {
        for (component in components) {
            if (component === this) throw FireflyException("Action [$name] cannot contain itself")
        }
        modalForm = null
        modalComponents = components.toList()
        syncChildren()
    }

    /**
     * Dialog body from a full [Schema] (keeps [Schema.record] so `Select.relationship` can resolve
     * options on create/manage dialogs).
     */
    fun schema(form: Schema): Action = apply {
        for (component in form.components()) {
            if (component === this) throw FireflyException("Action [$name] cannot contain itself")
        }
        modalForm = form
        modalComponents = form.components()
        syncChildren()
    }

    fun modalSubmitAction(action: Action): Action = apply {
        if (action === this) throw FireflyException("Action [$name] cannot submit itself")
        submitAction = action
        syncChildren()
    }

    fun modalCancelAction(action: Action): Action = apply {
        if (action === this) throw FireflyException("Action [$name] cannot cancel itself")
        cancelAction = action
        syncChildren()
    }

    fun extraModalFooterActions(vararg actions: Action): Action = apply {
        for (action in actions) {
            if (action === this) throw FireflyException("Action [$name] cannot contain itself")
        }
        footerActions = actions.toList()
        syncChildren()
    }

    fun action(callback: ActionCallback): Action = apply { this.callback = callback }

    fun before(callback: ActionCallback): Action = apply { beforeCallback = callback }

    fun after(callback: ActionCallback): Action = apply { afterCallback = callback }

    fun arguments(values: Map<String, Any?>): Action = apply { arguments = LinkedHashMap(values) }

    fun argument(key: String): Any? = arguments[key]

    fun successRedirectUrl(value: String): Action = apply { successRedirectSetting.setLiteral(value) }

    fun successRedirectUrl(value: Function<Get, String>): Action = apply { successRedirectSetting.setUsing(value) }

    fun failureRedirectUrl(value: String): Action = apply { failureRedirectSetting.setLiteral(value) }

    fun failureRedirectUrl(value: Function<Get, String>): Action = apply { failureRedirectSetting.setUsing(value) }

    private fun checkAttributes(values: Map<String, String>) {
        for ((key, _) in values) {
            if (!ATTRIBUTE.matches(key)) throw FireflyException("Invalid attribute [$key]")
            if (RESERVED.contains(key)) throw FireflyException("Attribute [$key] is reserved")
        }
    }

    fun halt(): Nothing = throw ActionHalt()

    fun failure(message: String): Nothing = throw ActionFailure(message)

    fun mountDialog(schema: Schema): Dialog {
        val get = SchemaBridge.reader(schema, this)
        if (!showsDialog(get)) throw FireflyException("Action [$name] has no dialog")
        return buildDialog(schema, get)
    }

    fun call(schema: Schema): ActionResult {
        if (isHidden(schema)) throw FireflyException("Action [$name] is hidden")
        if (disabledNow(schema)) throw FireflyException("Action [$name] is disabled")
        val get = SchemaBridge.reader(schema, this)
        try {
            val before = beforeCallback
            if (before != null) before.run(this, schema)
            val resolved = urlSetting.resolve(get)
            if (resolved != null && resolved.isEmpty()) throw FireflyException("Action [$name] URL is empty")
            if (resolved == null) {
                val handler = callback
                if (handler == null) throw FireflyException("Action [$name] has no handler")
                handler.run(this, schema)
            }
            val after = afterCallback
            if (after != null) after.run(this, schema)
            val redirect = if (resolved != null) resolved else successRedirectSetting.resolve(get)
            return ActionResult(ActionResult.SUCCESS, resolved, redirect, null)
        } catch (halted: ActionHalt) {
            return ActionResult(ActionResult.HALTED, null, null, halted.message)
        } catch (failed: ActionFailure) {
            return ActionResult(ActionResult.FAILED, null, failureRedirectSetting.resolve(get), failed.message)
        }
    }

    override fun renderBody(schema: Schema): String {
        if (isHidden(schema)) return ""
        val named = storedView()
        if (named != null) return renderNamedView(schema, named)
        val get = SchemaBridge.reader(schema, this)
        val opens = showsDialog(get)
        val trigger = triggerHtml(schema, get, opens)
        if (!opens) return trigger
        // open=true: parent mount controls visibility (display:none / x-show). Closed dialogs must
        // not use Dialog's default hidden chrome alone without a mount — that still covered the page
        // when TableButton dumped raw Dialog HTML.
        val dialogHtml = decorate(buildDialog(schema, get).render(open = true))
        val escape = if (closeEscape) """ @keydown.escape.window="open = false"""" else ""
        // data-ff-dialog-*: chrome script opens/closes without Alpine (DefaultLayout has no Alpine).
        // Alpine attrs remain for Wire-hosted mounts.
        return """<div class="ff-action-mount ${placementClass()}" data-ff-dialog-mount x-data="{ open: false }"$escape>$trigger<div class="ff-action-dialog" data-ff-dialog-panel x-show="open" x-cloak style="display:none" hidden>$dialogHtml</div></div>"""
    }

    private fun renderNamedView(schema: Schema, named: String): String {
        val get = SchemaBridge.reader(schema, this)
        val label = storedLabel(get)
        val data = mapOf(
            "path" to absolutePath(),
            "name" to name,
            "label" to (if (label == null) name else label),
            "content" to modalComponents.joinToString("") { it.render(schema) },
        )
        val rendered = schema.renderView(named, data)
        if (rendered != null) return rendered
        return """<div data-ff-view="${escapeHtml(named)}"></div>"""
    }

    private fun showsDialog(get: Get): Boolean {
        if (suppress) return false
        if (modalHiddenFlag) return false
        if (mount) return true
        if (confirm.resolve(get)) return true
        if (modalComponents.isNotEmpty()) return true
        if (modalHeadingSetting.isSet() || modalDescriptionSetting.isSet()) return true
        return false
    }

    private fun buildDialog(schema: Schema, get: Get): Dialog {
        val dialog = Dialog.make(dialogTitle(get))
        if (mode == "sidebar-end") dialog.slideOver()
        else if (mode == "sidebar-start") dialog.slideOverStart()
        else if (mode == "fullscreen") dialog.fullscreen()
        else dialog.modal()
        val width = modalWidthSetting.resolve(get)
        if (width != null) {
            if (!WIDTHS.contains(width)) throw FireflyException("Unknown dialog width [$width]")
            dialog.width(width)
        }
        dialog.draggable(draggable)
        dialog.closeButton(showClose)
        dialog.body(dialogBody(schema, get))
        return dialog
    }

    private fun dialogTitle(get: Get): String {
        val heading = modalHeadingSetting.resolve(get)
        if (heading != null) return heading
        val label = storedLabel(get)
        if (label != null) return label
        return name
    }

    private fun dialogBody(schema: Schema, get: Get): String {
        val icon = modalIconHtml(get)
        val description = modalDescriptionSetting.resolve(get)
        val descriptionHtml = if (description == null) {
            ""
        } else {
            """<p class="ff-action-description">${escapeHtml(description)}</p>"""
        }
        val footer = footerHtml(schema, get)
        val post = postUrlSetting.resolve(get)
        // Create/edit: one native form wraps fields + footer so inputs POST with Submit.
        // Confirm-only (delete): empty schema keeps a mini form around the submit button.
        // Close (X) lives on the official Dialog header — not duplicated in the body.
        if (post != null && modalComponents.isNotEmpty()) {
            // Flat field names (`name`, not `create.name`) so Cadence/store columns match.
            // Prefer [modalForm] so relationship Selects keep the resource model record (#337).
            // Fresh Schema re-roots fields (flat `name`, not `create.name`) and copies the record
            // + column grid so create dialogs match edit (`formColumns` / schema.columns) (#358).
            val form = modalForm
            val schemaHtml =
                if (form != null) {
                    val rebuilt =
                        Schema.make()
                            .record(form.record())
                            .components(*form.components().toTypedArray())
                    val cols = form.columns()
                    if (cols != null) rebuilt.columns(cols)
                    rebuilt.render()
                } else {
                    Schema.make().components(*modalComponents.toTypedArray()).render()
                }
            syncChildren()
            return """$icon$descriptionHtml<form method="post" action="${escapeHtml(post)}" class="ff-action-form">$formExtrasHtml<div class="ff-action-schema">$schemaHtml</div><div class="ff-action-footer">$footer</div></form>"""
        }
        val schemaHtml = modalComponents.joinToString("") { it.render(schema) }
        return """$icon$descriptionHtml<div class="ff-action-schema">$schemaHtml</div><div class="ff-action-footer">$footer</div>"""
    }

    private fun modalIconHtml(get: Get): String {
        val iconName = modalIconSetting.resolve(get)
        if (iconName == null) return ""
        val color = modalIconColorSetting.resolve(get)
        val attr = if (color == null) "" else """ data-ff-color="${escapeHtml(color)}""""
        return """<span class="ff-action-modal-icon"$attr>${Icon.fromName(iconName).svg()}</span>"""
    }

    private fun footerHtml(schema: Schema, get: Get): String {
        val submit = submitAction
        val submitHtml = if (submit != null) submit.render(schema) else defaultSubmit(get)
        val cancel = cancelAction
        val cancelHtml = if (cancel != null) cancel.render(schema) else defaultCancel(get)
        val extra = footerActions.joinToString("") { it.render(schema) }
        return submitHtml + cancelHtml + extra
    }

    private fun defaultSubmit(get: Get): String {
        val custom = modalSubmitSetting.resolve(get)
        val text = if (custom != null) custom else if (confirm.resolve(get)) "Confirm" else "Submit"
        val focus = if (autofocus) " autofocus" else ""
        val label = escapeHtml(text)
        val post = postUrlSetting.resolve(get)
        if (post != null) {
            if (modalComponents.isNotEmpty()) {
                // dialogBody already opened the field POST form.
                return """<button type="submit" class="ff-action-submit"$focus>$label</button>"""
            }
            return """<form method="post" action="${escapeHtml(post)}" class="ff-action-post" style="display:inline">$formExtrasHtml<button type="submit" class="ff-action-submit"$focus>$label</button></form>"""
        }
        val link = urlSetting.resolve(get)
        if (link != null) {
            val target = if (openInNewTab.resolve(get)) """ target="_blank" rel="noopener noreferrer"""" else ""
            return """<a class="ff-action-submit" x-data="ffAction('$name')" href="${escapeHtml(link)}"$target$focus>$label</a>"""
        }
        return """<button type="button" class="ff-action-submit" x-data="ffAction('$name')" wire:click="$name"$focus>$label</button>"""
    }

    private fun defaultCancel(get: Get): String {
        val custom = modalCancelSetting.resolve(get)
        val text = if (custom != null) custom else "Cancel"
        return """<button type="button" class="ff-action-cancel" data-ff-dialog-close @click="open = false">${escapeHtml(text)}</button>"""
    }

    private fun triggerHtml(schema: Schema, get: Get, opens: Boolean): String {
        val text = storedLabel(get) ?: name
        val iconName = iconSetting.resolve(get)
        val iconHtml = if (iconName == null) "" else Icon.fromName(iconName).svg()
        val position = iconPositionSetting.resolve(get) ?: "before"
        if (position != "before" && position != "after") throw FireflyException("Unknown icon position [$position]")
        val visible = if (style == "icon-button") "" else """<span class="ff-action-label">${escapeHtml(text)}</span>"""
        val inner = if (position == "after") visible + iconHtml else iconHtml + visible
        val badge = badgeHtml(get)
        val disabled = disabledNow(schema)
        val classes = "ff-action-trigger ${styleClass()} ${placementClass()}"
        val attrs = tooltipAttr(get) + colorAttr(get) + successAttr(get)
        val aria = """ aria-label="${escapeHtml(text)}""""
        if (opens) {
            val disabledAttr = if (disabled) " disabled" else ""
            return """<button type="button" class="$classes" data-ff-action="$name" data-ff-dialog-open$aria$attrs$disabledAttr @click="open = true">$inner$badge</button>"""
        }
        val formId = submitFormId
        if (formId != null) {
            val disabledAttr = if (disabled) " disabled" else ""
            return """<button type="submit" form="${escapeHtml(formId)}" class="$classes" data-ff-action="$name"$aria$attrs$disabledAttr>$inner$badge</button>"""
        }
        val link = urlSetting.resolve(get)
        if (link != null) {
            if (disabled) {
                return """<a class="$classes" data-ff-action="$name" x-data="ffAction('$name')" aria-disabled="true"$aria$attrs>$inner$badge</a>"""
            }
            val target = if (openInNewTab.resolve(get)) """ target="_blank" rel="noopener noreferrer"""" else ""
            return """<a class="$classes" data-ff-action="$name" x-data="ffAction('$name')" href="${escapeHtml(link)}"$target$aria$attrs>$inner$badge</a>"""
        }
        val disabledAttr = if (disabled) " disabled" else ""
        return """<button type="button" class="$classes" data-ff-action="$name" x-data="ffAction('$name')" wire:click="$name"$aria$attrs$disabledAttr>$inner$badge</button>"""
    }

    private fun badgeHtml(get: Get): String {
        val text = badgeSetting.resolve(get)
        if (text == null) return ""
        val color = badgeColorSetting.resolve(get)
        val attr = if (color == null) "" else """ data-ff-color="${escapeHtml(color)}""""
        return """<span class="ff-action-badge"$attr>${escapeHtml(text)}</span>"""
    }

    private fun tooltipAttr(get: Get): String {
        val tip = tooltipSetting.resolve(get)
        if (tip == null) return ""
        return """ title="${escapeHtml(tip)}""""
    }

    private fun colorAttr(get: Get): String {
        val color = colorSetting.resolve(get)
        val size = sizeSetting.resolve(get)
        if (size != null && !SIZES.contains(size)) throw FireflyException("Unknown action size [$size]")
        val styleValue = triggerStyle(color, size)
        val styleAttr = if (styleValue.isEmpty()) "" else """ style="$styleValue""""
        if (color == null) return styleAttr
        return """ data-ff-color="${escapeHtml(color)}"$styleAttr"""
    }

    private fun successAttr(get: Get): String {
        val url = successRedirectSetting.resolve(get)
        if (url == null) return ""
        return """ data-ff-success-url="${escapeHtml(url)}""""
    }

    private fun styleClass(): String {
        if (style == "link") return "ff-action-link"
        if (style == "icon-button") return "ff-action-icon-button"
        if (style == "outlined") return "ff-action-outlined"
        if (style == "grouped") return "ff-action-grouped"
        return "ff-action-button"
    }

    private fun placementClass(): String = "ff-placement-${placementToken.token}"

    private fun decorate(html: String): String {
        val marker = "<div class=\"ff-dialog-overlay\"></div>"
        val overlay = if (closeAway) {
            "<div class=\"ff-dialog-overlay\" data-ff-dialog-close @click=\"open = false\"></div>"
        } else {
            marker
        }
        // Sync Alpine `open` when the official Dialog header X is clicked.
        return html.replace(marker, overlay).replace(
            """class="ff-dialog-close" aria-label="Close" data-ff-dialog-close">""",
            """class="ff-dialog-close" aria-label="Close" data-ff-dialog-close @click="open = false">""",
        )
    }

    private fun syncChildren() {
        val all = ArrayList<Component>()
        all.addAll(modalComponents)
        val submit = submitAction
        if (submit != null) all.add(submit)
        val cancel = cancelAction
        if (cancel != null) all.add(cancel)
        all.addAll(footerActions)
        replaceChildren(all)
    }

    companion object {
        private val WIDTHS = setOf("sm", "md", "lg", "xl", "screen")
        private val SIZES = setOf("xs", "sm", "md", "lg", "xl")
        private val ATTRIBUTE = Regex("^[A-Za-z_:][A-Za-z0-9_.:-]*$")
        private val RESERVED = setOf("wire:click", "x-data", "type", "href", "disabled")

        @JvmStatic
        fun make(name: String): Action {
            val action = Action(name)
            ComponentConfig.apply(action)
            return action
        }

        @JvmStatic
        fun configureUsing(block: Consumer<Action>?) {
            if (block == null) {
                ComponentConfig.set(Action::class.java, null)
                return
            }
            ComponentConfig.set(Action::class.java, Consumer { component ->
                block.accept(component as Action)
            })
        }

        @JvmStatic
        fun gate(gate: ActionGate?) {
            ActionGateHolder.current = gate
        }
    }
}
