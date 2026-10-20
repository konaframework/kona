package org.konaframework.support.console

import java.io.PrintStream

/**
 * Canonical Termwind-inspired console IO for **all** Kona terminal CLIs
 * (`kona`, `./grind`, installers that shell out, future commands).
 *
 * Brand colors: light caramel `#DF8723`, accent `#74453F`. Prompt frames use dim
 * `┌┐└┘` boxes and a cyan `›` pointer.
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
        badge("INFO", message, BG_GREEN, WHITE)
    }

    fun success(message: String) {
        badge("DONE", message, BG_GREEN, WHITE)
    }

    fun warn(message: String) {
        badge("WARN", message, BG_YELLOW, BLACK)
    }

    fun error(message: String) {
        err.println(badgeLine("ERROR", message, BG_RED, WHITE))
    }

    /** Open a session with a dim top bar and bold title. */
    fun intro(title: String) {
        writeln()
        bar("┌", title, GREEN + BOLD)
        writeln()
    }

    /** Close a session with a dim bottom bar. */
    fun outro(message: String) {
        writeln()
        bar("└", message, DIM)
        writeln()
    }

    /** Boxed labeled remark (`INFO` / `WARN` / `ERROR` / `NOTE`). */
    @JvmOverloads
    fun note(message: String, label: String = "NOTE") {
        val color = when (label.uppercase()) {
            "WARN", "WARNING" -> YELLOW
            "ERROR" -> RED
            "INFO", "DONE", "SUCCESS" -> GREEN
            else -> CYAN
        }
        promptFrame(label.uppercase(), listOf(message), pointerLine = -1, accent = color)
    }

    fun title(message: String) {
        writeln()
        writeln(paint(BOLD + GREEN, message))
        writeln(paint(DIM, "─".repeat(message.length.coerceAtMost(48))))
    }

    fun subtitle(message: String) {
        writeln(paint(CYAN, message))
    }

    fun twoColumn(left: String, right: String, width: Int = 22) {
        val padded = left.padEnd(width)
        writeln("  ${paint(CYAN + BOLD, padded)} ${paint(DIM, right)}")
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
        frame(title, lines, contentWidth, pointerLine = -1, accent = GREEN)
    }

    /**
     * Input frame: dim `┌ ┐ └ ┘` box, optional cyan `›` on [pointerLine].
     */
    @JvmOverloads
    fun promptFrame(
        label: String,
        body: List<String>,
        pointerLine: Int = -1,
        accent: String = CYAN,
        width: Int = PROMPT_WIDTH,
    ) {
        val rows = if (body.isEmpty()) listOf("") else body
        var raw = plainLength(label) + 1
        rows.forEachIndexed { index, line ->
            val pointer = if (index == pointerLine) 2 else 0
            val len = pointer + plainLength(line)
            if (len > raw) {
                raw = len
            }
        }
        val contentWidth = when {
            raw < width -> width
            raw > 72 -> 72
            else -> raw
        }
        frame(label, rows, contentWidth, pointerLine, accent)
    }

    /** Submitted prompt: green value inside the same frame. */
    fun promptSubmitted(label: String, value: String) {
        promptFrame(label, listOf(paint(GREEN, value)), pointerLine = -1, accent = GREEN)
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

    private fun badge(label: String, message: String, bg: String, fg: String) {
        writeln(badgeLine(label, message, bg, fg))
    }

    private fun badgeLine(label: String, message: String, bg: String, fg: String): String =
        "  ${paint(bg + fg + BOLD, " $label ")} $message"

    private fun bar(corner: String, title: String, titleColor: String) {
        val heading = title.take(48)
        val fill = (PROMPT_WIDTH - heading.length - 1).coerceAtLeast(0)
        writeln(
            " ${paint(DIM, "$corner ")}" +
                paint(titleColor, heading) +
                paint(DIM, " " + "─".repeat(fill) + if (corner == "┌") "┐" else "┘"),
        )
    }

    private fun frame(
        title: String,
        lines: List<String>,
        contentWidth: Int,
        pointerLine: Int,
        accent: String,
    ) {
        val heading = title.take(contentWidth)
        val dashes = (contentWidth - heading.length - 1).coerceAtLeast(0)
        writeln(
            paint(DIM, " ┌ ") + paint(accent, heading) + paint(DIM, " " + "─".repeat(dashes) + "┐"),
        )
        lines.forEachIndexed { index, line ->
            val marker = if (index == pointerLine) paint(CYAN + BOLD, "› ") else "  "
            val body = marker + line
            val pad = (contentWidth - plainLength(body)).coerceAtLeast(0)
            writeln(paint(DIM, " │") + " $body${" ".repeat(pad)} " + paint(DIM, "│"))
        }
        writeln(paint(DIM, " └" + "─".repeat(contentWidth + 2) + "┘"))
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
        const val PRIMARY = "\u001B[38;2;223;135;35m"
        const val ACCENT = "\u001B[38;2;116;69;63m"
        const val GRAY = "\u001B[90m"
        const val WHITE = "\u001B[97m"
        const val BLACK = "\u001B[30m"
        const val BG_GREEN = "\u001B[42m"
        const val BG_YELLOW = "\u001B[43m"
        const val BG_RED = "\u001B[41m"
        const val BG_CYAN = "\u001B[46m"
        const val TASK_WIDTH = 72
        const val PROMPT_WIDTH = 50
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
