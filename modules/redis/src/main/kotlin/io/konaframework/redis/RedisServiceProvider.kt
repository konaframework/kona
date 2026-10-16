package io.konaframework.redis

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

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
