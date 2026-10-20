package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Redis connections.
 *
 * Kona reads Redis from the top-level `redis.*` config (not nested under
 * `database.redis`). Default client is in-memory for zero-dependency local
 * development; set `REDIS_CLIENT=lettuce` to talk to a real Redis server.
 */
object Redis : ConfigFile {
    override fun key(): String = "redis"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Default Redis Connection
        |--------------------------------------------------------------------------
        |
        | This option controls which named connection RedisManager uses when
        | none is specified. Cache and queue typically pin their own names
        | (`cache`, `default`) so they do not collide with application keys.
        |
        */
        "default" to Env.getOrDefault("REDIS_CONNECTION", "default"),

        /*
        |--------------------------------------------------------------------------
        | Redis Client
        |--------------------------------------------------------------------------
        |
        | Supported: "memory" (in-process, default), "lettuce" (network Redis).
        |
        */
        "client" to Env.getOrDefault("REDIS_CLIENT", "memory"),

        /*
        |--------------------------------------------------------------------------
        | Redis Connections
        |--------------------------------------------------------------------------
        |
        | Each named connection may set `url` (takes precedence) or host / port /
        | database / password. `database` is the Redis logical DB index.
        |
        */
        "connections" to mapOf(
            "default" to mapOf(
                "url" to Env.get("REDIS_URL"),
                "host" to Env.getOrDefault("REDIS_HOST", "127.0.0.1"),
                "username" to Env.get("REDIS_USERNAME"),
                "password" to Env.get("REDIS_PASSWORD"),
                "port" to (Env.getOrDefault("REDIS_PORT", "6379").toIntOrNull() ?: 6379),
                "database" to Env.getOrDefault("REDIS_DB", "0"),
            ),
            "cache" to mapOf(
                "url" to Env.get("REDIS_URL"),
                "host" to Env.getOrDefault("REDIS_HOST", "127.0.0.1"),
                "username" to Env.get("REDIS_USERNAME"),
                "password" to Env.get("REDIS_PASSWORD"),
                "port" to (Env.getOrDefault("REDIS_PORT", "6379").toIntOrNull() ?: 6379),
                "database" to Env.getOrDefault("REDIS_CACHE_DB", "1"),
            ),
        ),
    )
}
