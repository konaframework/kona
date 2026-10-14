package dev.kona.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kona.mail.Mailer
import org.slf4j.LoggerFactory
import java.nio.file.Files
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
 * Persists notification arrays in memory, optionally mirrored to a JSON file so rows survive restart.
 *
 * Pass a [path] (or set `notifications.database.path` via [NotificationServiceProvider]) for file-backed storage.
 * Omit [path] for the default in-memory-only channel.
 */
class DatabaseChannel @JvmOverloads constructor(
    private val path: Path? = null,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : NotificationChannel {
    private val rows = CopyOnWriteArrayList<Map<String, Any?>>()

    init {
        load()
    }

    fun all(): List<Map<String, Any?>> = rows.toList()

    fun clear() {
        rows.clear()
        persist()
    }

    override fun send(notifiable: Notifiable, notification: Notification) {
        rows += mapOf(
            "notifiable" to (notifiable.routeNotificationFor("database") ?: notifiable.toString()),
            "type" to notification.javaClass.name,
            "data" to notification.toArray(notifiable),
        )
        persist()
    }

    private fun persist() {
        val target = path ?: return
        val parent = target.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows.toList()))
    }

    private fun load() {
        val target = path ?: return
        if (!Files.exists(target) || Files.size(target) == 0L) {
            return
        }
        try {
            val loaded: List<Map<String, Any?>> = mapper.readValue(Files.readString(target))
            rows.clear()
            rows.addAll(loaded)
        } catch (_: Exception) {
            rows.clear()
        }
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
