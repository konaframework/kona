package dev.kona.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.core.Config;
import dev.kona.redis.InMemoryRedisConnection;
import dev.kona.redis.RedisManager;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaCacheApiTest {
    @Test
    void storesRepositoryAndManagerFromJava(@TempDir Path base) {
        ArrayCacheStore array = new ArrayCacheStore();
        Repository repo = new Repository(array);
        assertTrue(repo.put("a", 1, 60));
        assertEquals(1, repo.get("a"));
        assertEquals(2, repo.get("missing", 2));
        assertEquals(1, repo.remember("a", 60, () -> 99));
        assertEquals(5, repo.rememberForever("b", () -> 5));
        assertEquals(1L, repo.increment("counter"));
        assertEquals(0L, repo.decrement("counter"));
        assertEquals(5L, repo.increment("counter", 5));
        assertEquals(3L, repo.decrement("counter", 2));

        FileCacheStore files = new FileCacheStore(base.resolve("f"));
        files.forever("k", Map.of("n", 3));
        assertEquals(3, ((Number) ((Map<?, ?>) files.get("k")).get("n")).intValue());

        RedisCacheStore redis = new RedisCacheStore(new InMemoryRedisConnection());
        redis.put("r", "v", 30);
        assertEquals("v", redis.get("r"));
        RedisCacheStore redisPrefixed = new RedisCacheStore(new InMemoryRedisConnection(), "j:");
        redisPrefixed.forever("p", "1");
        assertEquals("1", redisPrefixed.get("p"));

        Config config = new Config();
        config.set("cache.default", "array");
        config.set("cache.stores.array.driver", "array");
        CacheManager manager = new CacheManager(config, base, new RedisManager(new Config()));
        assertEquals("array", manager.defaultDriver());
        manager.store().put("x", "y", 10);
        assertEquals("y", manager.driver().get("x"));

        Application app = new Application(base.resolve("app"));
        app.getConfig().set("cache.default", "array");
        app.register(new CacheServiceProvider());
        assertEquals(null, app.resolve(Repository.class).get("none"));
    }
}
