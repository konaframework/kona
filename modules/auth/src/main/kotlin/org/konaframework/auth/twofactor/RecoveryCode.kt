package org.konaframework.auth.twofactor

import java.security.SecureRandom

/**
 * One-time recovery codes for two-factor authentication (Laravel Fortify `RecoveryCode`).
 *
 * Default format: `{10 alnum}-{10 alnum}` (e.g. `a1b2c3d4e5-f6g7h8i9j0`).
 */
object RecoveryCode {
    private val random = SecureRandom()
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    @Volatile
    private var generator: (() -> String)? = null

    /** Override the generator (tests / custom formats). Pass null to restore the default. */
    @JvmStatic
    fun useGenerator(block: (() -> String)?) {
        generator = block
    }

    /** Generate a single recovery code. */
    @JvmStatic
    fun generate(): String {
        val custom = generator
        if (custom != null) {
            return custom()
        }
        return "${segment(10)}-${segment(10)}"
    }

    /** Generate [count] unique recovery codes (default 8). */
    @JvmStatic
    @JvmOverloads
    fun generateMany(count: Int = 8): List<String> {
        require(count > 0) { "count must be positive" }
        val codes = linkedSetOf<String>()
        while (codes.size < count) {
            codes.add(generate())
        }
        return codes.toList()
    }

    private fun segment(length: Int): String {
        val chars = CharArray(length)
        for (i in 0 until length) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }
}
