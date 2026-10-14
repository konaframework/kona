package dev.kona.queue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kona.redis.RedisConnection

/**
 * Redis-backed queue using get/set list encoding (works with memory and Lettuce clients).
 */
class RedisQueue(
    private val redis: RedisConnection,
    private val prefix: String = "kona_queues:",
) : Queue {
    private val mapper = jacksonObjectMapper()

    override fun push(job: Job, queue: String): String {
        val key = job.javaClass.name
        JobRegistry.register(key) { job }
        val payload = JobRegistry.payloadFor(job, key)
        append(listKey(queue), JobRegistry.encode(payload))
        return payload.id
    }

    override fun pop(queue: String): QueuePayload? {
        val listKey = listKey(queue)
        val list = readList(listKey)
        if (list.isEmpty()) return null
        val raw = list.removeAt(0)
        writeList(listKey, list)
        return JobRegistry.decode(raw)
    }

    override fun size(queue: String): Long = readList(listKey(queue)).size.toLong()

    override fun clear(queue: String) {
        redis.del(listKey(queue))
    }

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) {
        append(listKey(queue), JobRegistry.encode(payload))
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
        if (list.isEmpty()) {
            redis.del(key)
        } else {
            redis.set(key, mapper.writeValueAsString(list))
        }
    }
}
