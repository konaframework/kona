package org.konaframework.queue

import org.konaframework.core.Application
import org.konaframework.core.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileFailedJobProviderTest {
    @TempDir
    lateinit var dir: Path

    private fun payload(id: String = "job-1"): QueuePayload =
        QueuePayload(
            id = id,
            displayName = "Demo",
            tries = 3,
            backoffSeconds = 0,
            attempts = 1,
            jobKey = "demo",
        )

    @Test
    fun persistReloadForgetAndFlush() {
        val path = dir.resolve("failed.json")
        val provider = FileFailedJobProvider(path)
        provider.log("array", "default", payload("a"), RuntimeException("boom"))
        provider.log("array", "default", payload("b"), RuntimeException())
        assertTrue(Files.exists(path))
        assertEquals(2, provider.all().size)

        val reloaded = FileFailedJobProvider(path)
        assertEquals(2, reloaded.all().size)
        assertEquals("boom", reloaded.find("a")!!.exception)
        assertTrue(reloaded.forget("a"))
        assertFalse(reloaded.forget("missing"))
        assertNull(reloaded.find("a"))
        assertEquals(1, FileFailedJobProvider(path).all().size)

        val again = FileFailedJobProvider(path)
        again.flush()
        assertTrue(again.all().isEmpty())
        assertTrue(FileFailedJobProvider(path).all().isEmpty())
    }

    @Test
    fun loadsEmptyOrCorruptFileSafely() {
        val empty = dir.resolve("empty.json")
        Files.writeString(empty, "")
        assertTrue(FileFailedJobProvider(empty).all().isEmpty())

        val corrupt = dir.resolve("corrupt.json")
        Files.writeString(corrupt, "{not-json")
        assertTrue(FileFailedJobProvider(corrupt).all().isEmpty())

        val missing = dir.resolve("nested").resolve("failed.json")
        val provider = FileFailedJobProvider(missing)
        provider.log("c", "q", payload(), IllegalStateException("x"))
        assertTrue(Files.exists(missing))
    }

    @Test
    fun persistWorksWhenPathHasNoParent() {
        val path = Path.of("kona-failed-noparent-${System.nanoTime()}.json")
        assertNull(path.parent)
        try {
            val provider = FileFailedJobProvider(path)
            provider.log("c", "q", payload("np"), RuntimeException("e"))
            assertTrue(Files.exists(path))
            assertEquals(1, FileFailedJobProvider(path).all().size)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun queueManagerWiresFileDriverFromConfig() {
        val path = dir.resolve("failed-jobs.json")
        val config = Config().apply {
            set("queue.failed.driver", "file")
            set("queue.failed.path", path.toAbsolutePath().toString())
        }
        val manager = QueueManager(config, null, null, dir)
        assertTrue(manager.failed is FileFailedJobProvider)
        manager.failed.log("sync", "default", payload("z"), RuntimeException("fail"))
        assertEquals(1, FileFailedJobProvider(path).all().size)

        val relative = Config().apply {
            set("queue.failed.driver", "file")
            set("queue.failed.path", "storage/failed.json")
        }
        val relMgr = QueueManager(relative, null, null, dir)
        relMgr.failed.log("sync", "default", payload("r"), RuntimeException("r"))
        assertTrue(Files.exists(dir.resolve("storage/failed.json")))

        val defaultPath = Config().apply { set("queue.failed.driver", "file") }
        val defMgr = QueueManager(defaultPath, null, null, dir)
        defMgr.failed.log("sync", "default", payload("d"), RuntimeException("d"))
        assertTrue(
            Files.exists(dir.resolve("storage/framework/failed_jobs.json")),
        )

        assertTrue(QueueManager(Config()).failed !is FileFailedJobProvider)
    }

    @Test
    fun serviceProviderUsesAppBasePathForFailedFile(@TempDir base: Path) {
        val app = Application.builder(base)
            .configure {
                it.set("queue.failed.driver", "file")
                it.set("queue.failed.path", "failed.json")
            }
            .withProvider(QueueServiceProvider())
            .boot()
        val failed = app.resolve(FailedJobProvider::class.java)
        assertTrue(failed is FileFailedJobProvider)
        failed.log("array", "default", payload("p"), RuntimeException("e"))
        assertTrue(Files.exists(base.resolve("failed.json")))
    }
}
