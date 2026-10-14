package dev.kona.security

import dev.kona.http.HttpException
import dev.kona.http.Request
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Named rate limiters (Laravel RateLimiter shape).
 */
data class RateLimit @JvmOverloads constructor(
    val maxAttempts: Int,
    val decaySeconds: Int = 60,
)

fun interface RateLimitCallback {
    fun limit(request: Request): RateLimit
}

class RateLimitExceededException @JvmOverloads constructor(
    val retryAfterSeconds: Int,
    message: String = "Too Many Attempts.",
) : HttpException(429, message, mapOf("Retry-After" to retryAfterSeconds.toString()))

class RateLimiter @JvmOverloads constructor(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val limiters = ConcurrentHashMap<String, RateLimitCallback>()
    private val hits = ConcurrentHashMap<String, HitState>()

    fun `for`(name: String, callback: RateLimitCallback): RateLimiter {
        limiters[name] = callback
        return this
    }

    fun forName(name: String, callback: RateLimitCallback): RateLimiter = `for`(name, callback)

    fun limiter(name: String): RateLimitCallback? = limiters[name]

    @JvmOverloads
    fun attempt(key: String, maxAttempts: Int, decaySeconds: Int = 60): Boolean {
        if (tooManyAttempts(key, maxAttempts)) {
            return false
        }
        hit(key, decaySeconds)
        return true
    }

    fun tooManyAttempts(key: String, maxAttempts: Int): Boolean {
        val state = hits[normalized(key)] ?: return false
        val now = Instant.now(clock)
        if (!state.expiresAt.isAfter(now)) {
            hits.remove(normalized(key))
            return false
        }
        return state.count.get() >= maxAttempts
    }

    @JvmOverloads
    fun hit(key: String, decaySeconds: Int = 60): Int {
        val now = Instant.now(clock)
        val normalizedKey = normalized(key)
        val existing = hits[normalizedKey]
        if (existing == null) {
            val fresh = HitState(AtomicInteger(1), now.plusSeconds(decaySeconds.toLong()))
            hits[normalizedKey] = fresh
            return 1
        }
        if (!existing.expiresAt.isAfter(now)) {
            val fresh = HitState(AtomicInteger(1), now.plusSeconds(decaySeconds.toLong()))
            hits[normalizedKey] = fresh
            return 1
        }
        return existing.count.incrementAndGet()
    }

    fun retriesLeft(key: String, maxAttempts: Int): Int {
        val state = hits[normalized(key)] ?: return maxAttempts
        val now = Instant.now(clock)
        if (!state.expiresAt.isAfter(now)) {
            hits.remove(normalized(key))
            return maxAttempts
        }
        return (maxAttempts - state.count.get()).coerceAtLeast(0)
    }

    fun availableIn(key: String): Int {
        val state = hits[normalized(key)] ?: return 0
        val now = Instant.now(clock)
        val seconds = state.expiresAt.epochSecond - now.epochSecond
        return seconds.toInt().coerceAtLeast(0)
    }

    fun clear(key: String) {
        hits.remove(normalized(key))
    }

    fun clearAll() {
        hits.clear()
    }

    fun resolve(name: String, request: Request): RateLimit {
        val callback = limiters[name]
            ?: throw IllegalArgumentException("Rate limiter [$name] is not defined")
        return callback.limit(request)
    }

    private fun normalized(key: String): String = key

    private data class HitState(
        val count: AtomicInteger,
        val expiresAt: Instant,
    )
}
