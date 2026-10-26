package org.konaframework.firefly.tables

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.Dialog
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

enum class ButtonMode {
    MODAL,
    SLIDE_OVER,
    FULLSCREEN,
}

/**
 * Row and header button. Presentation goes through [Dialog].
 * Typed action objects attach when the actions module is on main.
 */
open class TableButton(val name: String) {
    private val labelValue = TextSetting()
    private val hiddenFlag = FlagSetting(false)
    private val visibleFlag = FlagSetting(true)
    private val disabledFlag = FlagSetting(false)
    private val urlValue = TextSetting()
    private var mode = ButtonMode.MODAL
    private var requiresSelection = false
    private val schemaParts = ArrayList<Component>()

    fun label(value: String): TableButton = apply { labelValue.set(value) }

    fun label(value: Function<Cell, String>): TableButton = apply { labelValue.set(value) }

    fun hidden(on: Boolean): TableButton = apply { hiddenFlag.set(on) }

    fun hidden(predicate: Predicate<Cell>): TableButton = apply { hiddenFlag.set(predicate) }

    fun visible(on: Boolean): TableButton = apply { visibleFlag.set(on) }

    fun visible(predicate: Predicate<Cell>): TableButton = apply { visibleFlag.set(predicate) }

    fun disabled(on: Boolean): TableButton = apply { disabledFlag.set(on) }

    fun disabled(predicate: Predicate<Cell>): TableButton = apply { disabledFlag.set(predicate) }

    fun url(value: String): TableButton = apply { urlValue.set(value) }

    fun url(value: Function<Cell, String>): TableButton = apply { urlValue.set(value) }

    fun modal(): TableButton = apply { mode = ButtonMode.MODAL }

    fun slideOver(): TableButton = apply { mode = ButtonMode.SLIDE_OVER }

    fun fullscreen(): TableButton = apply { mode = ButtonMode.FULLSCREEN }

    fun requiresSelection(on: Boolean): TableButton = apply { requiresSelection = on }

    fun schema(vararg components: Component): TableButton = apply {
        schemaParts.clear()
        schemaParts.addAll(components)
    }

    fun isHidden(cell: Cell): Boolean = hiddenFlag.get(cell) || !visibleFlag.get(cell)

    fun render(cell: Cell, selectionEmpty: Boolean): String {
        if (isHidden(cell)) return ""
        val label = labelValue.get(cell, name)
        val href = urlValue.get(cell, "")
        val disabled = disabledFlag.get(cell) || (requiresSelection && selectionEmpty)
        val disabledAttr = if (disabled) """ disabled""" else ""
        if (href.isNotEmpty()) {
            return """<a class="ff-table-button" href="${escapeHtml(href)}"$disabledAttr>${escapeHtml(label)}</a>"""
        }
        val dialog = Dialog.make(label)
        when (mode) {
            ButtonMode.MODAL -> dialog.modal()
            ButtonMode.SLIDE_OVER -> dialog.slideOver()
            ButtonMode.FULLSCREEN -> dialog.fullscreen()
        }
        if (schemaParts.isNotEmpty()) {
            dialog.body(Schema.make().components(*schemaParts.toTypedArray()).render())
        }
        return """
            <button type="button" class="ff-table-button" x-data="ffAction('${escapeHtml(name)}')" x-on:click="${'$'}wire.${'$'}call('${escapeHtml(name)}')"$disabledAttr>${escapeHtml(label)}</button>
            ${dialog.render()}
        """.trimIndent()
    }

    companion object {
        private var configurer: Consumer<TableButton>? = null

        @JvmStatic
        fun make(name: String): TableButton {
            val button = TableButton(name)
            configurer?.accept(button)
            return button
        }

        @JvmStatic
        fun configureUsing(block: Consumer<TableButton>?) {
            configurer = block
        }
    }
}
