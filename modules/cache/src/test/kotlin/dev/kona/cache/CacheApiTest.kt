package dev.kona.cache

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.redis.InMemoryRedisConnection
import dev.kona.redis.RedisManager
import dev.kona.redis.RedisServiceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CacheApiTest {
    @Test
    fun arrayFileRedisStoresAndRepository() {
        val clock = object : Clock() {
            private var instant = Instant.parse("2020-01-01T00:00:00Z")
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = instant
            fun advance(seconds: Long) {
                instant = instant.plusSeconds(seconds)
            }
        }
        val array = ArrayCacheStore(clock)
        array.put("a", "1", 5)
        assertEquals("1", array.get("a"))
        assertTrue(array.has("a"))
        clock.advance(6)
        assertNull(array.get("a"))
        array.put("b", "2", 0)
        assertNull(array.get("b"))
        array.forever("c", "3")
        assertEquals("3", array.get("c"))
        assertTrue(array.forget("c"))
        assertFalse(array.forget("c"))
        array.forever("d", "4")
        array.flush()
        assertNull(array.get("d"))

        val dir = Files.createTempDirectory("kona-cache-file")
        val file = FileCacheStore(dir, clock)
        file.put("k", mapOf("x" to 1), 10)
        assertEquals(mapOf("x" to 1), file.get("k"))
        clock.advance(11)
        assertNull(file.get("k"))
        file.put("z", "gone", 0)
        assertNull(file.get("z"))
        file.forever("f", "forever")
        assertEquals("forever", file.get("f"))
        assertTrue(file.forget("f"))
        file.forever("g", 1)
        file.flush()
        assertNull(file.get("g"))
        file.flush()

        val redis = RedisCacheStore(InMemoryRedisConnection(), "p:")
        redis.put("a", "1", 30)
        assertEquals("1", redis.get("a"))
        redis.put("b", "2", 0)
        assertNotNull(redis.get("b"))
        redis.forever("c", listOf(1, 2))
        assertEquals(listOf(1, 2), redis.get("c"))
        assertTrue(redis.forget("c"))
        redis.forever("d", "x")
        redis.flush()
        assertNull(redis.get("d"))
        assertNull(redis.get("missing"))

        val repo = Repository(ArrayCacheStore())
        assertTrue(repo.put("k", "v", 60))
        assertEquals("v", repo.get("k"))
        assertEquals("v", repo.get("k", "d"))
        assertEquals("d", repo.get("missing", "d"))
        assertTrue(repo.has("k"))
        assertTrue(repo.missing("no"))
        assertFalse(repo.add("k", "x", 10))
        assertTrue(repo.add("n", "y", 10))
        assertEquals("computed", repo.remember("r", 10) { "computed" })
        assertEquals("computed", repo.remember("r", 10) { "other" })
        assertEquals("forever", repo.rememberForever("rf") { "forever" })
        assertEquals("forever", repo.rememberForever("rf") { "nope" })
        assertEquals("y", repo.pull("n"))
        assertNull(repo.get("n"))
        assertTrue(repo.forever("z", 1))
        assertTrue(repo.forget("z"))
        assertTrue(repo.flush())
        assertNotNull(repo.store())
    }

    @Test
    fun cacheManagerAndProvider() {
        val base = Files.createTempDirectory("kona-cache-mgr")
        val config = Config()
        config.set("cache.default", "array")
        val manager = CacheManager(config, base)
        assertEquals("array", manager.defaultDriver())
        assertEquals(manager.store(), manager.driver())
        manager.store().put("a", 1, 10)
        assertEquals(1, manager.driver("array").get("a"))

        config.set("cache.stores.file.driver", "file")
        val fileRepo = manager.store("file")
        fileRepo.put("f", "1", 20)
        assertEquals("1", fileRepo.get("f"))

        config.set("cache.stores.custom.driver", "memory")
        assertNotNull(manager.store("custom").store())

        assertThrows(IllegalArgumentException::class.java) {
            CacheManager(Config().also { it.set("cache.stores.bad.driver", "nope") }, base).store("bad")
        }
        assertThrows(IllegalStateException::class.java) {
            CacheManager(Config().also { it.set("cache.stores.redis.driver", "redis") }, base).store("redis")
        }

        val redisConfig = Config()
        redisConfig.set("redis.client", "memory")
        redisConfig.set("cache.stores.redis.driver", "redis")
        redisConfig.set("cache.prefix", "t:")
        val withRedis = CacheManager(redisConfig, base, RedisManager(redisConfig))
        withRedis.store("redis").put("k", "v", 30)
        assertEquals("v", withRedis.driver("redis").get("k"))

        val app = Application(base)
        app.config.set("redis.client", "memory")
        app.config.set("cache.default", "array")
        app.register(RedisServiceProvider())
        app.register(CacheServiceProvider())
        assertNotNull(app.resolve(CacheManager::class.java).store())
        assertNotNull(app.resolve(Repository::class.java))
    }
}
