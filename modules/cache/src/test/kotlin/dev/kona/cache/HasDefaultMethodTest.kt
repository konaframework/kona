package dev.kona.cache

import dev.kona.redis.InMemoryRedisConnection
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HasDefaultMethodTest {
    @Test
    fun interfaceHasOnFileAndRedis(@TempDir dir: Path) {
        val file = FileCacheStore(dir)
        assertFalse(file.has("missing"))
        file.forever("k", "v")
        assertTrue(file.has("k"))

        val redis = RedisCacheStore(InMemoryRedisConnection())
        assertFalse(redis.has("missing"))
        redis.forever("k", "v")
        assertTrue(redis.has("k"))
    }
}
