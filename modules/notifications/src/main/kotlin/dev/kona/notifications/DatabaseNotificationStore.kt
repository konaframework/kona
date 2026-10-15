package dev.kona.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kona.extract.Database
import java.time.Instant

/**
 * Extract/SQL-backed notification store (Laravel `notifications` table).
 */
class DatabaseNotificationStore(
    private val database: Database,
    private val table: String = "notifications",
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : NotificationStore {
    init {
        ensureTable()
    }

    override fun add(record: NotificationRecord): NotificationRecord {
        database.table(table).insert(
            mapOf(
                "id" to record.id,
                "type" to record.type,
                "notifiable" to record.notifiable,
                "data" to mapper.writeValueAsString(record.data),
                "read_at" to record.readAt?.toString(),
                "created_at" to record.createdAt.toString(),
            ),
        )
        return record
    }

    override fun all(): List<NotificationRecord> =
        database.table(table).orderBy("created_at").get().map { rowToRecord(it) }

    override fun forNotifiable(notifiable: String): List<NotificationRecord> =
        database.table(table)
            .where("notifiable", notifiable)
            .orderBy("created_at")
            .get()
            .map { rowToRecord(it) }

    override fun find(id: String): NotificationRecord? {
        val row = database.table(table).where("id", id).first() ?: return null
        return rowToRecord(row)
    }

    override fun markAsRead(id: String): NotificationRecord? {
        val existing = find(id) ?: return null
        if (existing.readAt != null) {
            return existing
        }
        val readAt = Instant.now()
        database.table(table).where("id", id).update(mapOf("read_at" to readAt.toString()))
        return existing.copy(readAt = readAt)
    }

    override fun markAllAsRead(notifiable: String): Int {
        val unread = unread(notifiable)
        if (unread.isEmpty()) {
            return 0
        }
        val readAt = Instant.now().toString()
        for (row in unread) {
            database.table(table).where("id", row.id).update(mapOf("read_at" to readAt))
        }
        return unread.size
    }

    override fun clear() {
        database.table(table).delete()
    }

    private fun ensureTable() {
        // Quote identifiers so H2/Postgres keep lowercase names matching QueryBuilder wraps.
        database.connection().statement(
            """
            CREATE TABLE IF NOT EXISTS "$table" (
              "id" VARCHAR(64) PRIMARY KEY,
              "type" VARCHAR(512) NOT NULL,
              "notifiable" VARCHAR(512) NOT NULL,
              "data" CLOB,
              "read_at" VARCHAR(64),
              "created_at" VARCHAR(64) NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun rowToRecord(row: Map<String, Any?>): NotificationRecord {
        val data = parseDataColumn(row["data"])
        return NotificationRecord.fromMap(
            mapOf(
                "id" to row["id"],
                "type" to row["type"],
                "notifiable" to row["notifiable"],
                "data" to data,
                "read_at" to row["read_at"],
                "created_at" to row["created_at"],
            ),
        )
    }

    private fun parseDataColumn(raw: Any?): Map<String, Any?> {
        if (raw == null) {
            return emptyMap()
        }
        return try {
            mapper.readValue(raw.toString())
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
