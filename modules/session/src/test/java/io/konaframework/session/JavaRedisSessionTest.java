package io.konaframework.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.konaframework.redis.InMemoryRedisConnection;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaRedisSessionTest {
    @Test
    void redisSessionStoreFromJava() {
        InMemoryRedisConnection redis = new InMemoryRedisConnection();
        RedisSessionStore store = new RedisSessionStore(redis, "java_sess:");
        String id = SessionId.generate();
        assertTrue(store.read(id).isEmpty());
        store.write(id, Map.of("user", "ada"), 15);
        assertEquals("ada", store.read(id).get("user"));
        store.destroy(id);
        assertTrue(store.read(id).isEmpty());
    }
}
