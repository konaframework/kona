package dev.kona.cache

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.redis.RedisManager

class CacheServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(CacheManager::class.java) { _ ->
            val redis =
                if (app.container.bound(RedisManager::class.java)) {
                    app.resolve(RedisManager::class.java)
                } else {
                    null
                }
            CacheManager(app.config, app.basePath, redis)
        }
        app.container.singleton(Repository::class.java) { _ ->
            app.resolve(CacheManager::class.java).store()
        }
    }
}
