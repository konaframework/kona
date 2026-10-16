package io.konaframework.queue

import io.konaframework.cadence.DatabaseConnection

/**
 * Database-backed queue using a `jobs` table.
 *
 * Call [ensureTable] once (or migrate) before pushing.
 */
class DatabaseQueue(
    private val connection: DatabaseConnection,
    private val table: String = "jobs",
) : Queue {
    fun ensureTable() {
        connection.statement(
            """
            CREATE TABLE IF NOT EXISTS $table (
              id VARCHAR(64) PRIMARY KEY,
              queue VARCHAR(255) NOT NULL,
              payload CLOB NOT NULL,
              attempts INT NOT NULL,
              available_at BIGINT NOT NULL,
              created_at BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun push(job: Job, queue: String, delaySeconds: Long, batchId: String?): String {
        val key = job.javaClass.name
        JobRegistry.register(key) { job }
        val payload = JobRegistry.payloadFor(job, key, batchId)
        release(payload, queue, delaySeconds)
        return payload.id
    }

    fun push(job: Job, queue: String): String = push(job, queue, 0, null)

    fun push(job: Job): String = push(job, "default", 0, null)

    override fun release(payload: QueuePayload, queue: String, delaySeconds: Long) {
        val now = System.currentTimeMillis()
        connection.insert(
            "INSERT INTO $table (id, queue, payload, attempts, available_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            listOf(
                payload.id,
                queue,
                JobRegistry.encode(payload),
                payload.attempts,
                now + delaySeconds * 1000,
                now,
            ),
        )
    }

    override fun pop(queue: String): QueuePayload? {
        val now = System.currentTimeMillis()
        val row = connection.selectOne(
            "SELECT id, payload, attempts FROM $table WHERE queue = ? AND available_at <= ? ORDER BY id ASC",
            listOf(queue, now),
        ) ?: return null
        val id = requireCell(row, "id")
        val payloadJson = requireCell(row, "payload").toString()
        val attempts = (requireCell(row, "attempts") as Number).toInt()
        connection.delete("DELETE FROM $table WHERE id = ?", listOf(id))
        return JobRegistry.decode(payloadJson).withAttempt(attempts)
    }

    override fun size(queue: String): Long {
        val row = connection.selectOne(
            "SELECT COUNT(*) AS c FROM $table WHERE queue = ?",
            listOf(queue),
        ) ?: return 0L
        return (requireCell(row, "c") as Number).toLong()
    }

    override fun clear(queue: String) {
        connection.delete("DELETE FROM $table WHERE queue = ?", listOf(queue))
    }

    private fun requireCell(row: Map<String, Any?>, name: String): Any {
        for ((key, value) in row) {
            if (key.equals(name, ignoreCase = true) && value != null) {
                return value
            }
        }
        throw IllegalStateException("Queue row missing column [$name]")
    }
}
