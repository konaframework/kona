package org.konaframework.auth.twofactor

import org.konaframework.security.Encrypter

/**
 * Enable two-factor authentication: store an encrypted secret + recovery codes
 * (Laravel Fortify `EnableTwoFactorAuthentication`). Does **not** set confirmed_at —
 * call [ConfirmTwoFactorAuthentication] after the user proves a valid OTP.
 */
class EnableTwoFactorAuthentication @JvmOverloads constructor(
    private val provider: TwoFactorAuthenticationProvider,
    private val encrypter: Encrypter,
    private val recoveryCodeCount: Int = 8,
) {
    @JvmOverloads
    fun enable(user: TwoFactorAuthenticatable, force: Boolean = false): TwoFactorAuthenticatable {
        if (!force && !user.getTwoFactorSecret().isNullOrBlank()) {
            return user
        }
        val secret = provider.generateSecretKey()
        val codes = RecoveryCode.generateMany(recoveryCodeCount)
        user.setTwoFactorSecret(encrypter.encryptString(secret))
        user.setTwoFactorRecoveryCodes(encrypter.encrypt(codes))
        user.setTwoFactorConfirmedAt(null)
        user.saveTwoFactorState()
        return user
    }
}
