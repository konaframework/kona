package dev.kona.queue

/**
 * Runs jobs immediately on push (Laravel `sync` driver).
 */
class SyncQueue : Queue {
    override fun push(job: Job, queue: String): String {
        val payload = JobRegistry.payloadFor(job)
        job.handle()
        return payload.id
    }

    override fun pop(queue: String): QueuePayload? = null

    override fun size(queue: String): Long = 0

    override fun clear(queue: String) = Unit

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) = Unit
}
