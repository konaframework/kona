package org.konaframework.cli

import org.konaframework.support.console.ConsoleIO
import org.konaframework.support.console.ConsolePrompts
import org.konaframework.support.console.PromptEofException
import org.konaframework.support.console.PromptInterruptedException
import java.io.InputStream
import java.io.PrintStream

/**
 * Interactive prompts for `kona new`: boxed TTY widgets (same look as grind
 * [org.konaframework.grind.Prompts]). Injectable streams keep tests non-blocking
 * and use the line-based fallback. Open so tests can record which questions ran.
 */
open class InstallerPrompts internal constructor(private val inner: ConsolePrompts) {
    @JvmOverloads
    constructor(
        input: InputStream = System.`in`,
        output: PrintStream = System.out,
        color: Boolean = ConsoleIO.detectColor(),
    ) : this(ConsolePrompts(input, output, color, eofThrows = true))

    @JvmOverloads
    open fun ask(question: String, default: String? = null): String = wrap { inner.ask(question, default) }

    @JvmOverloads
    open fun confirm(question: String, default: Boolean = false): Boolean = wrap { inner.confirm(question, default) }

    @JvmOverloads
    open fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String =
        wrap { inner.choice(question, options, defaultIndex) }

    private fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (_: PromptEofException) {
            throw KonaCliException(STDIN_UNREADABLE)
        } catch (_: PromptInterruptedException) {
            throw KonaCliException("Cancelled.")
        }
}
