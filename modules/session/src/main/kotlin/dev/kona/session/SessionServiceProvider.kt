package dev.kona.session

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

/**
 * Registers [SessionManager] as a container singleton (alias `session.manager`).
 */
class SessionServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(SessionManager::class.java) { _ ->
            SessionManager.fromConfig(app.config, app.basePath)
        }
        app.container.alias("session.manager", SessionManager::class.java.name)
    }
}
