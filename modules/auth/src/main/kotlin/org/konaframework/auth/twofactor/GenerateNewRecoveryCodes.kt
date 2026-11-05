package org.konaframework.auth.twofactor

import org.konaframework.security.Encrypter

/**
 * Replace the user's recovery codes (Laravel Fortify `GenerateNewRecoveryCodes`).
 */
class GenerateNewRecoveryCodes(
    private val encrypter: Encrypter,
    private val count: Int = 8,
) {
    fun generate(user: TwoFactorAuthenticatable): List<String> {
        val codes = RecoveryCode.generateMany(count)
        user.setTwoFactorRecoveryCodes(encrypter.encrypt(codes))
        user.saveTwoFactorState()
        return codes
    }
}
