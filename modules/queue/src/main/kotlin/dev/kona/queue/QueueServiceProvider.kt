package dev.kona.queue

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.cadence.Database
import dev.kona.redis.RedisManager

class QueueServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(QueueManager::class.java) { _ ->
            val redis =
                if (app.container.bound(RedisManager::class.java)) {
                    app.resolve(RedisManager::class.java)
                } else {
                    null
                }
            val database =
                if (app.container.bound(Database::class.java)) {
                    app.resolve(Database::class.java)
                } else {
                    null
                }
            QueueManager(app.config, redis, database, app.basePath)
        }
        app.container.singleton(Bus::class.java) { _ ->
            Bus(app.resolve(QueueManager::class.java))
        }
        app.container.singleton(FailedJobProvider::class.java) { _ ->
            app.resolve(QueueManager::class.java).failed
        }
    }
}
