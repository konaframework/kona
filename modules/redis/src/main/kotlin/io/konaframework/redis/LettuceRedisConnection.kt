package io.konaframework.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

/**
 * Narrow sync command surface used by [LettuceRedisConnection] so command
 * paths can be tested without a live Redis server.
 */
internal interface RedisSync {
    fun get(key: String): String?

    fun set(key: String, value: String)

    fun setex(key: String, seconds: Long, value: String)

    fun del(vararg keys: String): Long

    fun exists(key: String): Long

    fun expire(key: String, seconds: Long): Boolean

    fun ttl(key: String): Long

    fun keys(pattern: String): List<String>

    fun flushdb()

    fun ping(): String
}

/** Adapts Lettuce [RedisCommands] to [RedisSync]. */
internal class LettuceSync(
    private val commands: RedisCommands<String, String>,
) : RedisSync {
    override fun get(key: String): String? = commands.get(key)

    override fun set(key: String, value: String) {
        commands.set(key, value)
    }

    override fun setex(key: String, seconds: Long, value: String) {
        commands.setex(key, seconds, value)
    }

    override fun del(vararg keys: String): Long = commands.del(*keys)

    override fun exists(key: String): Long = commands.exists(key)

    override fun expire(key: String, seconds: Long): Boolean = commands.expire(key, seconds)

    override fun ttl(key: String): Long = commands.ttl(key)

    override fun keys(pattern: String): List<String> = commands.keys(pattern)

    override fun flushdb() {
        commands.flushdb()
    }

    override fun ping(): String = commands.ping()
}

/**
 * Lettuce-backed [RedisConnection].
 */
class LettuceRedisConnection internal constructor(
    private val sync: RedisSync,
    private val onClose: () -> Unit,
) : RedisConnection {
    constructor(
        client: RedisClient,
        connection: StatefulRedisConnection<String, String>,
    ) : this(
        LettuceSync(connection.sync()),
        onClose = {
            connection.close()
            client.shutdown()
        },
    )

    override fun get(key: String): String? = sync.get(key)

    override fun set(key: String, value: String) {
        sync.set(key, value)
    }

    override fun setex(key: String, seconds: Long, value: String) {
        sync.setex(key, seconds, value)
    }

    override fun del(vararg keys: String): Long {
        if (keys.isEmpty()) {
            return 0
        }
        return sync.del(*keys)
    }

    override fun exists(key: String): Boolean = sync.exists(key) > 0

    override fun expire(key: String, seconds: Long): Boolean = sync.expire(key, seconds)

    override fun ttl(key: String): Long = sync.ttl(key)

    override fun keys(pattern: String): List<String> = sync.keys(pattern).sorted()

    override fun flushDb() {
        sync.flushdb()
    }

    override fun ping(): String = sync.ping()

    override fun close() {
        onClose()
    }

    companion object {
        @Volatile
        internal var liveOpener: (RedisURI) -> LettuceRedisConnection = ::openLive

        @JvmStatic
        fun connect(uri: String): LettuceRedisConnection {
            return liveOpener(RedisURI.create(uri))
        }

        @JvmStatic
        fun connect(host: String, port: Int): LettuceRedisConnection {
            return connect(host, port, 0)
        }

        @JvmStatic
        fun connect(host: String, port: Int, database: Int): LettuceRedisConnection {
            val uri = RedisURI.Builder.redis(host, port).withDatabase(database).build()
            return liveOpener(uri)
        }

        internal fun open(
            uri: RedisURI,
            opener: (RedisURI) -> LettuceRedisConnection,
        ): LettuceRedisConnection {
            return opener(uri)
        }

        internal fun openLive(
            uri: RedisURI,
            newClient: (RedisURI) -> RedisClient = { RedisClient.create(it) },
            connectClient: (RedisClient) -> StatefulRedisConnection<String, String> = { it.connect() },
        ): LettuceRedisConnection {
            val client = newClient(uri)
            val connection = connectClient(client)
            return LettuceRedisConnection(client, connection)
        }
    }
}
