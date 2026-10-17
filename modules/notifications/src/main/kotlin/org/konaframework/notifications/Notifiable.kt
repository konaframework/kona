package org.konaframework.notifications

/**
 * Something that can receive notifications (user, anonymous route, …).
 */
interface Notifiable {
    /** Routing address for [channel] (`mail`, `database`, `log`, …). */
    fun routeNotificationFor(channel: String): String?
}
