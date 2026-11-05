package org.konaframework.auth.twofactor

/**
 * Clear two-factor secret, recovery codes, and confirmation
 * (Laravel Fortify `DisableTwoFactorAuthentication`).
 */
class DisableTwoFactorAuthentication {
    fun disable(user: TwoFactorAuthenticatable): TwoFactorAuthenticatable {
        user.setTwoFactorSecret(null)
        user.setTwoFactorRecoveryCodes(null)
        user.setTwoFactorConfirmedAt(null)
        user.saveTwoFactorState()
        return user
    }
}
