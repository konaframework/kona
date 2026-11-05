package org.konaframework.auth.twofactor

import org.konaframework.security.Encrypter
import java.time.Instant

/**
 * User that can enroll in TOTP two-factor authentication (Laravel Fortify
 * `TwoFactorAuthenticatable`).
 *
 * Secrets and recovery-code payloads are stored **encrypted** (via [Encrypter]).
 */
interface TwoFactorAuthenticatable {
    fun getTwoFactorSecret(): String?

    fun setTwoFactorSecret(secret: String?)

    fun getTwoFactorRecoveryCodes(): String?

    fun setTwoFactorRecoveryCodes(codes: String?)

    fun getTwoFactorConfirmedAt(): Instant?

    fun setTwoFactorConfirmedAt(confirmedAt: Instant?)

    /** Persist attribute changes (Cadence `save`, in-memory no-op, …). */
    fun saveTwoFactorState()

    /**
     * True when two-factor is enabled. When [requiresConfirmation] is true (Fortify default),
     * both secret and [getTwoFactorConfirmedAt] must be set.
     */
    fun hasEnabledTwoFactorAuthentication(requiresConfirmation: Boolean = true): Boolean {
        val secret = getTwoFactorSecret()
        if (secret.isNullOrBlank()) {
            return false
        }
        if (!requiresConfirmation) {
            return true
        }
        return getTwoFactorConfirmedAt() != null
    }

    /** Decrypt and return recovery codes, or an empty list. */
    fun recoveryCodes(encrypter: Encrypter): List<String> {
        val payload = getTwoFactorRecoveryCodes() ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            encrypter.decrypt(payload, List::class.java) as List<String>
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Replace a used recovery code with a freshly generated one and persist. */
    fun replaceRecoveryCode(code: String, encrypter: Encrypter) {
        val current = recoveryCodes(encrypter).toMutableList()
        val index = current.indexOfFirst { it == code }
        if (index < 0) {
            return
        }
        current[index] = RecoveryCode.generate()
        setTwoFactorRecoveryCodes(encrypter.encrypt(current))
        saveTwoFactorState()
    }

    /** `otpauth://` URL for the user's current (decrypted) secret. */
    fun twoFactorQrCodeUrl(
        provider: TwoFactorAuthenticationProvider,
        encrypter: Encrypter,
        issuer: String,
        account: String,
    ): String {
        val encrypted = getTwoFactorSecret()
            ?: throw IllegalStateException("Two-factor authentication is not enabled.")
        val secret = encrypter.decryptString(encrypted)
        return provider.qrCodeUrl(issuer, account, secret)
    }
}
