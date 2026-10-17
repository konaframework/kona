package org.konaframework.auth.passwords

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Persists password-reset tokens (Laravel password reset repository shape). */
interface PasswordResetTokenRepository {
    fun save(record: PasswordBroker.ResetRecord)

    fun find(email: String): PasswordBroker.ResetRecord?

    fun delete(email: String)

    fun clear()
}

/** In-memory token store (default for tests and zero-config boot). */
class InMemoryPasswordResetTokenRepository : PasswordResetTokenRepository {
    private val tokens = ConcurrentHashMap<String, PasswordBroker.ResetRecord>()

    override fun save(record: PasswordBroker.ResetRecord) {
        tokens[record.email] = record
    }

    override fun find(email: String): PasswordBroker.ResetRecord? = tokens[email]

    override fun delete(email: String) {
        tokens.remove(email)
    }

    override fun clear() {
        tokens.clear()
    }

    fun all(): Map<String, PasswordBroker.ResetRecord> = tokens.toMap()
}

/**
 * JDBC-backed reset tokens (`password_reset_tokens` by default).
 *
 * Survives process restarts when the same database is reused.
 */
class DatabasePasswordResetTokenRepository @JvmOverloads constructor(
    private val connection: org.konaframework.cadence.DatabaseConnection,
    private val table: String = "password_reset_tokens",
) : PasswordResetTokenRepository {
    init {
        ensureTable()
    }

    fun ensureTable() {
        connection.statement(
            """
            CREATE TABLE IF NOT EXISTS $table (
              email VARCHAR(255) PRIMARY KEY,
              token VARCHAR(255) NOT NULL,
              user_id VARCHAR(255) NOT NULL,
              created_at BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun save(record: PasswordBroker.ResetRecord) {
        delete(record.email)
        connection.insert(
            "INSERT INTO $table (email, token, user_id, created_at) VALUES (?, ?, ?, ?)",
            listOf(record.email, record.tokenHash, record.userId.toString(), record.createdAt.toEpochMilli()),
        )
    }

    override fun find(email: String): PasswordBroker.ResetRecord? {
        val rows = connection.select(
            "SELECT email, token, user_id, created_at FROM $table WHERE email = ?",
            listOf(email),
        )
        val row = rows.firstOrNull() ?: return null
        return PasswordBroker.ResetRecord(
            email = cell(row, "email").toString(),
            tokenHash = cell(row, "token").toString(),
            userId = cell(row, "user_id").toString(),
            createdAt = Instant.ofEpochMilli((cell(row, "created_at") as Number).toLong()),
        )
    }

    override fun delete(email: String) {
        connection.delete("DELETE FROM $table WHERE email = ?", listOf(email))
    }

    override fun clear() {
        connection.delete("DELETE FROM $table", emptyList())
    }

    private fun cell(row: Map<String, Any?>, name: String): Any {
        for ((key, value) in row) {
            if (key.equals(name, ignoreCase = true) && value != null) {
                return value
            }
        }
        error("$table row missing [$name]")
    }
}
