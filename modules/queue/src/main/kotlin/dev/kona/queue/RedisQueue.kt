package dev.kona.queue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kona.redis.RedisConnection

/** Redis-backed queue using get/set list encoding. */
class RedisQueue(
    private val redis: RedisConnection,
    private val prefix: String = "kona_queues:",
) : Queue {
    private val mapper = jacksonObjectMapper()

    override fun push(job: Job, queue: String, delaySeconds: Long, batchId: String?): String {
        val key = job.javaClass.name
        JobRegistry.register(key) { job }
        val payload = JobRegistry.payloadFor(job, key, batchId).withDelay(delaySeconds)
        append(listKey(queue), JobRegistry.encode(payload))
        return payload.id
    }

    fun push(job: Job, queue: String): String = push(job, queue, 0, null)

    fun push(job: Job): String = push(job, "default", 0, null)

    override fun pop(queue: String): QueuePayload? {
        val listKey = listKey(queue)
        val list = readList(listKey)
        if (list.isEmpty()) return null
        val now = System.currentTimeMillis()
        val index = list.indexOfFirst { JobRegistry.decode(it).isAvailable(now) }
        if (index < 0) return null
        val raw = list.removeAt(index)
        writeList(listKey, list)
        return JobRegistry.decode(raw)
    }

    override fun size(queue: String): Long = readList(listKey(queue)).size.toLong()

    override fun clear(queue: String) {
        redis.del(listKey(queue))
    }

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) {
        append(listKey(queue), JobRegistry.encode(payload.withDelay(delaySeconds)))
    }

    private fun append(listKey: String, encoded: String) {
        val list = readList(listKey)
        list.add(encoded)
        writeList(listKey, list)
    }

    private fun listKey(queue: String) = "$prefix$queue"

    private fun readList(key: String): MutableList<String> {
        val raw = redis.get(key) ?: return mutableListOf()
        return mapper.readValue(raw)
    }

    private fun writeList(key: String, list: List<String>) {
        if (list.isEmpty()) redis.del(key)
        else redis.set(key, mapper.writeValueAsString(list))
    }
}
