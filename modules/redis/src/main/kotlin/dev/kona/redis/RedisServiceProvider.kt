package dev.kona.redis

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

class RedisServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(RedisManager::class.java) { _ ->
            RedisManager(app.config)
        }
        app.container.singleton(RedisConnection::class.java) { _ ->
            app.resolve(RedisManager::class.java).connection()
        }
    }
}
