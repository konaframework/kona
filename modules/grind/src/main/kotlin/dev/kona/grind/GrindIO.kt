package dev.kona.grind

import java.io.PrintStream

enum class Verbosity {
    QUIET,
    NORMAL,
    VERBOSE,
}

/**
 * Shared console IO for grind (Laravel console output / verbosity).
 */
class GrindIO(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    var verbosity: Verbosity = Verbosity.NORMAL,
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

    fun error(message: String) {
        err.println(message)
    }

    fun verbose(message: String) {
        if (verbosity == Verbosity.VERBOSE) {
            out.println(message)
        }
    }

    companion object {
        fun fromOptions(options: Map<String, String>): GrindIO {
            val verbosity = when {
                options.containsKey("quiet") || options.containsKey("q") -> Verbosity.QUIET
                options.containsKey("verbose") || options.containsKey("v") -> Verbosity.VERBOSE
                else -> Verbosity.NORMAL
            }
            return GrindIO(verbosity = verbosity)
        }
    }
}
