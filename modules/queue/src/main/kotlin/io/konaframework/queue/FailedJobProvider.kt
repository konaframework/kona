package io.konaframework.queue

import java.util.concurrent.CopyOnWriteArrayList

data class FailedJob(
    val id: String,
    val connection: String,
    val queue: String,
    val payload: QueuePayload,
    val exception: String,
    val failedAt: Long = System.currentTimeMillis(),
)

/**
 * Stores failed jobs (Laravel `failed_jobs` shape for listing/retry).
 *
 * Default is in-memory. Use [FileFailedJobProvider] (or `queue.failed.driver=file`)
 * when retries must survive process restart.
 */
open class FailedJobProvider {
    protected val jobs = CopyOnWriteArrayList<FailedJob>()

    open fun log(connection: String, queue: String, payload: QueuePayload, exception: Throwable) {
        jobs += FailedJob(
            id = payload.id,
            connection = connection,
            queue = queue,
            payload = payload,
            exception = exception.message ?: exception.javaClass.name,
        )
        persist()
    }

    open fun all(): List<FailedJob> = jobs.toList()

    open fun find(id: String): FailedJob? = jobs.find { it.id == id }

    open fun forget(id: String): Boolean {
        val removed = jobs.removeIf { it.id == id }
        if (removed) {
            persist()
        }
        return removed
    }

    open fun flush() {
        jobs.clear()
        persist()
    }

    /** Hook for durable providers; no-op in memory. */
    protected open fun persist() = Unit
}
