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
    private var postUrl: String? = null
    private var formExtras: Function<Cell, String> = Function { "" }
    private var confirmHeading: String? = null
    private var confirmDescription: String = ""
    private var bodyHtml: Function<Cell, String>? = null

    fun label(value: String): TableButton = apply { labelValue.set(value) }

    fun label(value: Function<Cell, String>): TableButton = apply { labelValue.set(value) }

    fun hidden(): TableButton = hidden(true)

    fun hidden(on: Boolean): TableButton = apply { hiddenFlag.set(on) }

    fun hidden(predicate: Predicate<Cell>): TableButton = apply { hiddenFlag.set(predicate) }

    fun visible(): TableButton = visible(true)

    fun visible(on: Boolean): TableButton = apply { visibleFlag.set(on) }

    fun visible(predicate: Predicate<Cell>): TableButton = apply { visibleFlag.set(predicate) }

    fun disabled(): TableButton = disabled(true)

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
        bodyHtml = null
    }

    /**
     * Custom dialog body per row (e.g. filled edit/view markup for a simple-resource manage page).
     * Takes precedence over [schema] when set.
     */
    fun body(html: Function<Cell, String>): TableButton = apply {
        bodyHtml = html
        schemaParts.clear()
    }

    /**
     * Row delete (and similar): open a confirm dialog that POSTs [actionUrl] with CSRF extras
     * and the row key. Works without Alpine via chrome `data-ff-dialog-*`.
     */
    @JvmOverloads
    fun confirmPost(
        actionUrl: String,
        csrfFieldHtml: String,
        heading: String = "Delete",
        description: String = "Delete this record permanently?",
    ): TableButton = apply {
        if (actionUrl.isBlank()) throw org.konaframework.firefly.support.FireflyException("Confirm post action is blank")
        postUrl = actionUrl
        confirmHeading = heading
        confirmDescription = description
        formExtras = Function { cell ->
            val key = cell.get("key")?.toString().orEmpty()
            buildString {
                append(csrfFieldHtml)
                if (key.isNotEmpty()) {
                    append("""<input type="hidden" name="id" value="${escapeHtml(key)}" />""")
                }
            }
        }
    }

    fun isHidden(cell: Cell): Boolean = hiddenFlag.get(cell) || !visibleFlag.get(cell)

    /** Resolved href when this button is a link; null for dialog mounts (#377). */
    internal fun urlFor(cell: Cell): String? {
        val href = urlValue.get(cell, "")
        return href.takeIf { it.isNotEmpty() }
    }

    @JvmOverloads
    open fun render(cell: Cell, selectionEmpty: Boolean, menuItem: Boolean = false): String {
        if (isHidden(cell)) return ""
        val label = labelValue.get(cell, name)
        val href = urlValue.get(cell, "")
        val locked = disabledFlag.get(cell)
        val disabled = locked || (requiresSelection && selectionEmpty)
        // The chrome script re-enables selection-bound buttons as rows are checked in the browser.
        val selectionAttrs = when {
            !requiresSelection -> ""
            locked -> """ data-ff-requires-selection data-ff-selection-locked"""
            else -> """ data-ff-requires-selection"""
        }
        val disabledAttr = (if (disabled) """ disabled""" else "") + selectionAttrs
        val triggerClass = if (menuItem) "ff-menu-link" else "ff-table-button"
        if (href.isNotEmpty()) {
            // data-ff-action lets row clicks target this link by name (#377).
            return """<a class="$triggerClass" href="${escapeHtml(href)}" data-ff-action="${escapeHtml(name)}"$disabledAttr>${escapeHtml(label)}</a>"""
        }
        val dialog = Dialog.make(confirmHeading ?: label)
        when (mode) {
            ButtonMode.MODAL -> dialog.modal()
            ButtonMode.SLIDE_OVER -> dialog.slideOver()
            ButtonMode.FULLSCREEN -> dialog.fullscreen()
        }
        val post = postUrl
        if (post != null) {
            val extras = formExtras.apply(cell)
            val descriptionHtml =
                if (confirmDescription.isEmpty()) {
                    ""
                } else {
                    """<p class="ff-action-description">${escapeHtml(confirmDescription)}</p>"""
                }
            dialog.body(
                """
                $descriptionHtml
                <form method="post" action="${escapeHtml(post)}" class="ff-delete-form">
                  $extras
                  <div class="ff-action-footer">
                    <button type="button" class="ff-action-cancel" data-ff-dialog-close>Cancel</button>
                    <button type="submit" class="ff-action-submit">${escapeHtml(label)}</button>
                  </div>
                </form>
                """.trimIndent(),
            )
        } else {
            val custom = bodyHtml
            if (custom != null) {
                dialog.body(custom.apply(cell))
            } else if (schemaParts.isNotEmpty()) {
                dialog.body(Schema.make().components(*schemaParts.toTypedArray()).render())
            }
        }
        // Keep the dialog in the DOM for Wire/modal mounts, but never leave it interactive.
        // Raw Dialog is position:fixed;inset:0 — without a closed wrapper it blocks the list page.
        // style="display:none" + hidden + x-cloak work even when Alpine is not on DefaultLayout.
        // data-ff-dialog-*: chrome script opens/closes without Alpine.
        return """
            <div class="ff-table-button-mount" data-ff-dialog-mount x-data="{ open: false }">
              <button type="button" class="$triggerClass" data-ff-action="${escapeHtml(name)}" data-ff-dialog-open @click="open = true" x-on:click="${'$'}wire.${'$'}call('${escapeHtml(name)}')"$disabledAttr>${escapeHtml(label)}</button>
              <div class="ff-table-button-dialog" data-ff-dialog-panel x-show="open" x-cloak style="display:none" hidden aria-hidden="true" data-ff-action-boot="ffAction('${escapeHtml(name)}')">${dialog.render(open = true)}</div>
            </div>
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

        /** Groups [buttons] under a ⋯ trigger (default resource row actions). */
        @JvmStatic
        fun group(vararg buttons: TableButton): TableButton = ActionMenuButton(buttons.toList())

        @JvmStatic
        fun configureUsing(block: Consumer<TableButton>?) {
            configurer = block
        }
    }
}
