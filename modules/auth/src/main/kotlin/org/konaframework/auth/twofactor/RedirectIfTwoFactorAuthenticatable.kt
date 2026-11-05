package org.konaframework.auth.twofactor

import org.konaframework.auth.AuthManager
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.UserProvider
import org.konaframework.http.Request
import org.konaframework.http.Response

/**
 * After a successful password check, either complete login or park the user id in the
 * session and redirect to the two-factor challenge (Laravel Fortify
 * `RedirectIfTwoFactorAuthenticatable`).
 */
class RedirectIfTwoFactorAuthenticatable @JvmOverloads constructor(
    private val auth: AuthManager,
    private val users: UserProvider,
    private val challengePath: String = "/two-factor-challenge",
) {
    /**
     * Validate credentials. When the user has 2FA enabled, store a pending login and
     * return a redirect to [challengePath] without calling [AuthManager.login].
     * Otherwise log the user in and return null (caller continues with their success response).
     *
     * Returns a failed-credentials marker via [Result.invalid] when credentials do not match.
     */
    @JvmOverloads
    fun handle(
        request: Request,
        credentials: Map<String, Any?>,
        remember: Boolean = false,
    ): Result {
        auth.setRequest(request)
        val user = users.retrieveByCredentials(credentials.filterKeys { it != "password" && it != "remember" })
            ?: return Result(Result.Status.INVALID, null, null)
        if (!users.validateCredentials(user, credentials)) {
            return Result(Result.Status.INVALID, null, null)
        }
        if (user is TwoFactorAuthenticatable && TwoFactor.enabled(user)) {
            TwoFactor.rememberLogin(request, user.getAuthIdentifier(), remember)
            return Result(Result.Status.CHALLENGE, null, Response.redirect(challengePath, 302))
        }
        auth.login(user, remember)
        return Result(Result.Status.AUTHENTICATED, user, null)
    }

    class Result(
        val status: Status,
        val user: Authenticatable?,
        val response: Response?,
    ) {
        enum class Status { INVALID, CHALLENGE, AUTHENTICATED }

        fun failed(): Boolean = status == Status.INVALID

        fun challenged(): Boolean = status == Status.CHALLENGE

        fun passed(): Boolean = status == Status.AUTHENTICATED
    }
}
