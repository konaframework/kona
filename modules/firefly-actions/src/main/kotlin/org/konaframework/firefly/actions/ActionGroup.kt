package org.konaframework.firefly.actions

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.Icon
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/** A dropdown or inline row of [Action]s. */
open class ActionGroup protected constructor() : Component("") {
    private val authorization = Authorization()
    private val iconSetting = TextSetting()
    private val colorSetting = TextSetting()
    private val tooltipSetting = TextSetting()
    private var menu: Boolean = true
    private var placementToken: ActionPlacement = ActionPlacement.SCHEMA

    override fun kind(): String = "action-group"

    override fun label(value: String): ActionGroup {
        super.label(value)
        return this
    }

    override fun label(value: Function<Get, String>): ActionGroup {
        super.label(value)
        return this
    }

    override fun isHidden(schema: Schema): Boolean {
        if (!isAuthorized(schema)) return true
        return super.isHidden(schema)
    }

    fun isAuthorized(schema: Schema): Boolean = authorization.permits(schema, this)

    fun icon(value: String): ActionGroup = apply { iconSetting.setLiteral(value) }

    fun icon(value: Function<Get, String>): ActionGroup = apply { iconSetting.setUsing(value) }

    fun color(value: String): ActionGroup = apply { colorSetting.setLiteral(value) }

    fun color(value: Function<Get, String>): ActionGroup = apply { colorSetting.setUsing(value) }

    fun tooltip(value: String): ActionGroup = apply { tooltipSetting.setLiteral(value) }

    fun tooltip(value: Function<Get, String>): ActionGroup = apply { tooltipSetting.setUsing(value) }

    fun placement(placement: ActionPlacement): ActionGroup = apply { placementToken = placement }

    fun placement(): ActionPlacement = placementToken

    fun authorize(on: Boolean): ActionGroup = apply { authorization.allow(on) }

    fun authorize(predicate: Predicate<Get>): ActionGroup = apply { authorization.allow(predicate) }

    fun authorize(ability: String): ActionGroup = apply { authorization.ability(ability) }

    fun button(): ActionGroup = apply { menu = false }

    fun dropdown(): ActionGroup = apply { menu = true }

    fun actions(vararg actions: Action): ActionGroup = apply { replaceChildren(actions.toList()) }

    override fun renderBody(schema: Schema): String {
        if (isHidden(schema)) return ""
        val get = SchemaBridge.reader(schema, this)
        val text = storedLabel(get)
        val labelHtml = if (text == null) "" else """<span class="ff-action-label">${escapeHtml(text)}</span>"""
        val iconName = iconSetting.resolve(get)
        val iconHtml = if (iconName == null) "" else Icon.fromName(iconName).svg()
        val tip = tooltipSetting.resolve(get)
        val title = if (tip == null) "" else """ title="${escapeHtml(tip)}""""
        val color = colorSetting.resolve(get)
        val styleValue = triggerStyle(color, null)
        val styleAttr = if (styleValue.isEmpty()) "" else """ style="$styleValue""""
        val colorAttr = if (color == null) "" else """ data-ff-color="${escapeHtml(color)}""""
        val disabled = disabledNow(schema)
        val buttonDisabled = if (disabled) " disabled" else ""
        val ariaDisabled = if (disabled) """ aria-disabled="true"""" else ""
        val place = "ff-placement-${placementToken.token}"
        val items = childHtml(schema)
        if (menu) {
            return """<div class="ff-action-group ff-action-group-dropdown $place" x-data="{ open: false }"><button type="button" class="ff-action-group-trigger"$title$colorAttr$styleAttr$buttonDisabled @click="open = !open">$iconHtml$labelHtml</button><div class="ff-action-group-menu" x-show="open" style="display:none">$items</div></div>"""
        }
        return """<div class="ff-action-group ff-action-group-inline $place"$colorAttr$styleAttr$ariaDisabled><span class="ff-action-group-label"$title>$iconHtml$labelHtml</span><div class="ff-action-group-items">$items</div></div>"""
    }

    companion object {
        @JvmStatic
        fun make(vararg actions: Action): ActionGroup {
            val group = ActionGroup()
            group.actions(*actions)
            ComponentConfig.apply(group)
            return group
        }

        @JvmStatic
        fun make(actions: List<Action>): ActionGroup = make(*actions.toTypedArray())

        @JvmStatic
        fun configureUsing(block: Consumer<ActionGroup>?) {
            if (block == null) {
                ComponentConfig.set(ActionGroup::class.java, null)
                return
            }
            ComponentConfig.set(ActionGroup::class.java, Consumer { component ->
                block.accept(component as ActionGroup)
            })
        }
    }
}
