package io.konaframework.auth.tokens

import io.konaframework.auth.Authenticatable
import io.konaframework.auth.AuthenticationException
import io.konaframework.auth.SessionGuard
import io.konaframework.auth.TokenGuard
import io.konaframework.auth.UserProvider
import io.konaframework.http.Middleware
import io.konaframework.http.Request
import io.konaframework.http.Response

/**
 * Middleware that authenticates via `Authorization: Bearer <token>`.
 */
class AuthenticateWithToken @JvmOverloads constructor(
    private val tokens: PersonalAccessTokenManager,
    private val users: UserProvider? = null,
    private val ability: String? = null,
) : Middleware {
    override fun handle(request: Request, next: io.konaframework.http.Handler): Response {
        val plain = TokenGuard.bearerToken(request)
            ?: throw AuthenticationException()
        val token = tokens.findToken(plain) ?: throw AuthenticationException()
        if (ability != null && !token.can(ability)) {
            throw AuthenticationException("Token is missing the required ability.")
        }
        val resolvedUser = resolveUser(token, plain)
        request.setAttribute(SessionGuard.USER_ATTRIBUTE, resolvedUser)
        request.setAttribute(TokenGuard.TOKEN_ATTRIBUTE, token)
        return next.handle(request)
    }

    private fun resolveUser(token: PersonalAccessToken, plain: String): Authenticatable? {
        val provider = users
        if (provider == null) {
            return tokens.findUser(plain)
        }
        val fromProvider = provider.retrieveById(token.tokenableId)
        if (fromProvider != null) {
            return fromProvider
        }
        return tokens.findUser(plain)
    }
}
