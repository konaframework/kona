package io.konaframework.redis

import io.konaframework.core.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedisManagerTest {
    @Test
    fun memoryClientCachedAndDisconnected() {
        val config = Config().apply {
            set("redis.default", "main")
            set("redis.client", "memory")
        }
        val manager = RedisManager(config)
        assertEquals("main", manager.defaultName())
        val first = manager.connection()
        val again = manager.connection("main")
        assertSame(first, again)
        assertTrue(first is InMemoryRedisConnection)
        first.set("k", "v")
        assertEquals("v", again.get("k"))
        manager.disconnect()
        val fresh = manager.connection()
        assertNullSafe(fresh.get("k"))
        fresh.set("a", "1")
        manager.disconnectAll()
        assertNullSafe(manager.connection().get("a"))
    }

    @Test
    fun arrayAliasUsesMemory() {
        val manager = RedisManager(Config().apply { set("redis.client", "array") })
        assertTrue(manager.connection() is InMemoryRedisConnection)
    }

    @Test
    fun lettuceUrlPathUsesConnect() {
        val config = Config().apply {
            set("redis.client", "lettuce")
            set("redis.connections.default.url", "://bad")
        }
        val manager = RedisManager(config)
        assertThrows(Exception::class.java) { manager.connection() }
    }

    @Test
    fun lettuceHostPortPathUsesConnect() {
        val config = Config().apply {
            set("redis.client", "lettuce")
            set("redis.connections.default.host", "127.0.0.1")
            set("redis.connections.default.port", 1)
            set("redis.connections.default.database", 2)
        }
        val manager = RedisManager(config)
        assertThrows(Exception::class.java) { manager.connection() }
    }

    private fun assertNullSafe(value: String?) {
        org.junit.jupiter.api.Assertions.assertNull(value)
    }
}
