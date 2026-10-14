package dev.kona.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kona.redis.RedisConnection

/**
 * Redis-backed session store (Laravel `redis` session driver).
 */
class RedisSessionStore @JvmOverloads constructor(
    private val redis: RedisConnection,
    private val prefix: String = "kona_session:",
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : SessionStore {
    override fun read(id: String): Map<String, Any?> {
        val raw = redis.get(prefixed(id)) ?: return emptyMap()
        return mapper.readValue(raw)
    }

    override fun write(id: String, data: Map<String, Any?>, lifetimeMinutes: Int) {
        val payload = mapper.writeValueAsString(data)
        val seconds = lifetimeMinutes.toLong().coerceAtLeast(1) * 60L
        redis.setex(prefixed(id), seconds, payload)
    }

    override fun destroy(id: String) {
        redis.del(prefixed(id))
    }

    private fun prefixed(id: String): String = prefix + id
}
