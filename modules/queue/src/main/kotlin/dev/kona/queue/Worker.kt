package dev.kona.queue

import org.slf4j.LoggerFactory

/**
 * Processes jobs from a queue connection until empty (or [maxJobs] reached).
 */
class Worker @JvmOverloads constructor(
    private val manager: QueueManager,
    private val connectionName: String = manager.defaultConnection(),
    private val queueName: String = manager.defaultQueue(connectionName),
) {
    private val log = LoggerFactory.getLogger(Worker::class.java)

    @JvmOverloads
    fun run(maxJobs: Int = Int.MAX_VALUE): Int {
        var processed = 0
        while (processed < maxJobs) {
            val queue = manager.connection(connectionName)
            val payload = queue.pop(queueName) ?: break
            processed += 1
            val attempt = payload.withAttempt(payload.attempts + 1)
            try {
                JobRegistry.create(attempt).handle()
            } catch (ex: Exception) {
                if (attempt.attempts < attempt.tries) {
                    log.warn("Job {} failed attempt {}: {}", attempt.displayName, attempt.attempts, ex.message)
                    // Delay via queue release (availableAt); do not block the worker thread.
                    queue.release(attempt, queueName, attempt.backoffSeconds)
                } else {
                    log.error("Job {} failed permanently: {}", attempt.displayName, ex.message)
                    manager.failed.log(connectionName, queueName, attempt, ex)
                }
            }
        }
        return processed
    }
}
