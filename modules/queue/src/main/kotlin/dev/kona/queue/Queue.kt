package dev.kona.queue

/**
 * Named queue connection (Laravel Queue contract subset).
 */
interface Queue {
    fun push(job: Job, queue: String = "default", delaySeconds: Long = 0): String

    fun pop(queue: String = "default"): QueuePayload?

    fun size(queue: String = "default"): Long

    fun clear(queue: String = "default")

    /** Re-queue a payload after a failed attempt (optional delay). */
    fun release(payload: QueuePayload, queue: String = "default", delaySeconds: Long = 0)
}
