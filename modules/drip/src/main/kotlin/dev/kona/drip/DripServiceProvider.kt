package dev.kona.drip

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

class DripServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Drip::class.java) { Drip() }
    }
}
