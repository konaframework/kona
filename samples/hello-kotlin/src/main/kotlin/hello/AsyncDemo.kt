package hello

import dev.kona.core.Kona
import dev.kona.events.EventDispatcher
import dev.kona.events.EventServiceProvider
import dev.kona.filesystem.FilesystemManager
import dev.kona.filesystem.FilesystemServiceProvider
import dev.kona.mail.Content
import dev.kona.mail.Envelope
import dev.kona.mail.LogTransport
import dev.kona.mail.MailServiceProvider
import dev.kona.mail.Mailer
import dev.kona.mail.Mailable
import dev.kona.notifications.Notifiable
import dev.kona.notifications.Notification
import dev.kona.notifications.NotificationSender
import dev.kona.notifications.NotificationServiceProvider
import dev.kona.queue.Bus
import dev.kona.queue.Job
import dev.kona.queue.QueueServiceProvider
import dev.kona.queue.ShouldQueue
import dev.kona.queue.Worker
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

object AsyncDemo {
    data class OrderShipped(val id: String)

    @JvmStatic
    fun run(base: Path): Map<String, String> {
        val app = Kona.create(arrayOf("--base=$base"))
            .configure {
                it.set("queue.default", "array")
                it.set("queue.connections.array.driver", "array")
                it.set("mail.default", "log")
                it.set("filesystems.default", "local")
                it.set("filesystems.disks.local.root", base.resolve("storage").toString())
            }
            .providers(
                EventServiceProvider(),
                QueueServiceProvider(),
                MailServiceProvider(),
                NotificationServiceProvider(),
                FilesystemServiceProvider(),
            )
            .boot()

        val seen = mutableListOf<String>()
        app.resolve(EventDispatcher::class.java).listen(OrderShipped::class.java) { seen += it.id }
        app.resolve(EventDispatcher::class.java).dispatch(OrderShipped("42"))

        val counter = AtomicInteger()
        app.resolve(Bus::class.java).dispatch(
            object : Job, ShouldQueue {
                override fun handle() {
                    counter.incrementAndGet()
                }
            },
        )
        Worker(app.resolve(dev.kona.queue.QueueManager::class.java)).run(maxJobs = 1)

        val transport = LogTransport()
        Mailer(transport, null).to("user@example.com").send(
            object : Mailable() {
                override fun envelope() = Envelope(subject = "Hi")

                override fun content() = Content(text = "Hello")
            },
        )

        app.resolve(NotificationSender::class.java).send(
            object : Notifiable {
                override fun routeNotificationFor(channel: String) = "user@example.com"
            },
            object : Notification() {
                override fun via(notifiable: Notifiable) = listOf("log", "database")

                override fun toArray(notifiable: Notifiable) = mapOf("ok" to true)
            },
        )

        val disk = app.resolve(FilesystemManager::class.java).disk()
        disk.put("demo.txt", "stored")

        return mapOf(
            "event" to seen.joinToString(),
            "jobs" to counter.get().toString(),
            "mail" to transport.messages().size.toString(),
            "file" to (disk.getString("demo.txt") ?: ""),
        )
    }
}
