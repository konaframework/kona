package org.konaframework.security

/**
 * Thrown when encryption or decryption fails.
 */
class EncryptionException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
