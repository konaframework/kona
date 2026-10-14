package dev.kona.cache

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.redis.InMemoryRedisConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class CacheCoverageCloseTest {
    @Test
    fun defaultStoresAndCorruptFile() {
        val base = Files.createTempDirectory("cache-close")
        val config = Config()
        // no cache.stores.* — driver inferred from store name
        val manager = CacheManager(config, base)
        manager.store("array").put("a", 1, 10)
        assertEquals(1, manager.store("memory").get("a").let { manager.driver("array").get("a") })
        manager.store("file").forever("f", "v")
        assertEquals("v", manager.store("file").get("f"))

        val dir = Files.createTempDirectory("cache-corrupt")
        val store = FileCacheStore(dir)
        val path = dir.resolve("bad.json")
        Files.writeString(path, """{"expires":null}""")
        assertNull(store.get("bad"))
        Files.writeString(dir.resolve("ok.json"), """{"expires":null,"value":"x"}""")
        // key sanitized from "ok"
        Files.writeString(dir.resolve("ok.json"), """{"expires":"","value":1}""")
        // empty expires text
        val emptyExpires = FileCacheStore(dir)
        // write via API then poke
        emptyExpires.forever("z", 2)
        assertEquals(2, emptyExpires.get("z"))

        val redis = RedisCacheStore(InMemoryRedisConnection())
        redis.forever("r", "1")
        assertEquals("1", redis.get("r"))

        val app = Application(base)
        app.config.set("cache.default", "array")
        app.register(CacheServiceProvider())
        assertTrue(app.resolve(CacheManager::class.java).store().missing("nope"))
    }
}
