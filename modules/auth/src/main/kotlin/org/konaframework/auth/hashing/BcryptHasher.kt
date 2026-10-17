package org.konaframework.auth.hashing

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Bcrypt password hasher (Laravel `bcrypt` driver).
 *
 * Uses a lightweight Spring-free bcrypt implementation.
 */
class BcryptHasher @JvmOverloads constructor(
    private val rounds: Int = DEFAULT_ROUNDS,
) : Hasher {
    init {
        require(rounds in 4..31) { "Bcrypt rounds must be between 4 and 31" }
    }

    override fun make(value: String): String =
        BCrypt.withDefaults().hashToString(rounds, truncate(value).toCharArray())

    override fun check(value: String, hashed: String): Boolean {
        if (hashed.isBlank()) {
            return false
        }
        return try {
            BCrypt.verifyer().verify(truncate(value).toCharArray(), hashed).verified
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Bcrypt only uses the first 72 bytes of the password. */
    private fun truncate(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 72) {
            return value
        }
        return String(bytes.copyOfRange(0, 72), Charsets.UTF_8)
    }

    override fun needsRehash(hashed: String): Boolean {
        val cost = parseCost(hashed) ?: return true
        return cost != rounds
    }

    companion object {
        const val DEFAULT_ROUNDS: Int = 10

        @JvmStatic
        fun parseCost(hashed: String): Int? {
            // $2a$10$... / $2y$12$... / $2b$08$...
            if (hashed.length < 7 || hashed[0] != '$') {
                return null
            }
            val parts = hashed.split('$')
            if (parts.size < 4) {
                return null
            }
            return parts[2].toIntOrNull()
        }
    }
}
