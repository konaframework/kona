package org.konaframework.auth.twofactor

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits) with Google Authenticator–compatible
 * Base32 secrets — same shape as Fortify / `pragmarx/google2fa`.
 */
object Totp {
    const val DEFAULT_PERIOD_SECONDS: Long = 30
    const val DEFAULT_DIGITS: Int = 6
    const val DEFAULT_SECRET_BYTES: Int = 20
    const val DEFAULT_WINDOW: Int = 1

    private val random = SecureRandom()
    private val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray()

    /** Generate a new Base32-encoded secret ([byteLength] random bytes, default 20 → 32 chars). */
    @JvmStatic
    @JvmOverloads
    fun generateSecret(byteLength: Int = DEFAULT_SECRET_BYTES): String {
        require(byteLength > 0) { "byteLength must be positive" }
        val bytes = ByteArray(byteLength).also { random.nextBytes(it) }
        return encodeBase32(bytes)
    }

    /**
     * Verify [code] against [secret] for the current (or [atEpochSeconds]) time step,
     * accepting [window] steps of clock skew on each side. Returns the matching time-step
     * counter when valid, or null.
     */
    @JvmStatic
    @JvmOverloads
    fun verify(
        secret: String,
        code: String,
        atEpochSeconds: Long = System.currentTimeMillis() / 1000,
        window: Int = DEFAULT_WINDOW,
        periodSeconds: Long = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS,
    ): Long? {
        val trimmed = code.trim().replace(" ", "")
        if (trimmed.length != digits || !trimmed.all { it.isDigit() }) {
            return null
        }
        val key = decodeBase32(secret) ?: return null
        val counter = atEpochSeconds / periodSeconds
        for (offset in -window..window) {
            val candidate = counter + offset
            if (candidate < 0) continue
            if (hotp(key, candidate, digits) == trimmed) {
                return candidate
            }
        }
        return null
    }

    /** Current TOTP code for [secret] (tests / debugging). */
    @JvmStatic
    @JvmOverloads
    fun code(
        secret: String,
        atEpochSeconds: Long = System.currentTimeMillis() / 1000,
        periodSeconds: Long = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS,
    ): String {
        val key = decodeBase32(secret) ?: throw IllegalArgumentException("Invalid Base32 secret")
        return hotp(key, atEpochSeconds / periodSeconds, digits)
    }

    /**
     * `otpauth://totp/…` URL for authenticator apps
     * (`otpauth://totp/{issuer}:{account}?secret=…&issuer=…`).
     */
    @JvmStatic
    @JvmOverloads
    fun otpAuthUrl(
        issuer: String,
        account: String,
        secret: String,
        digits: Int = DEFAULT_DIGITS,
        periodSeconds: Long = DEFAULT_PERIOD_SECONDS,
    ): String {
        val label = encodeLabel(issuer, account)
        val query =
            "secret=${secret.trim()}&issuer=${percentEncode(issuer)}" +
                "&algorithm=SHA1&digits=$digits&period=$periodSeconds"
        return "otpauth://totp/$label?$query"
    }

    internal fun hotp(key: ByteArray, counter: Long, digits: Int): String {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        val otp = binary % 10.0.pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }

    internal fun encodeBase32(data: ByteArray): String {
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                out.append(BASE32_ALPHABET[(buffer shr bitsLeft) and 0x1f])
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
        }
        return out.toString()
    }

    internal fun decodeBase32(encoded: String): ByteArray? {
        val cleaned = encoded.trim().uppercase().replace("=", "").replace(" ", "")
        if (cleaned.isEmpty() || cleaned.any { it !in BASE32_ALPHABET }) {
            return null
        }
        val out = ArrayList<Byte>(cleaned.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in cleaned) {
            val value = BASE32_ALPHABET.indexOf(ch)
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun encodeLabel(issuer: String, account: String): String =
        "${percentEncode(issuer)}:${percentEncode(account)}"

    private fun percentEncode(value: String): String =
        buildString(value.length + 8) {
            for (ch in value) {
                when {
                    ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' || ch == '~' -> append(ch)
                    else -> {
                        val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                        for (b in bytes) {
                            append('%')
                            append(((b.toInt() shr 4) and 0xf).toString(16).uppercase())
                            append((b.toInt() and 0xf).toString(16).uppercase())
                        }
                    }
                }
            }
        }
}
