package dev.kona.queue

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.cadence.Database
import dev.kona.redis.InMemoryRedisConnection
import dev.kona.redis.RedisManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** Public no-arg job for cross-process [JobRegistry.create] reflection. */
class RestorableTestJob : Job {
    override fun handle() {}
}

/** Jackson-friendly job whose fields survive encode/decode without an in-process factory. */
class DurableTokenJob : Job {
    var token: String = ""

    override fun handle() {
        SEEN.add(token)
    }

    companion object {
        val SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }
}

class QueueTest {
    @AfterEach
    fun reset() {
        JobRegistry.clear()
    }

    class CountingJob(private val counter: AtomicInteger) : Job, ShouldQueue, JobContract {
        override fun handle() {
            counter.incrementAndGet()
        }

        override fun tries(): Int = 2

        override fun backoffSeconds(): Long = 0

        override fun displayName(): String = "CountingJob"
    }

    class FailingJob(
        private val failUntil: Int,
        private val attemptsSeen: AtomicInteger,
        private val backoff: Long = 0,
    ) : Job, ShouldQueue, JobContract {
        override fun handle() {
            val n = attemptsSeen.incrementAndGet()
            if (n <= failUntil) throw RuntimeException("boom-$n")
        }

        override fun tries(): Int = 3

        override fun backoffSeconds(): Long = backoff

        override fun displayName(): String = "FailingJob"
    }

    @Test
    fun syncBusAndArrayWorker() {
        val counter = AtomicInteger()
        val sync = SyncQueue()
        assertNull(sync.pop())
        assertEquals(0, sync.size())
        sync.clear()
        sync.push(Job { counter.incrementAndGet() })
        assertEquals(1, counter.get())

        val syncMgr = QueueManager(Config().also { it.set("queue.default", "sync") })
        Bus(syncMgr).dispatch(Job { counter.incrementAndGet() })
        Bus(syncMgr).dispatchSync(Job { counter.incrementAndGet() })
        assertEquals(3, counter.get())

        val arrayMgr = arrayManager()
        Bus(arrayMgr).dispatch(CountingJob(counter))
        assertEquals(3, counter.get())
        assertEquals(1, Worker(arrayMgr).run())
        assertEquals(4, counter.get())
        assertEquals(0, Worker(arrayMgr).run(maxJobs = 0))
    }

    @Test
    fun retriesFailedJobsAndRedisDatabase(@TempDir base: Path) {
        val counter = AtomicInteger()
        val arrayMgr = arrayManager()
        val q = arrayMgr.connection() as ArrayQueue

        val attempts = AtomicInteger()
        q.push(FailingJob(10, attempts))
        repeat(5) { Worker(arrayMgr).run(maxJobs = 1) }
        assertEquals(1, arrayMgr.failed.all().size)
        val failed = arrayMgr.failed.all().first()
        assertNotNull(arrayMgr.failed.find(failed.id))
        assertTrue(arrayMgr.failed.forget(failed.id))
        assertNull(arrayMgr.failed.find(failed.id))
        arrayMgr.failed.flush()
        assertTrue(arrayMgr.failed.all().isEmpty())

        attempts.set(0)
        q.push(FailingJob(1, attempts, backoff = 0))
        Worker(arrayMgr).run(maxJobs = 1)
        assertEquals(1, q.size())
        Worker(arrayMgr).run(maxJobs = 1)
        assertEquals(0, q.size())
        assertEquals(2, attempts.get())

        val redis = RedisQueue(InMemoryRedisConnection(), "p:")
        JobRegistry.register(CountingJob::class.java.name) { CountingJob(counter) }
        redis.push(CountingJob(counter))
        assertEquals(1, redis.size())
        assertNotNull(redis.pop())
        redis.push(CountingJob(counter))
        redis.release(JobRegistry.payloadFor(CountingJob(counter), CountingJob::class.java.name))
        assertTrue(redis.size() >= 1)
        redis.clear()
        assertEquals(0, redis.size())
        redis.release(JobRegistry.payloadFor(CountingJob(counter)).withDelay(3600), "default", 0)
        assertNull(redis.pop())
        redis.clear()

        val redisMgr = QueueManager(
            Config().also {
                it.set("queue.default", "redis")
                it.set("queue.connections.redis.driver", "redis")
                it.set("queue.prefix", "t:")
            },
            RedisManager(Config().also { it.set("redis.client", "memory") }),
        )
        redisMgr.connection().push(CountingJob(counter))
        val beforeRedis = counter.get()
        assertEquals(1, Worker(redisMgr).run())
        assertEquals(beforeRedis + 1, counter.get())

        val redisQ = redisMgr.connection() as RedisQueue
        val redisAttempts = AtomicInteger()
        redisQ.push(FailingJob(10, redisAttempts, backoff = 0))
        repeat(5) { Worker(redisMgr).run(maxJobs = 1) }
        assertTrue(redisMgr.failed.all().isNotEmpty())

        val db = Database.fromConfig(
            Config().also {
                it.set("database.default", "h2")
                it.set("database.connections.h2.url", "jdbc:h2:mem:queue_${System.nanoTime()};DB_CLOSE_DELAY=-1")
                it.set("database.connections.h2.username", "sa")
                it.set("database.connections.h2.password", "")
            },
        )
        val dbQueue = DatabaseQueue(db.connection())
        dbQueue.ensureTable()
        dbQueue.push(CountingJob(counter))
        assertEquals(1, dbQueue.size())
        assertNotNull(dbQueue.pop())
        dbQueue.push(CountingJob(counter))
        dbQueue.clear()
        assertEquals(0, dbQueue.size())

        val dbMgr = QueueManager(
            Config().also {
                it.set("queue.default", "database")
                it.set("queue.connections.database.driver", "database")
                it.set("queue.connections.database.table", "jobs")
            },
            null,
            db,
        )
        dbMgr.connection().push(CountingJob(counter))
        val beforeDb = counter.get()
        assertEquals(1, Worker(dbMgr).run())
        assertEquals(beforeDb + 1, counter.get())

        val failAttempts = AtomicInteger()
        dbMgr.connection().push(
            object : Job, ShouldQueue, JobContract {
                override fun handle() {
                    failAttempts.incrementAndGet()
                    throw RuntimeException("db-fail")
                }

                override fun tries(): Int = 2

                override fun backoffSeconds(): Long = 0

                override fun displayName(): String = "DbFail"
            },
        )
        Worker(dbMgr).run(maxJobs = 1)
        Worker(dbMgr).run(maxJobs = 1)
        assertTrue(dbMgr.failed.all().isNotEmpty())
        db.close()

        assertThrows(IllegalStateException::class.java) {
            QueueManager(
                Config().also {
                    it.set("queue.default", "redis")
                    it.set("queue.connections.redis.driver", "redis")
                },
            ).connection()
        }
        assertThrows(IllegalStateException::class.java) {
            QueueManager(
                Config().also {
                    it.set("queue.default", "database")
                    it.set("queue.connections.database.driver", "database")
                },
            ).connection()
        }
        assertThrows(IllegalArgumentException::class.java) {
            QueueManager(
                Config().also {
                    it.set("queue.default", "weird")
                    it.set("queue.connections.weird.driver", "nope")
                },
            ).connection()
        }
        assertEquals("sync", QueueManager(Config()).defaultConnection())
        assertEquals("default", QueueManager(Config()).defaultQueue())
        assertEquals("default", QueueManager(Config()).defaultQueue("sync"))
        QueueManager(Config(), RedisManager(Config().also { it.set("redis.client", "memory") }))
        QueueManager(Config(), null, null)
        assertThrows(IllegalStateException::class.java) { JobRegistry.create("missing") }
        val p = JobRegistry.payloadFor(Job { })
        assertEquals(p.id, JobRegistry.decode(JobRegistry.encode(p)).id)

        // named drivers without explicit config map
        assertTrue(QueueManager(Config().also { it.set("queue.default", "memory") }).connection() is ArrayQueue)
        assertTrue(QueueManager(Config().also { it.set("queue.default", "array") }).connection() is ArrayQueue)
        assertTrue(QueueManager(Config().also { it.set("queue.default", "sync") }).connection() is SyncQueue)

        val app = Application(base)
        app.config.set("queue.default", "array")
        app.config.set("queue.connections.array.driver", "array")
        app.register(QueueServiceProvider()).boot()
        app.resolve(Bus::class.java).dispatch(Job { counter.incrementAndGet() })
        app.resolve(FailedJobProvider::class.java).flush()

        SyncQueue().release(JobRegistry.payloadFor(Job { }))
        Worker(syncMgr(), "sync").run(maxJobs = 1)

        val tiny = AtomicInteger()
        val backoffMgr = arrayManager()
        (backoffMgr.connection() as ArrayQueue).push(FailingJob(1, tiny, backoff = 0))
        Worker(backoffMgr).run(maxJobs = 2)
        assertTrue(tiny.get() >= 2)

        // Delayed release: job is not available until availableAt.
        val delayed = arrayManager()
        val delayedQueue = delayed.connection() as ArrayQueue
        val payload = JobRegistry.payloadFor(CountingJob(AtomicInteger())).withDelay(60)
        delayedQueue.release(payload, "default", 0) // already delayed via withDelay
        assertNull(delayedQueue.pop("default"))
        delayedQueue.release(payload.copy(availableAt = 0), "default", 0)
        assertNotNull(delayedQueue.pop("default"))

        // Cross-process style: restore via no-arg constructor when factory missing.
        JobRegistry.clear()
        assertThrows(IllegalStateException::class.java) { JobRegistry.create("dev.kona.queue.DoesNotExistJob") }
        val restored = JobRegistry.create(RestorableTestJob::class.java.name)
        assertTrue(restored is RestorableTestJob)

        // Durable body: push, clear factories, worker restores from jobBody JSON.
        DurableTokenJob.SEEN.clear()
        val durableMgr = arrayManager()
        val durableQueue = durableMgr.connection() as ArrayQueue
        val job = DurableTokenJob().also { it.token = "alpha" }
        durableQueue.push(job)
        JobRegistry.clear()
        assertEquals(1, Worker(durableMgr).run(maxJobs = 1))
        assertTrue(DurableTokenJob.SEEN.contains("alpha"))
        val encoded = JobRegistry.payloadFor(DurableTokenJob().also { it.token = "beta" })
        assertNotNull(encoded.jobBody)
        JobRegistry.clear()
        JobRegistry.create(encoded).handle()
        assertTrue(DurableTokenJob.SEEN.contains("beta"))

        // jobBody serialize failure → null body; corrupt body falls back to no-arg ctor.
        class BoomJob : Job {
            fun getExplode(): String = throw RuntimeException("nope")
            override fun handle() = Unit
        }
        val boomPayload = JobRegistry.payloadFor(BoomJob())
        assertNull(boomPayload.jobBody)
        JobRegistry.clear()
        val badBody = encoded.copy(jobKey = RestorableTestJob::class.java.name, jobBody = "{not-json")
        assertTrue(JobRegistry.create(badBody) is RestorableTestJob)
    }

    private fun arrayManager() =
        QueueManager(
            Config().also {
                it.set("queue.default", "array")
                it.set("queue.connections.array.driver", "array")
            },
        )

    private fun syncMgr() =
        QueueManager(Config().also { it.set("queue.default", "sync") })
}
