package org.konaframework.support.console

import java.io.PrintStream

/**
 * Canonical Termwind-inspired console IO for **all** Kona terminal CLIs
 * (`kona`, `./grind`, installers that shell out, future commands).
 *
 * Brand colors: primary Mocha `#6F4E37`, accent deep espresso `#3D2914`.
 * Honors `NO_COLOR`, `FORCE_COLOR` (`0` disables), and `TERM=dumb`.
 */
class ConsoleIO(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    private val color: Boolean = detectColor(),
) {
    fun writeln(message: String = "") {
        out.println(message)
    }

    fun write(message: String) {
        out.print(message)
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
            val pad = (contentWidth - plainLength(line)).coerceAtLeast(0)
            writeln("${paint(PRIMARY, "│")} ${line}${" ".repeat(pad)} ${paint(PRIMARY, "│")}")
        }
        writeln(paint(PRIMARY + BOLD, bottom))
    }

    /** Single-line progress bar; [current] is clamped to `[0, total]`. */
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

    /**
     * Print an aligned table. Column widths follow the widest cell per column
     * (plain text length, ANSI stripped).
     */
    fun table(headers: List<String>, rows: List<List<String>>) {
        require(headers.isNotEmpty()) { "headers must not be empty" }
        val width = headers.size
        val normalized = rows.map { row ->
            require(row.size == width) { "row size ${row.size} != header size $width" }
            row
        }
        val colWidths = IntArray(width) { col ->
            var max = plainLength(headers[col])
            normalized.forEach { row ->
                val len = plainLength(row[col])
                if (len > max) max = len
            }
            max
        }
        fun pad(cell: String, col: Int): String {
            val pad = (colWidths[col] - plainLength(cell)).coerceAtLeast(0)
            return cell + " ".repeat(pad)
        }
        writeln(headers.mapIndexed { i, h -> paint(PRIMARY + BOLD, pad(h, i)) }.joinToString("  "))
        writeln(colWidths.joinToString("  ") { paint(DIM, "─".repeat(it)) })
        normalized.forEach { row ->
            writeln(row.mapIndexed { i, cell -> pad(cell, i) }.joinToString("  "))
        }
    }

    /**
     * One migration/seeder row: name, dotted fill, optional duration, then a colored status
     * (`DONE` / `FAIL`). Width is [TASK_WIDTH] columns so a batch lines up like a table.
     */
    @JvmOverloads
    fun taskLine(name: String, status: String, millis: Long? = null, ok: Boolean = true) {
        val duration = if (millis == null) "" else " ${formatDuration(millis.coerceAtLeast(0))}"
        val used = 2 + plainLength(name) + 1 + duration.length + 1 + status.length
        val dots = ".".repeat((TASK_WIDTH - used).coerceAtLeast(1))
        val paintedStatus = paint(if (ok) GREEN else RED, status)
        val paintedDots = paint(DIM, dots)
        val paintedDuration = if (duration.isEmpty()) "" else paint(DIM, duration)
        writeln("  $name $paintedDots$paintedDuration $paintedStatus")
    }

    fun paint(codes: String, text: String): String =
        if (color) "$codes$text$RESET" else text

    fun plainLength(text: String): Int =
        text.replace(ANSI_REGEX, "").length

    companion object {
        const val RESET = "\u001B[0m"
        const val BOLD = "\u001B[1m"
        const val DIM = "\u001B[2m"
        const val RED = "\u001B[31m"
        const val GREEN = "\u001B[32m"
        const val YELLOW = "\u001B[33m"
        const val CYAN = "\u001B[36m"
        const val PRIMARY = "\u001B[38;2;111;78;55m"
        const val ACCENT = "\u001B[38;2;61;41;20m"
        const val TASK_WIDTH = 72
        private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*m")

        @JvmStatic
        fun formatDuration(millis: Long): String {
            val clamped = millis.coerceAtLeast(0)
            if (clamped < 1000L) {
                return "${clamped}ms"
            }
            val whole = clamped / 1000L
            val hundredths = ((clamped % 1000L) / 10L).toInt()
            return "$whole.${hundredths.toString().padStart(2, '0')}s"
        }

        fun detectColor(env: Map<String, String> = System.getenv()): Boolean {
            if (env.containsKey("NO_COLOR")) return false
            val force = env["FORCE_COLOR"]
            if (force != null) return force != "0"
            val term = env["TERM"] ?: return false
            return term != "dumb"
        }
    }
}
