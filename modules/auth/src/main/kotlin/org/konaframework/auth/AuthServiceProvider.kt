package org.konaframework.auth

import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.hashing.Argon2Hasher
import org.konaframework.auth.hashing.BcryptHasher
import org.konaframework.auth.hashing.HashManager
import org.konaframework.auth.passwords.DatabasePasswordResetTokenRepository
import org.konaframework.auth.passwords.InMemoryPasswordResetTokenRepository
import org.konaframework.auth.passwords.PasswordBroker
import org.konaframework.auth.passwords.PasswordResetTokenRepository
import org.konaframework.auth.tokens.DatabasePersonalAccessTokenRepository
import org.konaframework.auth.tokens.InMemoryPersonalAccessTokenRepository
import org.konaframework.auth.tokens.PersonalAccessTokenManager
import org.konaframework.auth.tokens.PersonalAccessTokenRepository
import org.konaframework.auth.twofactor.ConfirmTwoFactorAuthentication
import org.konaframework.auth.twofactor.DisableTwoFactorAuthentication
import org.konaframework.auth.twofactor.EnableTwoFactorAuthentication
import org.konaframework.auth.twofactor.GenerateNewRecoveryCodes
import org.konaframework.auth.twofactor.TwoFactor
import org.konaframework.auth.twofactor.TwoFactorAuthenticationProvider
import org.konaframework.cadence.CastRegistry
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.security.Encrypter
import org.konaframework.security.EncryptionException
import org.konaframework.security.RateLimiter

/**
 * Registers hashing, auth, passwords, gate, encryption, tokens, rate limiting, and two-factor.
 *
 * Session module is soft-depended: [SessionGuard] reads `request.getAttribute("session")`.
 *
 * Optional config:
 * - `hash.driver` — `bcrypt` (default) or `argon2` / `argon2id`
 * - `auth.passwords.driver` — `array` (default) or `database` (needs [Database])
 * - `auth.passwords.table` — reset-token table; falls back to `auth.passwords.users.table`
 * - `auth.tokens.driver` — `array` (default) or `database` (needs [Database])
 * - `auth.two_factor.confirm` — require confirmation OTP before 2FA counts as enabled (default true)
 * - `auth.two_factor.window` — TOTP clock-skew window in steps (default 1)
 *
 * Wire [CadenceUserProvider] in app bootstrap when authenticating Cadence models.
 */
class AuthServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        val rounds = app.config.getInt("hash.rounds", BcryptHasher.DEFAULT_ROUNDS)
        val hashDriver = app.config.getString("hash.driver") ?: "bcrypt"
        val hashManager = HashManager(
            defaultDriver = "bcrypt",
            defaultRounds = rounds,
            argon2Memory = app.config.getInt("hash.argon2.memory", Argon2Hasher.DEFAULT_MEMORY),
            argon2Iterations = app.config.getInt("hash.argon2.iterations", Argon2Hasher.DEFAULT_ITERATIONS),
            argon2Parallelism = app.config.getInt("hash.argon2.parallelism", Argon2Hasher.DEFAULT_PARALLELISM),
        )
        when (hashDriver) {
            "argon2", "argon2id" -> hashManager.setDefaultDriver(hashDriver)
        }
        app.container.instance(HashManager::class.java, hashManager)
        app.container.alias("hash", HashManager::class.java.name)
        Hash.setManager(hashManager)
        CastRegistry.register("hashed") { HashedCast() }

        val users = InMemoryUserProvider(hashManager.driver())
        app.container.instance(InMemoryUserProvider::class.java, users)
        app.container.alias(UserProvider::class.java.name, InMemoryUserProvider::class.java.name)

        val auth = AuthManager()
        auth.provider("users", users)
        auth.extend("web", SessionGuard("web", users))
        app.container.instance(AuthManager::class.java, auth)
        app.container.alias("auth", AuthManager::class.java.name)
        Auth.setManager(auth)

        val tokenRepo = resolveTokenRepository(app)
        app.container.instance(PersonalAccessTokenRepository::class.java, tokenRepo)
        val tokens = PersonalAccessTokenManager(
            repository = tokenRepo,
            users = users,
        )
        app.container.instance(PersonalAccessTokenManager::class.java, tokens)
        auth.extend("api", TokenGuard("api", tokens, users))

        val resetRepo = resolvePasswordRepository(app)
        app.container.instance(PasswordResetTokenRepository::class.java, resetRepo)
        val passwords = PasswordBroker(users, hashManager.driver(), tokens = resetRepo)
        app.container.instance(PasswordBroker::class.java, passwords)
        app.container.alias("auth.password", PasswordBroker::class.java.name)

        val gate = Gate().setUserResolver { auth.user() }
        Gate.setDefault(gate)
        app.container.instance(Gate::class.java, gate)
        app.container.alias("gate", Gate::class.java.name)

        val rateLimiter = RateLimiter()
        app.container.instance(RateLimiter::class.java, rateLimiter)
        app.container.alias("rateLimiter", RateLimiter::class.java.name)

        registerEncrypter(app)
        registerTwoFactor(app)
    }

    private fun registerTwoFactor(app: Application) {
        val confirms = app.config.getString("auth.two_factor.confirm")
            ?.let { it.equals("true", ignoreCase = true) || it == "1" }
            ?: true
        TwoFactor.setRequiresConfirmation(confirms)
        val window = app.config.getInt("auth.two_factor.window", 1)
        val provider = TwoFactorAuthenticationProvider(window = window)
        app.container.instance(TwoFactorAuthenticationProvider::class.java, provider)
        TwoFactor.setProvider(provider)

        val encrypter =
            if (app.container.bound(Encrypter::class.java)) {
                app.resolve(Encrypter::class.java)
            } else {
                null
            }
        TwoFactor.setEncrypter(encrypter)
        if (encrypter != null) {
            app.container.instance(
                EnableTwoFactorAuthentication::class.java,
                EnableTwoFactorAuthentication(provider, encrypter),
            )
            app.container.instance(
                ConfirmTwoFactorAuthentication::class.java,
                ConfirmTwoFactorAuthentication(provider, encrypter),
            )
            app.container.instance(DisableTwoFactorAuthentication::class.java, DisableTwoFactorAuthentication())
            app.container.instance(
                GenerateNewRecoveryCodes::class.java,
                GenerateNewRecoveryCodes(encrypter),
            )
        }
    }

    private fun passwordResetTable(app: Application): String {
        val top = app.config.getString("auth.passwords.table")
        if (!top.isNullOrBlank()) {
            return top
        }
        return app.config.getString("auth.passwords.users.table", "password_reset_tokens")
    }

    override fun boot(app: Application) {
        // Encrypter may be absent until app.key is configured; Hash is already set in register.
    }

    private fun resolvePasswordRepository(app: Application): PasswordResetTokenRepository {
        val driver = app.config.getString("auth.passwords.driver", "array")
        if (driver == "database" && app.container.bound(Database::class.java)) {
            val table = passwordResetTable(app)
            return DatabasePasswordResetTokenRepository(
                app.resolve(Database::class.java).connection(),
                table,
            )
        }
        return InMemoryPasswordResetTokenRepository()
    }

    private fun resolveTokenRepository(app: Application): PersonalAccessTokenRepository {
        val driver = app.config.getString("auth.tokens.driver", "array")
        if (driver == "database" && app.container.bound(Database::class.java)) {
            val table = app.config.getString("auth.tokens.table", "personal_access_tokens")
            return DatabasePersonalAccessTokenRepository(
                app.resolve(Database::class.java).connection(),
                table,
            )
        }
        return InMemoryPersonalAccessTokenRepository()
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
