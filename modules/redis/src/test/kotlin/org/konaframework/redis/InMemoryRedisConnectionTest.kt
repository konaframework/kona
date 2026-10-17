package org.konaframework.redis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InMemoryRedisConnectionTest {
    private val start = Instant.parse("2024-01-01T00:00:00Z")
    private var now = start
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
    }

    @Test
    fun setGetExpireTtlKeysAndPatterns() {
        val redis = InMemoryRedisConnection(clock)
        assertEquals("PONG", redis.ping())
        assertNull(redis.get("missing"))
        assertFalse(redis.exists("missing"))
        assertEquals(-2, redis.ttl("missing"))
        assertFalse(redis.expire("missing", 10))

        redis.set("a", "1")
        assertEquals("1", redis.get("a"))
        assertTrue(redis.exists("a"))
        assertEquals(-1, redis.ttl("a"))
        assertTrue(redis.expire("a", 5))
        assertEquals(5, redis.ttl("a"))

        redis.setex("b", 0, "2")
        assertNull(redis.get("b"))
        assertFalse(redis.exists("b"))
        redis.setex("b", 2, "2")
        assertEquals("2", redis.get("b"))
        now = start.plusSeconds(3)
        assertNull(redis.get("b"))
        assertFalse(redis.exists("b"))

        redis.setex("c", 10, "3")
        redis.set("dot.key", "x")
        redis.set("plain", "y")
        redis.set("question?", "z")
        assertEquals(listOf("a", "c", "dot.key", "plain", "question?"), redis.keys("*"))
        assertEquals(listOf("dot.key"), redis.keys("dot.*"))
        assertEquals(listOf("question?"), redis.keys("question?"))
        assertEquals(listOf("plain"), redis.keys("plai?"))
        assertEquals(emptyList<String>(), redis.keys("nope"))

        now = start.plusSeconds(20)
        assertEquals(-2, redis.ttl("c"))
        assertNull(redis.get("c"))

        redis.setex("d", 5, "v")
        now = start.plusSeconds(30)
        assertFalse(redis.expire("d", 10))
        assertNull(redis.get("d"))

        redis.set("keep", "1")
        redis.set("drop", "2")
        assertEquals(0, redis.del())
        assertEquals(1, redis.del("drop", "missing"))
        assertEquals("1", redis.get("keep"))

        redis.flushDb()
        assertEquals(emptyList<String>(), redis.keys("*"))
        redis.set("x", "1")
        redis.close()
        assertNull(redis.get("x"))
    }

    @Test
    fun expiredKeysFilteredFromKeys() {
        val redis = InMemoryRedisConnection(clock)
        now = start
        redis.setex("gone", 1, "1")
        redis.set("live", "2")
        now = start.plusSeconds(2)
        assertEquals(listOf("live"), redis.keys("*"))
    }
}
