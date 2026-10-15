package dev.kona.grind

import java.io.PrintStream

enum class Verbosity {
    QUIET,
    NORMAL,
    VERBOSE,
}

/**
 * Shared console IO for grind — Termwind / Laravel-style colors, boxes, and progress.
 *
 * Respects `NO_COLOR`, `FORCE_COLOR`, and `TERM=dumb`. Disable color in tests via [color].
 */
class GrindIO(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    var verbosity: Verbosity = Verbosity.NORMAL,
    private val color: Boolean = detectColor(),
) {
    fun writeln() {
        writeln("")
    }

    fun writeln(message: String) {
        if (verbosity != Verbosity.QUIET) {
            out.println(message)
        }
    }

    fun write(message: String) {
        if (verbosity != Verbosity.QUIET) {
            out.print(message)
        }
    }

    fun line(message: String = "") = writeln(message)

    fun info(message: String) {
        writeln("${paint(CYAN, "ℹ")}  $message")
    }

    fun success(message: String) {
        writeln("${paint(GREEN, "✓")}  $message")
    }

    fun warn(message: String) {
        writeln("${paint(YELLOW, "!")}  $message")
    }

    fun error(message: String) {
        err.println("${paint(RED, "✗")}  $message")
    }

    fun title(message: String) {
        writeln()
        writeln(paint(BOLD + PRIMARY, message))
        writeln(paint(DIM, "─".repeat(message.length.coerceAtMost(48))))
    }

    fun subtitle(message: String) {
        writeln(paint(ACCENT, message))
    }

    fun twoColumn(left: String, right: String, width: Int = 22) {
        val padded = left.padEnd(width)
        writeln("${paint(PRIMARY + BOLD, padded)} ${paint(DIM, right)}")
    }

    fun box(title: String, lines: List<String>) {
        var raw = plainLength(title)
        for (line in lines) {
            val len = plainLength(line)
            if (len > raw) {
                raw = len
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
            val visible = plainLength(line)
            val pad = (contentWidth - visible).coerceAtLeast(0)
            writeln("${paint(PRIMARY, "│")} ${line}${" ".repeat(pad)} ${paint(PRIMARY, "│")}")
        }
        writeln(paint(PRIMARY + BOLD, bottom))
    }

    /**
     * Renders a single-line progress bar. [current] is clamped to `[0, total]`.
     */
    fun progress(current: Int, total: Int, label: String = "") {
        require(total > 0) { "total must be positive" }
        val clamped = current.coerceIn(0, total)
        val width = 28
        val filled = (clamped * width) / total
        val bar = "█".repeat(filled) + "░".repeat(width - filled)
        val pct = (clamped * 100) / total
        val suffix = if (label.isEmpty()) "" else " $label"
        writeln("${paint(PRIMARY, bar)} ${paint(DIM, "$pct%")}$suffix")
    }

    fun verbose(message: String) {
        if (verbosity == Verbosity.VERBOSE) {
            out.println(paint(DIM, message))
        }
    }

    private fun paint(codes: String, text: String): String =
        if (color) "$codes$text$RESET" else text

    private fun plainLength(text: String): Int =
        text.replace(ANSI_REGEX, "").length

    companion object {
        private const val RESET = "\u001B[0m"
        private const val BOLD = "\u001B[1m"
        private const val DIM = "\u001B[2m"
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
        private const val YELLOW = "\u001B[33m"
        private const val CYAN = "\u001B[36m"
        private const val PRIMARY = "\u001B[38;2;255;154;0m"
        private const val ACCENT = "\u001B[38;2;40;98;145m"
        private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*m")

        /**
         * Color when the terminal looks interactive. Honors `NO_COLOR` / `FORCE_COLOR`.
         * Uses `TERM` (not [System.console]) so CI and unit tests can cover every branch.
         */
        fun detectColor(env: Map<String, String> = System.getenv()): Boolean {
            if (env.containsKey("NO_COLOR")) return false
            val force = env["FORCE_COLOR"]
            if (force != null) return force != "0"
            val term = env["TERM"] ?: return false
            return term != "dumb"
        }

        fun fromOptions(options: Map<String, String>, color: Boolean = detectColor()): GrindIO {
            val verbosity = when {
                options.containsKey("quiet") || options.containsKey("q") -> Verbosity.QUIET
                options.containsKey("verbose") || options.containsKey("v") -> Verbosity.VERBOSE
                else -> Verbosity.NORMAL
            }
            return GrindIO(verbosity = verbosity, color = color)
        }
    }
}
