package org.konaframework.firefly.forms

import org.konaframework.icons.Icons

/** Outline Heroicons for inline field controls (input actions, item headers, chips). */
internal object FormIcons {
    const val EYE = "heroicon-o-eye"
    const val EYE_SLASH = "heroicon-o-eye-slash"
    const val CLIPBOARD = "heroicon-o-clipboard"
    const val CHECK = "heroicon-o-check"
    const val X_MARK = "heroicon-o-x-mark"
    const val ARROW_UP = "heroicon-o-arrow-up"
    const val ARROW_DOWN = "heroicon-o-arrow-down"
    const val TRASH = "heroicon-o-trash"
    const val DUPLICATE = "heroicon-o-document-duplicate"
    const val CHEVRON_DOWN = "heroicon-o-chevron-down"
    const val PLUS = "heroicon-o-plus"
    const val GRIP = "heroicon-o-bars-2"

    fun svg(name: String): String = Icons.svg(name).className("ff-icon").toHtml()

    /** Icon-only `type="button"` control; [data] is the bare `data-ff-…` attribute that the chrome script binds. */
    fun button(css: String, label: String, data: String, icon: String): String =
        """<button type="button" class="$css" $data aria-label="${esc(label)}" title="${esc(label)}">${svg(icon)}</button>"""
}
