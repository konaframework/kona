package org.konaframework.auth.twofactor

import org.konaframework.auth.session.SessionAttributes
import org.konaframework.http.Request
import org.konaframework.security.Encrypter

/**
 * Session keys and helpers for the two-factor login challenge
 * (Laravel Fortify pending-login / `login.id` shape).
 */
object TwoFactor {
    const val LOGIN_ID: String = "login.id"
    const val LOGIN_REMEMBER: String = "login.remember"
    const val CONFIRMS: String = "auth.two_factor.confirm"

    @Volatile
    private var provider: TwoFactorAuthenticationProvider? = null

    @Volatile
    private var encrypter: Encrypter? = null

    @Volatile
    private var requiresConfirmation: Boolean = true

    @JvmStatic
    fun setProvider(value: TwoFactorAuthenticationProvider?) {
        provider = value
    }

    @JvmStatic
    fun provider(): TwoFactorAuthenticationProvider =
        provider
            ?: throw IllegalStateException(
                "TwoFactorAuthenticationProvider is not set; register AuthServiceProvider with app.key",
            )

    @JvmStatic
    fun setEncrypter(value: Encrypter?) {
        encrypter = value
    }

    @JvmStatic
    fun encrypter(): Encrypter =
        encrypter
            ?: throw IllegalStateException("Encrypter is not set; configure app.key for two-factor secrets")

    @JvmStatic
    fun setRequiresConfirmation(value: Boolean) {
        requiresConfirmation = value
    }

    @JvmStatic
    fun requiresConfirmation(): Boolean = requiresConfirmation

    /** Store a pending login after password success when 2FA is enabled. */
    @JvmStatic
    @JvmOverloads
    fun rememberLogin(request: Request, userId: Any, remember: Boolean = false) {
        val session = SessionAttributes.ensure(request)
        session.put(LOGIN_ID, userId)
        session.put(LOGIN_REMEMBER, remember)
    }

    /** Pull the pending login id (null when absent). Does not clear. */
    @JvmStatic
    fun pendingLoginId(request: Request): Any? =
        SessionAttributes.resolve(request)?.get(LOGIN_ID)

    /** Whether the pending login asked for remember-me. */
    @JvmStatic
    fun pendingRemember(request: Request): Boolean {
        val raw = SessionAttributes.resolve(request)?.get(LOGIN_REMEMBER) ?: return false
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> raw.toString().equals("true", ignoreCase = true) || raw.toString() == "1"
        }
    }

    /** Clear pending login challenge state. */
    @JvmStatic
    fun forgetLogin(request: Request) {
        SessionAttributes.resolve(request)?.let { session ->
            session.forget(LOGIN_ID)
            session.forget(LOGIN_REMEMBER)
        }
    }

    /** True when [user] has confirmed (or enabled) two-factor auth. */
    @JvmStatic
    fun enabled(user: TwoFactorAuthenticatable): Boolean =
        user.hasEnabledTwoFactorAuthentication(requiresConfirmation)
}
