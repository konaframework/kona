package dev.kona.queue

import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Processes jobs from a queue connection until empty (or [WorkerOptions] stop). */
class Worker @JvmOverloads constructor(
    private val manager: QueueManager,
    private val connectionName: String = manager.defaultConnection(),
    private val queueName: String = manager.defaultQueue(connectionName),
    private val options: WorkerOptions = WorkerOptions(),
    private val bus: Bus = Bus(manager),
) {
    private val log = LoggerFactory.getLogger(Worker::class.java)

    @JvmOverloads
    fun run(maxJobs: Int = options.maxJobs): Int {
        val limit = when {
            options.once -> 1
            maxJobs < options.maxJobs -> maxJobs
            else -> options.maxJobs
        }
        var processed = 0
        var idleCycles = 0
        while (processed < limit) {
            if (exceededMemory()) {
                log.warn("Worker memory limit exceeded ({} MB); stopping", options.memoryMb)
                break
            }
            val queue = manager.connection(connectionName)
            val payload = queue.pop(queueName)
            if (payload == null) {
                if (options.stopWhenEmpty) {
                    break
                }
                sleepQuietly(options.sleepSeconds)
                idleCycles += 1
                if (idleCycles >= options.maxIdleCycles) {
                    break
                }
                continue
            }
            idleCycles = 0
            processPayload(queue, payload)
            processed += 1
        }
        return processed
    }

    private fun processPayload(queue: Queue, payload: QueuePayload) {
        val tries = options.maxTries ?: payload.tries
        val backoff = options.backoffSeconds ?: payload.backoffSeconds
        val attempt = payload.copy(tries = tries, backoffSeconds = backoff).withAttempt(payload.attempts + 1)
        val job = JobRegistry.create(attempt)
        var acquiredUnique = false
        if (job is ShouldBeUnique) {
            if (!UniqueLock.acquire(job.uniqueId(), job.uniqueForSeconds())) {
                queue.release(payload, queueName, 10)
                return
            }
            acquiredUnique = true
        }
        try {
            runWithTimeout(job)
        } catch (ex: ReleaseJobException) {
            log.info("Job {} released: {}", attempt.displayName, ex.message)
            queue.release(attempt, queueName, ex.delaySeconds)
        } catch (ex: Exception) {
            if (attempt.attempts < attempt.tries) {
                log.warn("Job {} failed attempt {}: {}", attempt.displayName, attempt.attempts, ex.message)
                queue.release(attempt, queueName, attempt.backoffSeconds)
            } else {
                log.error("Job {} failed permanently: {}", attempt.displayName, ex.message)
                manager.failed.log(connectionName, queueName, attempt, ex)
            }
        } finally {
            if (acquiredUnique) {
                UniqueLock.release((job as ShouldBeUnique).uniqueId())
            }
        }
    }

    private fun runWithTimeout(job: Job) {
        val timeout = options.timeoutSeconds
        if (timeout <= 0) {
            WorkerContext.bind(bus, connectionName, queueName)
            try {
                JobPipeline.run(job)
            } finally {
                WorkerContext.clear()
            }
            return
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<Unit> {
                WorkerContext.bind(bus, connectionName, queueName)
                try {
                    JobPipeline.run(job)
                } finally {
                    WorkerContext.clear()
                }
            }
            try {
                future.get(timeout, TimeUnit.SECONDS)
            } catch (ex: TimeoutException) {
                future.cancel(true)
                throw RuntimeException("Job timed out after ${timeout}s")
            } catch (ex: ExecutionException) {
                val cause = ex.cause
                if (cause is Exception) {
                    throw cause
                }
                throw RuntimeException(cause ?: ex)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun exceededMemory(): Boolean {
        if (options.memoryMb <= 0) {
            return false
        }
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return usedMb >= options.memoryMb
    }

    private fun sleepQuietly(seconds: Long) {
        if (seconds <= 0) {
            return
        }
        try {
            Thread.sleep(seconds * 1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
