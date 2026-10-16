package io.konaframework.queue

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** In-process rate limiter for jobs. */
object JobRateLimiter {
    private data class Hit(val count: AtomicInteger, var expiresAt: Long)

    private val hits = ConcurrentHashMap<String, Hit>()

    @JvmStatic
    @JvmOverloads
    fun attempt(key: String, maxAttempts: Int, decaySeconds: Int = 60): Boolean {
        if (tooManyAttempts(key, maxAttempts)) return false
        hit(key, decaySeconds)
        return true
    }

    @JvmStatic
    fun tooManyAttempts(key: String, maxAttempts: Int): Boolean {
        val state = hits[key] ?: return false
        val now = System.currentTimeMillis()
        if (state.expiresAt <= now) {
            hits.remove(key)
            return false
        }
        return state.count.get() >= maxAttempts
    }

    @JvmStatic
    @JvmOverloads
    fun hit(key: String, decaySeconds: Int = 60): Int {
        val now = System.currentTimeMillis()
        val existing = hits[key]
        if (existing == null || existing.expiresAt <= now) {
            hits[key] = Hit(AtomicInteger(1), now + decaySeconds.coerceAtLeast(1) * 1000L)
            return 1
        }
        return existing.count.incrementAndGet()
    }

    @JvmStatic
    fun clear() {
        hits.clear()
    }
}

/** Middleware that releases the job when the rate limit is exceeded. */
class RateLimited @JvmOverloads constructor(
    private val maxAttempts: Int,
    private val decaySeconds: Int = 60,
    private val key: String = "default",
) : JobMiddleware {
    override fun handle(job: Job, next: Runnable) {
        val limiterKey = "$key:${job.javaClass.name}"
        if (!JobRateLimiter.attempt(limiterKey, maxAttempts, decaySeconds)) {
            throw ReleaseJobException(delaySeconds = decaySeconds.toLong(), message = "Rate limited")
        }
        next.run()
    }
}
