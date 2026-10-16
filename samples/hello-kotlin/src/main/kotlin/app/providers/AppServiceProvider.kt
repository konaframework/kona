package app.providers

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

class AppServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        // Bind application services into app.container here.
    }

    override fun boot(app: Application) {
        // Runs after every provider has registered.
    }
}
