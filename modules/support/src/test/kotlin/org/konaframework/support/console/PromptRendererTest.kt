package org.konaframework.support.console

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class PromptRendererTest {
    @Test
    fun redrawsInPlaceAndParksCursor() {
        val buf = ByteArrayOutputStream()
        val style = ConsoleIO(PrintStream(buf), color = false)
        val renderer = PromptRenderer(PrintStream(buf), style, 40, raw = true)
        renderer.hideCursor()
        renderer.drawAsk("App name", "Hi", submitted = false)
        renderer.parkCursorOnValue(6)
        renderer.drawAsk("App name", "Hi!", submitted = false)
        renderer.drawAsk("App name", "Hi!", submitted = true)
        renderer.finishSubmitted()
        val text = buf.toString()
        assertTrue(text.contains("App name"))
        assertTrue(text.contains("Hi!"))
        assertTrue(text.contains(PromptRenderer.HIDE))
        assertTrue(text.contains(PromptRenderer.SHOW))
        assertTrue(text.contains(PromptRenderer.CLEAR_DOWN))
        assertTrue(text.contains("\u001b[1A") || text.contains("\u001b[3A"))
        assertTrue(text.contains("\r\n"))
        assertTrue(text.contains("\u001b[6G") || text.contains("\u001b[2A"))
    }

    @Test
    fun selectUsesPointerAndSubmittedGreen() {
        val buf = ByteArrayOutputStream()
        val style = ConsoleIO(PrintStream(buf), color = true)
        val renderer = PromptRenderer(PrintStream(buf), style, 40, raw = false)
        renderer.drawSelect("Language", listOf("Kotlin", "Java"), 0)
        renderer.drawSelect("Language", listOf("Kotlin", "Java"), 1)
        renderer.drawSelect("Language", listOf("Kotlin", "Java"), 1, submitted = true)
        renderer.finishSubmitted()
        val text = buf.toString()
        assertTrue(text.contains("Kotlin"))
        assertTrue(text.contains("Java"))
        assertTrue(text.contains("›"))
        assertTrue(text.contains("\n"))
        assertFalse(text.contains("\r\n"))
    }

    @Test
    fun csiHelper() {
        assertTrue(PromptRenderer.csi("3A").endsWith("3A"))
        assertTrue(PromptRenderer.VALUE_COLUMN == 4)
    }
}
