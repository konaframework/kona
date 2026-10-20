package org.konaframework.support.console

/**
 * One decoded keypress from a raw TTY (or a test byte source).
 */
sealed interface TerminalKey {
    data class CharKey(val text: String) : TerminalKey

    data object Enter : TerminalKey

    data object Backspace : TerminalKey

    data object Delete : TerminalKey

    data object Up : TerminalKey

    data object Down : TerminalKey

    data object Left : TerminalKey

    data object Right : TerminalKey

    data object Home : TerminalKey

    data object End : TerminalKey

    data object Esc : TerminalKey

    data object Tab : TerminalKey

    data object CtrlC : TerminalKey

    data object Eof : TerminalKey

    data object Ignored : TerminalKey
}

/** Byte-at-a-time source; [available] is used only to disambiguate a lone Esc from CSI. */
interface ByteSource {
    fun read(): Int

    fun available(): Int
}
