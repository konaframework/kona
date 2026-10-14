package dev.kona.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64

/**
 * Cookie session driver: payload travels in the session cookie as Base64(JSON).
 *
 * **Encryption / signing is deferred** to the M3 encryption slice. Payloads are
 * unsigned Base64 JSON suitable for tests and local development only — do not
 * use in production until encrypt/sign lands.
 */
class CookieSessionStore @JvmOverloads constructor(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : SessionStore {
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
            val json = String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
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
        outboundPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mapper.writeValueAsString(envelope).toByteArray(Charsets.UTF_8))
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
            val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            val envelope = mapper.readValue(json, Map::class.java) as Map<String, Any?>
            envelope["id"] as? String
        } catch (_: Exception) {
            null
        }
    }
}
