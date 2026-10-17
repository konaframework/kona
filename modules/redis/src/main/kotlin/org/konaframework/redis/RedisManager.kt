package org.konaframework.redis

import org.konaframework.core.Config
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves named Redis connections from config.
 *
 * Config keys:
 * - `redis.default` — connection name (default `default`)
 * - `redis.client` — `memory` (default) or `lettuce`
 * - `redis.connections.{name}.url` — redis URI for lettuce
 * - `redis.connections.{name}.host` / `port` / `database`
 */
class RedisManager(
    private val config: Config,
) {
    private val connections = ConcurrentHashMap<String, RedisConnection>()

    fun connection(): RedisConnection = connection(defaultName())

    fun connection(name: String): RedisConnection =
        connections.getOrPut(name) { create(name) }

    fun defaultName(): String = config.getString("redis.default", "default")

    fun disconnect(name: String = defaultName()) {
        connections.remove(name)?.close()
    }

    fun disconnectAll() {
        val keys = connections.keys.toList()
        for (key in keys) {
            disconnect(key)
        }
    }

    private fun create(name: String): RedisConnection {
        val client = config.getString("redis.client", "memory").lowercase()
        if (client == "memory" || client == "array") {
            return InMemoryRedisConnection()
        }
        val prefix = "redis.connections.$name"
        val url = config.getString("$prefix.url")
        if (url != null) {
            return LettuceRedisConnection.connect(url)
        }
        val host = config.getString("$prefix.host", "127.0.0.1")
        val port = config.getInt("$prefix.port", 6379)
        val database = config.getInt("$prefix.database", 0)
        return LettuceRedisConnection.connect(host, port, database)
    }
}
