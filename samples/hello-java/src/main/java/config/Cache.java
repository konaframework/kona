package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache stores ({@code config/cache.php} shape).
 *
 * <p>Supported drivers: {@code array} / {@code memory}, {@code file}, {@code redis}.
 */
public final class Cache implements ConfigFile {
    @Override
    public String key() {
        return "cache";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("driver", "array");

        Map<String, Object> file = new LinkedHashMap<>();
        file.put("driver", "file");
        file.put("path", Env.getOrDefault("CACHE_PATH", "storage/framework/cache/data"));

        Map<String, Object> redis = new LinkedHashMap<>();
        redis.put("driver", "redis");
        redis.put("connection", Env.getOrDefault("REDIS_CACHE_CONNECTION", "cache"));

        Map<String, Object> stores = new LinkedHashMap<>();
        stores.put("array", array);
        stores.put("file", file);
        stores.put("redis", redis);

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Cache Store
        |--------------------------------------------------------------------------
        |
        | This option controls the default cache store that will be used by the
        | framework. This connection is utilized if another isn't explicitly
        | specified when running a cache operation inside the application.
        |
        */
        values.put("default", Env.getOrDefault("CACHE_STORE", "array"));

        /*
        |--------------------------------------------------------------------------
        | Cache Stores
        |--------------------------------------------------------------------------
        |
        | Here you may define all of the cache "stores" for your application as
        | well as their drivers. You may even define multiple stores for the
        | same cache driver to group types of items stored in your caches.
        |
        | Supported drivers: "array", "memory", "file", "redis"
        |
        */
        values.put("stores", stores);

        /*
        |--------------------------------------------------------------------------
        | Cache Key Prefix
        |--------------------------------------------------------------------------
        |
        | When utilizing Redis (or any shared cache), there might be other
        | applications using the same store. Prefix every cache key to avoid
        | collisions.
        |
        */
        values.put("prefix", Env.getOrDefault("CACHE_PREFIX", "kona_cache:"));
        return values;
    }
}
