package dev.kona.queue

import com.fasterxml.jackson.databind.ObjectMapper
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
) {
    fun withAttempt(next: Int): QueuePayload = copy(attempts = next)

    /** Apply an additional delay; `0` leaves [availableAt] unchanged. */
    fun withDelay(delaySeconds: Long): QueuePayload =
        if (delaySeconds <= 0) {
            this
        } else {
            copy(availableAt = System.currentTimeMillis() + delaySeconds * 1000)
        }

    fun isAvailable(now: Long = System.currentTimeMillis()): Boolean = availableAt <= now
}

/**
 * Registry of serializable job factories keyed by [jobKey].
 * In-process queues restore jobs through this map; cross-process workers fall back to a
 * public no-arg constructor on the [jobKey] class name.
 */
object JobRegistry {
    private val factories = ConcurrentHashMap<String, () -> Job>()
    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun register(key: String, factory: () -> Job) {
        factories[key] = factory
    }

    fun clear() {
        factories.clear()
    }

    fun create(key: String): Job {
        factories[key]?.let { return it.invoke() }
        return try {
            val type = Class.forName(key)
            val ctor = type.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance() as Job
        } catch (ex: Exception) {
            throw IllegalStateException(
                "No job registered for key [$key] and no public no-arg constructor to restore it",
                ex,
            )
        }
    }

    fun encode(payload: QueuePayload): String = mapper.writeValueAsString(payload)

    fun decode(json: String): QueuePayload = mapper.readValue(json, QueuePayload::class.java)

    fun payloadFor(job: Job): QueuePayload = payloadFor(job, job.javaClass.name)

    fun payloadFor(job: Job, jobKey: String): QueuePayload {
        val contract = job as? JobContract
        val displayName = if (contract != null) contract.displayName() else job.javaClass.name
        val tries = if (contract != null) contract.tries() else 3
        val backoff = if (contract != null) contract.backoffSeconds() else 0L
        return QueuePayload(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            tries = tries,
            backoffSeconds = backoff,
            attempts = 0,
            jobKey = jobKey,
        )
    }
}
