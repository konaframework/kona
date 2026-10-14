package dev.kona.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kona.redis.RedisConnection

class RedisCacheStore @JvmOverloads constructor(
    private val redis: RedisConnection,
    private val prefix: String = "kona_cache:",
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : CacheStore {
    override fun get(key: String): Any? {
        val raw = redis.get(prefixed(key)) ?: return null
        return mapper.readValue(raw, Any::class.java)
    }

    override fun put(key: String, value: Any?, seconds: Long) {
        val payload = mapper.writeValueAsString(value)
        if (seconds <= 0) {
            redis.setex(prefixed(key), 1, payload)
        } else {
            redis.setex(prefixed(key), seconds, payload)
        }
    }

    override fun forever(key: String, value: Any?) {
        redis.set(prefixed(key), mapper.writeValueAsString(value))
    }

    override fun forget(key: String): Boolean = redis.del(prefixed(key)) > 0

    override fun flush() {
        val keys = redis.keys("$prefix*")
        if (keys.isNotEmpty()) {
            redis.del(*keys.toTypedArray())
        }
    }

    private fun prefixed(key: String): String = prefix + key
}
