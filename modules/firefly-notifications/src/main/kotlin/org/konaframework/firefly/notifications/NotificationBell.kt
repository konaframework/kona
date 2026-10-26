package org.konaframework.firefly.notifications

import org.konaframework.notifications.DatabaseChannel
import org.konaframework.notifications.NotificationRecord

/**
 * Database notification bell. Rows come from the existing [DatabaseChannel] store.
 */
class NotificationBell(
    private val channel: DatabaseChannel,
    private val notifiableId: String,
) {
    fun records(): List<NotificationRecord> = channel.forNotifiable(notifiableId)

    fun unread(): List<NotificationRecord> = records().filter { it.isUnread() }

    fun markAsRead(id: String): Boolean = channel.markAsRead(id) != null

    fun markAllAsRead(): Int = channel.markAllAsRead(notifiableId)

    fun render(): String {
        val rows = records()
        val items = StringBuilder()
        for (row in rows) {
            val rawTitle = row.data["title"]
            val title = if (rawTitle == null) row.type else rawTitle.toString()
            val unread = if (row.isUnread()) "true" else "false"
            items.append(
                """<li data-ff-id="${escapeHtml(row.id)}" data-ff-unread="$unread">${escapeHtml(title)}</li>""",
            )
        }
        return """<div class="ff-notification-bell" wire:poll.30s><span class="ff-notification-count">${unread().size}</span><ul>$items</ul></div>"""
    }
}
