package dev.kona.notifications

import dev.kona.core.Application
import dev.kona.mail.Content
import dev.kona.mail.Envelope
import dev.kona.mail.LogTransport
import dev.kona.mail.MailServiceProvider
import dev.kona.mail.Mailable
import dev.kona.mail.Mailer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NotificationTest {
    class User(val email: String) : Notifiable {
        override fun routeNotificationFor(channel: String): String? =
            when (channel) {
                "mail", "log", "database" -> email
                else -> null
            }
    }

    class InvoicePaid : Notification() {
        override fun via(notifiable: Notifiable) = listOf("mail", "database", "log")

        override fun toMail(notifiable: Notifiable) =
            object : Mailable() {
                override fun envelope() = Envelope(subject = "Paid")

                override fun content() = Content(text = "Thanks")
            }

        override fun toArray(notifiable: Notifiable) = mapOf("status" to "paid")
    }

    class EmptyMail : Notification() {
        override fun via(notifiable: Notifiable) = listOf("mail")
    }

    @Test
    fun sendsThroughChannels(@TempDir base: Path) {
        val transport = LogTransport()
        val mailer = Mailer(transport, null)
        val db = DatabaseChannel()
        val log = LogChannel()
        val sender = NotificationSender()
            .extend("mail", MailChannel(mailer))
            .extend("database", db)
            .extend("log", log)

        sender.send(User("u@x.com"), InvoicePaid())
        assertEquals(1, transport.messages().size)
        assertEquals(1, db.all().size)
        assertEquals(1, log.entries().size)
        db.clear()
        log.clear()
        assertTrue(db.all().isEmpty())
        assertTrue(log.entries().isEmpty())

        assertThrows(IllegalArgumentException::class.java) { sender.channel("missing") }
        assertThrows(IllegalStateException::class.java) {
            MailChannel(mailer).send(User("u@x.com"), EmptyMail())
        }
        assertThrows(IllegalStateException::class.java) {
            MailChannel(mailer).send(
                object : Notifiable {
                    override fun routeNotificationFor(channel: String) = null
                },
                InvoicePaid(),
            )
        }

        val noRoute = object : Notifiable {
            override fun routeNotificationFor(channel: String) = null
        }
        log.send(noRoute, InvoicePaid())
        assertEquals(1, log.entries().size)

        val bareNotification = object : Notification() {
            override fun via(notifiable: Notifiable) = listOf("database", "log")
        }
        assertEquals(null, bareNotification.toMail(User("x@y.z")))
        assertEquals(emptyMap<String, Any?>(), bareNotification.toArray(User("x@y.z")))
        db.send(noRoute, bareNotification)
        assertNotNull(db.all().last()["notifiable"])

        val app = Application(base)
        app.register(MailServiceProvider()).register(NotificationServiceProvider()).boot()
        app.resolve(NotificationSender::class.java).send(User("a@b.c"), InvoicePaid())
        assertTrue(app.resolve(DatabaseChannel::class.java).all().isNotEmpty())

        val bare = Application(base.resolve("bare"))
        bare.register(NotificationServiceProvider()).boot()
        assertThrows(IllegalArgumentException::class.java) {
            bare.resolve(NotificationSender::class.java).channel("mail")
        }
    }
}
