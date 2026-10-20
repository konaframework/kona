package org.konaframework.grind

import org.konaframework.support.console.ConsoleIO
import org.konaframework.support.console.ConsolePrompts
import org.konaframework.support.console.PromptInterruptedException
import java.io.InputStream
import java.io.PrintStream

/**
 * Boxed TTY prompts for grind: dim `┌┐└┘` frames, cyan `›` pointer, cursor on the
 * value row, in-place green submit. Arrow keys move the pointer. Injected streams
 * (tests, pipes) keep the line-based fallback.
 */
class Prompts
@JvmOverloads
constructor(
    input: InputStream = System.`in`,
    output: PrintStream = System.out,
    color: Boolean = ConsoleIO.detectColor(),
) {
    private val inner = ConsolePrompts(input, output, color)

    @JvmOverloads
    fun ask(question: String, default: String? = null): String = wrap { inner.ask(question, default) }

    @JvmOverloads
    fun confirm(question: String, default: Boolean = false): Boolean = wrap { inner.confirm(question, default) }

    @JvmOverloads
    fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String =
        wrap { inner.choice(question, options, defaultIndex) }

    private fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (_: PromptInterruptedException) {
            throw GrindException("Cancelled.")
        }
}
