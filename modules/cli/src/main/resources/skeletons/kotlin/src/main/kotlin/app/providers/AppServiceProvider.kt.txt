package app.providers

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class AppServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        // Bind application services into app.container here.
    }

    override fun boot(app: Application) {
        // Runs after every provider has registered.
    }
}
