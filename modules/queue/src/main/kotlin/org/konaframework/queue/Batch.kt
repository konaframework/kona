package org.konaframework.queue

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Consumer

/** Persists batch progress (Laravel batch repository shape). */
interface BatchRepository {
    fun store(batch: Batch)

    fun find(id: String): Batch?

    fun save(batch: Batch)

    fun clear()
}

/** In-memory batch repository (default). */
class InMemoryBatchRepository : BatchRepository {
    private val batches = ConcurrentHashMap<String, Batch>()

    override fun store(batch: Batch) {
        batches[batch.id] = batch
    }

    override fun find(id: String): Batch? = batches[id]

    override fun save(batch: Batch) {
        batches[batch.id] = batch
    }

    override fun clear() {
        batches.clear()
    }

    fun all(): List<Batch> = batches.values.toList()
}

/**
 * JDBC-backed batches (`job_batches` by default).
 *
 * Callbacks (`then` / `catch` / `finally`) remain in-process on the [Batch] instance
 * held by [memory]; durable rows track counts for status across workers.
 */
class DatabaseBatchRepository @JvmOverloads constructor(
    private val connection: org.konaframework.cadence.DatabaseConnection,
    private val table: String = "job_batches",
    private val memory: InMemoryBatchRepository = InMemoryBatchRepository(),
) : BatchRepository {
    init {
        ensureTable()
    }

    fun ensureTable() {
        connection.statement(
            """
            CREATE TABLE IF NOT EXISTS $table (
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
    }

    override fun store(batch: Batch) {
        memory.store(batch)
        persist(batch)
    }

    override fun find(id: String): Batch? {
        memory.find(id)?.let { return it }
        val rows = connection.select(
            """
            SELECT id, name, total_jobs, pending_jobs, failed_jobs, failed_job_ids,
                   allow_failures, cancelled, finished, created_at
            FROM $table WHERE id = ?
            """.trimIndent(),
            listOf(id),
        )
        val row = rows.firstOrNull() ?: return null
        val batch = Batch.restore(row, memory)
        memory.store(batch)
        return batch
    }

    override fun save(batch: Batch) {
        memory.save(batch)
        persist(batch)
    }

    override fun clear() {
        memory.clear()
        connection.delete("DELETE FROM $table", emptyList())
    }

    private fun persist(batch: Batch) {
        connection.delete("DELETE FROM $table WHERE id = ?", listOf(batch.id))
        connection.insert(
            """
            INSERT INTO $table
              (id, name, total_jobs, pending_jobs, failed_jobs, failed_job_ids,
               allow_failures, cancelled, finished, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                batch.id,
                batch.name,
                batch.totalJobs,
                batch.pendingJobs(),
                batch.failedJobs(),
                batch.failedJobIds().joinToString(","),
                batch.allowsFailures(),
                batch.cancelled(),
                batch.finished(),
                batch.createdAt,
            ),
        )
    }
}

/**
 * A dispatched set of jobs with optional `then` / `catch` / `finally` callbacks.
 */
class Batch internal constructor(
    val id: String,
    val name: String?,
    val totalJobs: Int,
    val createdAt: Long,
    private val allowFailures: Boolean,
    private val repository: BatchRepository,
    private var thenCallback: Consumer<Batch>?,
    private var catchCallback: BiConsumer<Batch, Throwable?>?,
    private var finallyCallback: Consumer<Batch>?,
) {
    private val pending = AtomicInteger(totalJobs)
    private val failed = AtomicInteger(0)
    private val failedIds = ConcurrentHashMap.newKeySet<String>()
    private val ids = mutableListOf<String>()
    @Volatile private var cancelledFlag = false
    @Volatile private var finishedFlag = false
    @Volatile private var lastException: Throwable? = null

    fun size(): Int = totalJobs

    fun ids(): List<String> = synchronized(ids) { ids.toList() }

    fun pendingJobs(): Int = pending.get()

    fun failedJobs(): Int = failed.get()

    fun failedJobIds(): List<String> = failedIds.toList()

    fun allowsFailures(): Boolean = allowFailures

    fun cancelled(): Boolean = cancelledFlag

    fun finished(): Boolean = finishedFlag

    fun progress(): Double {
        if (totalJobs == 0) {
            return 100.0
        }
        val done = totalJobs - pending.get()
        return (done.toDouble() / totalJobs) * 100.0
    }

    fun cancel() {
        cancelledFlag = true
        finish(null)
    }

    internal fun attachJobId(jobId: String) {
        synchronized(ids) { ids += jobId }
    }

    internal fun recordSuccess(jobId: String) {
        if (finishedFlag) {
            return
        }
        val left = pending.decrementAndGet()
        repository.save(this)
        QueueEvents.fire(BatchJobSucceeded(id, jobId))
        if (left <= 0) {
            finish(null)
        }
    }

    internal fun recordFailure(jobId: String, error: Throwable?) {
        if (finishedFlag) {
            return
        }
        failed.incrementAndGet()
        failedIds.add(jobId)
        lastException = error
        val left = pending.decrementAndGet()
        repository.save(this)
        QueueEvents.fire(BatchJobFailed(id, jobId, error?.message))
        if (!allowFailures) {
            finish(error)
            return
        }
        if (left <= 0) {
            finish(error)
        }
    }

    private fun finish(error: Throwable?) {
        if (finishedFlag) {
            return
        }
        finishedFlag = true
        if (failed.get() > 0 && !allowFailures) {
            cancelledFlag = true
        }
        repository.save(this)
        val failure = error ?: lastException
        val failedBatch = cancelledFlag || (failed.get() > 0 && !allowFailures)
        try {
            if (failedBatch) {
                val onCatch = catchCallback
                if (onCatch != null) {
                    onCatch.accept(this, failure)
                }
            } else {
                val onThen = thenCallback
                if (onThen != null) {
                    onThen.accept(this)
                }
            }
        } finally {
            val onFinally = finallyCallback
            if (onFinally != null) {
                onFinally.accept(this)
            }
            QueueEvents.fire(BatchFinished(id, failed.get(), cancelledFlag))
        }
    }

    internal fun applyRestoredState(
        pendingJobs: Int,
        failedJobs: Int,
        failedJobIds: Collection<String>,
        cancelled: Boolean,
        finished: Boolean,
    ) {
        pending.set(pendingJobs)
        failed.set(failedJobs)
        failedIds.addAll(failedJobIds)
        cancelledFlag = cancelled
        finishedFlag = finished
    }

    companion object {
        internal fun restore(row: Map<String, Any?>, repository: BatchRepository): Batch {
            fun cell(name: String): Any {
                for ((k, v) in row) {
                    if (k.equals(name, ignoreCase = true) && v != null) return v
                }
                error("job_batches missing [$name]")
            }
            fun boolCell(name: String): Boolean {
                val v = cell(name)
                return v == true || v.toString().equals("true", true) || v.toString() == "1"
            }
            val failedIdsRaw =
                row.entries.firstOrNull { it.key.equals("failed_job_ids", true) }?.value?.toString().orEmpty()
            val batch = Batch(
                id = cell("id").toString(),
                name = row.entries.firstOrNull { it.key.equals("name", true) }?.value?.toString(),
                totalJobs = (cell("total_jobs") as Number).toInt(),
                createdAt = (cell("created_at") as Number).toLong(),
                allowFailures = boolCell("allow_failures"),
                repository = repository,
                thenCallback = null,
                catchCallback = null,
                finallyCallback = null,
            )
            batch.applyRestoredState(
                pendingJobs = (cell("pending_jobs") as Number).toInt(),
                failedJobs = (cell("failed_jobs") as Number).toInt(),
                failedJobIds = failedIdsRaw.split(',').filter { it.isNotBlank() },
                cancelled = boolCell("cancelled"),
                finished = boolCell("finished"),
            )
            return batch
        }
    }
}

/** Fluent builder for a job batch. */
class PendingBatch(
    private val bus: Bus,
    private val jobs: List<Job>,
    private val repository: BatchRepository = BatchRepositories.get(),
) {
    private var thenCallback: Consumer<Batch>? = null
    private var catchCallback: BiConsumer<Batch, Throwable?>? = null
    private var finallyCallback: Consumer<Batch>? = null
    private var allowFailures: Boolean = false
    private var batchName: String? = null

    fun then(callback: Consumer<Batch>): PendingBatch = apply { thenCallback = callback }

    fun catch(callback: BiConsumer<Batch, Throwable?>): PendingBatch = apply { catchCallback = callback }

    /** Java: `onFinally`. */
    @JvmName("onFinally")
    fun finally(callback: Consumer<Batch>): PendingBatch = apply { finallyCallback = callback }

    @JvmOverloads
    fun allowFailures(allow: Boolean = true): PendingBatch = apply { allowFailures = allow }

    fun name(name: String): PendingBatch = apply { batchName = name }

    @JvmOverloads
    fun dispatch(connection: String? = null, queue: String? = null): Batch {
        require(jobs.isNotEmpty()) { "Cannot dispatch an empty batch" }
        val batch = Batch(
            id = UUID.randomUUID().toString().replace("-", ""),
            name = batchName,
            totalJobs = jobs.size,
            createdAt = System.currentTimeMillis(),
            allowFailures = allowFailures,
            repository = repository,
            thenCallback = thenCallback,
            catchCallback = catchCallback,
            finallyCallback = finallyCallback,
        )
        repository.store(batch)
        QueueEvents.fire(BatchDispatched(batch.id, batch.totalJobs, batch.name))
        for (job in jobs) {
            val id = bus.dispatch(job, connection, queue, 0, batch.id)
            batch.attachJobId(id)
        }
        repository.save(batch)
        return batch
    }
}

/** Process-wide batch repository (default in-memory). */
object BatchRepositories {
    @Volatile
    private var repository: BatchRepository = InMemoryBatchRepository()

    @JvmStatic
    fun get(): BatchRepository = repository

    @JvmStatic
    fun set(repository: BatchRepository) {
        this.repository = repository
    }

    @JvmStatic
    fun reset() {
        repository = InMemoryBatchRepository()
    }
}

/** Lightweight queue domain events (optional [QueueEvents] dispatcher). */
data class BatchDispatched(val batchId: String, val totalJobs: Int, val name: String?)

data class BatchJobSucceeded(val batchId: String, val jobId: String)

data class BatchJobFailed(val batchId: String, val jobId: String, val message: String?)

data class BatchFinished(val batchId: String, val failedJobs: Int, val cancelled: Boolean)

data class JobQueued(val jobId: String, val connection: String?, val queue: String?, val batchId: String?)

data class JobProcessed(val jobId: String, val connection: String, val queue: String)

data class JobFailed(val jobId: String, val connection: String, val queue: String, val message: String?)

object QueueEvents {
    @Volatile
    private var dispatcher: ((Any) -> Unit)? = null

    @JvmStatic
    fun setDispatcher(dispatcher: Consumer<Any>?) {
        this.dispatcher = dispatcher?.let { d -> { event -> d.accept(event) } }
    }

    @JvmStatic
    fun clearDispatcher() {
        dispatcher = null
    }

    @JvmStatic
    fun fire(event: Any) {
        dispatcher?.invoke(event)
    }
}
