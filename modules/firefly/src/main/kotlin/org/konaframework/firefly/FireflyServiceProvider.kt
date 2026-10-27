package org.konaframework.firefly

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

/**
 * Mounts discovered and [Firefly.register] panels once the router exists.
 * `./grind install firefly` registers this provider, and an app subclass can register more panels.
 */
open class FireflyServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        // Soft reload keeps this object alive; drop the prior Application's mount first.
        Firefly.prepare(app)
        FireflyConfig.discovery(app.config)
    }

    override fun boot(app: Application) {
        Firefly.mount(app)
    }
}
