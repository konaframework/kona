package dev.kona.grind

import dev.kona.core.Kona
import dev.kona.queue.JobRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

class GrindQueueTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Kona.reset()
        JobRegistry.clear()
        QueueFixtureApp.counter.set(0)
        QueueFixtureApp.seedFailed.set(true)
    }

    private fun capture(block: () -> Int): Pair<Int, String> {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            return block() to buffer.toString()
        } finally {
            System.setOut(original)
        }
    }

    private fun grind(vararg args: String): Pair<Int, String> =
        capture { GrindApplication().run(arrayOf(*args)) }

    @Test
    fun queueWorkFailedRetryFlush() {
        val project = fixtureProject(temp.resolve("q"))
        val boot = "--bootstrap=${QueueFixtureApp::class.java.name}"

        val work = grind("queue:work", "--base=$project", boot)
        assertEquals(0, work.first)
        assertTrue(work.second.contains("Processed 1 job(s)."))
        assertEquals(1, QueueFixtureApp.counter.get())

        val listed = grind("queue:failed", "--base=$project", boot)
        assertEquals(0, listed.first)
        assertTrue(listed.second.contains("fail-1"))

        assertEquals(0, grind("queue:retry", "fail-1", "--base=$project", boot).first)
        QueueFixtureApp.seedFailed.set(false)
        assertTrue(grind("queue:failed", "--base=$project", boot).second.contains("No failed jobs."))

        QueueFixtureApp.seedFailed.set(true)
        assertEquals(0, grind("queue:flush", "--base=$project", boot).first)
        assertEquals(0, grind("queue:retry", "--all", "--base=$project", boot).first)

        assertEquals(1, grind("queue:work", "--base=$project", "--bootstrap=${NoQueueApp::class.java.name}").first)
        assertEquals(1, grind("queue:failed", "--base=$project", "--bootstrap=${NoQueueApp::class.java.name}").first)
        assertEquals(1, grind("queue:retry", "--base=$project", boot).first)
        assertEquals(1, grind("queue:retry", "missing-id", "--base=$project", boot).first)

        assertEquals(
            0,
            grind(
                "queue:work",
                "--base=$project",
                boot,
                "--connection=array",
                "--queue=default",
                "--max=2",
            ).first,
        )
        assertEquals(0, grind("queue:work", "--base=$project", boot, "--max=0").first)
        assertEquals(0, grind("queue:work", "--base=$project", boot, "--max=nope").first)
        assertEquals(
            0,
            grind(
                "queue:work",
                "--base=$project",
                boot,
                "--once",
                "--stop-when-empty",
                "--timeout=30",
                "--memory=0",
                "--tries=2",
                "--backoff=0",
            ).first,
        )
        assertEquals(
            0,
            grind(
                "queue:work",
                "--base=$project",
                boot,
                "--daemon",
                "--once",
                "--timeout=-1",
                "--sleep=nope",
            ).first,
        )
        assertEquals(0, grind("queue:failed", "--base=$project", "--bootstrap=${FailedOnlyApp::class.java.name}").first)
        assertEquals(1, grind("queue:retry", "x", "--base=$project", "--bootstrap=${FailedOnlyApp::class.java.name}").first)
    }
}
