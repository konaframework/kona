package org.konaframework.cadence

/**
 * Password hashing bridge used by [CastType.HASHED].
 *
 * Auth registers an implementation that delegates to `Hash.make` / `Hash.isHashed`
 * so Cadence does not depend on the auth module.
 */
interface AttributeHasher {
    /** Hash a plaintext value with the application's configured hasher. */
    fun make(value: String): String

    /** True when [value] already looks like a stored password hash. */
    fun isHashed(value: String): Boolean
}

/**
 * Process-wide [AttributeHasher] for [CastType.HASHED].
 *
 * Wired by auth when the hash manager is set; clear with [clear].
 */
object CastHasher {
    @Volatile
    private var hasher: AttributeHasher? = null

    @JvmStatic
    fun set(value: AttributeHasher) {
        hasher = value
    }

    @JvmStatic
    fun clear() {
        hasher = null
    }

    @JvmStatic
    fun get(): AttributeHasher =
        hasher
            ?: throw CadenceException(
                "Cast hasher is not set; register AuthServiceProvider or call CastHasher.set before using CastType.HASHED",
            )

    @JvmStatic
    fun getOrNull(): AttributeHasher? = hasher
}
