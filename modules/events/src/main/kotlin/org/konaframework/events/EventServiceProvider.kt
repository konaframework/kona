package org.konaframework.events

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class EventServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(EventDispatcher::class.java) { EventDispatcher() }
    }
}
