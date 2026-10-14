package dev.kona.search

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

class SearchServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(SearchManager::class.java) {
            SearchManager(app.config).also { Scout.setManager(it) }
        }
    }

    override fun boot(app: Application) {
        app.resolve(SearchManager::class.java)
    }
}
