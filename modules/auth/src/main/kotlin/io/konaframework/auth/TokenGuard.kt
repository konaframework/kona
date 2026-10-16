package io.konaframework.auth

import io.konaframework.auth.tokens.PersonalAccessToken
import io.konaframework.auth.tokens.PersonalAccessTokenManager
import io.konaframework.http.Request

/**
 * Bearer-token guard backed by [PersonalAccessTokenManager].
 */
class TokenGuard(
    private val name: String,
    private val tokens: PersonalAccessTokenManager,
    private val users: UserProvider,
) : Guard {
    private var request: Request? = null
    private var user: Authenticatable? = null
    private var accessToken: PersonalAccessToken? = null
    private var resolved: Boolean = false

    fun getName(): String = name

    fun currentAccessToken(): PersonalAccessToken? {
        user()
        return accessToken
    }

    override fun setRequest(request: Request): Guard {
        this.request = request
        this.user = null
        this.accessToken = null
        this.resolved = false
        return this
    }

    override fun attempt(credentials: Map<String, Any?>, remember: Boolean): Boolean {
        val candidate = resolveFromCredentials(credentials) ?: return false
        login(candidate.first, remember)
        accessToken = candidate.second
        return true
    }

    override fun validate(credentials: Map<String, Any?>): Boolean =
        resolveFromCredentials(credentials) != null

    override fun loginUsingId(id: Any, remember: Boolean): Authenticatable? {
        val found = users.retrieveById(id) ?: return null
        login(found, remember)
        return found
    }

    override fun once(credentials: Map<String, Any?>): Boolean {
        val candidate = resolveFromCredentials(credentials) ?: return false
        this.user = candidate.first
        this.accessToken = candidate.second
        this.resolved = true
        request?.setAttribute(SessionGuard.USER_ATTRIBUTE, candidate.first)
        return true
    }

    override fun onceUsingId(id: Any): Boolean {
        val found = users.retrieveById(id) ?: return false
        this.user = found
        this.resolved = true
        request?.setAttribute(SessionGuard.USER_ATTRIBUTE, found)
        return true
    }

    override fun login(user: Authenticatable, remember: Boolean) {
        this.user = user
        this.resolved = true
        val req = request
        if (req != null) {
            req.setAttribute(SessionGuard.USER_ATTRIBUTE, user)
        }
    }

    override fun logout() {
        val current = accessToken
        if (current != null) {
            tokens.delete(current)
        }
        user = null
        accessToken = null
        resolved = true
        val req = request
        if (req != null) {
            req.setAttribute(SessionGuard.USER_ATTRIBUTE, null)
        }
    }

    override fun check(): Boolean = user() != null

    override fun id(): Any? {
        val current = user()
        return if (current != null) current.getAuthIdentifier() else null
    }

    override fun user(): Authenticatable? {
        if (resolved) {
            return user
        }
        resolved = true
        val req = request
        if (req == null) {
            return null
        }
        val bearer = bearerToken(req)
        if (bearer == null) {
            return null
        }
        val token = tokens.findToken(bearer)
        if (token == null) {
            return null
        }
        val found = users.retrieveById(token.tokenableId)
        if (found == null) {
            return null
        }
        accessToken = token
        user = found
        req.setAttribute(SessionGuard.USER_ATTRIBUTE, found)
        req.setAttribute(TOKEN_ATTRIBUTE, token)
        return found
    }

    private fun resolveFromCredentials(credentials: Map<String, Any?>): Pair<Authenticatable, PersonalAccessToken>? {
        val raw = credentials["token"] ?: return null
        val found = tokens.findToken(raw.toString()) ?: return null
        val candidate = users.retrieveById(found.tokenableId) ?: return null
        return candidate to found
    }

    companion object {
        const val TOKEN_ATTRIBUTE: String = "personal_access_token"

        @JvmStatic
        fun bearerToken(request: Request?): String? {
            if (request == null) {
                return null
            }
            val header = request.header("Authorization") ?: return null
            if (!header.startsWith("Bearer ", ignoreCase = true)) {
                return null
            }
            val value = header.substring(7).trim()
            return value.ifEmpty { null }
        }
    }
}
