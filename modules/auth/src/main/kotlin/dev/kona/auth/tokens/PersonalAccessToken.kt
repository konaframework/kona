package dev.kona.auth.tokens

import java.time.Instant

/**
 * Stored personal access token (Sanctum-class). Plain text is shown only at creation.
 */
data class PersonalAccessToken(
    val id: String,
    val tokenableId: Any,
    val name: String,
    val tokenHash: String,
    val abilities: List<String> = listOf("*"),
    val createdAt: Instant = Instant.now(),
    var lastUsedAt: Instant? = null,
) {
    fun can(ability: String): Boolean =
        abilities.contains("*") || abilities.contains(ability)
}

/**
 * Result of [PersonalAccessTokenManager.create] — plain text returned once.
 */
data class NewAccessToken(
    val accessToken: PersonalAccessToken,
    val plainTextToken: String,
)
