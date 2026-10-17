package org.konaframework.redis

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceConnectSuccessTest {
    private val fake = LettuceRedisConnection(object : RedisSync {
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

    @BeforeEach
    fun installOpener() {
        LettuceRedisConnection.liveOpener = { fake }
    }

    @AfterEach
    fun restoreOpener() {
        LettuceRedisConnection.liveOpener = LettuceRedisConnection::openLive
    }

    @Test
    fun connectOverloadsSucceedViaHook() {
        assertSame(fake, LettuceRedisConnection.connect("redis://localhost:6379/0"))
        assertSame(fake, LettuceRedisConnection.connect("127.0.0.1", 6379))
        assertSame(fake, LettuceRedisConnection.connect("127.0.0.1", 6379, 1))
        assertEquals("PONG", LettuceRedisConnection.connect("redis://localhost").ping())
    }
}
