package dev.kona.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64
import java.util.function.Function

/**
 * Cookie session driver: payload travels in the session cookie.
 *
 * Encoding priority:
 * 1. [encrypt] / [decrypt] when provided (AES via Encrypter)
 * 2. HMAC signing via [signKey] when encryption is off
 * 3. Plain URL-safe Base64(JSON) (tests / no app key)
 */
class CookieSessionStore @JvmOverloads constructor(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val encrypt: ((String) -> String)? = null,
    private val decrypt: ((String) -> String)? = null,
    private val signKey: ByteArray? = null,
) : SessionStore {
    /** Java-friendly constructor taking [Function] codecs. */
    constructor(
        mapper: ObjectMapper,
        encrypt: Function<String, String>?,
        decrypt: Function<String, String>?,
    ) : this(
        mapper,
        encrypt?.let { fn -> { s: String -> fn.apply(s) } },
        decrypt?.let { fn -> { s: String -> fn.apply(s) } },
        null,
    )

    /** Java-friendly constructor with optional HMAC signing key. */
    constructor(
        mapper: ObjectMapper,
        encrypt: Function<String, String>?,
        decrypt: Function<String, String>?,
        signKey: ByteArray?,
    ) : this(
        mapper,
        encrypt?.let { fn -> { s: String -> fn.apply(s) } },
        decrypt?.let { fn -> { s: String -> fn.apply(s) } },
        signKey,
    )

    /** Inbound cookie payload for the current request (set by [SessionManager]). */
    @Volatile
    var inboundPayload: String? = null

    /** Outbound cookie payload after [write] / [destroy] (read by [SessionManager]). */
    @Volatile
    var outboundPayload: String = ""

    override fun read(id: String): Map<String, Any?> {
        val raw = inboundPayload
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            val json = decodePayload(raw) ?: return emptyMap()
            @Suppress("UNCHECKED_CAST")
            val envelope = mapper.readValue(json, Map::class.java) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val data = envelope["data"] as? Map<String, Any?> ?: emptyMap()
            LinkedHashMap(data)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun write(id: String, data: Map<String, Any?>, lifetimeMinutes: Int) {
        val envelope = mapOf("id" to id, "data" to data)
        val json = mapper.writeValueAsString(envelope)
        outboundPayload = encodePayload(json)
    }

    override fun destroy(id: String) {
        outboundPayload = ""
    }

    /** Decodes session id embedded in [payload], if present. */
    fun readId(payload: String?): String? {
        if (payload.isNullOrBlank()) {
            return null
        }
        return try {
            val json = decodePayload(payload) ?: return null
            @Suppress("UNCHECKED_CAST")
            val envelope = mapper.readValue(json, Map::class.java) as Map<String, Any?>
            envelope["id"] as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun encodePayload(json: String): String {
        val encryptFn = encrypt
        if (encryptFn != null) {
            return encryptFn(json)
        }
        val key = signKey
        if (key != null) {
            return CookieSigner.sign(json, key)
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    private fun decodePayload(raw: String): String? {
        return try {
            val decryptFn = decrypt
            if (decryptFn != null) {
                return decryptFn(raw)
            }
            val key = signKey
            if (key != null) {
                return CookieSigner.unsign(raw, key)
            }
            String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
