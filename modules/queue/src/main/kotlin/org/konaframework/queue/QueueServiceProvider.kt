package org.konaframework.queue

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.cadence.Database
import org.konaframework.redis.RedisManager

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
        val batchDriver = app.config.getString("queue.batch.driver", "array")
        if (batchDriver == "database" && app.container.bound(Database::class.java)) {
            val table = app.config.getString("queue.batch.table", "job_batches")
            val repo = DatabaseBatchRepository(app.resolve(Database::class.java).connection(), table)
            BatchRepositories.set(repo)
            app.container.instance(BatchRepository::class.java, repo)
        } else {
            val repo = InMemoryBatchRepository()
            BatchRepositories.set(repo)
            app.container.instance(BatchRepository::class.java, repo)
        }
    }
}
