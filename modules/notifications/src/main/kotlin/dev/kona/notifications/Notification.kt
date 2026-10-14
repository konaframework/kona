package dev.kona.notifications

import dev.kona.mail.Mailable

/**
 * Laravel-style notification. Declare channels in [via]; implement channel payloads as needed.
 */
abstract class Notification {
    abstract fun via(notifiable: Notifiable): List<String>

    open fun toMail(notifiable: Notifiable): Mailable? = null

    open fun toArray(notifiable: Notifiable): Map<String, Any?> = emptyMap()
}
