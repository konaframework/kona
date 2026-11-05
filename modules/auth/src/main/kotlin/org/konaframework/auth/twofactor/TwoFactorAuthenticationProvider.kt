package org.konaframework.auth.twofactor

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * TOTP helper services (Laravel Fortify `TwoFactorAuthenticationProvider`).
 *
 * Successfully used codes are remembered for one window so the same OTP cannot be replayed
 * (in-memory by default; swap [usedCodes] for a distributed store in multi-node deploys).
 */
class TwoFactorAuthenticationProvider @JvmOverloads constructor(
    private val window: Int = Totp.DEFAULT_WINDOW,
    private val periodSeconds: Long = Totp.DEFAULT_PERIOD_SECONDS,
    private val usedCodes: UsedCodeStore = InMemoryUsedCodeStore(),
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Generate a new Base32 secret ([secretLength] random bytes). */
    @JvmOverloads
    fun generateSecretKey(secretLength: Int = Totp.DEFAULT_SECRET_BYTES): String =
        Totp.generateSecret(secretLength)

    /** Authenticator `otpauth://` URL for [secret]. */
    fun qrCodeUrl(companyName: String, companyEmail: String, secret: String): String =
        Totp.otpAuthUrl(companyName, companyEmail, secret)

    /**
     * Verify [code] against the plain [secret]. Returns true when valid and not previously
     * accepted in this window.
     */
    fun verify(secret: String, code: String): Boolean {
        val trimmed = code.trim().replace(" ", "")
        val cacheKey = "kona.2fa_codes." + md5(trimmed)
        val newerThan = usedCodes.get(cacheKey)
        val matched = Totp.verify(
            secret = secret,
            code = trimmed,
            atEpochSeconds = Instant.now(clock).epochSecond,
            window = window,
            periodSeconds = periodSeconds,
        ) ?: return false
        if (newerThan != null && matched <= newerThan) {
            return false
        }
        usedCodes.put(cacheKey, matched, (window.coerceAtLeast(1)) * periodSeconds)
        return true
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}

/** Remembers accepted TOTP counters to prevent replay. */
interface UsedCodeStore {
    fun get(key: String): Long?

    fun put(key: String, counter: Long, seconds: Long)
}

/** Process-local [UsedCodeStore]. */
class InMemoryUsedCodeStore @JvmOverloads constructor(
    private val clock: Clock = Clock.systemUTC(),
) : UsedCodeStore {
    private val entries = ConcurrentHashMap<String, Entry>()

    override fun get(key: String): Long? {
        val entry = entries[key] ?: return null
        if (!entry.expiresAt.isAfter(Instant.now(clock))) {
            entries.remove(key)
            return null
        }
        return entry.counter
    }

    override fun put(key: String, counter: Long, seconds: Long) {
        entries[key] = Entry(counter, Instant.now(clock).plusSeconds(seconds))
    }

    fun clear() {
        entries.clear()
    }

    private data class Entry(val counter: Long, val expiresAt: Instant)
}
