package dev.kona.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory notification store, optionally mirrored to a JSON file.
 */
class MemoryNotificationStore(
    private val path: Path? = null,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : NotificationStore {
    private val rows = CopyOnWriteArrayList<NotificationRecord>()

    init {
        load()
    }

    override fun add(record: NotificationRecord): NotificationRecord {
        rows += record
        persist()
        return record
    }

    override fun all(): List<NotificationRecord> = rows.toList()

    override fun forNotifiable(notifiable: String): List<NotificationRecord> =
        rows.filter { it.notifiable == notifiable }

    override fun find(id: String): NotificationRecord? = rows.firstOrNull { it.id == id }

    override fun markAsRead(id: String): NotificationRecord? {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) {
            return null
        }
        val updated = rows[index].copy(readAt = Instant.now())
        rows[index] = updated
        persist()
        return updated
    }

    override fun markAllAsRead(notifiable: String): Int {
        var count = 0
        for (i in rows.indices) {
            val row = rows[i]
            if (row.notifiable == notifiable && row.isUnread()) {
                rows[i] = row.copy(readAt = Instant.now())
                count++
            }
        }
        if (count > 0) {
            persist()
        }
        return count
    }

    override fun clear() {
        rows.clear()
        persist()
    }

    private fun persist() {
        val target = path ?: return
        val parent = target.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val payload = rows.map { it.toMap() }
        Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload))
    }

    private fun load() {
        val target = path ?: return
        if (!Files.exists(target) || Files.size(target) == 0L) {
            return
        }
        try {
            val loaded: List<Map<String, Any?>> = mapper.readValue(Files.readString(target))
            rows.clear()
            rows.addAll(loaded.map { NotificationRecord.fromMap(it) })
        } catch (_: Exception) {
            rows.clear()
        }
    }
}
