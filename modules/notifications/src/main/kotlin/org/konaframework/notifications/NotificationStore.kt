package org.konaframework.notifications

/**
 * Durable (or in-memory) storage for database-channel notifications.
 */
interface NotificationStore {
    fun add(record: NotificationRecord): NotificationRecord

    fun all(): List<NotificationRecord>

    fun forNotifiable(notifiable: String): List<NotificationRecord>

    fun unread(notifiable: String): List<NotificationRecord> =
        forNotifiable(notifiable).filter { it.isUnread() }

    fun find(id: String): NotificationRecord?

    fun markAsRead(id: String): NotificationRecord?

    fun markAllAsRead(notifiable: String): Int

    fun clear()
}
