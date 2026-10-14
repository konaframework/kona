package dev.kona.queue

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory FIFO queue for tests and single-process workers.
 */
class ArrayQueue : Queue {
    private val queues = ConcurrentHashMap<String, ArrayDeque<String>>()

    private fun deque(queue: String): ArrayDeque<String> =
        queues.getOrPut(queue) { ArrayDeque() }

    override fun push(job: Job, queue: String): String {
        val key = job.javaClass.name
        JobRegistry.register(key) { job }
        val payload = JobRegistry.payloadFor(job, key)
        synchronized(deque(queue)) {
            deque(queue).addLast(JobRegistry.encode(payload))
        }
        return payload.id
    }

    override fun pop(queue: String): QueuePayload? {
        val raw = synchronized(deque(queue)) {
            deque(queue).pollFirst()
        } ?: return null
        return JobRegistry.decode(raw)
    }

    override fun size(queue: String): Long =
        synchronized(deque(queue)) {
            deque(queue).size.toLong()
        }

    override fun clear(queue: String) {
        synchronized(deque(queue)) {
            deque(queue).clear()
        }
    }

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) {
        synchronized(deque(queue)) {
            deque(queue).addLast(JobRegistry.encode(payload))
        }
    }
}
