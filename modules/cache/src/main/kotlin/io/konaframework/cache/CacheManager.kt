package io.konaframework.cache

import io.konaframework.core.Config
import io.konaframework.redis.RedisManager
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves named cache stores from config (Laravel CacheManager).
 *
 * - `cache.default` — store name (default `array`)
 * - `cache.stores.{name}.driver` — `array` / `memory`, `file`, `redis`
 * - `cache.stores.{name}.path` — file store directory
 * - `cache.prefix` — redis key prefix
 */
class CacheManager @JvmOverloads constructor(
    private val config: Config,
    private val basePath: Path,
    private val redis: RedisManager? = null,
) {
    private val stores = ConcurrentHashMap<String, Repository>()

    fun store(): Repository = store(defaultDriver())

    fun store(name: String): Repository =
        stores.getOrPut(name) { Repository(createStore(name)) }

    fun driver(): Repository = store()

    fun driver(name: String): Repository = store(name)

    fun defaultDriver(): String = config.getString("cache.default", "array")

    private fun createStore(name: String): CacheStore {
        val configured = config.getString("cache.stores.$name.driver")
        val driver = when {
            configured != null -> configured
            name.equals("array", true) || name.equals("memory", true) ||
                name.equals("file", true) || name.equals("redis", true) -> name
            else -> "array"
        }
        return when (driver.lowercase()) {
            "array", "memory" -> ArrayCacheStore()
            "file" -> {
                val path = config.getString("cache.stores.$name.path")
                    ?: basePath.resolve("storage/framework/cache/data").toString()
                FileCacheStore(Path.of(path))
            }
            "redis" -> {
                val manager = redis
                    ?: throw IllegalStateException("Redis cache store requires a RedisManager")
                val prefix = config.getString("cache.prefix", "kona_cache:")
                RedisCacheStore(manager.connection(), prefix)
            }
            else -> throw IllegalArgumentException("Cache store [$name] is not defined.")
        }
    }
}
