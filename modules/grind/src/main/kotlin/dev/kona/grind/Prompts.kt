package dev.kona.grind

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Interactive prompts (Laravel Prompts / Smith ask-confirm-select).
 * Injectable streams keep tests non-interactive.
 */
class Prompts(
    private val input: InputStream = System.`in`,
    private val output: PrintStream = System.out,
    private val color: Boolean = GrindIO.detectColor(),
) {
    private val reader = BufferedReader(InputStreamReader(input))

    fun ask(question: String, default: String? = null): String {
        val suffix = if (default != null) " [$default]" else ""
        output.print("${paint(PRIMARY, "?")} $question$suffix: ")
        output.flush()
        val line = reader.readLine()?.trim().orEmpty()
        return if (line.isEmpty() && default != null) default else line
    }

    fun confirm(question: String, default: Boolean = false): Boolean {
        val hint = if (default) "Y/n" else "y/N"
        output.print("${paint(PRIMARY, "?")} $question ${paint(DIM, "($hint)")}: ")
        output.flush()
        val line = reader.readLine()?.trim()?.lowercase().orEmpty()
        if (line.isEmpty()) return default
        return line == "y" || line == "yes"
    }

    fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String {
        require(options.isNotEmpty()) { "choice requires options" }
        require(defaultIndex in options.indices) { "defaultIndex out of range" }
        output.println("${paint(PRIMARY + BOLD, question)}")
        options.forEachIndexed { index, option ->
            output.println("  ${paint(ACCENT, "[${index + 1}]")} $option")
        }
        output.print("${paint(PRIMARY, "?")} Select [${defaultIndex + 1}]: ")
        output.flush()
        val line = reader.readLine()?.trim().orEmpty()
        if (line.isEmpty()) return options[defaultIndex]
        val asInt = line.toIntOrNull()
        if (asInt != null && asInt in 1..options.size) {
            return options[asInt - 1]
        }
        val match = options.firstOrNull { it.equals(line, ignoreCase = true) }
        return match ?: options[defaultIndex]
    }

    private fun paint(codes: String, text: String): String =
        if (color) "$codes$text$RESET" else text

    companion object {
        private const val RESET = "\u001B[0m"
        private const val BOLD = "\u001B[1m"
        private const val DIM = "\u001B[2m"
        private const val PRIMARY = "\u001B[38;2;255;154;0m"
        private const val ACCENT = "\u001B[38;2;40;98;145m"
    }
}
