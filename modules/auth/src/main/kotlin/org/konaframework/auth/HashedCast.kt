package org.konaframework.auth

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CastsAttributes

/**
 * Hashes plain-text values on write with the configured [Hash] manager, and leaves values
 * that are already hashes untouched. Use as `casts = mapOf("password" to HashedCast())`
 * or the string cast `"hashed"` (registered by [AuthServiceProvider]).
 */
class HashedCast : CastsAttributes {
    override fun get(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? = value

    override fun set(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) return null
        val text = value.toString()
        if (text.isEmpty() || Hash.isHashed(text)) return text
        return Hash.make(text)
    }
}
