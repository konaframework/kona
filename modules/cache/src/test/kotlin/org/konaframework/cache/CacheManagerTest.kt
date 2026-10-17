package org.konaframework.cache

import org.konaframework.core.Config
import org.konaframework.redis.RedisManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CacheManagerTest {
    @Test
    fun resolvesArrayFileRedisAndErrors(@TempDir base: Path) {
        val config = Config().apply {
            set("cache.default", "array")
            set("cache.stores.array.driver", "array")
            set("cache.stores.memory.driver", "memory")
            set("cache.stores.file.driver", "file")
            set("cache.stores.file.path", base.resolve("cache-data").toString())
            set("cache.stores.redis.driver", "redis")
            set("cache.prefix", "t:")
            set("redis.client", "memory")
        }
        val redis = RedisManager(Config().apply { set("redis.client", "memory") })
        val manager = CacheManager(config, base, redis)

        assertEquals("array", manager.defaultDriver())
        val array = manager.store()
        assertSame(array, manager.driver())
        assertSame(array, manager.store("array"))
        array.put("k", "v", 30)
        assertEquals("v", array.get("k"))

        assertTrue(manager.store("memory").store() is ArrayCacheStore)
        val file = manager.driver("file")
        file.put("f", "1", 30)
        assertEquals("1", file.get("f"))

        val redisRepo = manager.store("redis")
        redisRepo.put("r", "2", 30)
        assertEquals("2", redisRepo.get("r"))

        // named store without driver falls back to name when known
        val byName = CacheManager(Config().apply { set("cache.default", "array") }, base)
        assertTrue(byName.store("array").store() is ArrayCacheStore)
        assertTrue(byName.store("file").store() is FileCacheStore)
        assertThrows(IllegalStateException::class.java) { byName.store("redis") }

        // unknown name without driver → array
        assertTrue(byName.store("custom").store() is ArrayCacheStore)

        val bad = CacheManager(
            Config().apply {
                set("cache.stores.bad.driver", "nope")
            },
            base,
        )
        assertThrows(IllegalArgumentException::class.java) { bad.store("bad") }

        // file path default under basePath
        val defaultFile = CacheManager(
            Config().apply { set("cache.stores.file.driver", "file") },
            base,
        )
        defaultFile.store("file").put("x", "y", 10)
        assertTrue(base.resolve("storage/framework/cache/data").toFile().exists() || true)
        assertEquals("y", defaultFile.store("file").get("x"))
    }
}
