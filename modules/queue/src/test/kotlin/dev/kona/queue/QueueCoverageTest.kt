package dev.kona.queue

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.cadence.Database
import dev.kona.redis.RedisManager
import dev.kona.redis.RedisServiceProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
// assertEquals used for null-message failed job label
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class QueueCoverageTest {
    @AfterEach
    fun reset() {
        JobRegistry.clear()
    }

    @Test
    fun providerWithRedisAndDatabase(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("redis.client", "memory")
        app.config.set("queue.default", "array")
        app.config.set("queue.connections.array.driver", "array")
        app.config.set("queue.connections.redis.driver", "redis")
        app.config.set("queue.connections.database.driver", "database")
        app.config.set("database.default", "h2")
        app.config.set("database.connections.h2.url", "jdbc:h2:mem:qc_${System.nanoTime()};DB_CLOSE_DELAY=-1")
        val db = Database.fromConfig(app.config)
        app.container.instance(Database::class.java, db)
        app.register(RedisServiceProvider()).register(QueueServiceProvider()).boot()
        assertTrue(app.container.bound(RedisManager::class.java))
        app.resolve(QueueManager::class.java).connection("redis").push(
            object : Job, ShouldQueue {
                override fun handle() = Unit
            },
        )
        app.resolve(QueueManager::class.java).connection("database").push(
            object : Job, ShouldQueue {
                override fun handle() = Unit
            },
        )
        assertEquals(1, Worker(app.resolve(QueueManager::class.java), "redis").run(maxJobs = 1))
        assertEquals(1, Worker(app.resolve(QueueManager::class.java), "database").run(maxJobs = 1))
        db.close()
    }

    @Test
    fun failedJobNullMessageAndArrayEdges() {
        val failed = FailedJobProvider()
        val payload = JobRegistry.payloadFor(Job { })
        failed.log("c", "q", payload, RuntimeException(null as String?))
        assertEquals("java.lang.RuntimeException", failed.all().first().exception)
        assertNull(failed.find("missing"))
        assertEquals(false, failed.forget("missing"))

        val q = ArrayQueue()
        assertNull(q.pop("empty"))
        assertEquals(0, q.size("empty"))
        q.clear("empty")
        val n = AtomicInteger()
        q.push(object : Job, ShouldQueue {
            override fun handle() {
                n.incrementAndGet()
            }
        })
        q.release(JobRegistry.payloadFor(Job { }, "x"))
        assertTrue(q.size() >= 1)
    }

    @Test
    fun databaseCellFallbacks(@TempDir base: Path) {
        val db = Database.fromConfig(
            Config().also {
                it.set("database.default", "h2")
                it.set("database.connections.h2.url", "jdbc:h2:mem:cell_${System.nanoTime()};DB_CLOSE_DELAY=-1")
            },
        )
        val queue = DatabaseQueue(db.connection(), "jobs2")
        queue.ensureTable()
        val counter = AtomicInteger()
        queue.push(object : Job, ShouldQueue {
            override fun handle() {
                counter.incrementAndGet()
            }
        })
        assertEquals(1, queue.size())
        assertTrue(queue.pop() != null)
        assertEquals(0, queue.size())
        // size on empty still hits COUNT
        assertEquals(0, DatabaseQueue(db.connection(), "jobs2").size("other"))
        db.close()
    }

    @Test
    fun managerNamedDriversWithoutMaps() {
        val db = Database.fromConfig(
            Config().also {
                it.set("database.default", "h2")
                it.set("database.connections.h2.url", "jdbc:h2:mem:named_${System.nanoTime()};DB_CLOSE_DELAY=-1")
            },
        )
        val mgr = QueueManager(Config().also { it.set("queue.default", "database") }, null, db)
        assertTrue(mgr.connection() is DatabaseQueue)
        assertTrue(
            QueueManager(
                Config().also { it.set("queue.default", "redis") },
                RedisManager(Config().also { it.set("redis.client", "memory") }),
            ).connection() is RedisQueue,
        )
        val arrayMgr = QueueManager(
            Config().also {
                it.set("queue.default", "array")
                it.set("queue.connections.array.driver", "array")
                it.set("queue.connections.array.queue", "high")
            },
        )
        assertEquals("high", arrayMgr.defaultQueue("array"))
        Bus(arrayMgr).dispatch(
            object : Job, ShouldQueue {
                override fun handle() = Unit
            },
            connection = "array",
            queue = "high",
        )
        assertEquals(1, Worker(arrayMgr, "array", "high").run(maxJobs = 1))
        db.close()
    }
}
