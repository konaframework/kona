package dev.kona.queue

/**
 * Runs jobs immediately on push (Laravel `sync` driver).
 * Delay is ignored — sync always executes inline.
 */
class SyncQueue : Queue {
    override fun push(job: Job, queue: String, delaySeconds: Long, batchId: String?): String {
        val payload = JobRegistry.payloadFor(job, job.javaClass.name, batchId)
        try {
            JobPipeline.run(job)
            if (batchId != null) {
                BatchRepositories.get().find(batchId)?.recordSuccess(payload.id)
            }
            QueueEvents.fire(JobProcessed(payload.id, "sync", queue))
        } catch (ex: Exception) {
            if (batchId != null) {
                BatchRepositories.get().find(batchId)?.recordFailure(payload.id, ex)
            }
            QueueEvents.fire(JobFailed(payload.id, "sync", queue, ex.message))
            throw ex
        }
        return payload.id
    }

    fun push(job: Job, queue: String): String = push(job, queue, 0, null)

    fun push(job: Job): String = push(job, "default", 0, null)

    override fun pop(queue: String): QueuePayload? = null

    override fun size(queue: String): Long = 0

    override fun clear(queue: String) = Unit

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) = Unit
}
