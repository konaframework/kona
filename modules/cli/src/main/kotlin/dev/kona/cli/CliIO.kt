package dev.kona.cli

import java.io.PrintStream

/**
 * Termwind-inspired console styling for the global `kona` CLI (mirrors grind [dev.kona.grind.GrindIO]).
 */
class CliIO(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    private val color: Boolean = detectColor(),
) {
    fun writeln(message: String = "") = out.println(message)

    fun info(message: String) = writeln("${paint(CYAN, "ℹ")}  $message")

    fun success(message: String) = writeln("${paint(GREEN, "✓")}  $message")

    fun error(message: String) = err.println("${paint(RED, "✗")}  $message")

    fun box(title: String, lines: List<String>) {
        var raw = title.length
        for (line in lines) {
            if (line.length > raw) {
                raw = line.length
            }
        }
        val contentWidth = when {
            raw < 24 -> 24
            raw > 56 -> 56
            else -> raw
        }
        val heading = title.take(contentWidth)
        val top = "╭─ $heading " + "─".repeat((contentWidth - heading.length - 1).coerceAtLeast(0)) + "╮"
        val bottom = "╰" + "─".repeat(contentWidth + 2) + "╯"
        writeln(paint(PRIMARY + BOLD, top))
        lines.forEach { line ->
            val pad = (contentWidth - line.length).coerceAtLeast(0)
            writeln("${paint(PRIMARY, "│")} ${line}${" ".repeat(pad)} ${paint(PRIMARY, "│")}")
        }
        writeln(paint(PRIMARY + BOLD, bottom))
    }

    fun twoColumn(left: String, right: String, width: Int = 22) {
        writeln("${paint(PRIMARY + BOLD, left.padEnd(width))} ${paint(DIM, right)}")
    }

    private fun paint(codes: String, text: String): String =
        if (color) "$codes$text$RESET" else text

    companion object {
        private const val RESET = "\u001B[0m"
        private const val BOLD = "\u001B[1m"
        private const val DIM = "\u001B[2m"
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
        private const val CYAN = "\u001B[36m"
        private const val PRIMARY = "\u001B[38;2;255;154;0m"

        fun detectColor(env: Map<String, String> = System.getenv()): Boolean {
            if (env.containsKey("NO_COLOR")) return false
            val force = env["FORCE_COLOR"]
            if (force != null) return force != "0"
            val term = env["TERM"] ?: return false
            return term != "dumb"
        }
    }
}
