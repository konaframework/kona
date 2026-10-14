package dev.kona.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProcessTest {
    @Test
    fun runsExternalCommands(@TempDir base: Path) {
        val runner = ProcessRunner()
        val result = runner.run(listOf("sh", "-c", "echo hello"), base.toFile(), mapOf("K" to "v"))
        assertTrue(result.successful())
        assertTrue(result.output.contains("hello"))
        assertEquals(0, result.exitCode)

        assertTrue(runner.run(listOf("sh", "-c", "true")).successful())
        assertTrue(runner.run(listOf("sh", "-c", "true"), base.toFile()).successful())
        assertTrue(runner.run(listOf("sh", "-c", "true"), base.toFile(), emptyMap()).successful())
        assertTrue(runner.run(listOf("sh", "-c", "true"), null, emptyMap(), 30).successful())

        assertTrue(Process.run("sh", "-c", "exit 0").successful())
        assertTrue(Process.runner().run("sh", "-c", "true").successful())

        assertThrows(IllegalStateException::class.java) {
            ProcessRunner(1).run(listOf("sh", "-c", "sleep 5"), null, emptyMap(), 1)
        }

        val fail = runner.run(listOf("sh", "-c", "echo err 1>&2; exit 2"))
        assertEquals(2, fail.exitCode)
        assertTrue(fail.errorOutput.contains("err"))
    }
}
