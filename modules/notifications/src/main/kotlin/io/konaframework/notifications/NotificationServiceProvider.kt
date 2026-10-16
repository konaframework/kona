package io.konaframework.notifications

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider
import io.konaframework.cadence.Database
import io.konaframework.mail.Mailer
import io.konaframework.queue.Bus
import java.nio.file.Path

class NotificationServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(NotificationStore::class.java) {
            resolveStore(app)
        }
        app.container.singleton(DatabaseChannel::class.java) {
            DatabaseChannel(app.resolve(NotificationStore::class.java))
        }
        app.container.singleton(LogChannel::class.java) { LogChannel() }
        app.container.singleton(NotificationSender::class.java) { _ ->
            val bus =
                if (app.container.bound(Bus::class.java)) {
                    app.resolve(Bus::class.java)
                } else {
                    null
                }
            val sender = NotificationSender(bus)
            sender.extend("database", app.resolve(DatabaseChannel::class.java))
            sender.extend("log", app.resolve(LogChannel::class.java))
            if (app.container.bound(Mailer::class.java)) {
                sender.extend("mail", MailChannel(app.resolve(Mailer::class.java)))
            }
            sender
        }
    }

    companion object {
        /**
         * `notifications.database.driver`: `database` (Cadence), `file` / `json`, or `memory` (default).
         * When driver is `database` and [Database] is bound, uses [DatabaseNotificationStore].
         * `notifications.database.path` still selects JSON file persistence for the memory/file driver.
         * `notifications.database.table` names the Cadence table (default `notifications`).
         */
        fun resolveStore(app: Application): NotificationStore {
            val driver = app.config.getString("notifications.database.driver", "memory").lowercase()
            if (driver == "database" || driver == "extract") {
                if (app.container.bound(Database::class.java)) {
                    val table = app.config.getString("notifications.database.table", "notifications")
                    return DatabaseNotificationStore(app.resolve(Database::class.java), table)
                }
            }
            return MemoryNotificationStore(resolveDatabasePath(app))
        }

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
