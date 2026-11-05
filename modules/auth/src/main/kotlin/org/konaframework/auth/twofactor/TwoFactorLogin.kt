package org.konaframework.auth.twofactor

import org.konaframework.auth.AuthManager
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.UserProvider
import org.konaframework.http.Request
import org.konaframework.security.Encrypter
import org.konaframework.security.RateLimitExceededException
import org.konaframework.security.RateLimiter

/**
 * Complete a pending two-factor login with a TOTP code or recovery code
 * (Laravel Fortify `TwoFactorAuthenticatedSessionController` store).
 */
class TwoFactorLogin @JvmOverloads constructor(
    private val auth: AuthManager,
    private val users: UserProvider,
    private val provider: TwoFactorAuthenticationProvider,
    private val encrypter: Encrypter,
    private val rateLimiter: RateLimiter? = null,
    private val maxAttempts: Int = 5,
    private val decaySeconds: Int = 60,
) {
    /**
     * Attempt challenge completion. [code] is a TOTP; [recoveryCode] is an alternate.
     * Returns the authenticated user, or null when invalid / no pending login.
     *
     * @throws RateLimitExceededException when too many attempts
     */
    @JvmOverloads
    fun attempt(
        request: Request,
        code: String? = null,
        recoveryCode: String? = null,
    ): Authenticatable? {
        val pendingId = TwoFactor.pendingLoginId(request) ?: return null
        val key = "two-factor-challenge:$pendingId"
        if (rateLimiter != null && rateLimiter.tooManyAttempts(key, maxAttempts)) {
            throw RateLimitExceededException(rateLimiter.availableIn(key))
        }
        val user = users.retrieveById(pendingId) ?: run {
            TwoFactor.forgetLogin(request)
            return null
        }
        if (user !is TwoFactorAuthenticatable || !TwoFactor.enabled(user)) {
            TwoFactor.forgetLogin(request)
            return null
        }
        val remember = TwoFactor.pendingRemember(request)
        val ok = when {
            !recoveryCode.isNullOrBlank() -> consumeRecoveryCode(user, recoveryCode.trim())
            !code.isNullOrBlank() -> verifyTotp(user, code)
            else -> false
        }
        if (!ok) {
            rateLimiter?.hit(key, decaySeconds)
            return null
        }
        rateLimiter?.clear(key)
        TwoFactor.forgetLogin(request)
        auth.setRequest(request)
        auth.login(user, remember)
        return user
    }

    private fun verifyTotp(user: TwoFactorAuthenticatable, code: String): Boolean {
        val encrypted = user.getTwoFactorSecret() ?: return false
        val secret = encrypter.decryptString(encrypted)
        return provider.verify(secret, code)
    }

    private fun consumeRecoveryCode(user: TwoFactorAuthenticatable, code: String): Boolean {
        val codes = user.recoveryCodes(encrypter)
        if (code !in codes) {
            return false
        }
        user.replaceRecoveryCode(code, encrypter)
        return true
    }
}
