package dev.kona.auth.tokens

import dev.kona.auth.Authenticatable
import dev.kona.auth.AuthenticationException
import dev.kona.auth.SessionGuard
import dev.kona.auth.TokenGuard
import dev.kona.auth.UserProvider
import dev.kona.http.Middleware
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * Middleware that authenticates via `Authorization: Bearer <token>`.
 */
class AuthenticateWithToken @JvmOverloads constructor(
    private val tokens: PersonalAccessTokenManager,
    private val users: UserProvider? = null,
    private val ability: String? = null,
) : Middleware {
    override fun handle(request: Request, next: dev.kona.http.Handler): Response {
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
