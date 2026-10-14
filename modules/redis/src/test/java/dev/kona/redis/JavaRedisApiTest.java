package dev.kona.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import dev.kona.core.Config;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaRedisApiTest {
    @Test
    void inMemoryManagerAndProviderFromJava(@TempDir Path base) {
        InMemoryRedisConnection redis = new InMemoryRedisConnection();
        redis.set("k", "v");
        assertEquals("v", redis.get("k"));
        assertEquals("PONG", redis.ping());
        redis.setex("t", 30, "x");
        assertTrue(redis.ttl("t") > 0);
        assertEquals(1L, redis.del("t"));
        redis.flushDb();

        Config config = new Config();
        config.set("redis.client", "memory");
        RedisManager manager = new RedisManager(config);
        assertTrue(manager.connection() instanceof InMemoryRedisConnection);

        Application app = new Application(base);
        app.getConfig().set("redis.client", "memory");
        app.register(new RedisServiceProvider());
        assertEquals("PONG", app.resolve(RedisConnection.class).ping());

        assertThrows(Exception.class, () -> LettuceRedisConnection.connect("://bad-uri"));
        assertThrows(Exception.class, () -> LettuceRedisConnection.connect("127.0.0.1", 1));
        assertThrows(Exception.class, () -> LettuceRedisConnection.connect("127.0.0.1", 1, 0));
    }

    @Test
    void staticConnectForwardersSucceedWithStubOpener() {
        LettuceRedisConnection fake = LettuceTestSupport.fakeConnection();
        LettuceTestSupport.withOpener(
                fake,
                () -> {
                    assertSame(fake, LettuceRedisConnection.connect("redis://localhost"));
                    assertSame(fake, LettuceRedisConnection.connect("localhost", 6379));
                    assertSame(fake, LettuceRedisConnection.connect("localhost", 6379, 0));
                });
    }
}
