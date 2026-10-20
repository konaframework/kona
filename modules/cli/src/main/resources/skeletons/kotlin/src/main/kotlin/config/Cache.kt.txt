package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Cache stores (`config/cache.php` shape).
 *
 * Supported drivers: `array` / `memory`, `file`, `redis`.
 */
object Cache : ConfigFile {
    override fun key(): String = "cache"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Default Cache Store
        |--------------------------------------------------------------------------
        |
        | This option controls the default cache store that will be used by the
        | framework. This connection is utilized if another isn't explicitly
        | specified when running a cache operation inside the application.
        |
        */
        "default" to Env.getOrDefault("CACHE_STORE", "array"),

        /*
        |--------------------------------------------------------------------------
        | Cache Stores
        |--------------------------------------------------------------------------
        |
        | Here you may define all of the cache "stores" for your application as
        | well as their drivers. You may even define multiple stores for the
        | same cache driver to group types of items stored in your caches.
        |
        | Supported drivers: "array", "memory", "file", "redis"
        |
        */
        "stores" to mapOf(
            "array" to mapOf(
                "driver" to "array",
            ),
            "file" to mapOf(
                "driver" to "file",
                "path" to Env.getOrDefault("CACHE_PATH", "storage/framework/cache/data"),
            ),
            "redis" to mapOf(
                "driver" to "redis",
                "connection" to Env.getOrDefault("REDIS_CACHE_CONNECTION", "cache"),
            ),
        ),

        /*
        |--------------------------------------------------------------------------
        | Cache Key Prefix
        |--------------------------------------------------------------------------
        |
        | When utilizing Redis (or any shared cache), there might be other
        | applications using the same store. Prefix every cache key to avoid
        | collisions.
        |
        */
        "prefix" to Env.getOrDefault("CACHE_PREFIX", "kona_cache:"),
    )
}
