package io.konaframework.grind

import io.konaframework.support.console.ConsoleIO
import java.io.PrintStream

enum class Verbosity {
    QUIET,
    NORMAL,
    VERBOSE,
}

/**
 * grind console IO — [ConsoleIO] (Termwind-style) plus quiet/verbose filtering.
 *
 * All new terminal commands must use [ConsoleIO] / [GrindIO], never raw `println`.
 */
class GrindIO(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    var verbosity: Verbosity = Verbosity.NORMAL,
    private val color: Boolean = detectColor(),
) {
    private val console = ConsoleIO(out, err, color)

    fun writeln() {
        writeln("")
    }

    fun writeln(message: String) {
        if (verbosity != Verbosity.QUIET) {
            console.writeln(message)
        }
    }

    fun write(message: String) {
        if (verbosity != Verbosity.QUIET) {
            console.write(message)
        }
    }

    fun line(message: String = "") = writeln(message)

    fun info(message: String) {
        if (verbosity != Verbosity.QUIET) console.info(message)
    }

    fun success(message: String) {
        if (verbosity != Verbosity.QUIET) console.success(message)
    }

    fun warn(message: String) {
        if (verbosity != Verbosity.QUIET) console.warn(message)
    }

    fun error(message: String) {
        console.error(message)
    }

    fun title(message: String) {
        if (verbosity != Verbosity.QUIET) console.title(message)
    }

    fun subtitle(message: String) {
        if (verbosity != Verbosity.QUIET) console.subtitle(message)
    }

    fun twoColumn(left: String, right: String, width: Int = 22) {
        if (verbosity != Verbosity.QUIET) console.twoColumn(left, right, width)
    }

    fun box(title: String, lines: List<String>) {
        if (verbosity != Verbosity.QUIET) console.box(title, lines)
    }

    fun progress(current: Int, total: Int, label: String = "") {
        if (verbosity != Verbosity.QUIET) console.progress(current, total, label)
    }

    fun table(headers: List<String>, rows: List<List<String>>) {
        if (verbosity != Verbosity.QUIET) console.table(headers, rows)
    }

    fun verbose(message: String) {
        if (verbosity == Verbosity.VERBOSE) {
            console.writeln(console.paint(ConsoleIO.DIM, message))
        }
    }

    companion object {
        fun detectColor(env: Map<String, String> = System.getenv()): Boolean =
            ConsoleIO.detectColor(env)

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
