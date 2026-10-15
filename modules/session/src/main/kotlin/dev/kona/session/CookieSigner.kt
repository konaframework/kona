package dev.kona.session

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 cookie payload integrity (Laravel-style signed cookies).
 *
 * Wire format: `{url-safe-base64(payload)}.{url-safe-base64(mac)}`.
 */
object CookieSigner {
    private const val ALGORITHM = "HmacSHA256"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    @JvmStatic
    fun sign(payload: String, key: ByteArray): String {
        val body = encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))
        val mac = mac(body, key)
        return "$body.${encoder.encodeToString(mac)}"
    }

    /** Returns the original payload when the signature is valid; otherwise null. */
    @JvmStatic
    fun unsign(signed: String, key: ByteArray): String? {
        val sep = signed.lastIndexOf('.')
        if (sep <= 0 || sep == signed.lastIndex) {
            return null
        }
        val body = signed.substring(0, sep)
        val given = try {
            decoder.decode(signed.substring(sep + 1))
        } catch (_: Exception) {
            return null
        }
        val expected = mac(body, key)
        if (!constantTimeEquals(given, expected)) {
            return null
        }
        return try {
            String(decoder.decode(body), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Decode `base64:…` / raw base64 APP_KEY material into key bytes (any length ≥ 1). */
    @JvmStatic
    fun keyFromAppKey(appKey: String): ByteArray? {
        val trimmed = appKey.trim().removePrefix("base64:")
        if (trimmed.isEmpty()) {
            return null
        }
        return try {
            Base64.getDecoder().decode(trimmed)
        } catch (_: Exception) {
            trimmed.toByteArray(Charsets.UTF_8)
        }
    }

    private fun mac(body: String, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8))
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) {
            return false
        }
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
