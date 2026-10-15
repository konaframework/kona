package dev.kona.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kona.mail.Mailer
import org.slf4j.LoggerFactory
import java.nio.file.Path
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
 * Persists notifications through a [NotificationStore].
 *
 * Construct with a [Path] for JSON-file memory store, omit for in-memory only, or pass an explicit [store]
 * (for example [DatabaseNotificationStore] when Extract is bound).
 */
class DatabaseChannel @JvmOverloads constructor(
    private val store: NotificationStore = MemoryNotificationStore(),
) : NotificationChannel {
    /** JSON-file / in-memory convenience constructor (legacy shape). */
    constructor(
        path: Path?,
        mapper: ObjectMapper = jacksonObjectMapper(),
    ) : this(MemoryNotificationStore(path, mapper))

    fun store(): NotificationStore = store

    /** Legacy map rows (includes `id` / `read_at` when present). */
    fun all(): List<Map<String, Any?>> = store.all().map { it.toMap() }

    fun records(): List<NotificationRecord> = store.all()

    fun forNotifiable(notifiable: String): List<NotificationRecord> = store.forNotifiable(notifiable)

    fun unread(notifiable: String): List<NotificationRecord> = store.unread(notifiable)

    fun markAsRead(id: String): NotificationRecord? = store.markAsRead(id)

    fun markAllAsRead(notifiable: String): Int = store.markAllAsRead(notifiable)

    fun clear() {
        store.clear()
    }

    override fun send(notifiable: Notifiable, notification: Notification) {
        val key = notifiable.routeNotificationFor("database") ?: notifiable.toString()
        store.add(
            NotificationRecord.create(
                notifiable = key,
                type = notification.javaClass.name,
                data = notification.toArray(notifiable),
            ),
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
