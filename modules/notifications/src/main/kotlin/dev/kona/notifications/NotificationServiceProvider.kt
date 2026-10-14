package dev.kona.notifications

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.mail.Mailer

class NotificationServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(DatabaseChannel::class.java) { DatabaseChannel() }
        app.container.singleton(LogChannel::class.java) { LogChannel() }
        app.container.singleton(NotificationSender::class.java) { _ ->
            val sender = NotificationSender()
            sender.extend("database", app.resolve(DatabaseChannel::class.java))
            sender.extend("log", app.resolve(LogChannel::class.java))
            if (app.container.bound(Mailer::class.java)) {
                sender.extend("mail", MailChannel(app.resolve(Mailer::class.java)))
            }
            sender
        }
    }
}
