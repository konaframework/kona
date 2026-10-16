package dev.kona.queue

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class QueuePayload(
    val id: String,
    val displayName: String,
    val tries: Int,
    val backoffSeconds: Long,
    val attempts: Int,
    val jobKey: String,
    /** Epoch millis when the job may be popped; `0` means immediately available. */
    val availableAt: Long = 0,
    /**
     * Jackson JSON of the job instance so workers in another process can restore state
     * without an in-process [JobRegistry] factory.
     */
    val jobBody: String? = null,
    /** Optional [Batch] id when this job was dispatched via [PendingBatch]. */
    val batchId: String? = null,
) {
    fun withAttempt(next: Int): QueuePayload = copy(attempts = next)

    /** Apply an additional delay; `0` leaves [availableAt] unchanged. */
    fun withDelay(delaySeconds: Long): QueuePayload =
        if (delaySeconds <= 0) {
            this
        } else {
            copy(availableAt = System.currentTimeMillis() + delaySeconds * 1000)
        }

    fun withBatchId(id: String?): QueuePayload = copy(batchId = id)

    fun isAvailable(now: Long = System.currentTimeMillis()): Boolean = availableAt <= now
}

/**
 * Registry of job factories and Jackson (de)serialization for durable payloads.
 *
 * Prefer restoring from [QueuePayload.jobBody] so database/redis/array workers can run
 * without the original process's in-memory factory map. Factories remain a fast path for
 * anonymous / non-serializable jobs in the same JVM.
 */
object JobRegistry {
    private val factories = ConcurrentHashMap<String, () -> Job>()
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    fun register(key: String, factory: () -> Job) {
        factories[key] = factory
    }

    fun clear() {
        factories.clear()
    }

    /** Restore a job from a payload (body first, then factory / no-arg constructor). */
    fun create(payload: QueuePayload): Job = create(payload.jobKey, payload.jobBody)

    fun create(key: String, jobBody: String? = null): Job {
        // Same-JVM fast path: keep the original instance captured at push time.
        factories[key]?.let { return it.invoke() }
        if (jobBody != null) {
            try {
                val type = Class.forName(key)
                return mapper.readValue(jobBody, type) as Job
            } catch (_: Exception) {
                // Fall through to no-arg constructor.
            }
        }
        return try {
            val type = Class.forName(key)
            val ctor = type.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance() as Job
        } catch (ex: Exception) {
            throw IllegalStateException(
                "No job registered for key [$key] and could not restore from jobBody / no-arg constructor",
                ex,
            )
        }
    }

    fun encode(payload: QueuePayload): String = mapper.writeValueAsString(payload)

    fun decode(json: String): QueuePayload = mapper.readValue(json, QueuePayload::class.java)

    fun payloadFor(job: Job): QueuePayload = payloadFor(job, job.javaClass.name, null)

    @JvmOverloads
    fun payloadFor(job: Job, jobKey: String, batchId: String? = null): QueuePayload {
        val contract = job as? JobContract
        val displayName = if (contract != null) contract.displayName() else job.javaClass.name
        val tries = if (contract != null) contract.tries() else 3
        val backoff = if (contract != null) contract.backoffSeconds() else 0L
        val body = try {
            mapper.writeValueAsString(job)
        } catch (_: Exception) {
            null
        }
        return QueuePayload(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            tries = tries,
            backoffSeconds = backoff,
            attempts = 0,
            jobKey = jobKey,
            jobBody = body,
            batchId = batchId,
        )
    }
}
