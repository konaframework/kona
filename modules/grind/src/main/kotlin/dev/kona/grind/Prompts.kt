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
) {
    private val reader = BufferedReader(InputStreamReader(input))

    fun ask(question: String, default: String? = null): String {
        val suffix = if (default != null) " [$default]" else ""
        output.print("$question$suffix: ")
        output.flush()
        val line = reader.readLine()?.trim().orEmpty()
        return if (line.isEmpty() && default != null) default else line
    }

    fun confirm(question: String, default: Boolean = false): Boolean {
        val hint = if (default) "Y/n" else "y/N"
        output.print("$question ($hint): ")
        output.flush()
        val line = reader.readLine()?.trim()?.lowercase().orEmpty()
        if (line.isEmpty()) return default
        return line == "y" || line == "yes"
    }

    fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String {
        require(options.isNotEmpty()) { "choice requires options" }
        require(defaultIndex in options.indices) { "defaultIndex out of range" }
        output.println(question)
        options.forEachIndexed { index, option ->
            output.println("  [${index + 1}] $option")
        }
        output.print("Select [${defaultIndex + 1}]: ")
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
}
