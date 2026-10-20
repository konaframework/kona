package org.konaframework.support.console

import java.io.PrintStream

/**
 * Draws boxed prompts and replaces the live widget in place (`CSI nA` + clear-down)
 * so a submitted compact frame does not stack under the live one.
 */
class PromptRenderer(
    private val output: PrintStream,
    private val style: ConsoleIO,
    val width: Int,
    private val raw: Boolean,
) {
    private var liveLines = 0
    private var parkedOnValue = false

    fun hideCursor() {
        write(HIDE)
    }

    fun showCursor() {
        write(SHOW)
    }

    fun drawSelect(
        label: String,
        options: List<String>,
        pointerLine: Int,
        submitted: Boolean = false,
    ) {
        val accent = if (submitted) ConsoleIO.GREEN else ConsoleIO.CYAN
        val body =
            options.mapIndexed { index, option ->
                when {
                    submitted && index == pointerLine -> style.paint(ConsoleIO.GREEN, option)
                    index == pointerLine -> style.paint(ConsoleIO.CYAN, option)
                    else -> option
                }
            }
        val pointer = if (submitted) -1 else pointerLine
        replace(style.promptFrameLines(label, body, pointer, accent, width, pointerPrefix = !submitted))
    }

    fun drawAsk(label: String, value: String, submitted: Boolean) {
        val accent = if (submitted) ConsoleIO.GREEN else ConsoleIO.CYAN
        val body = if (submitted) style.paint(ConsoleIO.GREEN, value) else value
        replace(style.promptFrameLines(label, listOf(body), -1, accent, width, pointerPrefix = false))
    }

    fun parkCursorOnValue(column: Int) {
        write(csi("${2}A") + csi("${column.coerceAtLeast(1)}G"))
        parkedOnValue = true
    }

    fun finishSubmitted() {
        liveLines = 0
        parkedOnValue = false
        showCursor()
        flush()
    }

    private fun replace(lines: List<String>) {
        if (liveLines > 0) {
            val up = if (parkedOnValue) 1 else liveLines
            write(csi("${up}A") + "\r" + CLEAR_DOWN)
            parkedOnValue = false
        }
        lines.forEach { line ->
            output.print(line)
            output.print(if (raw) "\r\n" else "\n")
        }
        liveLines = lines.size
        parkedOnValue = false
        flush()
    }

    private fun write(text: String) {
        output.print(text)
        flush()
    }

    private fun flush() {
        output.flush()
    }

    companion object {
        const val HIDE = "\u001b[?25l"
        const val SHOW = "\u001b[?25h"
        const val CLEAR_DOWN = "\u001b[J"
        const val VALUE_COLUMN = 4

        fun csi(body: String): String = "\u001b[$body"
    }
}
