package dev.kona.cli

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CliIOTest {
    @Test
    fun styledWithoutColor() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = CliIO(PrintStream(out), PrintStream(err), color = false)
        io.info("i")
        io.success("s")
        io.error("e")
        io.box("kona", listOf("a", "b"))
        io.twoColumn("new", "create")
        io.writeln()
        val text = out.toString()
        assertTrue(text.contains("ℹ  i"))
        assertTrue(text.contains("✓  s"))
        assertTrue(text.contains("╭─"))
        assertTrue(text.contains("new"))
        assertFalse(text.contains("\u001B["))
        assertTrue(err.toString().contains("✗  e"))
    }

    @Test
    fun styledWithColorDetectAndBoxWidths() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = CliIO(PrintStream(out), PrintStream(err), color = true)
        io.success("ok")
        io.error("bad")
        io.box("Hi", listOf("x"))
        io.box("mid-sized-title-value!!", listOf("mid-sized-title-value!!"))
        io.box("Z".repeat(60), listOf("W".repeat(60)))
        assertTrue(out.toString().contains("\u001B["))
        assertTrue(err.toString().contains("\u001B["))

        assertFalse(CliIO.detectColor(env = mapOf("NO_COLOR" to "1")))
        assertTrue(CliIO.detectColor(env = mapOf("FORCE_COLOR" to "1")))
        assertFalse(CliIO.detectColor(env = mapOf("FORCE_COLOR" to "0")))
        assertFalse(CliIO.detectColor(env = mapOf("TERM" to "dumb")))
        assertFalse(CliIO.detectColor(env = emptyMap()))
        assertTrue(CliIO.detectColor(env = mapOf("TERM" to "xterm")))
        CliIO.detectColor()
    }
}
