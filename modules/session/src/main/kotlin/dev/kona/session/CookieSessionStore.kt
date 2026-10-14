package dev.kona.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64
import java.util.function.Function

/**
 * Cookie session driver: payload travels in the session cookie.
 *
 * By default the cookie value is URL-safe Base64(JSON). When [encrypt] / [decrypt]
 * are provided (typically AES via `dev.kona.security.Encrypter`), the cookie holds
 * the encrypted string instead of raw Base64 — preferred for production.
 */
class CookieSessionStore @JvmOverloads constructor(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val encrypt: ((String) -> String)? = null,
    private val decrypt: ((String) -> String)? = null,
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
        return if (encryptFn != null) {
            encryptFn(json)
        } else {
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.toByteArray(Charsets.UTF_8))
        }
    }

    private fun decodePayload(raw: String): String? {
        val decryptFn = decrypt
        return try {
            if (decryptFn != null) {
                decryptFn(raw)
            } else {
                String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }
}
