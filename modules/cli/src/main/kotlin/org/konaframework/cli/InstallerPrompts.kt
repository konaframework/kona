package org.konaframework.cli

import org.konaframework.support.console.ConsoleIO
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Interactive prompts for `kona new`: dim `┌┐└┘` frames, cyan `›` pointer, green submitted value.
 * Same visual language as grind [org.konaframework.grind.Prompts]. Injectable streams keep tests
 * non-blocking.
 */
class InstallerPrompts
@JvmOverloads
constructor(
    private val input: InputStream = System.`in`,
    private val output: PrintStream = System.out,
    private val color: Boolean = ConsoleIO.detectColor(),
) {
    private val reader = BufferedReader(InputStreamReader(input))
    private val style = ConsoleIO(output, output, color)

    @JvmOverloads
    fun ask(question: String, default: String? = null): String {
        val preview = if (default.isNullOrEmpty()) "" else style.paint(ConsoleIO.DIM, default)
        style.promptFrame(question, listOf(preview))
        output.flush()
        val raw = reader.readLine()
        val line = if (raw == null) "" else raw.trim()
        val answer = if (line.isEmpty() && default != null) default else line
        style.promptSubmitted(question, answer)
        return answer
    }

    @JvmOverloads
    fun confirm(question: String, default: Boolean = false): Boolean {
        val options = listOf("Yes", "No")
        val pointer = if (default) 0 else 1
        style.promptFrame(question, options, pointerLine = pointer)
        output.flush()
        val raw = reader.readLine()
        val line = if (raw == null) "" else raw.trim().lowercase()
        val yes = when {
            line.isEmpty() -> default
            line == "y" || line == "yes" || line == "1" -> true
            line == "n" || line == "no" || line == "2" -> false
            else -> default
        }
        style.promptSubmitted(question, if (yes) "Yes" else "No")
        return yes
    }

    @JvmOverloads
    fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String {
        if (options.isEmpty()) {
            throw IllegalArgumentException("choice requires options")
        }
        if (defaultIndex < 0 || defaultIndex >= options.size) {
            throw IllegalArgumentException("defaultIndex out of range")
        }
        style.promptFrame(question, options, pointerLine = defaultIndex)
        output.flush()
        val raw = reader.readLine()
        val line = if (raw == null) "" else raw.trim()
        val picked = when {
            line.isEmpty() -> options[defaultIndex]
            else -> {
                val asInt = line.toIntOrNull()
                when {
                    asInt != null && asInt in 1..options.size -> options[asInt - 1]
                    else -> options.firstOrNull { it.equals(line, ignoreCase = true) } ?: options[defaultIndex]
                }
            }
        }
        style.promptSubmitted(question, picked)
        return picked
    }
}
