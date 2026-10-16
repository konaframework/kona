package io.konaframework.cadence.migrations

import io.konaframework.cadence.DatabaseConnection
import io.konaframework.cadence.schema.Schema
import io.konaframework.cadence.schema.SchemaGrammar
import java.sql.Connection
import java.util.function.Function

/**
 * Applies and reverts [MigrationRegistry] entries on one JDBC [connection], recording each run in
 * the [table] (default `extract_migrations`) together with its batch number. Every `migrate()`
 * call that runs something opens a new batch; `rollback()` reverts whole batches, newest first.
 */
class Migrator @JvmOverloads constructor(
    private val connection: Connection,
    private val registry: MigrationRegistry,
    grammar: SchemaGrammar? = null,
    val table: String = DEFAULT_TABLE,
) {
    val schema: Schema = if (grammar == null) Schema(connection) else Schema(connection, grammar)

    private val quotedTable: String get() = schema.grammar.wrap(table)

    /** Creates the bookkeeping table when it does not exist yet. */
    fun install() {
        if (!schema.hasTable(table)) {
            schema.create(table) {
                id()
                string("migration")
                integer("batch")
            }
        }
    }

    /** Names already recorded as run, oldest first. */
    fun ran(): List<String> = records().map { it.first }

    fun pending(): List<String> {
        val done = ran().toSet()
        return registry.names.filter { it !in done }
    }

    fun status(): List<MigrationStatus> {
        val batches = records().toMap()
        return registry.names.map { MigrationStatus(it, batches[it]) }
    }

    /** Runs every pending migration in one new batch; returns the names that ran. */
    fun migrate(): List<String> {
        val pending = pending()
        if (pending.isEmpty()) {
            return emptyList()
        }
        val batch = (records().map { it.second }.maxOrNull() ?: 0) + 1
        pending.forEach { name ->
            transaction(name) {
                registry[name]!!.up(schema)
                connection.prepareStatement(
                    "INSERT INTO $quotedTable (${wrap("migration")}, ${wrap("batch")}) VALUES (?, ?)",
                ).use { statement ->
                    statement.setString(1, name)
                    statement.setInt(2, batch)
                    statement.executeUpdate()
                }
            }
        }
        return pending
    }

    /** Reverts the last [steps] batches, newest migration first; returns the names reverted. */
    @JvmOverloads
    fun rollback(steps: Int = 1): List<String> {
        require(steps > 0) { "Rollback steps must be positive" }
        val records = records()
        val batches = records.map { it.second }.distinct().sortedDescending().take(steps).toSet()
        return revert(records.filter { it.second in batches }.map { it.first }.reversed())
    }

    /** Reverts every migration that has run. */
    fun reset(): List<String> = revert(ran().reversed())

    private fun revert(names: List<String>): List<String> {
        names.forEach { name ->
            val migration = registry[name] ?: throw MigrationException("Migration not found: $name")
            transaction(name) {
                migration.down(schema)
                connection.prepareStatement("DELETE FROM $quotedTable WHERE ${wrap("migration")} = ?").use {
                    it.setString(1, name)
                    it.executeUpdate()
                }
            }
        }
        return names
    }

    private fun records(): List<Pair<String, Int>> {
        install()
        val sql = "SELECT ${wrap("migration")}, ${wrap("batch")} FROM $quotedTable ORDER BY ${wrap("id")}"
        return connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString(1) to rs.getInt(2))
                    }
                }
            }
        }
    }

    private fun transaction(name: String, block: () -> Unit) {
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            block()
            connection.commit()
        } catch (ex: Exception) {
            connection.rollback()
            throw MigrationException("Migration [$name] failed: ${ex.message}", ex)
        } finally {
            connection.autoCommit = autoCommit
        }
    }

    private fun wrap(identifier: String): String = schema.grammar.wrap(identifier)

    companion object {
        const val DEFAULT_TABLE = "extract_migrations"

        /**
         * Borrows one JDBC connection from [connection] for the duration of [block], with a schema
         * grammar matching the connection's identifier quoting.
         */
        @JvmStatic
        @JvmOverloads
        fun <R> using(
            connection: DatabaseConnection,
            registry: MigrationRegistry,
            table: String = DEFAULT_TABLE,
            block: Function<Migrator, R>,
        ): R = connection.withConnection { jdbc ->
            val grammar = SchemaGrammar.forProduct(jdbc.metaData.databaseProductName, connection.grammar)
            block.apply(Migrator(jdbc, registry, grammar, table))
        }
    }
}
