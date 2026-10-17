package org.konaframework.session

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * CSRF token helpers (Laravel `csrf_token()` / session `_token` shape).
 */
object Csrf {
    const val TOKEN_KEY: String = "_token"

    private val random = SecureRandom()

    /** Returns the session CSRF token, generating one when missing. */
    @JvmStatic
    fun token(session: Session): String {
        val existing = session.get(TOKEN_KEY) as? String
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = generate()
        session.put(TOKEN_KEY, generated)
        return generated
    }

    @JvmStatic
    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    @JvmStatic
    fun tokensMatch(expected: String?, provided: String?): Boolean {
        if (expected.isNullOrBlank() || provided.isNullOrBlank()) {
            return false
        }
        val a = expected.toByteArray(Charsets.UTF_8)
        val b = provided.toByteArray(Charsets.UTF_8)
        if (a.size != b.size) {
            return false
        }
        return MessageDigest.isEqual(a, b)
    }
}
