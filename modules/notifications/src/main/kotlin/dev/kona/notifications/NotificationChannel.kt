package dev.kona.notifications

import dev.kona.mail.Mailer
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

fun interface NotificationChannel {
    fun send(notifiable: Notifiable, notification: Notification)
}

class MailChannel(
    private val mailer: Mailer,
) : NotificationChannel {
    override fun send(notifiable: Notifiable, notification: Notification) {
        val mailable = notification.toMail(notifiable)
            ?: throw IllegalStateException("Notification ${notification.javaClass.name} has no toMail()")
        val route = notifiable.routeNotificationFor("mail")
            ?: throw IllegalStateException("Notifiable has no mail route")
        mailer.to(route).send(mailable)
    }
}

/**
 * Persists notification arrays in memory (stand-in for a database notifications table).
 */
class DatabaseChannel : NotificationChannel {
    private val rows = CopyOnWriteArrayList<Map<String, Any?>>()

    fun all(): List<Map<String, Any?>> = rows.toList()

    fun clear() {
        rows.clear()
    }

    override fun send(notifiable: Notifiable, notification: Notification) {
        rows += mapOf(
            "notifiable" to (notifiable.routeNotificationFor("database") ?: notifiable.toString()),
            "type" to notification.javaClass.name,
            "data" to notification.toArray(notifiable),
        )
    }
}

class LogChannel : NotificationChannel {
    private val log = LoggerFactory.getLogger(LogChannel::class.java)
    private val entries = CopyOnWriteArrayList<String>()

    fun entries(): List<String> = entries.toList()

    fun clear() {
        entries.clear()
    }

    override fun send(notifiable: Notifiable, notification: Notification) {
        val line =
            "notify ${notification.javaClass.simpleName} -> ${notifiable.routeNotificationFor("log") ?: notifiable} ${notification.toArray(notifiable)}"
        entries += line
        log.info(line)
    }
}
