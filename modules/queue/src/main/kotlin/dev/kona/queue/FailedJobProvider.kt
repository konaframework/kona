package dev.kona.queue

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
 * Stores failed jobs in memory (swap for database later). Laravel `failed_jobs` shape for listing/retry.
 */
class FailedJobProvider {
    private val jobs = CopyOnWriteArrayList<FailedJob>()

    fun log(connection: String, queue: String, payload: QueuePayload, exception: Throwable) {
        jobs += FailedJob(
            id = payload.id,
            connection = connection,
            queue = queue,
            payload = payload,
            exception = exception.message ?: exception.javaClass.name,
        )
    }

    fun all(): List<FailedJob> = jobs.toList()

    fun find(id: String): FailedJob? = jobs.find { it.id == id }

    fun forget(id: String): Boolean = jobs.removeIf { it.id == id }

    fun flush() {
        jobs.clear()
    }
}
