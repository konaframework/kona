package org.konaframework.queue

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.core.Config
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.function.Consumer

class StoredThen : Consumer<Batch> {
    var note: String = "ready"

    override fun accept(batch: Batch) {
        hits.incrementAndGet()
        lastNote.set(note)
    }

    companion object {
        val hits = AtomicInteger()
        val lastNote = AtomicReference<String>()
    }
}

class StoredCatch : BiConsumer<Batch, Throwable?> {
    override fun accept(batch: Batch, error: Throwable?) {
        hits.incrementAndGet()
        lastMessage.set(error?.message)
    }

    companion object {
        val hits = AtomicInteger()
        val lastMessage = AtomicReference<String?>()
    }
}

class StoredFinally : Consumer<Batch> {
    override fun accept(batch: Batch) {
        hits.incrementAndGet()
    }

    companion object {
        val hits = AtomicInteger()
    }
}

class NeedsArg(val label: String) : Consumer<Batch> {
    override fun accept(batch: Batch) = Unit
}

class LoopThen : Consumer<Batch> {
    var self: LoopThen? = null

    override fun accept(batch: Batch) = Unit
}

class CrossProcessBatchCallbackTest {
    @BeforeEach
    fun setUp() {
        JobRegistry.clear()
        BatchRepositories.reset()
        QueueEvents.clearDispatcher()
        StoredThen.hits.set(0)
        StoredThen.lastNote.set(null)
        StoredCatch.hits.set(0)
        StoredCatch.lastMessage.set(null)
        StoredFinally.hits.set(0)
    }

    @AfterEach
    fun tearDown() {
        JobRegistry.clear()
        BatchRepositories.reset()
        QueueEvents.clearDispatcher()
        Database.clearGlobal()
    }

    @Test
    fun concreteCallbacksRunFromAFreshRepository() {
        val database = database()
        val origin = DatabaseBatchRepository(database.connection())
        BatchRepositories.set(origin)
        val manager = arrayManager()
        val bus = Bus(manager)
        val thenCallback = StoredThen().also { it.note = "packed" }
        val batch = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then(thenCallback)
            .finally(StoredFinally())
            .dispatch()

        val other = DatabaseBatchRepository(database.connection())
        BatchRepositories.set(other)
        Worker(manager, bus = bus).run(maxJobs = 5)

        assertEquals(1, StoredThen.hits.get())
        assertEquals("packed", StoredThen.lastNote.get())
        assertEquals(1, StoredFinally.hits.get())
        assertTrue(other.find(batch.id)!!.finished())

        BatchRepositories.set(origin)
        val failing = bus.batch(
            object : Job, ShouldQueue {
                override fun handle() {
                    throw IllegalStateException("cross")
                }
            },
        ).catch(StoredCatch()).dispatch()
        val catcher = DatabaseBatchRepository(database.connection())
        BatchRepositories.set(catcher)
        Worker(manager, bus = bus, options = WorkerOptions(maxTries = 1)).run(maxJobs = 5)
        assertEquals(1, StoredCatch.hits.get())
        assertEquals("cross", StoredCatch.lastMessage.get())
        assertTrue(catcher.find(failing.id)!!.cancelled())
    }

    @Test
    fun lambdasStayOnTheDispatchingRepository() {
        val database = database()
        val origin = DatabaseBatchRepository(database.connection())
        BatchRepositories.set(origin)
        val manager = arrayManager()
        val bus = Bus(manager)
        val sameProcess = AtomicInteger()
        val stayed = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then { sameProcess.incrementAndGet() }
            .dispatch()
        Worker(manager, bus = bus).run(maxJobs = 5)
        assertEquals(1, sameProcess.get())
        assertTrue(stayed.finished())

        val crossed = AtomicInteger()
        val pending = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then { crossed.incrementAndGet() }
            .dispatch()
        val cold = DatabaseBatchRepository(database.connection())
        BatchRepositories.set(cold)
        Worker(manager, bus = bus).run(maxJobs = 5)
        assertEquals(0, crossed.get())
        assertTrue(cold.find(pending.id)!!.finished())
    }

    @Test
    fun legacyBatchTableGainsACallbacksColumn() {
        val database = database()
        database.connection().statement(
            """
            CREATE TABLE legacy_batches (
              id VARCHAR(64) PRIMARY KEY,
              name VARCHAR(255),
              total_jobs INT NOT NULL,
              pending_jobs INT NOT NULL,
              failed_jobs INT NOT NULL,
              failed_job_ids CLOB,
              allow_failures BOOLEAN NOT NULL,
              cancelled BOOLEAN NOT NULL,
              finished BOOLEAN NOT NULL,
              created_at BIGINT NOT NULL
            )
            """.trimIndent(),
        )
        val repo = DatabaseBatchRepository(database.connection(), "legacy_batches")
        BatchRepositories.set(repo)
        val manager = arrayManager()
        val bus = Bus(manager)
        val batch = bus.batch(object : Job, ShouldQueue { override fun handle() = Unit })
            .then(StoredThen())
            .dispatch()
        BatchRepositories.set(DatabaseBatchRepository(database.connection(), "legacy_batches"))
        Worker(manager, bus = bus).run(maxJobs = 5)
        assertEquals(1, StoredThen.hits.get())
        assertEquals("ready", StoredThen.lastNote.get())
        assertNotNull(
            database.connection().selectOne(
                "SELECT callbacks FROM legacy_batches WHERE id = ?",
                listOf(batch.id),
            ),
        )
    }

    @Test
    fun skipsCallbacksThatCannotBeStored() {
        class LocalThen : Consumer<Batch> {
            override fun accept(batch: Batch) = Unit
        }
        val anonymous = object : Consumer<Batch> {
            override fun accept(batch: Batch) = Unit
        }
        @Suppress("UNCHECKED_CAST")
        val proxy = Proxy.newProxyInstance(
            Consumer::class.java.classLoader,
            arrayOf(Consumer::class.java),
        ) { _, _, _ -> null } as Consumer<Batch>
        val loop = LoopThen().also { it.self = it }

        assertNull(BatchCallbacks.encode(null, null, null))
        assertNull(BatchCallbacks.encode(anonymous, null, null))
        assertNull(BatchCallbacks.encode(LocalThen(), null, null))
        assertNull(BatchCallbacks.encode(proxy, null, null))
        assertNull(BatchCallbacks.encode(NeedsArg("x"), null, null))
        assertNull(BatchCallbacks.encode(loop, null, null))
        assertNotNull(BatchCallbacks.encode(StoredThen(), null, null))
        assertNotNull(BatchCallbacks.encode(null, StoredCatch(), null))
        assertNotNull(BatchCallbacks.encode(null, null, StoredFinally()))
        assertNotNull(BatchCallbacks.encode(StoredThen(), StoredCatch(), StoredFinally()))
    }

    @Test
    fun restoreIgnoresCallbacksThatCannotBeRead() {
        val repo = InMemoryBatchRepository()
        val stored = BatchCallbacks.encode(StoredThen().also { it.note = "kept" }, StoredCatch(), StoredFinally())
        val catchOnly = stored!!.replace("\"then\"", "\"unused\"")
        val samples = listOf<Any?>(
            null,
            "   ",
            "not-json",
            """{"then":"nope"}""",
            """{"then":{}}""",
            """{"then":{"className":"","body":"{}"}}""",
            """{"then":{"className":"java.lang.String"}}""",
            """{"then":{"className":"java.lang.String","body":""}}""",
            """{"then":{"className":"java.lang.String","body":"\"hi\""}}""",
            """{"then":{"className":1,"body":"{}"}}""",
            """{"then":{"className":"java.lang.String","body":2}}""",
            """{"then":{"className":"no.Such","body":"{}"}}""",
            """{"catch":{"className":"${StoredThen::class.java.name}","body":"{}"}}""",
            stored,
            catchOnly,
        )
        for (sample in samples) {
            val row = linkedMapOf<String, Any?>(
                "id" to "row",
                "name" to null,
                "total_jobs" to 1,
                "pending_jobs" to 1,
                "failed_jobs" to 0,
                "failed_job_ids" to "",
                "allow_failures" to false,
                "cancelled" to false,
                "finished" to false,
                "created_at" to 1L,
                "CALLBACKS" to sample,
            )
            if (sample == null && samples.indexOf(sample) == 0) {
                row.remove("CALLBACKS")
            }
            Batch.restore(row, repo)
        }

        StoredThen.hits.set(0)
        StoredCatch.hits.set(0)
        StoredFinally.hits.set(0)
        val restored = Batch.restore(
            mapOf(
                "id" to "live",
                "total_jobs" to 1,
                "pending_jobs" to 1,
                "failed_jobs" to 0,
                "failed_job_ids" to "",
                "allow_failures" to true,
                "cancelled" to false,
                "finished" to false,
                "created_at" to 2L,
                "callbacks" to stored,
            ),
            repo,
        )
        restored.recordSuccess("job-1")
        assertEquals(1, StoredThen.hits.get())
        assertEquals("kept", StoredThen.lastNote.get())
        assertEquals(1, StoredFinally.hits.get())
        assertEquals(0, StoredCatch.hits.get())
    }

    private fun database(): Database =
        Database()
            .addConnection(
                "default",
                ConnectionConfig("jdbc:h2:mem:xcb${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
            )
            .setAsGlobal()

    private fun arrayManager(): QueueManager {
        val config = Config()
        config.set("queue.default", "array")
        config.set("queue.connections.array.driver", "array")
        return QueueManager(config)
    }
}
