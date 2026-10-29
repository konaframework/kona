package org.konaframework.firefly.forms

/** Outline 24px icons for inline field controls (input actions, item headers, chips). */
internal object FormIcons {
    const val EYE = "M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z|M15 12a3 3 0 11-6 0 3 3 0 016 0z"
    const val EYE_SLASH = "M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88"
    const val CLIPBOARD = "M15.666 3.888A2.25 2.25 0 0013.5 2.25h-3c-1.03 0-1.9.693-2.166 1.638m7.332 0c.055.194.084.4.084.612v0a.75.75 0 01-.75.75H9.75a.75.75 0 01-.75-.75v0c0-.212.03-.418.084-.612m7.332 0c.646.049 1.288.11 1.927.184 1.1.128 1.907 1.077 1.907 2.185V19.5a2.25 2.25 0 01-2.25 2.25H6.75A2.25 2.25 0 014.5 19.5V6.257c0-1.108.806-2.057 1.907-2.185a48.208 48.208 0 011.927-.184"
    const val CHECK = "M4.5 12.75l6 6 9-13.5"
    const val X_MARK = "M6 18L18 6M6 6l12 12"
    const val ARROW_UP = "M4.5 10.5L12 3m0 0l7.5 7.5M12 3v18"
    const val ARROW_DOWN = "M19.5 13.5L12 21m0 0l-7.5-7.5M12 21V3"
    const val TRASH = "M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"
    const val DUPLICATE = "M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-9.376a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 00-3.375-3.375h-1.5a1.125 1.125 0 01-1.125-1.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25"
    const val CHEVRON_DOWN = "M19.5 8.25l-7.5 7.5-7.5-7.5"
    const val PLUS = "M12 4.5v15m7.5-7.5h-15"
    const val GRIP = "M3.75 9h16.5m-16.5 6.75h16.5"

    /** `|` separates path segments of a multi-path icon. */
    fun svg(paths: String): String = buildString {
        append("""<svg class="ff-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">""")
        for (d in paths.split('|')) append("""<path stroke-linecap="round" stroke-linejoin="round" d="$d"/>""")
        append("</svg>")
    }

    /** Icon-only `type="button"` control; [data] is the bare `data-ff-…` attribute that the chrome script binds. */
    fun button(css: String, label: String, data: String, paths: String): String =
        """<button type="button" class="$css" $data aria-label="${esc(label)}" title="${esc(label)}">${svg(paths)}</button>"""
}
