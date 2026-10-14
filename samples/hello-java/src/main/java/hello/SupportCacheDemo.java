package hello;

import dev.kona.cache.CacheManager;
import dev.kona.cache.Repository;
import dev.kona.core.Config;
import dev.kona.redis.RedisManager;
import dev.kona.support.collection.Collection;
import dev.kona.support.str.Str;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exercises M4 support + cache surfaces for the Java hello sample. */
public final class SupportCacheDemo {
    private SupportCacheDemo() {}

    public static Map<String, Object> run(Path base) {
        Config config = new Config();
        config.set("redis.client", "memory");
        config.set("cache.default", "array");
        RedisManager redis = new RedisManager(config);
        Repository cache = new CacheManager(config, base, redis).store();
        cache.put("demo", Str.slug("Hello Kona"), 60);
        List<?> names = Collection.of(List.of(
            Map.of("name", "Ada"),
            Map.of("name", "Grace")
        )).pluck("name").toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("slug", cache.get("demo"));
        out.put("names", names);
        out.put("redis", redis.connection().ping());
        return out;
    }
}
