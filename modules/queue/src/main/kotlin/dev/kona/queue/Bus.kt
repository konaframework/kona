package dev.kona.queue

/**
 * Dispatches jobs to the default (or named) queue connection.
 * Jobs that do not implement [ShouldQueue] run synchronously via [SyncQueue] semantics.
 */
class Bus(
    private val manager: QueueManager,
) {
    @JvmOverloads
    fun dispatch(job: Job, connection: String? = null, queue: String? = null): String {
        if (job !is ShouldQueue) {
            job.handle()
            return JobRegistry.payloadFor(job).id
        }
        val name = connection ?: manager.defaultConnection()
        val q = manager.connection(name)
        val queueName = queue ?: manager.defaultQueue(name)
        return q.push(job, queueName)
    }

    fun dispatchSync(job: Job): String {
        job.handle()
        return JobRegistry.payloadFor(job).id
    }
}
