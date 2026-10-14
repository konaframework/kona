package dev.kona.notifications

import java.util.concurrent.ConcurrentHashMap

/**
 * Sends notifications through registered channels (`mail`, `database`, `log`).
 */
class NotificationSender {
    private val channels = ConcurrentHashMap<String, NotificationChannel>()

    fun extend(name: String, channel: NotificationChannel): NotificationSender {
        channels[name] = channel
        return this
    }

    fun channel(name: String): NotificationChannel =
        channels[name] ?: throw IllegalArgumentException("Notification channel [$name] is not defined.")

    fun send(notifiable: Notifiable, notification: Notification) {
        for (name in notification.via(notifiable)) {
            channel(name).send(notifiable, notification)
        }
    }
}
