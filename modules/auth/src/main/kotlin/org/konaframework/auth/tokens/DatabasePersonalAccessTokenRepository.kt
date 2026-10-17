package org.konaframework.auth.tokens

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.konaframework.cadence.DatabaseConnection
import java.time.Instant

/**
 * JDBC-backed personal access tokens (`personal_access_tokens` by default).
 */
class DatabasePersonalAccessTokenRepository @JvmOverloads constructor(
    private val connection: DatabaseConnection,
    private val table: String = "personal_access_tokens",
    private val mapper: ObjectMapper = ObjectMapper(),
) : PersonalAccessTokenRepository {
    init {
        ensureTable()
    }

    fun ensureTable() {
        connection.statement(
            """
            CREATE TABLE IF NOT EXISTS $table (
              id VARCHAR(64) PRIMARY KEY,
              tokenable_id VARCHAR(255) NOT NULL,
              name VARCHAR(255) NOT NULL,
              token_hash VARCHAR(128) NOT NULL,
              abilities CLOB NOT NULL,
              created_at BIGINT NOT NULL,
              last_used_at BIGINT
            )
            """.trimIndent(),
        )
    }

    override fun save(token: PersonalAccessToken) {
        delete(token.id)
        connection.insert(
            """
            INSERT INTO $table (id, tokenable_id, name, token_hash, abilities, created_at, last_used_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                token.id,
                token.tokenableId.toString(),
                token.name,
                token.tokenHash,
                mapper.writeValueAsString(token.abilities),
                token.createdAt.toEpochMilli(),
                token.lastUsedAt?.toEpochMilli(),
            ),
        )
    }

    override fun findById(id: String): PersonalAccessToken? {
        val rows = connection.select(
            "SELECT id, tokenable_id, name, token_hash, abilities, created_at, last_used_at FROM $table WHERE id = ?",
            listOf(id),
        )
        return rows.firstOrNull()?.let { toToken(it) }
    }

    override fun findByHash(tokenHash: String): PersonalAccessToken? {
        val rows = connection.select(
            "SELECT id, tokenable_id, name, token_hash, abilities, created_at, last_used_at FROM $table WHERE token_hash = ?",
            listOf(tokenHash),
        )
        return rows.firstOrNull()?.let { toToken(it) }
    }

    override fun delete(id: String) {
        connection.delete("DELETE FROM $table WHERE id = ?", listOf(id))
    }

    override fun deleteAllFor(tokenableId: Any) {
        connection.delete("DELETE FROM $table WHERE tokenable_id = ?", listOf(tokenableId.toString()))
    }

    override fun clear() {
        connection.delete("DELETE FROM $table", emptyList())
    }

    private fun toToken(row: Map<String, Any?>): PersonalAccessToken {
        val abilitiesJson = cell(row, "abilities").toString()
        val abilities = mapper.readValue(abilitiesJson, object : TypeReference<List<String>>() {})
        val lastUsed = optionalCell(row, "last_used_at")
        return PersonalAccessToken(
            id = cell(row, "id").toString(),
            tokenableId = cell(row, "tokenable_id").toString(),
            name = cell(row, "name").toString(),
            tokenHash = cell(row, "token_hash").toString(),
            abilities = abilities,
            createdAt = Instant.ofEpochMilli((cell(row, "created_at") as Number).toLong()),
            lastUsedAt = lastUsed?.let { Instant.ofEpochMilli((it as Number).toLong()) },
        )
    }

    private fun cell(row: Map<String, Any?>, name: String): Any {
        for ((key, value) in row) {
            if (key.equals(name, ignoreCase = true) && value != null) {
                return value
            }
        }
        error("$table row missing [$name]")
    }

    private fun optionalCell(row: Map<String, Any?>, name: String): Any? {
        for ((key, value) in row) {
            if (key.equals(name, ignoreCase = true)) {
                return value
            }
        }
        return null
    }
}
