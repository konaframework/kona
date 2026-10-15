package dev.kona.queue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kona.extract.DatabaseConnection

/**
 * Persists failed jobs in a `failed_jobs` table (Laravel shape) so retries survive restart.
 *
 * Config: `queue.failed.driver=database` and optional `queue.failed.table` (default `failed_jobs`).
 * Requires a [Database] on [QueueManager].
 */
class DatabaseFailedJobProvider @JvmOverloads constructor(
    private val connection: DatabaseConnection,
    private val table: String = "failed_jobs",
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : FailedJobProvider() {
    init {
        ensureTable()
        load()
    }

    fun ensureTable() {
        connection.statement(
            """
            CREATE TABLE IF NOT EXISTS $table (
              id VARCHAR(64) PRIMARY KEY,
              connection VARCHAR(255) NOT NULL,
              queue VARCHAR(255) NOT NULL,
              payload CLOB NOT NULL,
              exception CLOB NOT NULL,
              failed_at BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun persist() {
        connection.delete("DELETE FROM $table", emptyList())
        for (job in jobs) {
            connection.insert(
                "INSERT INTO $table (id, connection, queue, payload, exception, failed_at) VALUES (?, ?, ?, ?, ?, ?)",
                listOf(
                    job.id,
                    job.connection,
                    job.queue,
                    mapper.writeValueAsString(job.payload),
                    job.exception,
                    job.failedAt,
                ),
            )
        }
    }

    private fun load() {
        val rows = connection.select("SELECT id, connection, queue, payload, exception, failed_at FROM $table", emptyList())
        jobs.clear()
        for (row in rows) {
            try {
                jobs += FailedJob(
                    id = requireCell(row, "id").toString(),
                    connection = requireCell(row, "connection").toString(),
                    queue = requireCell(row, "queue").toString(),
                    payload = mapper.readValue(requireCell(row, "payload").toString(), QueuePayload::class.java),
                    exception = requireCell(row, "exception").toString(),
                    failedAt = (requireCell(row, "failed_at") as Number).toLong(),
                )
            } catch (_: Exception) {
                // skip corrupt or incomplete rows
            }
        }
    }

    private fun requireCell(row: Map<String, Any?>, name: String): Any {
        for ((key, value) in row) {
            if (key.equals(name, ignoreCase = true) && value != null) {
                return value
            }
        }
        error("failed_jobs row missing [$name]")
    }
}
