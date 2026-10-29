package org.konaframework.firefly.forms

import org.konaframework.support.json.Json

/**
 * Nested widgets (key-value, repeater, builder, multi-file upload) post one hidden JSON value
 * under the field name. State arrives either as the decoded structure or as that raw JSON text.
 */
internal object FormJson {
    fun encode(value: Any?): String = Json.stringify(value)

    /** The decoded value when [raw] is JSON text, else [raw] unchanged. */
    fun decode(raw: Any?): Any? {
        if (raw !is String) return raw
        val text = raw.trim()
        if (!text.startsWith("{") && !text.startsWith("[")) return raw
        return try {
            Json.mapper.readValue(text, Any::class.java)
        } catch (_: Exception) {
            raw
        }
    }
}
