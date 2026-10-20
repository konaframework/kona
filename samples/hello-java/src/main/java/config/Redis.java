package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis connections.
 *
 * <p>Kona reads Redis from the top-level {@code redis.*} config (not nested under
 * {@code database.redis}). Default client is in-memory for zero-dependency local
 * development; set {@code REDIS_CLIENT=lettuce} to talk to a real Redis server.
 */
public final class Redis implements ConfigFile {
    @Override
    public String key() {
        return "redis";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("default", redisConnection(Env.getOrDefault("REDIS_DB", "0")));
        connections.put("cache", redisConnection(Env.getOrDefault("REDIS_CACHE_DB", "1")));

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Redis Connection
        |--------------------------------------------------------------------------
        |
        | This option controls which named connection RedisManager uses when
        | none is specified. Cache and queue typically pin their own names
        | (`cache`, `default`) so they do not collide with application keys.
        |
        */
        values.put("default", Env.getOrDefault("REDIS_CONNECTION", "default"));

        /*
        |--------------------------------------------------------------------------
        | Redis Client
        |--------------------------------------------------------------------------
        |
        | Supported: "memory" (in-process, default), "lettuce" (network Redis).
        |
        */
        values.put("client", Env.getOrDefault("REDIS_CLIENT", "memory"));

        /*
        |--------------------------------------------------------------------------
        | Redis Connections
        |--------------------------------------------------------------------------
        |
        | Each named connection may set {@code url} (takes precedence) or host /
        | port / database / password. {@code database} is the Redis logical DB
        | index.
        |
        */
        values.put("connections", connections);
        return values;
    }

    private static Map<String, Object> redisConnection(String database) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", Env.get("REDIS_URL", null));
        map.put("host", Env.getOrDefault("REDIS_HOST", "127.0.0.1"));
        map.put("username", Env.get("REDIS_USERNAME", null));
        map.put("password", Env.get("REDIS_PASSWORD", null));
        map.put("port", envInt("REDIS_PORT", 6379));
        map.put("database", database);
        return map;
    }

    private static int envInt(String key, int fallback) {
        try {
            return Integer.parseInt(Env.getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
