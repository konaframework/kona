package org.konaframework.support.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream

class ConsolePromptsTest {
    private fun line(input: String, color: Boolean = false): Pair<ConsolePrompts, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val prompts =
            ConsolePrompts.forTest(
                ByteArrayInputStream(input.toByteArray()),
                PrintStream(out),
                color,
                widgets = false,
            )
        return prompts to out
    }

    private fun widget(bytes: ByteArray, color: Boolean = false, eofThrows: Boolean = false, columns: Int = 80): Pair<ConsolePrompts, RecordingRawMode> {
        val out = ByteArrayOutputStream()
        val raw = RecordingRawMode()
        val prompts =
            ConsolePrompts.forTest(
                ByteArrayInputStream(bytes),
                PrintStream(out),
                color,
                eofThrows,
                widgets = true,
                columns = columns,
                rawMode = raw,
            )
        return prompts to raw
    }

    private fun widgetOut(bytes: ByteArray, color: Boolean = true): Pair<ConsolePrompts, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val prompts =
            ConsolePrompts.forTest(
                ByteArrayInputStream(bytes),
                PrintStream(out),
                color,
                widgets = true,
                rawMode = RecordingRawMode(),
            )
        return prompts to out
    }

    @Test
    fun lineAskConfirmChoice() {
        val (p, out) = line("Ada\n\nyes\n2\nb\nzzz\n\n")
        assertEquals("Ada", p.ask("Name"))
        assertEquals("def", p.ask("Q", "def"))
        assertTrue(p.confirm("Ok?"))
        assertEquals("b", p.choice("P", listOf("a", "b")))
        assertEquals("b", p.choice("P", listOf("a", "b")))
        assertEquals("a", p.choice("P", listOf("a", "b")))
        assertEquals("a", p.choice("P", listOf("a", "b")))
        assertTrue(out.toString().contains("Name"))
        assertTrue(out.toString().contains("┌"))
        val (p2, _) = line("\n1\n2\nn\nno\nmaybe\n\n")
        assertEquals("", p2.ask("Empty"))
        assertTrue(p2.confirm("A", false))
        assertFalse(p2.confirm("B", true))
        assertFalse(p2.confirm("C", true))
        assertFalse(p2.confirm("C2", true))
        assertTrue(p2.confirm("D", true))
        assertFalse(p2.confirm("E", false))
        val (p3, _) = line("React\n99\n0\n")
        assertEquals("React", p3.choice("Adapter", listOf("Vue", "React"), 0))
        assertEquals("Vue", p3.choice("Adapter", listOf("Vue", "React"), 0))
        assertEquals("Svelte", p3.choice("Adapter", listOf("Vue", "React", "Svelte"), 2))
        assertThrows(IllegalArgumentException::class.java) { p3.choice("X", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { p3.choice("X", listOf("a"), 9) }
        assertThrows(IllegalArgumentException::class.java) { p3.choice("X", listOf("a"), -1) }
        assertThrows(IllegalArgumentException::class.java) { widget(bytes(0x0D)).first.choice("X", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { widget(bytes(0x0D)).first.choice("X", listOf("a"), 4) }
    }

    @Test
    fun lineEofAndIo() {
        val out = ByteArrayOutputStream()
        val eof =
            ConsolePrompts.forTest(
                ByteArrayInputStream(ByteArray(0)),
                PrintStream(out),
                eofThrows = true,
                widgets = false,
            )
        assertThrows(PromptEofException::class.java) { eof.ask("Where?") }
        assertThrows(PromptEofException::class.java) {
            ConsolePrompts.forTest(ByteArrayInputStream(ByteArray(0)), PrintStream(out), eofThrows = true, widgets = false)
                .confirm("Go?")
        }
        assertThrows(PromptEofException::class.java) {
            ConsolePrompts.forTest(ByteArrayInputStream(ByteArray(0)), PrintStream(out), eofThrows = true, widgets = false)
                .choice("Pick", listOf("A", "B"))
        }
        val emptyOk = ConsolePrompts.forTest(ByteArrayInputStream(ByteArray(0)), PrintStream(out), widgets = false)
        assertEquals("", emptyOk.ask("Q"))
        val throwing =
            object : InputStream() {
                override fun read(): Int = throw IOException("closed")
            }
        assertThrows(PromptEofException::class.java) {
            ConsolePrompts.forTest(throwing, PrintStream(out), eofThrows = true, widgets = false).ask("Q", "d")
        }
        assertEquals("d", ConsolePrompts.forTest(throwing, PrintStream(out), widgets = false).ask("Q", "d"))
        ConsolePrompts()
        ConsolePrompts(ByteArrayInputStream("\n".toByteArray()))
        ConsolePrompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out))
        ConsolePrompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out), false)
        ConsolePrompts(ByteArrayInputStream("\n".toByteArray()), PrintStream(out), false, false)
    }

    @Test
    fun widgetAskTypingCursorAndSubmit() {
        val utf = "é".toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        val keys =
            bytes(
                'X'.code,
                0x7F,
                0x1B, '['.code, 'D'.code,
                0x1B, '['.code, 'C'.code,
                0x1B, '['.code, 'H'.code,
                0x1B, '['.code, 'F'.code,
                0x1B, '['.code, '3'.code, '~'.code,
                0x08,
                *utf.toIntArray(),
                0x0D,
            )
        val (p, raw) = widget(keys)
        assertEquals("Adé", p.ask("App name", "Ada"))
        assertEquals(1, raw.enters)
        assertTrue(raw.closes >= 1)
        val (p2, out) = widgetOut("Hi\r".toByteArray())
        assertEquals("Hi", p2.ask("Name"))
        assertTrue(out.toString().contains("\u001b["))
        assertTrue(out.toString().contains("Name"))
        assertTrue(out.toString().contains(PromptRenderer.CLEAR_DOWN) || out.toString().contains("Hi"))
    }

    @Test
    fun widgetAskHomeDeleteLeftBackspace() {
        val seq = bytes('A'.code, 'B'.code, 'C'.code, 0x1B, '['.code, 'H'.code, 0x1B, '['.code, '3'.code, '~'.code, 0x0D)
        val (p, _) = widget(seq)
        assertEquals("BC", p.ask("N"))
        val seq2 = bytes('A'.code, 'B'.code, 0x1B, '['.code, 'D'.code, 0x7F, 0x0D)
        assertEquals("B", widget(seq2).first.ask("N"))
        val seq3 = bytes(0x1B, '['.code, 'D'.code, 0x7F, 0x1B, '['.code, 'C'.code, 0x1B, '['.code, '3'.code, '~'.code, 0x0D)
        assertEquals("", widget(seq3).first.ask("N"))
        val ignored =
            bytes(
                0x1B, '['.code, 'A'.code,
                0x1B, '['.code, 'B'.code,
                0x09,
                0x1B, '['.code, 'Z'.code,
                0x01,
                0x0D,
            )
        assertEquals("", widget(ignored).first.ask("N"))
        assertEquals("", widget(bytes(0x1B)).first.ask("N"))
    }

    @Test
    fun widgetConfirmArrowsAndLetters() {
        assertTrue(widget(bytes(0x0D)).first.confirm("Ok?", true))
        assertFalse(widget(bytes(0x0D)).first.confirm("Ok?", false))
        assertTrue(widget(bytes(0x1B, '['.code, 'B'.code, 0x0D)).first.confirm("Ok?", false))
        assertFalse(widget(bytes(0x1B, '['.code, 'A'.code, 0x0D)).first.confirm("Ok?", true))
        assertTrue(widget(bytes('k'.code, 0x0D)).first.confirm("Ok?", false))
        assertFalse(widget(bytes('j'.code, 0x0D)).first.confirm("Ok?", true))
        assertTrue(widget(bytes('y'.code)).first.confirm("Ok?", false))
        assertFalse(widget(bytes('n'.code)).first.confirm("Ok?", true))
        assertTrue(widget(bytes('1'.code)).first.confirm("Ok?", false))
        assertFalse(widget(bytes('2'.code)).first.confirm("Ok?", true))
        assertTrue(widget(bytes('x'.code, 0x0D)).first.confirm("Ok?", true))
        assertTrue(widget(bytes(0x09, 0x0D)).first.confirm("Ok?", true))
    }

    @Test
    fun widgetChoiceWrapDigitsAndJk() {
        val opts = listOf("Kotlin", "Java", "Go")
        assertEquals("Java", widget(bytes(0x1B, '['.code, 'B'.code, 0x0D)).first.choice("Lang", opts))
        assertEquals("Go", widget(bytes(0x1B, '['.code, 'A'.code, 0x0D)).first.choice("Lang", opts))
        assertEquals("Java", widget(bytes('j'.code, 0x0D)).first.choice("Lang", opts))
        assertEquals("Go", widget(bytes('k'.code, 0x0D)).first.choice("Lang", opts))
        assertEquals("Java", widget(bytes('2'.code)).first.choice("Lang", opts))
        assertEquals("Kotlin", widget(bytes('z'.code, '9'.code, 0x0D)).first.choice("Lang", opts))
        assertEquals("Kotlin", widget(bytes(0x0D)).first.choice("Lang", opts, 0))
    }

    @Test
    fun widgetCtrlCAndEof() {
        assertThrows(PromptInterruptedException::class.java) {
            widget(bytes(0x03)).first.ask("N")
        }
        assertThrows(PromptInterruptedException::class.java) {
            widget(bytes(0x03)).first.confirm("Q")
        }
        assertThrows(PromptInterruptedException::class.java) {
            widget(bytes(0x03)).first.choice("Q", listOf("a", "b"))
        }
        assertEquals("kept", widget(bytes(0x04)).first.ask("N", "kept"))
        assertTrue(widget(bytes(0x04)).first.confirm("Q", true))
        assertEquals("a", widget(bytes(0x04)).first.choice("Q", listOf("a", "b")))
        assertThrows(PromptEofException::class.java) {
            widget(bytes(0x04), eofThrows = true).first.ask("N")
        }
    }

    @Test
    fun rawModeFailureFallsBackToLine() {
        val out = ByteArrayOutputStream()
        val prompts =
            ConsolePrompts.forTest(
                ByteArrayInputStream("typed\n".toByteArray()),
                PrintStream(out),
                widgets = true,
                rawMode = ThrowingRawMode(),
            )
        assertEquals("typed", prompts.ask("Name"))
        assertTrue(out.toString().contains("Name"))
    }

    @Test
    fun autoDetectUsesProbeAndColumns() {
        val same = ByteArrayInputStream("Ada\n".toByteArray())
        val out = ByteArrayOutputStream()
        val probe =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    if (cmd == listOf("stty", "size")) CommandResult(0, "24 64") else CommandResult(0, "")
                },
            )
        val prompts =
            ConsolePrompts.forTest(
                same,
                PrintStream(out),
                widgets = null,
                columns = 0,
                probe = probe,
                env = mapOf("COLUMNS" to "70"),
                systemIn = same,
                rawMode = RecordingRawMode(),
            )
        assertEquals("Ada", prompts.ask("N"))
        val widgeted =
            ConsolePrompts.forTest(
                ByteArrayInputStream(bytes(0x0D)),
                PrintStream(ByteArrayOutputStream()),
                widgets = true,
                columns = 0,
                probe = probe,
                env = emptyMap(),
                rawMode = RecordingRawMode(),
            )
        assertEquals("", widgeted.ask("N"))
        val forcedLine =
            ConsolePrompts.forTest(
                ByteArrayInputStream("x\n".toByteArray()),
                PrintStream(ByteArrayOutputStream()),
                widgets = null,
                env = mapOf(Tty.LINE_PROMPTS_ENV to "1"),
                systemIn = ByteArrayInputStream("x\n".toByteArray()),
                probe = TtyProbe(CommandRunner { _, _, _ -> CommandResult(0, "") }),
            )
        assertEquals("x", forcedLine.ask("N"))
        val pipe = ByteArrayInputStream("z\n".toByteArray())
        val noTty =
            ConsolePrompts.forTest(
                pipe,
                PrintStream(ByteArrayOutputStream()),
                widgets = null,
                systemIn = pipe,
                probe = TtyProbe(CommandRunner { _, _, _ -> CommandResult(1, "") }),
            )
        assertEquals("z", noTty.ask("N"))
        val (colored, coloredOut) = line("\n", color = true)
        assertEquals("def", colored.ask("Q", "def"))
        assertTrue(coloredOut.toString().contains("def") || coloredOut.toString().contains("Q"))
        assertTrue(widget(bytes('Y'.code)).first.confirm("Ok?", false))
        assertEquals("Java", widget(bytes(0x1B, '['.code, 'C'.code, '2'.code)).first.choice("Lang", listOf("Kotlin", "Java")))
    }

    @Test
    fun visibleWindowAndCodePoints() {
        assertEquals("", stringify(emptyList()))
        assertEquals(listOf(0x1F600), codePoints("😀"))
        assertEquals("", visibleWindow(emptyList(), 0, 10).text)
        assertEquals("ab", visibleWindow(codePoints("ab"), 2, 0).text)
        val long = codePoints("abcdefghijklmnopqrstuvwxyz")
        val window = visibleWindow(long, 26, 5)
        assertEquals("vwxyz", window.text)
        assertEquals(5, window.cursor)
        val start = visibleWindow(long, 0, 5)
        assertEquals("abcde", start.text)
        assertEquals(0, start.cursor)
        val mid = visibleWindow(long, 10, 5)
        assertEquals(5, mid.text.length)
        assertTrue(mid.cursor in 0..5)
    }

    @Test
    fun widgetConfirmArrowsWhenAvailableReportsZero() {
        val out = ByteArrayOutputStream()
        val source = BufferSource(bytes(0x1B, '['.code, 'A'.code, 0x0D), 0)
        val prompts =
            ConsolePrompts.forTest(
                ByteArrayInputStream(ByteArray(0)),
                PrintStream(out),
                widgets = true,
                source = source,
                rawMode = RecordingRawMode(),
            )
        assertFalse(prompts.confirm("Port 8080 is in use. Use 8081 instead?", true))
        val down = BufferSource(bytes(0x1B, '['.code, 'B'.code, 0x0D), 0)
        val choice =
            ConsolePrompts.forTest(
                ByteArrayInputStream(ByteArray(0)),
                PrintStream(ByteArrayOutputStream()),
                widgets = true,
                source = down,
                rawMode = RecordingRawMode(),
            )
        assertEquals("Java", choice.choice("Lang", listOf("Kotlin", "Java")))
    }

    @Test
    fun widgetReadsControllingTtyWhenStdinIsPipe() {
        val ttyFile = java.nio.file.Files.createTempFile("kona-tty", ".in")
        java.nio.file.Files.write(ttyFile, bytes(0x1B, '['.code, 'A'.code, 0x0D))
        val pipe = ByteArrayInputStream("ignored\n".toByteArray())
        val probe =
            TtyProbe(
                CommandRunner { cmd, _, _ ->
                    if (cmd.contains("-g")) CommandResult(0, "saved\n") else CommandResult(1, "")
                },
                controllingTty = ttyFile.toAbsolutePath().toString(),
            )
        val prompts =
            ConsolePrompts.forTest(
                pipe,
                PrintStream(ByteArrayOutputStream()),
                widgets = null,
                probe = probe,
                systemIn = pipe,
                rawMode = RecordingRawMode(),
            )
        try {
            assertFalse(prompts.confirm("Port 8080 is in use. Use 8081 instead?", true))
        } finally {
            java.nio.file.Files.deleteIfExists(ttyFile)
        }
    }

    @Test
    fun missingControllingTtyFallsBackToStdin() {
        val pipe = ByteArrayInputStream(bytes(0x0D))
        val probe =
            TtyProbe(
                CommandRunner { _, _, _ -> CommandResult(1, "") },
                controllingTty = "/no/such/kona-tty-device",
            )
        val prompts =
            ConsolePrompts.forTest(
                pipe,
                PrintStream(ByteArrayOutputStream()),
                widgets = true,
                probe = probe,
                systemIn = pipe,
                rawMode = RecordingRawMode(),
            )
        assertTrue(prompts.confirm("Ok?", true))
        OwnedInput(ByteArrayInputStream(ByteArray(0)), owns = false).closeQuietly()
        val throwing =
            object : InputStream() {
                override fun read(): Int = -1

                override fun close() {
                    throw IOException("closed")
                }
            }
        OwnedInput(throwing, owns = true).closeQuietly()
    }

    @Test
    fun forTestSourceOverride() {
        val out = ByteArrayOutputStream()
        val source = BufferSource(bytes(0x0D))
        val prompts =
            ConsolePrompts.forTest(
                ByteArrayInputStream(ByteArray(0)),
                PrintStream(out),
                widgets = true,
                source = source,
                rawMode = RecordingRawMode(),
            )
        assertEquals("def", prompts.ask("Q", "def"))
    }
}

internal class RecordingRawMode : RawMode {
    var enters = 0
    var closes = 0

    override fun enter() {
        enters++
    }

    override fun close() {
        closes++
    }
}

internal class ThrowingRawMode : RawMode {
    override fun enter() = throw RawModeException("no tty")

    override fun close() {}
}

internal fun bytes(vararg values: Int): ByteArray = values.map { it.and(0xFF).toByte() }.toByteArray()
