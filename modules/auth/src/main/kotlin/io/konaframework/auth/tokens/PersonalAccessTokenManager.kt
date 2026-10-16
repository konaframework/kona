package io.konaframework.auth.tokens

import io.konaframework.auth.Authenticatable
import io.konaframework.auth.UserProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface PersonalAccessTokenRepository {
    fun save(token: PersonalAccessToken)

    fun findById(id: String): PersonalAccessToken?

    fun findByHash(tokenHash: String): PersonalAccessToken?

    fun delete(id: String)

    fun deleteAllFor(tokenableId: Any)

    fun clear()
}

class InMemoryPersonalAccessTokenRepository : PersonalAccessTokenRepository {
    private val byId = ConcurrentHashMap<String, PersonalAccessToken>()

    override fun save(token: PersonalAccessToken) {
        byId[token.id] = token
    }

    override fun findById(id: String): PersonalAccessToken? = byId[id]

    override fun findByHash(tokenHash: String): PersonalAccessToken? =
        byId.values.firstOrNull { it.tokenHash == tokenHash }

    override fun delete(id: String) {
        byId.remove(id)
    }

    override fun deleteAllFor(tokenableId: Any) {
        byId.entries.removeIf { it.value.tokenableId == tokenableId }
    }

    override fun clear() {
        byId.clear()
    }

    fun all(): List<PersonalAccessToken> = byId.values.toList()
}

/**
 * Creates and validates personal access tokens (SHA-256 hash at rest, Sanctum-class).
 *
 * Plain-text format: `{id}|{secret}` — shown once at creation.
 */
class PersonalAccessTokenManager @JvmOverloads constructor(
    private val repository: PersonalAccessTokenRepository = InMemoryPersonalAccessTokenRepository(),
    private val users: UserProvider? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()

    fun create(
        user: Authenticatable,
        name: String,
        abilities: List<String> = listOf("*"),
    ): NewAccessToken {
        val id = UUID.randomUUID().toString().replace("-", "")
        val secret = generateSecret()
        val plain = "$id|$secret"
        val record = PersonalAccessToken(
            id = id,
            tokenableId = user.getAuthIdentifier(),
            name = name,
            tokenHash = hashToken(plain),
            abilities = abilities,
            createdAt = Instant.now(clock),
        )
        repository.save(record)
        return NewAccessToken(record, plain)
    }

    fun findToken(plainTextToken: String): PersonalAccessToken? {
        if (!plainTextToken.contains('|')) {
            return null
        }
        val id = plainTextToken.substringBefore('|')
        if (id.isEmpty()) {
            return null
        }
        val record = repository.findById(id)
        if (record == null) {
            return null
        }
        if (record.tokenHash != hashToken(plainTextToken)) {
            return null
        }
        record.lastUsedAt = Instant.now(clock)
        repository.save(record)
        return record
    }

    fun findUser(plainTextToken: String): Authenticatable? {
        val token = findToken(plainTextToken)
        if (token == null) {
            return null
        }
        val provider = users
        if (provider == null) {
            return null
        }
        return provider.retrieveById(token.tokenableId)
    }

    fun delete(token: PersonalAccessToken) {
        repository.delete(token.id)
    }

    fun deleteAllFor(user: Authenticatable) {
        repository.deleteAllFor(user.getAuthIdentifier())
    }

    fun repository(): PersonalAccessTokenRepository = repository

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        @JvmStatic
        fun hashToken(plainTextToken: String): String =
            HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(plainTextToken.toByteArray(StandardCharsets.UTF_8)),
            )
    }
}
