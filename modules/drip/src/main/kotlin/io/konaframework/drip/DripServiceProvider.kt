package io.konaframework.drip

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

class DripServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Drip::class.java) { Drip() }
    }
}
