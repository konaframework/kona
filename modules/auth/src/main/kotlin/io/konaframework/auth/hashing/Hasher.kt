package io.konaframework.auth.hashing

/**
 * Password hasher (Laravel Hasher contract).
 */
interface Hasher {
    fun make(value: String): String

    fun check(value: String, hashed: String): Boolean

    fun needsRehash(hashed: String): Boolean
}
