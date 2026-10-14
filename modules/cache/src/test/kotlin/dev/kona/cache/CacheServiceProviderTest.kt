package dev.kona.cache

import dev.kona.core.Application
import dev.kona.redis.RedisServiceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CacheServiceProviderTest {
    @Test
    fun registersWithAndWithoutRedis(@TempDir base: Path) {
        val withoutRedis = Application(base)
        withoutRedis.config.set("cache.default", "array")
        withoutRedis.register(CacheServiceProvider())
        val manager = withoutRedis.resolve(CacheManager::class.java)
        val repo = withoutRedis.resolve(Repository::class.java)
        assertEquals(manager.store(), repo)
        repo.put("a", "1", 30)
        assertEquals("1", repo.get("a"))

        val withRedis = Application(base.resolve("with-redis"))
        withRedis.config.set("redis.client", "memory")
        withRedis.config.set("cache.default", "redis")
        withRedis.config.set("cache.stores.redis.driver", "redis")
        withRedis.register(RedisServiceProvider())
        withRedis.register(CacheServiceProvider())
        assertNotNull(withRedis.resolve(CacheManager::class.java).store("redis"))
    }
}
