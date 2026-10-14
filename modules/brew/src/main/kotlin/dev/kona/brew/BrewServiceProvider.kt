package dev.kona.brew

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.http.Router

/**
 * Binds [BrewManager] and, when HTTP is present, registers:
 * - `POST /brew/message`
 * - `GET /brew/brew.js`
 */
class BrewServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(BrewManager::class.java) {
            BrewManager().also { Brew.setManager(it) }
        }
    }

    override fun boot(app: Application) {
        val brew = app.resolve(BrewManager::class.java)
        Brew.setManager(brew)
        if (app.container.bound(Router::class.java)) {
            val router = app.resolve(Router::class.java)
            router.post("/brew/message") { brew.handleMessage(it) }
            router.get("/brew/brew.js") { brew.scriptResponse() }
            // Alias path for apps that prefer an underscored endpoint.
            router.post("/_brew/update") { brew.handleMessage(it) }
        }
    }
}
