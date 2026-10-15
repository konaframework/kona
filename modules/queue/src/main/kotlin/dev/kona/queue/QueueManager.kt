package dev.kona.queue

import dev.kona.core.Config
import dev.kona.extract.Database
import dev.kona.redis.RedisManager
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves named queue connections from config.
 *
 * - `queue.default` — connection name (default `sync`)
 * - `queue.connections.{name}.driver` — `sync`, `array`/`memory`, `redis`, `database`
 * - `queue.connections.{name}.queue` — default queue name
 * - `queue.prefix` — redis key prefix
 * - `queue.failed.driver` — `memory` (default), `file`, or `database`
 * - `queue.failed.path` — file path when driver is `file` (default `storage/framework/failed_jobs.json`)
 * - `queue.failed.table` — table when driver is `database` (default `failed_jobs`)
 */
class QueueManager(
    private val config: Config,
    private val redis: RedisManager?,
    private val database: Database?,
    private val basePath: Path = Path.of("").toAbsolutePath().normalize(),
) {
    constructor(config: Config) : this(config, null, null)

    constructor(config: Config, redis: RedisManager?) : this(config, redis, null)

    constructor(config: Config, redis: RedisManager?, database: Database?) :
        this(config, redis, database, Path.of("").toAbsolutePath().normalize())

    private val connections = ConcurrentHashMap<String, Queue>()
    val failed: FailedJobProvider = createFailedProvider()

    fun connection(): Queue = connection(defaultConnection())

    fun connection(name: String): Queue =
        connections.getOrPut(name) { create(name) }

    fun defaultConnection(): String = config.getString("queue.default", "sync")

    fun defaultQueue(): String = defaultQueue(defaultConnection())

    fun defaultQueue(connection: String): String =
        config.getString("queue.connections.$connection.queue", "default")

    private fun create(name: String): Queue {
        val configured = config.getString("queue.connections.$name.driver")
        val driver = if (configured != null) {
            configured
        } else if (
            name.equals("sync", true) || name.equals("array", true) ||
            name.equals("memory", true) || name.equals("redis", true) ||
            name.equals("database", true)
        ) {
            name
        } else {
            "sync"
        }
        return when (driver.lowercase()) {
            "sync" -> SyncQueue()
            "array", "memory" -> ArrayQueue()
            "redis" -> {
                if (redis == null) {
                    throw IllegalStateException("Redis queue requires a RedisManager")
                }
                val prefix = config.getString("queue.prefix", "kona_queues:")
                RedisQueue(redis.connection(), prefix)
            }
            "database" -> {
                if (database == null) {
                    throw IllegalStateException("Database queue requires a Database")
                }
                val table = config.getString("queue.connections.$name.table", "jobs")
                DatabaseQueue(database.connection(), table).also { it.ensureTable() }
            }
            else -> throw IllegalArgumentException("Queue connection [$name] is not defined.")
        }
    }

    private fun createFailedProvider(): FailedJobProvider {
        val driver = (config.getString("queue.failed.driver") ?: "memory").lowercase()
        return when (driver) {
            "file" -> FileFailedJobProvider(failedPath())
            "database" -> {
                if (database == null) {
                    throw IllegalStateException("Database failed jobs require a Database")
                }
                val table = config.getString("queue.failed.table", "failed_jobs")
                DatabaseFailedJobProvider(database.connection(), table)
            }
            else -> FailedJobProvider()
        }
    }

    private fun failedPath(): Path {
        val configured = config.getString("queue.failed.path")
        return if (configured.isNullOrBlank()) {
            basePath.resolve("storage").resolve("framework").resolve("failed_jobs.json")
        } else {
            val path = Path.of(configured)
            if (path.isAbsolute) path else basePath.resolve(configured)
        }
    }
}
