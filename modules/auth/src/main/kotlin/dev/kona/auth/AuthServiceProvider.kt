package dev.kona.auth

import dev.kona.auth.authorization.Gate
import dev.kona.auth.hashing.BcryptHasher
import dev.kona.auth.hashing.HashManager
import dev.kona.auth.passwords.PasswordBroker
import dev.kona.auth.tokens.InMemoryPersonalAccessTokenRepository
import dev.kona.auth.tokens.PersonalAccessTokenManager
import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.security.Encrypter
import dev.kona.security.EncryptionException
import dev.kona.security.RateLimiter

/**
 * Registers hashing, auth, passwords, gate, encryption, tokens, and rate limiting.
 *
 * Session module is soft-depended: [SessionGuard] reads `request.getAttribute("session")`.
 */
class AuthServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        val rounds = app.config.getInt("hash.rounds", BcryptHasher.DEFAULT_ROUNDS)
        val hashManager = HashManager(defaultRounds = rounds)
        app.container.instance(HashManager::class.java, hashManager)
        app.container.alias("hash", HashManager::class.java.name)
        Hash.setManager(hashManager)

        val users = InMemoryUserProvider(hashManager.driver())
        app.container.instance(InMemoryUserProvider::class.java, users)
        app.container.alias(UserProvider::class.java.name, InMemoryUserProvider::class.java.name)

        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        app.container.instance(AuthManager::class.java, auth)
        app.container.alias("auth", AuthManager::class.java.name)

        val tokens = PersonalAccessTokenManager(
            repository = InMemoryPersonalAccessTokenRepository(),
            users = users,
        )
        app.container.instance(PersonalAccessTokenManager::class.java, tokens)
        auth.extend("api", TokenGuard("api", tokens, users))

        val passwords = PasswordBroker(users, hashManager.driver())
        app.container.instance(PasswordBroker::class.java, passwords)
        app.container.alias("auth.password", PasswordBroker::class.java.name)

        val gate = Gate().setUserResolver { auth.user() }
        app.container.instance(Gate::class.java, gate)
        app.container.alias("gate", Gate::class.java.name)

        val rateLimiter = RateLimiter()
        app.container.instance(RateLimiter::class.java, rateLimiter)
        app.container.alias("rateLimiter", RateLimiter::class.java.name)

        registerEncrypter(app)
    }

    override fun boot(app: Application) {
        // Encrypter may be absent until app.key is configured; Hash is already set in register.
    }

    private fun registerEncrypter(app: Application) {
        val key = app.config.getString("app.key")
        if (key.isNullOrBlank()) {
            return
        }
        try {
            val encrypter = Encrypter.fromBase64Key(key)
            app.container.instance(Encrypter::class.java, encrypter)
            app.container.alias("encrypter", Encrypter::class.java.name)
        } catch (_: EncryptionException) {
            // Leave unbound when key is invalid; callers can register later.
        }
    }
}
