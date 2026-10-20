package org.konaframework.cli

import org.konaframework.support.console.ConsolePrompts
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

class InstallerPromptsTest {
    private fun prompts(input: String): Pair<InstallerPrompts, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val p = InstallerPrompts(ByteArrayInputStream(input.toByteArray()), PrintStream(out), color = false)
        return p to out
    }

    @Test
    fun constructorsAndEofReadLine() {
        InstallerPrompts()
        val empty = ByteArrayInputStream(ByteArray(0))
        val out = ByteArrayOutputStream()
        val stream = PrintStream(out)
        InstallerPrompts(empty)
        val eof = InstallerPrompts(ByteArrayInputStream(ByteArray(0)), stream)
        val eofEx = assertThrows(KonaCliException::class.java) { eof.ask("Where?") }
        assertEquals(STDIN_UNREADABLE, eofEx.message)
        val eof2 = InstallerPrompts(ByteArrayInputStream(ByteArray(0)), stream, false)
        assertThrows(KonaCliException::class.java) { eof2.confirm("Go?") }
        val eof3 = InstallerPrompts(ByteArrayInputStream(ByteArray(0)), stream, false)
        assertThrows(KonaCliException::class.java) { eof3.choice("Pick", listOf("Vue", "React")) }
        assertFalse(InstallerPrompts(ByteArrayInputStream("\n".toByteArray()), stream, false).confirm("OnlyQ"))
        val throwing = object : InputStream() {
            override fun read(): Int = throw IOException("closed")
        }
        val ioEx = assertThrows(KonaCliException::class.java) {
            InstallerPrompts(throwing, stream, false).ask("Q", "d")
        }
        assertEquals(STDIN_UNREADABLE, ioEx.message)
    }

    @Test
    fun askUsesDefaultOnEmptyAndTypedValue() {
        val (p, out) = prompts("\ntyped\n")
        assertEquals("kona-app", p.ask("Where?", "kona-app"))
        assertEquals("typed", p.ask("Again?", "ignored"))
        val text = out.toString()
        assertTrue(text.contains("Where?"))
        assertTrue(text.contains("kona-app"))
    }

    @Test
    fun askWithoutDefaultEmptyLineIsEmpty() {
        val (p, _) = prompts("\n")
        assertEquals("", p.ask("Name?"))
    }

    @Test
    fun confirmYesNoAndFallbacks() {
        val (p, _) = prompts("y\nn\nyes\nno\n1\n2\nmaybe\n\n")
        assertTrue(p.confirm("A", false))
        assertFalse(p.confirm("B", true))
        assertTrue(p.confirm("C", false))
        assertFalse(p.confirm("D", true))
        assertTrue(p.confirm("E", false))
        assertFalse(p.confirm("F", true))
        assertTrue(p.confirm("G", true))
        assertFalse(p.confirm("H", false))
    }

    @Test
    fun choiceNumberNameAndDefault() {
        val options = listOf("Vue", "React", "Svelte")
        val (p, out) = prompts("2\nReact\n99\n\n")
        assertEquals("React", p.choice("Adapter", options, 0))
        assertEquals("React", p.choice("Adapter", options, 0))
        assertEquals("Vue", p.choice("Adapter", options, 0))
        assertEquals("Svelte", p.choice("Adapter", options, 2))
        assertTrue(out.toString().contains("Adapter"))
        assertThrows(IllegalArgumentException::class.java) { p.choice("X", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { p.choice("X", options, 9) }
        assertThrows(IllegalArgumentException::class.java) { p.choice("X", options, -1) }
    }

    @Test
    fun interruptFromWidgetBecomesCliException() {
        val out = ByteArrayOutputStream()
        val inner =
            ConsolePrompts.forTest(
                ByteArrayInputStream(byteArrayOf(0x03)),
                PrintStream(out),
                widgets = true,
            )
        val p = InstallerPrompts(inner)
        val ex = assertThrows(KonaCliException::class.java) { p.ask("Name") }
        assertEquals("Cancelled.", ex.message)
        val innerChoice =
            ConsolePrompts.forTest(
                ByteArrayInputStream(byteArrayOf(0x03)),
                PrintStream(out),
                widgets = true,
            )
        assertThrows(KonaCliException::class.java) { InstallerPrompts(innerChoice).choice("P", listOf("a", "b")) }
        val innerConfirm =
            ConsolePrompts.forTest(
                ByteArrayInputStream(byteArrayOf(0x03)),
                PrintStream(out),
                widgets = true,
            )
        assertThrows(KonaCliException::class.java) { InstallerPrompts(innerConfirm).confirm("Go?") }
    }
}
