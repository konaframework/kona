package dev.kona.cadence

import org.slf4j.LoggerFactory
import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.function.Function
import javax.sql.DataSource

/**
 * One named connection backed by a [DataSource] (a HikariCP pool when configured through [Database]).
 *
 * Every value reaches the driver as a `PreparedStatement` parameter. Inside [transaction] all
 * statements issued on the same thread share the transaction's JDBC connection.
 */
class DatabaseConnection @JvmOverloads constructor(
    val name: String,
    val dataSource: DataSource,
    val grammar: Grammar = Grammar.ANSI,
) : AutoCloseable {
    private val bound = ThreadLocal<Connection?>()

    /**
     * Runs [block] with a JDBC connection (the transaction's one when inside [transaction]).
     * A [java.sql.SQLException] from the pool or [block] is rethrown as [CadenceException].
     */
    fun <R> withConnection(block: ConnectionCallback<R>): R {
        val current = bound.get()
        try {
            if (current != null) {
                return block.withConnection(current)
            }
            return dataSource.connection.use { block.withConnection(it) }
        } catch (e: SQLException) {
            throw CadenceException("Database error on connection [$name]: ${e.message}", e)
        }
    }

    /** Starts a fluent query against [table]. */
    fun table(table: String): QueryBuilder = QueryBuilder(this).from(table)

    /** Starts a fluent query with no table yet. */
    fun query(): QueryBuilder = QueryBuilder(this)

    @JvmOverloads
    fun select(sql: String, bindings: List<Any?> = emptyList()): List<Map<String, Any?>> =
        run(sql, bindings) { statement ->
            statement.executeQuery().use { readRows(it) }
        }

    @JvmOverloads
    fun selectOne(sql: String, bindings: List<Any?> = emptyList()): Map<String, Any?>? =
        select(sql, bindings).firstOrNull()

    @JvmOverloads
    fun insert(sql: String, bindings: List<Any?> = emptyList()): Boolean = statement(sql, bindings)

    /** Executes an insert and returns the first generated key, or null when the driver reports none. */
    @JvmOverloads
    fun insertGetId(sql: String, bindings: List<Any?> = emptyList()): Any? =
        run(sql, bindings, generatedKeys = true) { statement ->
            statement.executeUpdate()
            statement.generatedKeys.use { keys -> if (keys.next()) keys.getObject(1) else null }
        }

    @JvmOverloads
    fun update(sql: String, bindings: List<Any?> = emptyList()): Int = affectingStatement(sql, bindings)

    @JvmOverloads
    fun delete(sql: String, bindings: List<Any?> = emptyList()): Int = affectingStatement(sql, bindings)

    @JvmOverloads
    fun statement(sql: String, bindings: List<Any?> = emptyList()): Boolean =
        run(sql, bindings) { statement ->
            statement.execute()
            true
        }

    @JvmOverloads
    fun affectingStatement(sql: String, bindings: List<Any?> = emptyList()): Int =
        run(sql, bindings) { it.executeUpdate() }

    /**
     * Runs [block] in a transaction: commit on return, rollback on any throwable (rethrown).
     * Nested calls join the outer transaction.
     */
    fun <R> transaction(block: Function<DatabaseConnection, R>): R {
        if (bound.get() != null) {
            return block.apply(this)
        }
        return dataSource.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            bound.set(connection)
            try {
                val result = block.apply(this)
                connection.commit()
                result
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                bound.remove()
                connection.autoCommit = autoCommit
            }
        }
    }

    fun inTransaction(): Boolean = bound.get() != null

    /** Closes the underlying pool when the data source is closeable. */
    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    private fun <R> run(
        sql: String,
        bindings: List<Any?>,
        generatedKeys: Boolean = false,
        action: (PreparedStatement) -> R,
    ): R {
        log.debug("[{}] {} {}", name, sql, bindings)
        return withConnection { connection ->
            try {
                val statement = if (generatedKeys) {
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                } else {
                    connection.prepareStatement(sql)
                }
                statement.use {
                    bind(it, bindings)
                    action(it)
                }
            } catch (e: SQLException) {
                throw QueryException(sql, bindings, e)
            }
        }
    }

    private fun bind(statement: PreparedStatement, bindings: List<Any?>) {
        bindings.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.setNull(position, Types.NULL)
                is Instant -> statement.setTimestamp(position, Timestamp.from(value))
                is Enum<*> -> statement.setString(position, value.name)
                else -> statement.setObject(position, value)
            }
        }
    }

    private fun readRows(resultSet: ResultSet): List<Map<String, Any?>> {
        val meta = resultSet.metaData
        val rows = mutableListOf<Map<String, Any?>>()
        while (resultSet.next()) {
            val row = linkedMapOf<String, Any?>()
            var column = 1
            while (column <= meta.columnCount) {
                row[meta.getColumnLabel(column)] = readValue(resultSet.getObject(column))
                column++
            }
            rows.add(row)
        }
        return rows
    }

    private fun readValue(value: Any?): Any? = when (value) {
        is Clob -> value.getSubString(1, value.length().toInt())
        is Blob -> value.getBytes(1, value.length().toInt())
        else -> value
    }

    private companion object {
        private val log = LoggerFactory.getLogger(DatabaseConnection::class.java)
    }
}
