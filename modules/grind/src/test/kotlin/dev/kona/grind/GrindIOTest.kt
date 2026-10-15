package dev.kona.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class GrindIOTest {
    @Test
    fun styledOutputWithoutColor() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), PrintStream(err), Verbosity.NORMAL, color = false)
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
        val text = out.toString()
        assertTrue(text.contains("ℹ  hello"))
        assertTrue(text.contains("✓  done"))
        assertTrue(text.contains("!  careful"))
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("Sub"))
        assertTrue(text.contains("serve"))
        assertTrue(text.contains("Run server"))
        assertTrue(text.contains("╭─"))
        assertTrue(text.contains("one"))
        assertTrue(text.contains("25%"))
        assertTrue(text.contains("plain"))
        assertFalse(text.contains("\u001B["))
        assertTrue(err.toString().contains("✗  boom"))
        assertFalse(err.toString().contains("\u001B["))
    }

    @Test
    fun styledOutputWithColor() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), PrintStream(err), Verbosity.NORMAL, color = true)
        io.success("ok")
        io.error("bad")
        io.progress(4, 4)
        assertTrue(out.toString().contains("\u001B["))
        assertTrue(out.toString().contains("ok"))
        assertTrue(out.toString().contains("100%"))
        assertTrue(err.toString().contains("\u001B["))
        assertTrue(err.toString().contains("bad"))
    }

    @Test
    fun quietHidesStdoutButNotErrors() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), PrintStream(err), Verbosity.QUIET, color = false)
        io.success("hidden")
        io.info("hidden")
        io.warn("hidden")
        io.title("hidden")
        io.twoColumn("a", "b")
        io.box("t", listOf("x"))
        io.progress(1, 2)
        io.error("shown")
        assertEquals("", out.toString())
        assertTrue(err.toString().contains("shown"))
    }

    @Test
    fun verboseDetectColorAndProgress() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), verbosity = Verbosity.VERBOSE, color = false)
        io.verbose("detail")
        assertTrue(out.toString().contains("detail"))

        assertFalse(GrindIO.detectColor(env = mapOf("NO_COLOR" to "1")))
        assertTrue(GrindIO.detectColor(env = mapOf("FORCE_COLOR" to "1")))
        assertFalse(GrindIO.detectColor(env = mapOf("FORCE_COLOR" to "0")))
        assertFalse(GrindIO.detectColor(env = mapOf("TERM" to "dumb")))
        assertFalse(GrindIO.detectColor(env = emptyMap()))
        assertTrue(GrindIO.detectColor(env = mapOf("TERM" to "xterm-256color")))
        GrindIO.detectColor()

        assertEquals(Verbosity.QUIET, GrindIO.fromOptions(mapOf("q" to "true"), color = false).verbosity)
        assertEquals(Verbosity.QUIET, GrindIO.fromOptions(mapOf("quiet" to "true"), color = false).verbosity)
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("v" to "true"), color = false).verbosity)
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("verbose" to "true"), color = false).verbosity)
        assertEquals(Verbosity.NORMAL, GrindIO.fromOptions(emptyMap(), color = false).verbosity)
        GrindIO.fromOptions(emptyMap())

        assertThrows(IllegalArgumentException::class.java) {
            GrindIO(PrintStream(out), color = false).progress(0, 0)
        }
        val clamped = ByteArrayOutputStream()
        GrindIO(PrintStream(clamped), color = false).progress(99, 5)
        assertTrue(clamped.toString().contains("100%"))
        GrindIO(PrintStream(clamped), color = false).progress(0, 5)
    }

    @Test
    fun boxWidthBranches() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), color = true)
        io.box("Hi", listOf("a")) // raw < 24; line shorter than title path
        io.box("ab", listOf("longer-than-title-here")) // line longer than title
        io.box("Exact-width-title-here!", listOf("Exact-width-title-here!")) // mid
        io.box("X".repeat(60), listOf("Y".repeat(60))) // raw > 56
        io.box("alone", emptyList()) // no lines
        io.title("T".repeat(60))
        io.box("t", listOf("\u001B[31mred\u001B[0m"))
        assertTrue(out.toString().contains("╭─"))
        assertTrue(out.toString().contains("╰"))
    }
}
