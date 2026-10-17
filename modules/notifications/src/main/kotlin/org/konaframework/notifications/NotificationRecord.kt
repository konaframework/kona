package org.konaframework.notifications

import java.time.Instant
import java.util.UUID

/**
 * One stored database notification (Laravel `notifications` row shape).
 */
data class NotificationRecord(
    val id: String,
    val type: String,
    val data: Map<String, Any?>,
    val notifiable: String,
    val readAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    fun isUnread(): Boolean = readAt == null

    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "id" to id,
            "type" to type,
            "data" to data,
            "notifiable" to notifiable,
            "read_at" to readAt?.toString(),
            "created_at" to createdAt.toString(),
        )

    companion object {
        @JvmStatic
        fun create(notifiable: String, type: String, data: Map<String, Any?>): NotificationRecord {
            val createdAt = Instant.now()
            return NotificationRecord(
                id = UUID.randomUUID().toString(),
                type = type,
                data = data,
                notifiable = notifiable,
                readAt = null,
                createdAt = createdAt,
            )
        }

        @JvmStatic
        fun fromMap(map: Map<String, Any?>): NotificationRecord {
            val idValue = map["id"]?.toString()
            val id = if (idValue.isNullOrBlank()) UUID.randomUUID().toString() else idValue
            val createdAt = parseInstant(map["created_at"]) ?: Instant.now()
            return NotificationRecord(
                id = id,
                type = map["type"]?.toString().orEmpty(),
                data = mapData(map["data"]),
                notifiable = map["notifiable"]?.toString().orEmpty(),
                readAt = parseInstant(map["read_at"]),
                createdAt = createdAt,
            )
        }

        private fun mapData(value: Any?): Map<String, Any?> {
            val asMap = value as? Map<*, *> ?: return emptyMap()
            return asMap.entries.associate { it.key.toString() to it.value }
        }

        private fun parseInstant(value: Any?): Instant? {
            if (value == null) {
                return null
            }
            if (value is Instant) {
                return value
            }
            val text = value.toString()
            if (text.isBlank() || text == "null") {
                return null
            }
            return runCatching { Instant.parse(text) }.getOrNull()
        }
    }
}
