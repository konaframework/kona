package org.konaframework.redis

import io.lettuce.core.RedisURI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RedisCoverageCloseTest {
    @Test
    fun keysPatternEscapesAndExpiredEntry() {
        var now = Instant.parse("2024-01-01T00:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        }
        val redis = InMemoryRedisConnection(clock)
        redis.set("a(b)[c]{d}+e^f\$g|h", "1")
        redis.set("plain", "2")
        redis.set("q1", "3")
        assertEquals(listOf("a(b)[c]{d}+e^f\$g|h"), redis.keys("a(b)[c]{d}+e^f\$g|h"))
        assertEquals(listOf("plain"), redis.keys("plain"))
        assertEquals(listOf("q1"), redis.keys("q?"))

        redis.setex("exp", 1, "x")
        now = now.plusSeconds(2)
        // expired entry still present until touched; keys should skip it
        assertEquals(listOf("a(b)[c]{d}+e^f\$g|h", "plain", "q1"), redis.keys("*"))
    }

    @Test
    fun disconnectMissingAndConnectOverloads() {
        val manager = RedisManager(org.konaframework.core.Config().apply { set("redis.client", "memory") })
        manager.disconnect("never-created")
        manager.disconnectAll()
        val conn = manager.connection("default")
        manager.disconnect("default")
        manager.disconnect("default")
        assertNull(manager.connection().get("x"))
        assertTrue(conn !== manager.connection())

        val fake = LettuceRedisConnection(object : RedisSync {
            override fun get(key: String) = null
            override fun set(key: String, value: String) {}
            override fun setex(key: String, seconds: Long, value: String) {}
            override fun del(vararg keys: String) = 0L
            override fun exists(key: String) = 0L
            override fun expire(key: String, seconds: Long) = false
            override fun ttl(key: String) = -2L
            override fun keys(pattern: String) = emptyList<String>()
            override fun flushdb() {}
            override fun ping() = "PONG"
        }) {}
        assertEquals(
            fake,
            LettuceRedisConnection.open(RedisURI.create("redis://localhost")) { fake },
        )
        val previous = LettuceRedisConnection.liveOpener
        try {
            LettuceRedisConnection.liveOpener = { fake }
            assertEquals(fake, LettuceRedisConnection.connect("redis://localhost"))
            assertEquals(fake, LettuceRedisConnection.connect("localhost", 6379))
            assertEquals(fake, LettuceRedisConnection.connect("localhost", 6379, 1))
        } finally {
            LettuceRedisConnection.liveOpener = previous
        }
        assertThrows(Exception::class.java) {
            LettuceRedisConnection.connect("redis://127.0.0.1:1")
        }
    }
}
