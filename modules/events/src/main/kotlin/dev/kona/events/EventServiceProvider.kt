package dev.kona.events

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

class EventServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(EventDispatcher::class.java) { EventDispatcher() }
    }
}
