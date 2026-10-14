package dev.kona.notifications

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.mail.Mailer
import java.nio.file.Path

class NotificationServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(DatabaseChannel::class.java) {
            DatabaseChannel(resolveDatabasePath(app))
        }
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

    companion object {
        /** `notifications.database.path` — absolute or relative to the app base path; blank = in-memory only. */
        fun resolveDatabasePath(app: Application): Path? {
            val configured = app.config.getString("notifications.database.path", "").trim()
            if (configured.isEmpty()) {
                return null
            }
            val path = Path.of(configured)
            return if (path.isAbsolute) path else app.basePath.resolve(path)
        }
    }
}
