package org.konaframework.auth.twofactor

import org.konaframework.security.Encrypter
import java.time.Clock
import java.time.Instant

/**
 * Confirm two-factor enrollment with a valid TOTP code
 * (Laravel Fortify `ConfirmTwoFactorAuthentication`).
 */
class ConfirmTwoFactorAuthentication @JvmOverloads constructor(
    private val provider: TwoFactorAuthenticationProvider,
    private val encrypter: Encrypter,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Verify [code] against the user's pending secret and set `two_factor_confirmed_at`.
     * Returns false when the secret is missing or the code is invalid.
     */
    fun confirm(user: TwoFactorAuthenticatable, code: String): Boolean {
        val encrypted = user.getTwoFactorSecret() ?: return false
        val secret = encrypter.decryptString(encrypted)
        if (!provider.verify(secret, code)) {
            return false
        }
        user.setTwoFactorConfirmedAt(Instant.now(clock))
        user.saveTwoFactorState()
        return true
    }
}
