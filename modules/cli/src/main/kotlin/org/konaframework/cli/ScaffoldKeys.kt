package org.konaframework.cli

import java.security.SecureRandom
import java.util.Base64

/** Key material helpers for scaffolded apps (`APP_KEY` / `app.key`). */
object ScaffoldKeys {
    /** 32-byte random key in Laravel/Almasix `base64:…` form. */
    @JvmStatic
    fun generateAppKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return "base64:" + Base64.getEncoder().encodeToString(bytes)
    }
}
