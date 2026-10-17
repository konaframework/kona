package org.konaframework.auth.passwords

import org.konaframework.auth.Authenticatable
import org.konaframework.auth.UserProvider
import org.konaframework.auth.hashing.Hasher
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Password reset broker (Laravel PasswordBroker shape).
 *
 * Defaults to an in-memory [PasswordResetTokenRepository]; pass
 * [DatabasePasswordResetTokenRepository] for durable tokens.
 */
class PasswordBroker @JvmOverloads constructor(
    private val users: UserProvider,
    private val hasher: Hasher,
    private val expire: Duration = Duration.ofMinutes(60),
    private val clock: Clock = Clock.systemUTC(),
    private val tokens: PasswordResetTokenRepository = InMemoryPasswordResetTokenRepository(),
) {
    private val random = SecureRandom()

    fun repository(): PasswordResetTokenRepository = tokens

    /**
     * Creates a reset token for the user matching [credentials] (typically email).
     * Returns the plain-text token once, or null if no user matched.
     * Uses `email` from credentials when present; otherwise the auth identifier.
     */
    fun createToken(credentials: Map<String, Any?>): String? {
        val filtered = credentials.filterKeys { key -> key != "password" && key != "token" }
        val user = users.retrieveByCredentials(filtered)
        if (user == null) {
            return null
        }
        val plain = generateToken()
        val emailValue = credentials["email"]
        val email = when {
            emailValue != null -> emailValue.toString()
            user is HasPasswordEmail -> {
                val fromUser = user.getEmail()
                if (fromUser != null) fromUser else user.getAuthIdentifier().toString()
            }
            else -> user.getAuthIdentifier().toString()
        }
        tokens.save(
            ResetRecord(
                email = email,
                tokenHash = hasher.make(plain),
                userId = user.getAuthIdentifier(),
                createdAt = Instant.now(clock),
            ),
        )
        return plain
    }

    /** Validates [token] for [email] without consuming it. */
    fun tokenValid(email: String, token: String): Boolean {
        val record = tokens.find(email)
        if (record == null) {
            return false
        }
        if (isExpired(record)) {
            tokens.delete(email)
            return false
        }
        return hasher.check(token, record.tokenHash)
    }

    /**
     * Validates the token and updates the user's password via [callback].
     * Returns [PasswordResetResult].
     */
    fun reset(
        credentials: Map<String, Any?>,
        callback: PasswordResetCallback,
    ): PasswordResetResult {
        val emailValue = credentials["email"]
        if (emailValue == null) {
            return PasswordResetResult.INVALID_USER
        }
        val email = emailValue.toString()
        val tokenValue = credentials["token"]
        if (tokenValue == null) {
            return PasswordResetResult.INVALID_TOKEN
        }
        val token = tokenValue.toString()
        val passwordValue = credentials["password"]
        if (passwordValue == null) {
            return PasswordResetResult.INVALID_PASSWORD
        }
        val password = passwordValue.toString()

        val record = tokens.find(email)
        if (record == null) {
            return PasswordResetResult.INVALID_TOKEN
        }
        if (isExpired(record)) {
            tokens.delete(email)
            return PasswordResetResult.EXPIRED_TOKEN
        }
        if (!hasher.check(token, record.tokenHash)) {
            return PasswordResetResult.INVALID_TOKEN
        }
        val user = users.retrieveById(record.userId)
        if (user == null) {
            return PasswordResetResult.INVALID_USER
        }
        callback.reset(user, password)
        tokens.delete(email)
        return PasswordResetResult.RESET
    }

    fun deleteToken(email: String) {
        tokens.delete(email)
    }

    fun clear() {
        tokens.clear()
    }

    private fun isExpired(record: ResetRecord): Boolean =
        Instant.now(clock).isAfter(record.createdAt.plus(expire))

    private fun generateToken(): String {
        val bytes = ByteArray(40)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    data class ResetRecord(
        val email: String,
        val tokenHash: String,
        val userId: Any,
        val createdAt: Instant,
    )
}

fun interface PasswordResetCallback {
    fun reset(user: Authenticatable, newPassword: String)
}

enum class PasswordResetResult {
    RESET,
    INVALID_USER,
    INVALID_TOKEN,
    INVALID_PASSWORD,
    EXPIRED_TOKEN,
}

/** Optional email accessor for password resets when credentials omit email. */
interface HasPasswordEmail {
    fun getEmail(): String?
}
