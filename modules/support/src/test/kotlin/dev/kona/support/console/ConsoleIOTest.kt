package dev.kona.support.console

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ConsoleIOTest {
    @Test
    fun styledWithoutColor() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = ConsoleIO(PrintStream(out), PrintStream(err), color = false)
        io.info("hello")
        io.success("done")
        io.warn("careful")
        io.error("boom")
        io.title("Title")
        io.subtitle("Sub")
        io.twoColumn("serve", "Run server")
        io.box("Kona", listOf("one", "two"))
        io.progress(1, 4, "step")
        io.line()
        io.line("plain")
        io.write("w")
        val text = out.toString()
        assertTrue(text.contains("ℹ  hello"))
        assertTrue(text.contains("✓  done"))
        assertTrue(text.contains("!  careful"))
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("Sub"))
        assertTrue(text.contains("serve"))
        assertTrue(text.contains("╭─"))
        assertTrue(text.contains("25%"))
        assertTrue(text.contains("plain"))
        assertTrue(text.contains("w"))
        assertFalse(text.contains("\u001B["))
        assertTrue(err.toString().contains("✗  boom"))
    }

    @Test
    fun styledWithColorAndDetect() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = ConsoleIO(PrintStream(out), PrintStream(err), color = true)
        io.success("ok")
        io.error("bad")
        io.progress(4, 4)
        assertTrue(out.toString().contains("\u001B["))
        assertTrue(out.toString().contains("100%"))
        assertTrue(err.toString().contains("\u001B["))

        assertFalse(ConsoleIO.detectColor(env = mapOf("NO_COLOR" to "1")))
        assertTrue(ConsoleIO.detectColor(env = mapOf("FORCE_COLOR" to "1")))
        assertFalse(ConsoleIO.detectColor(env = mapOf("FORCE_COLOR" to "0")))
        assertFalse(ConsoleIO.detectColor(env = mapOf("TERM" to "dumb")))
        assertFalse(ConsoleIO.detectColor(env = emptyMap()))
        assertTrue(ConsoleIO.detectColor(env = mapOf("TERM" to "xterm-256color")))
        ConsoleIO.detectColor()

        assertThrows(IllegalArgumentException::class.java) {
            ConsoleIO(PrintStream(out), color = false).progress(0, 0)
        }
        val clamped = ByteArrayOutputStream()
        ConsoleIO(PrintStream(clamped), color = false).progress(99, 5)
        assertTrue(clamped.toString().contains("100%"))
        ConsoleIO(PrintStream(clamped), color = false).progress(0, 5)
    }

    @Test
    fun boxWidthBranches() {
        val out = ByteArrayOutputStream()
        val io = ConsoleIO(PrintStream(out), color = true)
        io.box("Hi", listOf("a")) // raw < 24
        io.box("ab", listOf("longer-than-title-here"))
        io.box("ABCDEFGHIJKLMNOPQRSTUVWXY", listOf("ABCDEFGHIJKLMNOPQRSTUVWXY")) // 25 → else
        io.box("X".repeat(60), listOf("Y".repeat(60))) // raw > 56
        io.box("alone", emptyList())
        io.title("T".repeat(60))
        io.box("t", listOf("\u001B[31mred\u001B[0m"))
        assertTrue(out.toString().contains("╭─"))
        assertTrue(io.plainLength("\u001B[31mx\u001B[0m") == 1)
        assertTrue(io.paint(ConsoleIO.RED, "x").contains("\u001B["))
        assertFalse(ConsoleIO(PrintStream(out), color = false).paint(ConsoleIO.RED, "x").contains("\u001B["))
    }
}
