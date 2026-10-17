package org.konaframework.broadcasting

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class BroadcastServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(BroadcastManager::class.java) {
            BroadcastManager(app.config).also { Broadcast.setManager(it) }
        }
    }

    override fun boot(app: Application) {
        app.resolve(BroadcastManager::class.java)
    }
}
