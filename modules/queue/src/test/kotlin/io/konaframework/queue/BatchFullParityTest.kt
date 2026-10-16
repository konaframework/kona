package io.konaframework.queue

import io.konaframework.cadence.ConnectionConfig
import io.konaframework.cadence.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BatchFullParityTest {
    @BeforeEach
    fun setUp() {
        JobRegistry.clear()
        BatchRepositories.reset()
        QueueEvents.clearDispatcher()
    }

    @AfterEach
    fun tearDown() {
        JobRegistry.clear()
        BatchRepositories.reset()
        QueueEvents.clearDispatcher()
        Database.clearGlobal()
    }

    private fun arrayManager(): QueueManager {
        val config = io.konaframework.core.Config()
        config.set("queue.default", "array")
        config.set("queue.connections.array.driver", "array")
        return QueueManager(config)
    }

    @Test
    fun thenCatchFinallyAndAllowFailures() {
        val mgr = arrayManager()
        val bus = Bus(mgr)
        val thenHit = AtomicInteger()
        val catchHit = AtomicInteger()
        val finallyHit = AtomicInteger()
        val events = mutableListOf<Any>()
        QueueEvents.setDispatcher { events += it }

        val ok = bus.batch(
            object : Job, ShouldQueue { override fun handle() = Unit },
            object : Job, ShouldQueue { override fun handle() = Unit },
        ).name("ok-batch")
            .then { thenHit.incrementAndGet() }
            .catch { _, _ -> catchHit.incrementAndGet() }
            .finally { finallyHit.incrementAndGet() }
            .dispatch()

        assertEquals(2, ok.size())
        assertEquals("ok-batch", ok.name)
        Worker(mgr, bus = bus).run(maxJobs = 10)
        assertTrue(ok.finished())
        assertEquals(1, thenHit.get())
        assertEquals(0, catchHit.get())
        assertEquals(1, finallyHit.get())
        assertEquals(100.0, ok.progress(), 0.01)
        assertTrue(events.any { it is BatchDispatched })
        assertTrue(events.any { it is BatchFinished })
        assertTrue(events.any { it is JobQueued })
        assertTrue(events.any { it is JobProcessed })
        assertTrue(events.any { it is BatchJobSucceeded })

        thenHit.set(0)
        catchHit.set(0)
        finallyHit.set(0)
        val boom = AtomicReference<Throwable>()
        val failing = bus.batch(
            object : Job, ShouldQueue {
                override fun handle() {
                    throw IllegalStateException("boom")
                }
            },
        ).allowFailures(false)
            .then { thenHit.incrementAndGet() }
            .catch { _, err ->
                catchHit.incrementAndGet()
                boom.set(err)
            }
            .finally { finallyHit.incrementAndGet() }
            .dispatch()

        Worker(mgr, bus = bus, options = WorkerOptions(maxTries = 1)).run(maxJobs = 5)
        assertTrue(failing.finished())
        assertTrue(failing.cancelled())
        assertEquals(0, thenHit.get())
        assertEquals(1, catchHit.get())
        assertEquals(1, finallyHit.get())
        assertNotNull(boom.get())
        assertTrue(events.any { it is JobFailed })
        assertTrue(events.any { it is BatchJobFailed })

        thenHit.set(0)
        catchHit.set(0)
        val allowed = bus.batch(
            object : Job, ShouldQueue {
                override fun handle() {
                    throw IllegalStateException("x")
                }
            },
            object : Job, ShouldQueue { override fun handle() = Unit },
        ).allowFailures(true)
            .then { thenHit.incrementAndGet() }
            .catch { _, _ -> catchHit.incrementAndGet() }
            .dispatch()
        Worker(mgr, bus = bus, options = WorkerOptions(maxTries = 1)).run(maxJobs = 10)
        assertTrue(allowed.finished())
        assertFalse(allowed.cancelled())
        assertEquals(1, thenHit.get())
        assertEquals(0, catchHit.get())
        assertEquals(1, allowed.failedJobs())
        assertFalse(allowed.failedJobIds().isEmpty())
        assertEquals(2, allowed.ids().size)
    }

    @Test
    fun databaseBatchRepositoryRoundTrip() {
        val db = Database()
            .addConnection(
                "default",
                ConnectionConfig("jdbc:h2:mem:batch${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
            )
            .setAsGlobal()
        val repo = DatabaseBatchRepository(db.connection())
        BatchRepositories.set(repo)
        val mgr = arrayManager()
        val bus = Bus(mgr)
        val batch = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .name("db")
            .dispatch()
        Worker(mgr, bus = bus).run(maxJobs = 5)
        assertTrue(batch.finished())

        // Fresh repository instance still finds durable row (callbacks absent).
        val found = DatabaseBatchRepository(db.connection()).find(batch.id)
        assertNotNull(found)
        assertEquals(0, found!!.pendingJobs())
        assertTrue(found.finished())
        assertEquals("db", found.name)

        repo.clear()
        assertThrows(IllegalArgumentException::class.java) {
            bus.batch(emptyList()).dispatch()
        }
        val cancelled = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit }).dispatch()
        cancelled.cancel()
        assertTrue(cancelled.cancelled())
        assertTrue(cancelled.finished())

        QueueEvents.fire(JobQueued("1", null, null, null))
        assertTrue(InMemoryBatchRepository().all().isEmpty())
        BatchRepositories.reset()
    }

    @Test
    fun syncQueueRecordsBatchAndEvents() {
        val config = io.konaframework.core.Config()
        config.set("queue.default", "sync")
        val mgr = QueueManager(config)
        val bus = Bus(mgr)
        val events = mutableListOf<Any>()
        QueueEvents.setDispatcher { events += it }
        val thenHit = AtomicInteger()
        val batch = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then { thenHit.incrementAndGet() }
            .dispatch()
        assertTrue(batch.finished())
        assertEquals(1, thenHit.get())
        assertTrue(events.any { it is JobProcessed })
    }

    @Test
    fun syncBatchFailureAndInlineBusBatch() {
        val config = io.konaframework.core.Config()
        config.set("queue.default", "sync")
        val mgr = QueueManager(config)
        val bus = Bus(mgr)
        val catchHit = AtomicInteger()
        assertThrows(IllegalStateException::class.java) {
            bus.batch(
                object : Job, ShouldQueue {
                    override fun handle() {
                        throw IllegalStateException("sync-fail")
                    }
                },
            ).catch { _, _ -> catchHit.incrementAndGet() }
                .dispatch()
        }
        assertEquals(1, catchHit.get())

        // Non-ShouldQueue jobs run inline on the bus with batchId.
        val inline = bus.batch(Job { }).dispatch()
        assertTrue(inline.finished())
        assertEquals(100.0, inline.progress(), 0.01)
    }

    @Test
    fun batchEdgeBranchesAndRestore() {
        val repo = InMemoryBatchRepository()
        BatchRepositories.set(repo)
        val emptyProgress =
            Batch.restore(
                mapOf(
                    "id" to "empty",
                    "total_jobs" to 0,
                    "pending_jobs" to 0,
                    "failed_jobs" to 0,
                    "failed_job_ids" to "",
                    "allow_failures" to "0",
                    "cancelled" to false,
                    "finished" to false,
                    "created_at" to 1L,
                ),
                repo,
            )
        assertEquals(100.0, emptyProgress.progress(), 0.01)

        val finished =
            Batch.restore(
                mapOf(
                    "ID" to "done",
                    "NAME" to "n",
                    "TOTAL_JOBS" to 1,
                    "PENDING_JOBS" to 0,
                    "FAILED_JOBS" to 0,
                    "FAILED_JOB_IDS" to "a",
                    "ALLOW_FAILURES" to "true",
                    "CANCELLED" to "1",
                    "FINISHED" to true,
                    "CREATED_AT" to 2L,
                ),
                repo,
            )
        finished.recordSuccess("x")
        finished.recordFailure("y", null)
        assertTrue(finished.finished())

        assertThrows(IllegalStateException::class.java) {
            Batch.restore(mapOf("id" to "x"), repo)
        }

        val mgr = arrayManager()
        val bus = Bus(mgr)
        // recordFailure with null error (error?.message null branch) before completion.
        val nullErr = bus.batch(
            object : Job, ShouldQueue { override fun handle() = Unit },
            object : Job, ShouldQueue { override fun handle() = Unit },
        ).allowFailures(true).dispatch()
        nullErr.recordFailure("manual", null)
        assertEquals(1, nullErr.failedJobs())

        // Last job fails with allowFailures → finish(error) when pending hits 0.
        val lastFail = bus.batch(
            object : Job, ShouldQueue { override fun handle() = Unit },
            object : Job, ShouldQueue {
                override fun handle() {
                    throw IllegalStateException("last")
                }
            },
        ).allowFailures(true).dispatch()
        Worker(mgr, bus = bus, options = WorkerOptions(maxTries = 1)).run(maxJobs = 10)
        assertTrue(lastFail.finished())
        assertEquals(1, lastFail.failedJobs())

        QueueEvents.setDispatcher(null as java.util.function.Consumer<Any>?)
        QueueEvents.fire(BatchFinished("x", 0, false))

        // Re-entrant finish via cancel after completion (covers finishedFlag guard).
        val done = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit }).dispatch()
        Worker(mgr, bus = bus).run(maxJobs = 5)
        assertTrue(done.finished())
        done.cancel()

        // Sync success/failure when batch id is unknown (find returns null).
        SyncQueue().push(object : Job, ShouldQueue { override fun handle() = Unit }, "default", 0, "no-such-batch")
        assertThrows(IllegalStateException::class.java) {
            SyncQueue().push(
                object : Job, ShouldQueue {
                    override fun handle() {
                        throw IllegalStateException("orphan")
                    }
                },
                "default",
                0,
                "no-such-batch",
            )
        }
        // Sync failure without batch id (catch path, batchId == null branch).
        assertThrows(IllegalStateException::class.java) {
            SyncQueue().push(
                object : Job, ShouldQueue {
                    override fun handle() {
                        throw IllegalStateException("no-batch")
                    }
                },
            )
        }

        // Inline bus job with unknown batch id (null-safe find) + named connection/queue labels.
        bus.dispatch(Job { }, batchId = "missing-batch")
        bus.dispatch(Job { }, connection = "array", queue = "default", batchId = null)

        // restore: null name, null cell skip, missing failed_job_ids, bool false string.
        val restored =
            Batch.restore(
                mapOf(
                    "id" to "r2",
                    "name" to null,
                    "total_jobs" to null,
                    "TOTAL_JOBS" to 2,
                    "pending_jobs" to 2,
                    "failed_jobs" to 0,
                    "allow_failures" to false,
                    "cancelled" to "false",
                    "finished" to "false",
                    "created_at" to 3L,
                ),
                repo,
            )
        assertNull(restored.name)
        assertTrue(restored.failedJobIds().isEmpty())
        Batch.restore(
            mapOf(
                "id" to "r3",
                "total_jobs" to 1,
                "pending_jobs" to 1,
                "failed_jobs" to 0,
                "failed_job_ids" to null,
                "allow_failures" to "TRUE",
                "cancelled" to false,
                "finished" to false,
                "created_at" to 4L,
            ),
            repo,
        )

        // finish catch path: cancelled with failures and allowFailures false.
        val catchOnly = AtomicInteger()
        val failCancel = bus.batch(
            object : Job, ShouldQueue {
                override fun handle() {
                    throw IllegalStateException("c")
                }
            },
        ).allowFailures(false)
            .catch { _, _ -> catchOnly.incrementAndGet() }
            .dispatch()
        Worker(mgr, bus = bus, options = WorkerOptions(maxTries = 1)).run(maxJobs = 5)
        assertEquals(1, catchOnly.get())
        assertTrue(failCancel.cancelled())

        // Exception paths through finish's finally (Kotlin duplicates finally branches).
        val withFinally = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then { throw IllegalStateException("then-boom") }
            .finally { }
            .dispatch()
        assertThrows(IllegalStateException::class.java) {
            withFinally.recordSuccess(withFinally.ids().first())
        }
        val noFinally = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then { throw IllegalStateException("then-boom-no-finally") }
            .dispatch()
        assertThrows(IllegalStateException::class.java) {
            noFinally.recordSuccess(noFinally.ids().first())
        }
    }

    @Test
    fun databaseBatchDriverViaProvider() {
        val app = io.konaframework.core.Application(java.nio.file.Files.createTempDirectory("qbatch"))
        app.config.set("queue.default", "array")
        app.config.set("queue.connections.array.driver", "array")
        app.config.set("queue.batch.driver", "database")
        app.config.set("queue.batch.table", "job_batches")
        app.config.set("database.default", "h2")
        app.config.set(
            "database.connections.h2.url",
            "jdbc:h2:mem:qbatch_${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        )
        val db = Database.fromConfig(app.config).setAsGlobal()
        app.container.instance(Database::class.java, db)
        app.register(QueueServiceProvider()).boot()
        assertTrue(BatchRepositories.get() is DatabaseBatchRepository)
        val bus = app.resolve(Bus::class.java)
        val batch = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit }).dispatch()
        Worker(app.resolve(QueueManager::class.java), bus = bus).run(maxJobs = 5)
        assertTrue(batch.finished())
        // Miss memory → load from JDBC (fresh repo shares same connection/table).
        val cold = DatabaseBatchRepository(db.connection())
        assertNotNull(cold.find(batch.id))
        assertNull(cold.find("missing-id"))
        DatabaseBatchRepository(db.connection(), "job_batches")
        db.close()
        Database.clearGlobal()
        BatchRepositories.reset()

        // database batch driver without Database bound → in-memory fallback.
        val app2 = io.konaframework.core.Application(java.nio.file.Files.createTempDirectory("qbatch2"))
        app2.config.set("queue.default", "array")
        app2.config.set("queue.connections.array.driver", "array")
        app2.config.set("queue.batch.driver", "database")
        app2.register(QueueServiceProvider()).boot()
        assertTrue(BatchRepositories.get() is InMemoryBatchRepository)
        BatchRepositories.reset()
    }
}
