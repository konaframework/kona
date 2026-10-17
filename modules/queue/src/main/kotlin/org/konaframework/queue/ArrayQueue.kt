package org.konaframework.queue

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/** In-memory FIFO queue for tests and single-process workers. */
class ArrayQueue : Queue {
    private val queues = ConcurrentHashMap<String, ArrayDeque<String>>()

    private fun deque(queue: String): ArrayDeque<String> =
        queues.getOrPut(queue) { ArrayDeque() }

    override fun push(job: Job, queue: String, delaySeconds: Long, batchId: String?): String {
        val key = job.javaClass.name
        JobRegistry.register(key) { job }
        val payload = JobRegistry.payloadFor(job, key, batchId).withDelay(delaySeconds)
        synchronized(deque(queue)) {
            deque(queue).addLast(JobRegistry.encode(payload))
        }
        return payload.id
    }

    fun push(job: Job, queue: String): String = push(job, queue, 0, null)

    fun push(job: Job): String = push(job, "default", 0, null)

    override fun pop(queue: String): QueuePayload? {
        val now = System.currentTimeMillis()
        synchronized(deque(queue)) {
            val d = deque(queue)
            val iter = d.iterator()
            while (iter.hasNext()) {
                val payload = JobRegistry.decode(iter.next())
                if (payload.isAvailable(now)) {
                    iter.remove()
                    return payload
                }
            }
            return null
        }
    }

    override fun size(queue: String): Long =
        synchronized(deque(queue)) { deque(queue).size.toLong() }

    override fun clear(queue: String) {
        synchronized(deque(queue)) { deque(queue).clear() }
    }

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) {
        val next = payload.withDelay(delaySeconds)
        synchronized(deque(queue)) {
            deque(queue).addLast(JobRegistry.encode(next))
        }
    }
}
