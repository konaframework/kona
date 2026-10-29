package org.konaframework.firefly.support

import java.util.function.Consumer

enum class DialogMode {
    MODAL,
    SIDEBAR,
    FULLSCREEN,
}

class Dialog private constructor(private val title: String) {
    private var body: String = ""
    private var mode: DialogMode = DialogMode.MODAL
    private var position: String = "end"
    private var draggable: Boolean = true
    private var showClose: Boolean = true
    private var expandable: Boolean = true
    private var resizable: Boolean = true
    private var width: String = "lg"

    fun body(html: String): Dialog = apply { body = html }

    fun modal(): Dialog = apply { mode = DialogMode.MODAL }

    fun slideOver(): Dialog = apply {
        mode = DialogMode.SIDEBAR
        position = "end"
    }

    fun slideOverStart(): Dialog = apply {
        mode = DialogMode.SIDEBAR
        position = "start"
    }

    fun fullscreen(): Dialog = apply { mode = DialogMode.FULLSCREEN }

    fun draggable(on: Boolean): Dialog = apply { draggable = on }

    /** Header close (X). Default true for every official dialog. */
    fun closeButton(on: Boolean): Dialog = apply { showClose = on }

    /**
     * Header maximize / restore control (Shamar). Default on for modal and slide-over;
     * ignored when the presentation mode is already [DialogMode.FULLSCREEN].
     */
    fun expandable(on: Boolean): Dialog = apply { expandable = on }

    /**
     * Corner resize handle so the user can size the window freely (Shamar).
     * Default on for modal; off for slide-over and fullscreen presentation.
     */
    fun resizable(on: Boolean): Dialog = apply { resizable = on }

    fun width(width: String): Dialog = apply {
        if (width !in WIDTHS) throw FireflyException("Unknown dialog width [$width]")
        this.width = width
    }

    /**
     * Render dialog chrome. Closed by default (`hidden`) so a raw dialog never covers the page.
     * Callers that show the dialog via a parent (`x-show` / `display:none` mount) pass [open]=true
     * so the dialog is interactive once that parent is revealed.
     */
    @JvmOverloads
    fun render(open: Boolean = false): String {
        val modeClass = when (mode) {
            DialogMode.MODAL -> "ff-dialog-modal"
            DialogMode.SIDEBAR -> "ff-dialog-sidebar"
            DialogMode.FULLSCREEN -> "ff-dialog-fullscreen"
        }
        val canDrag = draggable && mode != DialogMode.FULLSCREEN
        val canExpand = expandable && mode != DialogMode.FULLSCREEN
        val canResize = resizable && mode == DialogMode.MODAL
        val drag = if (canDrag) """ data-ff-drag="true"""" else ""
        val align = if (mode == DialogMode.MODAL) """ data-ff-align="top"""" else ""
        val side = if (mode == DialogMode.SIDEBAR) """ data-ff-position="$position"""" else ""
        val closed = if (open) {
            """ aria-modal="true""""
        } else {
            """ hidden aria-hidden="true""""
        }
        val headerActions = StringBuilder("""<div class="ff-dialog-header-actions">""")
        if (canExpand) {
            headerActions.append(
                """<button type="button" class="ff-dialog-expand" aria-label="Toggle fullscreen" data-ff-dialog-expand>${Icon.ARROWS_POINTING_OUT.svg()}</button>""",
            )
        }
        if (showClose) {
            headerActions.append(
                """<button type="button" class="ff-dialog-close" aria-label="Close" data-ff-dialog-close>${Icon.X.svg()}</button>""",
            )
        }
        headerActions.append("</div>")
        val resize =
            if (canResize) {
                """<span class="ff-dialog-resize" data-ff-dialog-resize aria-hidden="true"></span>"""
            } else {
                ""
            }
        return """
            <div class="ff-dialog $modeClass ff-dialog-$width" role="dialog"$closed$align$side$drag>
              <div class="ff-dialog-overlay"></div>
              <div class="ff-dialog-window">
                <header class="ff-dialog-header"${if (canDrag) """ data-ff-dialog-drag-handle""" else ""}><span class="ff-dialog-title">${escapeHtml(title)}</span>$headerActions</header>
                <div class="ff-dialog-body">$body</div>
                $resize
              </div>
            </div>
        """.trimIndent()
    }

    companion object {
        private val WIDTHS = setOf("sm", "md", "lg", "xl", "screen")
        private var configurer: Consumer<Dialog>? = null

        @JvmStatic
        fun configureUsing(block: Consumer<Dialog>?): Unit {
            configurer = block
        }

        @JvmStatic
        fun make(title: String): Dialog {
            val dialog = Dialog(title)
            configurer?.accept(dialog)
            return dialog
        }
    }
}
