package io.konaframework.auth

import io.konaframework.auth.hashing.HashManager

/**
 * Global Hash facade (Laravel `Hash::make` / `check` / `needsRehash`).
 */
object Hash {
    @Volatile
    private var manager: HashManager? = null

    @JvmStatic
    fun setManager(value: HashManager) {
        manager = value
    }

    @JvmStatic
    fun clearManager() {
        manager = null
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
}
