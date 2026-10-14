package dev.kona.session

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.redis.RedisConnection
import dev.kona.redis.RedisManager

/**
 * Registers [SessionManager] as a container singleton (alias `session.manager`).
 *
 * When `session.driver=redis`, resolves [RedisConnection] or [RedisManager] from the container
 * (register [dev.kona.redis.RedisServiceProvider] first).
 */
class SessionServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(SessionManager::class.java) { container ->
            val redis = when {
                container.bound(RedisManager::class.java) ->
                    container.resolve(RedisManager::class.java).connection()
                container.bound(RedisConnection::class.java) ->
                    container.resolve(RedisConnection::class.java)
                else -> null
            }
            SessionManager(app.config, app.basePath, redis)
        }
        app.container.alias("session.manager", SessionManager::class.java.name)
    }
}
