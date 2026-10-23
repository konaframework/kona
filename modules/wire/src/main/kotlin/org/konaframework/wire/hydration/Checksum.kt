package org.konaframework.wire.hydration

import com.fasterxml.jackson.databind.SerializationFeature
import org.konaframework.wire.CorruptComponentPayloadException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 snapshot checksums (`Checksum::generate` / `verify`). The checksum covers a canonical JSON form of
 * `{data, memo}` (sorted keys, whole doubles written as integers) so browser round-trips cannot break it.
 */
class Checksum(key: String) {
    private val prototype: Mac = Mac.getInstance(ALGORITHM).apply {
        init(SecretKeySpec(material(key), ALGORITHM))
    }

    fun generate(snapshot: Map<String, Any?>): String {
        val canonical = linkedMapOf("data" to snapshot["data"], "memo" to snapshot["memo"])
        val bytes = CANONICAL.writeValueAsBytes(normalize(canonical))
        val mac = prototype.clone() as Mac
        return HexFormat.of().formatHex(mac.doFinal(bytes))
    }

    /** Throws [CorruptComponentPayloadException] when the checksum is missing or does not match. */
    fun verify(snapshot: Map<String, Any?>) = verify(snapshot, false)

    fun verify(snapshot: Map<String, Any?>, debug: Boolean) {
        val provided = snapshot["checksum"] as? String ?: throw CorruptComponentPayloadException()
        val expected = generate(snapshot)
        if (!MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())) {
            if (debug) {
                throw CorruptComponentPayloadException(
                    "Wire checksum mismatch. Expected [$expected] but received [$provided]. " +
                        "Enable app.debug for this message (SupportChecksumErrorDebugging).",
                )
            }
            throw CorruptComponentPayloadException()
        }
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private val CANONICAL = Coercion.mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        private val processKey: ByteArray by lazy { ByteArray(32).also { SecureRandom().nextBytes(it) } }

        private fun material(key: String): ByteArray = when {
            key.isBlank() -> processKey
            key.startsWith("base64:") -> java.util.Base64.getDecoder().decode(key.removePrefix("base64:"))
            else -> key.toByteArray(Charsets.UTF_8)
        }

        internal fun normalize(value: Any?): Any? = when (value) {
            is Map<*, *> -> value.entries.associateTo(LinkedHashMap()) { (k, v) -> k.toString() to normalize(v) }
            is List<*> -> value.map(::normalize)
            is Double -> if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong() else value
            is Float -> normalize(value.toString().toDouble())
            is Number -> value.toLong()
            else -> value
        }
    }
}
