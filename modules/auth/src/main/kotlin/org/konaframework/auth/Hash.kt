package org.konaframework.auth

import org.konaframework.auth.hashing.Argon2Hasher
import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.hashing.HashManager
import org.konaframework.cadence.AttributeHasher
import org.konaframework.cadence.CastHasher

/**
 * Global Hash facade (`Hash.make` / `check` / `needsRehash` / `isHashed`).
 */
object Hash {
    @Volatile
    private var manager: HashManager? = null

    @JvmStatic
    fun setManager(value: HashManager) {
        manager = value
        CastHasher.set(
            object : AttributeHasher {
                override fun make(plain: String): String = manager!!.make(plain)

                override fun isHashed(candidate: String): Boolean = Hash.isHashed(candidate)
            },
        )
    }

    @JvmStatic
    fun clearManager() {
        manager = null
        CastHasher.clear()
    }

    @JvmStatic
    fun manager(): HashManager =
        manager ?: throw IllegalStateException("Hash manager is not set; register AuthServiceProvider or call Hash.setManager")

    @JvmStatic
    fun make(value: String): String = manager().make(value)

    @JvmStatic
    fun check(value: String, hashed: String): Boolean = manager().check(value, hashed)

    @JvmStatic
    fun needsRehash(hashed: String): Boolean = manager().needsRehash(hashed)

    /**
     * True when [value] already looks like a bcrypt or Argon2 password hash
     * (so [org.konaframework.cadence.CastType.HASHED] can leave it untouched).
     *
     * Does not require a registered manager — detection is format-based.
     */
    @JvmStatic
    fun isHashed(value: String): Boolean {
        if (value.isBlank()) {
            return false
        }
        if (isBcryptHash(value)) {
            return true
        }
        if (value.startsWith("\$argon2")) {
            return Argon2Hasher.parseParams(value) != null
        }
        return false
    }

    private fun isBcryptHash(value: String): Boolean {
        if (value.length < 60) {
            return false
        }
        if (!(value.startsWith("\$2y\$") || value.startsWith("\$2a\$") || value.startsWith("\$2b\$"))) {
            return false
        }
        return BcryptHasher.parseCost(value) != null
    }
}
