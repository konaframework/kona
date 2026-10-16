package io.konaframework.events

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

class EventServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(EventDispatcher::class.java) { EventDispatcher() }
    }
}
