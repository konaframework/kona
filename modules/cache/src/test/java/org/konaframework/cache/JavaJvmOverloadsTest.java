package org.konaframework.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.konaframework.core.Application;
import org.konaframework.core.Config;
import org.konaframework.redis.InMemoryRedisConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaJvmOverloadsTest {
    @Test
    void defaultConstructorsAndMissingExpires(@TempDir Path base) throws Exception {
        Config config = new Config();
        config.set("cache.default", "array");
        CacheManager manager = new CacheManager(config, base);
        manager.store().put("a", 1, 10);
        assertEquals(1, manager.store().get("a"));

        Path dir = base.resolve("files");
        FileCacheStore files = new FileCacheStore(dir);
        files.forever("k", "v");
        assertEquals("v", files.get("k"));
        FileCacheStore withClock = new FileCacheStore(dir, Clock.systemUTC());
        assertEquals("v", withClock.get("k"));
        FileCacheStore withMapper = new FileCacheStore(dir, Clock.systemUTC(), new ObjectMapper());
        assertEquals("v", withMapper.get("k"));

        Files.writeString(dir.resolve("noexp.json"), "{\"value\":\"x\"}");
        assertEquals("x", files.get("noexp"));

        RedisCacheStore redis = new RedisCacheStore(new InMemoryRedisConnection());
        redis.put("r", "1", 30);
        assertEquals("1", redis.get("r"));
        RedisCacheStore prefixed = new RedisCacheStore(new InMemoryRedisConnection(), "p:");
        prefixed.forever("z", "9");
        assertEquals("9", prefixed.get("z"));

        Application app = new Application(base.resolve("app"));
        app.getConfig().set("cache.default", "array");
        app.register(new CacheServiceProvider());
        assertNull(app.resolve(Repository.class).get("missing"));
    }
}
