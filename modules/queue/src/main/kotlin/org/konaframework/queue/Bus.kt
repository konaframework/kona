package org.konaframework.queue

import org.konaframework.cadence.DB
import org.konaframework.cadence.Database

/**
 * Dispatches jobs to the default (or named) queue connection.
 * Jobs that do not implement [ShouldQueue] run synchronously via [SyncQueue] semantics.
 *
 * When a job opts into after-commit dispatch ([ShouldQueueAfterCommit], [JobContract.afterCommit],
 * or `queue.after_commit`) and a Cadence transaction is open, the push is deferred until the
 * outermost commit.
 */
class Bus(
    private val manager: QueueManager,
) {
    @JvmOverloads
    fun dispatch(
        job: Job,
        connection: String? = null,
        queue: String? = null,
        delaySeconds: Long = 0,
        batchId: String? = null,
    ): String {
        if (shouldDeferUntilCommit(job)) {
            val payload = JobRegistry.payloadFor(job)
            DB.afterCommit(
                Runnable {
                    dispatchNow(job, connection, queue, delaySeconds, batchId)
                },
            )
            return payload.id
        }
        return dispatchNow(job, connection, queue, delaySeconds, batchId)
    }

    @JvmOverloads
    fun later(
        delaySeconds: Long,
        job: Job,
        connection: String? = null,
        queue: String? = null,
    ): String = dispatch(job, connection, queue, delaySeconds)

    fun dispatchSync(job: Job): String {
        JobPipeline.run(job)
        return JobRegistry.payloadFor(job).id
    }

    fun chain(vararg jobs: Job): PendingChain = PendingChain(this, jobs.toList())

    fun chain(jobs: List<Job>): PendingChain = PendingChain(this, jobs)

    fun batch(vararg jobs: Job): PendingBatch = PendingBatch(this, jobs.toList())

    fun batch(jobs: List<Job>): PendingBatch = PendingBatch(this, jobs)

    private fun shouldDeferUntilCommit(job: Job): Boolean {
        if (!Database.hasGlobal()) {
            return false
        }
        if (DB.transactionLevel() == 0) {
            return false
        }
        if (job is ShouldQueueAfterCommit) {
            return true
        }
        if (job is JobContract && job.afterCommit()) {
            return true
        }
        return manager.afterCommitDefault()
    }

    private fun dispatchNow(
        job: Job,
        connection: String?,
        queue: String?,
        delaySeconds: Long,
        batchId: String?,
    ): String {
        if (job !is ShouldQueue) {
            val payload = JobRegistry.payloadFor(job).withBatchId(batchId)
            JobPipeline.run(job)
            if (batchId != null) {
                BatchRepositories.get().find(batchId)?.recordSuccess(payload.id)
            }
            QueueEvents.fire(JobQueued(payload.id, connection, queue, batchId))
            QueueEvents.fire(JobProcessed(payload.id, connection ?: "sync", queue ?: "sync"))
            return payload.id
        }
        val name = connection ?: manager.defaultConnection()
        val q = manager.connection(name)
        val queueName = queue ?: manager.defaultQueue(name)
        val id = q.push(job, queueName, delaySeconds, batchId)
        QueueEvents.fire(JobQueued(id, name, queueName, batchId))
        return id
    }
}
