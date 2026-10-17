package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class InstallCommandTest {
    @Test
    fun konaInstallRedirectsToGrindInstall() {
        val err = ByteArrayOutputStream()
        val prev = System.err
        System.setErr(PrintStream(err))
        try {
            assertEquals(1, KonaApplication().run(arrayOf("install", "brew")))
        } finally {
            System.setErr(prev)
        }
        val text = err.toString()
        assert(text.contains("kona grind install")) { text }
        assert(text.contains("./grind install")) { text }
    }
}
