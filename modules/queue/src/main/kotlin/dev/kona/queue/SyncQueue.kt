package dev.kona.queue

/**
 * Runs jobs immediately on push (Laravel `sync` driver).
 * Delay is ignored — sync always executes inline.
 */
class SyncQueue : Queue {
    override fun push(job: Job, queue: String, delaySeconds: Long): String {
        val payload = JobRegistry.payloadFor(job)
        JobPipeline.run(job)
        return payload.id
    }

    fun push(job: Job, queue: String): String = push(job, queue, 0)

    fun push(job: Job): String = push(job, "default", 0)

    override fun pop(queue: String): QueuePayload? = null

    override fun size(queue: String): Long = 0

    override fun clear(queue: String) = Unit

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) = Unit
}
