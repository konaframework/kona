package hello

import dev.kona.cache.CacheManager
import dev.kona.core.Config
import dev.kona.redis.RedisManager
import dev.kona.support.collection.collect
import dev.kona.support.str.Str
import java.nio.file.Path

/** Exercises M4 support + cache surfaces for the hello sample. */
object SupportCacheDemo {
    fun run(base: Path): Map<String, Any?> {
        val config = Config()
        config.set("redis.client", "memory")
        config.set("cache.default", "array")
        val redis = RedisManager(config)
        val cache = CacheManager(config, base, redis).store()
        cache.put("demo", Str.slug("Hello Kona"), 60)
        val names = collect(mapOf("name" to "Ada"), mapOf("name" to "Grace")).pluck("name").toList()
        return mapOf(
            "slug" to cache.get("demo"),
            "names" to names,
            "redis" to redis.connection().ping(),
        )
    }
}
