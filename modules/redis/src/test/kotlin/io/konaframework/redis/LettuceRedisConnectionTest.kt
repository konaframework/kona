package io.konaframework.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Duration

class LettuceRedisConnectionTest {
    @Test
    fun commandsDelegateThroughRedisSync() {
        val calls = mutableListOf<String>()
        val sync = object : RedisSync {
            override fun get(key: String): String? {
                calls += "get:$key"
                return "v"
            }

            override fun set(key: String, value: String) {
                calls += "set:$key:$value"
            }

            override fun setex(key: String, seconds: Long, value: String) {
                calls += "setex:$key:$seconds:$value"
            }

            override fun del(vararg keys: String): Long {
                calls += "del:${keys.toList()}"
                return keys.size.toLong()
            }

            override fun exists(key: String): Long {
                calls += "exists:$key"
                return 1
            }

            override fun expire(key: String, seconds: Long): Boolean {
                calls += "expire:$key:$seconds"
                return true
            }

            override fun ttl(key: String): Long {
                calls += "ttl:$key"
                return 9
            }

            override fun keys(pattern: String): List<String> {
                calls += "keys:$pattern"
                return listOf("b", "a")
            }

            override fun flushdb() {
                calls += "flushdb"
            }

            override fun ping(): String {
                calls += "ping"
                return "PONG"
            }
        }
        var closed = false
        val redis = LettuceRedisConnection(sync) { closed = true }
        assertEquals("v", redis.get("k"))
        redis.set("k", "1")
        redis.setex("k", 2, "1")
        assertEquals(0, redis.del())
        assertEquals(2, redis.del("a", "b"))
        assertTrue(redis.exists("k"))
        assertTrue(redis.expire("k", 3))
        assertEquals(9, redis.ttl("k"))
        assertEquals(listOf("a", "b"), redis.keys("*"))
        redis.flushDb()
        assertEquals("PONG", redis.ping())
        redis.close()
        assertTrue(closed)
        assertTrue(calls.contains("flushdb"))
    }

    @Test
    fun existsFalseWhenZero() {
        val sync = object : RedisSync by ThrowingSync {
            override fun exists(key: String): Long = 0
        }
        assertFalse(LettuceRedisConnection(sync) {}.exists("k"))
    }

    @Test
    fun lettuceSyncAdaptsProxyCommands() {
        val commands = proxyCommands(
            get = { "g" },
            set = { _, _ -> "OK" },
            setex = { _, _, _ -> "OK" },
            del = { keys -> keys.size.toLong() },
            exists = { 2L },
            expire = { _, _ -> true },
            ttl = { 4L },
            keys = { listOf("z", "a") },
            flushdb = { "OK" },
            ping = { "PONG" },
        )
        val sync = LettuceSync(commands)
        assertEquals("g", sync.get("k"))
        sync.set("k", "v")
        sync.setex("k", 1, "v")
        assertEquals(2, sync.del("a", "b"))
        assertEquals(2, sync.exists("k"))
        assertTrue(sync.expire("k", 1))
        assertEquals(4, sync.ttl("k"))
        assertEquals(listOf("z", "a"), sync.keys("*"))
        sync.flushdb()
        assertEquals("PONG", sync.ping())
    }

    @Test
    fun connectRejectsGarbageUri() {
        assertThrows(Exception::class.java) {
            LettuceRedisConnection.connect("://not-a-valid-redis-uri")
        }
    }

    @Test
    fun connectStringAndHostFailWithoutServer() {
        assertThrows(Exception::class.java) {
            LettuceRedisConnection.connect("redis://127.0.0.1:1/0")
        }
        assertThrows(Exception::class.java) {
            LettuceRedisConnection.connect("127.0.0.1", 1)
        }
        assertThrows(Exception::class.java) {
            LettuceRedisConnection.connect("127.0.0.1", 1, 2)
        }
    }

    @Test
    fun openUsesCustomOpener() {
        val fake = LettuceRedisConnection(object : RedisSync by ThrowingSync {
            override fun ping(): String = "PONG"
        }) {}
        val uri = RedisURI.Builder.redis("127.0.0.1", 6379).withDatabase(2).build()
        val result = LettuceRedisConnection.open(uri) { fake }
        assertEquals(fake, result)
        assertEquals("PONG", result.ping())
    }

    @Test
    fun openLiveWithStubClientAndConnection() {
        val commands = proxyCommands(
            get = { "live" },
            set = { _, _ -> "OK" },
            setex = { _, _, _ -> "OK" },
            del = { 1L },
            exists = { 1L },
            expire = { _, _ -> true },
            ttl = { 1L },
            keys = { emptyList() },
            flushdb = { "OK" },
            ping = { "PONG" },
        )
        var connectionClosed = false
        var clientShutdown = false
        val connection = proxyConnection(commands) { connectionClosed = true }
        val client = object : RedisClient() {
            override fun shutdown() {
                clientShutdown = true
            }
        }
        val redis = LettuceRedisConnection.openLive(
            RedisURI.create("redis://localhost:6379"),
            newClient = { client },
            connectClient = { connection },
        )
        assertEquals("live", redis.get("k"))
        assertEquals("PONG", redis.ping())
        redis.close()
        assertTrue(connectionClosed)
        assertTrue(clientShutdown)
    }

    @Test
    fun openLiveDefaultConnectFailsFast() {
        assertThrows(Exception::class.java) {
            LettuceRedisConnection.openLive(
                RedisURI.Builder.redis("127.0.0.1", 1).withTimeout(Duration.ofMillis(200)).build(),
            )
        }
    }

    @Test
    fun publicConstructorClosesClientAndConnection() {
        val commands = proxyCommands(
            get = { null },
            set = { _, _ -> "OK" },
            setex = { _, _, _ -> "OK" },
            del = { 0L },
            exists = { 0L },
            expire = { _, _ -> false },
            ttl = { -2L },
            keys = { emptyList() },
            flushdb = { "OK" },
            ping = { "PONG" },
        )
        var connectionClosed = false
        var clientShutdown = false
        val connection = proxyConnection(commands) { connectionClosed = true }
        val client = object : RedisClient() {
            override fun shutdown() {
                clientShutdown = true
            }
        }
        val redis = LettuceRedisConnection(client, connection)
        assertEquals("PONG", redis.ping())
        redis.close()
        assertTrue(connectionClosed)
        assertTrue(clientShutdown)
    }

    private object ThrowingSync : RedisSync {
        override fun get(key: String): String? = error("unused")
        override fun set(key: String, value: String) = error("unused")
        override fun setex(key: String, seconds: Long, value: String) = error("unused")
        override fun del(vararg keys: String): Long = error("unused")
        override fun exists(key: String): Long = error("unused")
        override fun expire(key: String, seconds: Long): Boolean = error("unused")
        override fun ttl(key: String): Long = error("unused")
        override fun keys(pattern: String): List<String> = error("unused")
        override fun flushdb() = error("unused")
        override fun ping(): String = error("unused")
    }

    @Suppress("UNCHECKED_CAST")
    private fun proxyCommands(
        get: (String) -> String?,
        set: (String, String) -> String,
        setex: (String, Long, String) -> String,
        del: (Array<out String>) -> Long,
        exists: (String) -> Long,
        expire: (String, Long) -> Boolean,
        ttl: (String) -> Long,
        keys: (String) -> List<String>,
        flushdb: () -> String,
        ping: () -> String,
    ): RedisCommands<String, String> {
        return Proxy.newProxyInstance(
            RedisCommands::class.java.classLoader,
            arrayOf(RedisCommands::class.java),
        ) { _, method, args ->
            when (method.name) {
                "get" -> get(args!![0] as String)
                "set" -> set(args!![0] as String, args[1] as String)
                "setex" -> setex(args!![0] as String, args[1] as Long, args[2] as String)
                "del" -> {
                    val keysArg = args!![0]
                    val arr = when (keysArg) {
                        is Array<*> -> keysArg.map { it as String }.toTypedArray()
                        is String -> arrayOf(keysArg)
                        else -> emptyArray()
                    }
                    del(arr)
                }
                "exists" -> {
                    // Lettuce exists is exists(K... keys)
                    val keysArg = args!![0]
                    when (keysArg) {
                        is Array<*> -> exists(keysArg[0] as String)
                        is String -> exists(keysArg)
                        else -> exists("")
                    }
                }
                "expire" -> expire(args!![0] as String, args[1] as Long)
                "ttl" -> ttl(args!![0] as String)
                "keys" -> keys(args!![0] as String)
                "flushdb" -> flushdb()
                "ping" -> ping()
                else -> null
            }
        } as RedisCommands<String, String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun proxyConnection(
        commands: RedisCommands<String, String>,
        onClose: () -> Unit,
    ): StatefulRedisConnection<String, String> {
        return Proxy.newProxyInstance(
            StatefulRedisConnection::class.java.classLoader,
            arrayOf(StatefulRedisConnection::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "sync" -> commands
                "close" -> {
                    onClose()
                    null
                }
                "isOpen" -> true
                else -> null
            }
        } as StatefulRedisConnection<String, String>
    }
}
