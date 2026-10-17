package org.konaframework.drip

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class DripServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Drip::class.java) { Drip() }
    }
}
