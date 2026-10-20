package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        assertEquals("", eof.ask("Where?"))
        val eof2 = InstallerPrompts(ByteArrayInputStream(ByteArray(0)), stream, false)
        assertFalse(eof2.confirm("Go?"))
        assertEquals("Vue", eof2.choice("Pick", listOf("Vue", "React")))
        assertFalse(InstallerPrompts(ByteArrayInputStream("\n".toByteArray()), stream, false).confirm("OnlyQ"))
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
}
