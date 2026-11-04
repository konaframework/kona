package org.konaframework.support.console

import org.konaframework.support.KoverExclude
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Boxed TTY prompts: dim `┌┐└┘` frames, cyan `›`, cursor on the value row, in-place
 * submit (green value). Arrow keys move the pointer. When stdin is a pipe but `/dev/tty`
 * is usable (Gradle JavaExec), widgets still run against the controlling terminal.
 * When stdin is not a TTY (tests, pipes, `KONA_LINE_PROMPTS=1`) and no controlling
 * terminal is available, the same API falls back to line-based `readLine`.
 */
class ConsolePrompts private constructor(
    private val input: InputStream,
    private val output: PrintStream,
    private val color: Boolean,
    private val eofThrows: Boolean,
    private val env: Map<String, String>,
    private val systemIn: InputStream,
    private val probe: TtyProbe,
    private val openRaw: () -> RawMode,
    private val columnsOverride: Int,
    private val forceWidgets: Boolean?,
    private val sourceOverride: ByteSource?,
    private val passwordReader: () -> CharArray?,
    private val preferPasswordReader: Boolean,
) {
    @JvmOverloads
    constructor(
        input: InputStream = System.`in`,
        output: PrintStream = System.out,
        color: Boolean = ConsoleIO.detectColor(),
        eofThrows: Boolean = false,
    ) : this(
        input,
        output,
        color,
        eofThrows,
        env = System.getenv(),
        systemIn = System.`in`,
        probe = TtyProbe(controllingTty = DefaultControllingTty.path()),
        openRaw = ProductionRaw::open,
        columnsOverride = 0,
        forceWidgets = null,
        sourceOverride = null,
        passwordReader = Companion::readConsolePassword,
        preferPasswordReader = true,
    )

    private val style = ConsoleIO(output, output, color)
    private val reader by lazy { BufferedReader(InputStreamReader(input, Charsets.UTF_8)) }
    private var activeKeyInput: InputStream? = null

    @JvmOverloads
    fun ask(question: String, default: String? = null): String =
        runPrompt({ askLine(question, default) }) { renderer -> askWidget(question, default, renderer) }

    /**
     * Hidden-input prompt (password). Typed characters are not echoed; the widget shows `*` masks.
     * Line mode prefers [java.io.Console.readPassword] when a console is attached, otherwise reads
     * a normal line (pipes / tests).
     */
    @JvmOverloads
    fun secret(question: String = "Password"): String =
        runPrompt({ secretLine(question) }) { renderer -> secretWidget(question, renderer) }

    /** Alias of [secret]. */
    @JvmOverloads
    fun password(question: String = "Password"): String = secret(question)

    @JvmOverloads
    fun confirm(question: String, default: Boolean = false): Boolean =
        runPrompt({ confirmLine(question, default) }) { renderer -> confirmWidget(question, default, renderer) }

    @JvmOverloads
    fun choice(question: String, options: List<String>, defaultIndex: Int = 0): String =
        runChoice(question, options, defaultIndex)

    private fun runChoice(question: String, options: List<String>, defaultIndex: Int): String {
        require(options.isNotEmpty()) { "choice requires options" }
        require(defaultIndex in options.indices) { "defaultIndex out of range" }
        return runPrompt({ choiceLine(question, options, defaultIndex) }) { renderer ->
            choiceWidget(question, options, defaultIndex, renderer)
        }
    }

    private fun <T> runPrompt(line: () -> T, widget: (PromptRenderer) -> T): T {
        if (!useWidgets()) {
            return line()
        }
        val opened = openWidgetStream()
        if (opened.owns) {
            activeKeyInput = opened.stream
        }
        try {
            val raw = openRaw()
            val renderer = PromptRenderer(output, style, contentWidth(), raw = true)
            try {
                raw.enter()
            } catch (_: RawModeException) {
                return line()
            }
            renderer.hideCursor()
            try {
                return widget(renderer)
            } finally {
                renderer.showCursor()
                raw.close()
            }
        } finally {
            activeKeyInput = null
            opened.closeQuietly()
        }
    }

    private fun useWidgets(): Boolean {
        if (forceWidgets != null) {
            return forceWidgets
        }
        return Tty.shouldUseWidgets(input, systemIn, env, probe)
    }

    private fun contentWidth(): Int {
        val columns = if (columnsOverride > 0) columnsOverride else probe.columns(env)
        return Tty.promptContentWidth(columns)
    }

    private fun keys(): KeyDecoder {
        val override = sourceOverride
        if (override != null) {
            return KeyDecoder(override)
        }
        return KeyDecoder(InputStreamByteSource(activeKeyInput ?: input))
    }

    private fun openWidgetStream(): OwnedInput {
        if (probe.stdinIsTty()) {
            return OwnedInput(input, owns = false)
        }
        val path = probe.controllingTty ?: return OwnedInput(input, owns = false)
        return try {
            OwnedInput(FileInputStream(path), owns = true)
        } catch (_: IOException) {
            OwnedInput(input, owns = false)
        }
    }

    private fun askLine(question: String, default: String?): String {
        val preview = if (default.isNullOrEmpty()) "" else style.paint(ConsoleIO.DIM, default)
        style.promptFrame(question, listOf(preview), pointerPrefix = false)
        output.flush()
        val line = readLineOrEmpty()
        val answer = if (line.isEmpty() && default != null) default else line
        style.promptSubmitted(question, answer)
        return answer
    }

    private fun secretLine(question: String): String {
        style.promptFrame(question, listOf(""), pointerPrefix = false)
        output.flush()
        // Non-echoing reader on real stdin; injected streams (tests / pipes) use line input.
        val answer =
            if (preferPasswordReader && input === systemIn) {
                val chars = passwordReader()
                if (chars == null) {
                    ""
                } else {
                    String(chars)
                }
            } else {
                readLineOrEmpty()
            }
        style.promptSubmitted(question, if (answer.isEmpty()) "" else "*".repeat(answer.length.coerceAtMost(8)))
        return answer
    }

    private fun confirmLine(question: String, default: Boolean): Boolean {
        val options = listOf("Yes", "No")
        val pointer = if (default) 0 else 1
        style.promptFrame(question, options, pointerLine = pointer)
        output.flush()
        val line = readLineOrEmpty().lowercase()
        val yes =
            when {
                line.isEmpty() -> default
                line == "y" || line == "yes" || line == "1" -> true
                line == "n" || line == "no" || line == "2" -> false
                else -> default
            }
        style.promptSubmitted(question, if (yes) "Yes" else "No")
        return yes
    }

    private fun choiceLine(question: String, options: List<String>, defaultIndex: Int): String {
        style.promptFrame(question, options, pointerLine = defaultIndex)
        output.flush()
        val line = readLineOrEmpty()
        val picked =
            when {
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

    private fun askWidget(question: String, default: String?, renderer: PromptRenderer): String =
        editWidget(question, default, renderer, mask = false)

    private fun secretWidget(question: String, renderer: PromptRenderer): String =
        editWidget(question, null, renderer, mask = true)

    private fun editWidget(
        question: String,
        default: String?,
        renderer: PromptRenderer,
        mask: Boolean,
    ): String {
        val points = codePoints(default.orEmpty()).toMutableList()
        var cursor = points.size
        val decoder = keys()
        while (true) {
            val visible = visibleWindow(points, cursor, renderer.width)
            val display = if (mask) "*".repeat(visible.text.length) else visible.text
            renderer.hideCursor()
            renderer.drawAsk(question, display, submitted = false)
            renderer.parkCursorOnValue(PromptRenderer.VALUE_COLUMN + visible.cursor)
            renderer.showCursor()
            when (val key = decoder.next()) {
                TerminalKey.Enter -> {
                    val answer = stringify(points)
                    val submitted = if (mask) {
                        if (answer.isEmpty()) "" else "*".repeat(answer.length.coerceAtMost(8))
                    } else {
                        answer
                    }
                    renderer.hideCursor()
                    renderer.drawAsk(question, submitted, submitted = true)
                    renderer.finishSubmitted()
                    return answer
                }
                TerminalKey.Backspace -> {
                    if (cursor > 0) {
                        points.removeAt(cursor - 1)
                        cursor--
                    }
                }
                TerminalKey.Delete -> {
                    if (cursor < points.size) {
                        points.removeAt(cursor)
                    }
                }
                TerminalKey.Left -> cursor = (cursor - 1).coerceAtLeast(0)
                TerminalKey.Right -> cursor = (cursor + 1).coerceAtMost(points.size)
                TerminalKey.Home -> cursor = 0
                TerminalKey.End -> cursor = points.size
                is TerminalKey.CharKey -> {
                    val inserted = codePoints(key.text)
                    points.addAll(cursor, inserted)
                    cursor += inserted.size
                }
                TerminalKey.CtrlC -> throw PromptInterruptedException()
                TerminalKey.Eof -> return eofOr(stringify(points))
                TerminalKey.Up -> {}
                TerminalKey.Down -> {}
                TerminalKey.Esc -> {}
                TerminalKey.Tab -> {}
                TerminalKey.Ignored -> {
                    cursor = cursor
                }
            }
        }
    }

    private fun confirmWidget(question: String, default: Boolean, renderer: PromptRenderer): Boolean {
        var yes = default
        val decoder = keys()
        renderer.hideCursor()
        while (true) {
            val pointer = if (yes) 0 else 1
            renderer.drawSelect(question, listOf("Yes", "No"), pointer)
            when (val key = decoder.next()) {
                TerminalKey.Enter -> {
                    renderer.drawSelect(question, listOf("Yes", "No"), pointer, submitted = true)
                    renderer.finishSubmitted()
                    return yes
                }
                TerminalKey.Up, TerminalKey.Down -> yes = !yes
                is TerminalKey.CharKey -> {
                    when (val t = key.text.lowercase()) {
                        "k" -> yes = !yes
                        "j" -> yes = !yes
                        "y" -> {
                            yes = true
                            renderer.drawSelect(question, listOf("Yes", "No"), 0, submitted = true)
                            renderer.finishSubmitted()
                            return true
                        }
                        "1" -> {
                            yes = true
                            renderer.drawSelect(question, listOf("Yes", "No"), 0, submitted = true)
                            renderer.finishSubmitted()
                            return true
                        }
                        "n" -> {
                            yes = false
                            renderer.drawSelect(question, listOf("Yes", "No"), 1, submitted = true)
                            renderer.finishSubmitted()
                            return false
                        }
                        "2" -> {
                            yes = false
                            renderer.drawSelect(question, listOf("Yes", "No"), 1, submitted = true)
                            renderer.finishSubmitted()
                            return false
                        }
                        else -> {
                            t.length
                        }
                    }
                }
                TerminalKey.CtrlC -> throw PromptInterruptedException()
                TerminalKey.Eof -> return eofOr(default)
                else -> {}
            }
        }
    }

    private fun choiceWidget(
        question: String,
        options: List<String>,
        defaultIndex: Int,
        renderer: PromptRenderer,
    ): String {
        var index = defaultIndex
        val decoder = keys()
        renderer.hideCursor()
        while (true) {
            renderer.drawSelect(question, options, index)
            when (val key = decoder.next()) {
                TerminalKey.Enter -> {
                    renderer.drawSelect(question, options, index, submitted = true)
                    renderer.finishSubmitted()
                    return options[index]
                }
                TerminalKey.Up -> index = (index - 1 + options.size) % options.size
                TerminalKey.Down -> index = (index + 1) % options.size
                is TerminalKey.CharKey -> {
                    val t = key.text
                    when {
                        t == "k" -> index = (index - 1 + options.size) % options.size
                        t == "j" -> index = (index + 1) % options.size
                        else -> {
                            val digit = t.toIntOrNull()
                            if (digit != null && digit in 1..options.size) {
                                index = digit - 1
                                renderer.drawSelect(question, options, index, submitted = true)
                                renderer.finishSubmitted()
                                return options[index]
                            }
                        }
                    }
                }
                TerminalKey.CtrlC -> throw PromptInterruptedException()
                TerminalKey.Eof -> return eofOr(options[index])
                else -> {}
            }
        }
    }

    private fun readLineOrEmpty(): String {
        val raw =
            try {
                reader.readLine()
            } catch (_: IOException) {
                if (eofThrows) {
                    throw PromptEofException()
                }
                return ""
            }
        if (raw == null) {
            if (eofThrows) {
                throw PromptEofException()
            }
            return ""
        }
        return raw.trim()
    }

    private fun <T> eofOr(value: T): T {
        if (eofThrows) {
            throw PromptEofException()
        }
        return value
    }

    companion object {
        /** Production password reader (`System.console()` is not mockable in unit tests). */
        @JvmStatic
        @KoverExclude
        internal fun readConsolePassword(): CharArray? = System.console()?.readPassword()

        @JvmStatic
        @JvmOverloads
        fun forTest(
            input: InputStream,
            output: PrintStream,
            color: Boolean = false,
            eofThrows: Boolean = false,
            widgets: Boolean? = false,
            columns: Int = 80,
            rawMode: RawMode = NoopRawMode,
            probe: TtyProbe = TtyProbe(CommandRunner { _, _, _ -> CommandResult(1, "") }),
            env: Map<String, String> = emptyMap(),
            systemIn: InputStream = input,
            source: ByteSource? = null,
            passwordReader: (() -> CharArray?)? = null,
        ): ConsolePrompts =
            ConsolePrompts(
                input,
                output,
                color,
                eofThrows,
                env,
                systemIn,
                probe,
                openRaw = { rawMode },
                columnsOverride = columns,
                forceWidgets = widgets,
                sourceOverride = source,
                passwordReader = passwordReader ?: { null },
                preferPasswordReader = passwordReader != null,
            )
    }
}

internal class OwnedInput(val stream: InputStream, val owns: Boolean) {
    fun closeQuietly() {
        if (!owns) {
            return
        }
        try {
            stream.close()
        } catch (_: IOException) {
        }
    }
}

internal fun codePoints(text: String): List<Int> {
    val list = ArrayList<Int>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        list.add(cp)
        i += Character.charCount(cp)
    }
    return list
}

internal fun stringify(points: List<Int>): String {
    if (points.isEmpty()) {
        return ""
    }
    return String(points.toIntArray(), 0, points.size)
}

internal data class VisibleWindow(val text: String, val cursor: Int)

internal fun visibleWindow(points: List<Int>, cursor: Int, width: Int): VisibleWindow {
    if (points.isEmpty() || width <= 0) {
        return VisibleWindow(if (width <= 0) stringify(points) else "", cursor.coerceAtLeast(0))
    }
    val extra = if (cursor >= points.size) 0 else 1
    val start = (cursor - width + extra).coerceAtLeast(0)
    val end = (start + width).coerceAtMost(points.size)
    val slice = points.subList(start, end)
    return VisibleWindow(stringify(slice), cursor - start)
}
