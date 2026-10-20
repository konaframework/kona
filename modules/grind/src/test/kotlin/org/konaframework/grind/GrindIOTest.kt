package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/** Verbosity / fromOptions layer over [org.konaframework.support.console.ConsoleIO]. */
class GrindIOTest {
    @Test
    fun quietHidesStdoutButNotErrors() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), PrintStream(err), Verbosity.QUIET, color = false)
        io.success("hidden")
        io.info("hidden")
        io.warn("hidden")
        io.title("hidden")
        io.subtitle("hidden")
        io.twoColumn("a", "b")
        io.box("t", listOf("x"))
        io.progress(1, 2)
        io.line("hidden")
        io.write("x")
        io.writeln()
        io.taskLine("hidden", "DONE", 1)
        io.intro("hidden")
        io.outro("hidden")
        io.note("hidden")
        io.promptFrame("hidden", listOf("x"))
        io.promptSubmitted("hidden", "x")
        io.error("shown")
        assertEquals("", out.toString())
        assertTrue(err.toString().contains("shown"))
    }

    @Test
    fun verboseAndFromOptions() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), verbosity = Verbosity.VERBOSE, color = false)
        io.verbose("detail")
        io.success("ok")
        assertTrue(out.toString().contains("detail"))
        assertTrue(out.toString().contains("ok"))

        assertFalse(GrindIO.detectColor(env = mapOf("NO_COLOR" to "1")))
        assertTrue(GrindIO.detectColor(env = mapOf("TERM" to "xterm")))
        GrindIO.detectColor()

        assertEquals(Verbosity.QUIET, GrindIO.fromOptions(mapOf("q" to "true"), color = false).verbosity)
        assertEquals(Verbosity.QUIET, GrindIO.fromOptions(mapOf("quiet" to "true"), color = false).verbosity)
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("v" to "true"), color = false).verbosity)
        assertEquals(Verbosity.VERBOSE, GrindIO.fromOptions(mapOf("verbose" to "true"), color = false).verbosity)
        assertEquals(Verbosity.NORMAL, GrindIO.fromOptions(emptyMap(), color = false).verbosity)
        GrindIO.fromOptions(emptyMap())
    }

    @Test
    fun normalDelegatesStyledOutput() {
        val out = ByteArrayOutputStream()
        val io = GrindIO(PrintStream(out), color = false)
        io.info("i")
        io.warn("w")
        io.box("grind", listOf("a"))
        io.progress(2, 4)
        io.subtitle("sub")
        assertTrue(out.toString().contains("INFO"))
        assertTrue(out.toString().contains("WARN"))
        assertTrue(out.toString().contains("┌"))
        assertTrue(out.toString().contains("50%"))
        assertTrue(out.toString().contains("sub"))
    }

    @Test
    fun tableRespectsQuiet() {
        val out = ByteArrayOutputStream()
        val quiet = GrindIO(PrintStream(out), verbosity = Verbosity.QUIET, color = false)
        quiet.table(listOf("A"), listOf(listOf("1")))
        assertEquals("", out.toString())
        val normal = GrindIO(PrintStream(out), color = false)
        normal.table(listOf("A"), listOf(listOf("1")))
        assertTrue(out.toString().contains("A"))
        normal.taskLine("create_widgets_table", "DONE", 8)
        normal.taskLine("WidgetSeeder", "DONE")
        assertTrue(out.toString().contains("create_widgets_table"))
        assertTrue(out.toString().contains("DONE"))
        assertTrue(out.toString().contains("8ms"))
        normal.intro("grind")
        normal.outro("bye")
        normal.note("n")
        normal.note("w", "WARN")
        normal.promptFrame("Q", listOf("a"), 0)
        normal.promptSubmitted("Q", "a")
        assertTrue(out.toString().contains("grind"))
        assertEquals("x", normal.paint(org.konaframework.support.console.ConsoleIO.GREEN, "x"))
        val painted = GrindIO(PrintStream(out), color = true)
        assertTrue(painted.paint(org.konaframework.support.console.ConsoleIO.GREEN, "Ran").contains("\u001B["))
    }
}
