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
) {
    fun withAttempt(next: Int): QueuePayload = copy(attempts = next)
}

/**
 * Registry of serializable job factories keyed by [jobKey].
 * In-process queues (sync / array / redis / database) restore jobs through this map.
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
        val factory = factories[key]
        if (factory == null) {
            throw IllegalStateException("No job registered for key [$key]")
        }
        return factory.invoke()
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
