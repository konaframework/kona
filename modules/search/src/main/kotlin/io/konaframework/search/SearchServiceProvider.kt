package io.konaframework.search

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

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
