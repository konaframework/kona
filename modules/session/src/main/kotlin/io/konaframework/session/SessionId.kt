package io.konaframework.session

import java.security.SecureRandom

object SessionId {
    private val random = SecureRandom()

    @JvmStatic
    fun generate(): String {
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    @JvmStatic
    fun isValid(id: String): Boolean = FileSessionStore.isValidId(id)
}
