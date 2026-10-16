package io.konaframework.redis

import io.konaframework.core.Application
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RedisServiceProviderTest {
    @Test
    fun registersManagerAndDefaultConnection(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("redis.client", "memory")
        app.register(RedisServiceProvider())
        val manager = app.resolve(RedisManager::class.java)
        val connection = app.resolve(RedisConnection::class.java)
        assertSame(manager.connection(), connection)
        assertTrue(connection is InMemoryRedisConnection)
    }
}
