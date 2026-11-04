package org.konaframework.cadence

import org.slf4j.LoggerFactory
import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Savepoint
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.ArrayDeque
import java.util.function.Function
import javax.sql.DataSource

/**
 * One named connection backed by a [DataSource] (a HikariCP pool when configured through [Database]).
 *
 * Every value reaches the driver as a `PreparedStatement` parameter. Inside [transaction] /
 * [beginTransaction] all statements issued on the same thread share the transaction's JDBC connection.
 *
 * Nested [transaction] / [beginTransaction] calls use JDBC savepoints so an inner failure can roll
 * back only its own work. [afterCommit] callbacks run after the outermost commit and are discarded
 * on rollback.
 */
class DatabaseConnection @JvmOverloads constructor(
    val name: String,
    val dataSource: DataSource,
    val grammar: Grammar = Grammar.ANSI,
) : AutoCloseable {
    private val tx = ThreadLocal.withInitial { TxState() }

    private fun state(): TxState = tx.get()

    /**
     * Runs [block] with a JDBC connection (the transaction's one when inside [transaction]).
     * A [java.sql.SQLException] from the pool or [block] is rethrown as [CadenceException].
     */
    fun <R> withConnection(block: ConnectionCallback<R>): R {
        val current = state().bound
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

    /**
     * Executes an insert and returns the first generated key, or null when the driver reports none.
     * [keyColumn] is used on engines (Oracle) that require named generated-key columns.
     */
    @JvmOverloads
    fun insertGetId(sql: String, bindings: List<Any?> = emptyList(), keyColumn: String = "id"): Any? =
        run(sql, bindings, generatedKeys = true, keyColumn = keyColumn) { statement ->
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
     * Nested calls create savepoints.
     */
    fun <R> transaction(block: Function<DatabaseConnection, R>): R = transaction(block, 1)

    /**
     * Runs [block] in a transaction. When [attempts] is greater than 1, the whole closure is
     * retried on deadlock / serialization failure ([ConcurrencyErrorDetector]).
     */
    fun <R> transaction(block: Function<DatabaseConnection, R>, attempts: Int): R {
        require(attempts >= 1) { "Transaction attempts must be at least 1." }
        var attempt = 1
        while (true) {
            beginTransaction()
            val result: R
            try {
                result = block.apply(this)
            } catch (error: Throwable) {
                handleTransactionException(error, attempt, attempts)
                attempt++
                continue
            }
            try {
                commit()
            } catch (error: Throwable) {
                handleCommitException(error, attempt, attempts)
                attempt++
                continue
            }
            return result
        }
    }

    /** Current nesting depth (0 = not in a transaction). */
    fun transactionLevel(): Int = state().level

    /** True when [transactionLevel] is greater than zero. */
    fun inTransaction(): Boolean = transactionLevel() > 0

    /**
     * Begins a transaction or creates a savepoint when already nested.
     * Advanced callers must pair this with [commit] or [rollBack].
     */
    fun beginTransaction() {
        val txState = state()
        val current = txState.level
        if (current == 0) {
            val connection = txState.bound ?: dataSource.connection.also { borrowed ->
                txState.bound = borrowed
                txState.ownsBound = true
            }
            txState.previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
        } else {
            val connection = txState.bound
                ?: throw CadenceException("Cannot create a savepoint: no JDBC connection is bound.")
            try {
                txState.savepoints.addLast(connection.setSavepoint("kona_trans_${current + 1}"))
            } catch (e: SQLException) {
                throw CadenceException("Failed to create savepoint on connection [$name]: ${e.message}", e)
            }
        }
        txState.level = current + 1
    }

    /**
     * Commits the innermost transaction level. The real JDBC commit (and [afterCommit] dispatch)
     * happens only when the outermost level commits.
     */
    fun commit() {
        val txState = state()
        val current = txState.level
        if (current == 0) {
            throw CadenceException("There is no active transaction to commit on connection [$name].")
        }
        val connection = txState.bound
            ?: throw CadenceException("Cannot commit: no JDBC connection is bound.")
        if (current > 1) {
            val savepoint = txState.savepoints.removeLast()
            try {
                connection.releaseSavepoint(savepoint)
            } catch (_: SQLException) {
                // Some drivers do not support RELEASE; the savepoint is still discarded from our stack.
            }
            txState.level = current - 1
            return
        }
        try {
            connection.commit()
        } catch (e: SQLException) {
            throw CadenceException("Failed to commit transaction on connection [$name]: ${e.message}", e)
        }
        txState.level = 0
        txState.savepoints.clear()
        val callbacks = txState.afterCommitCallbacks.map { it.callback }
        txState.afterCommitCallbacks.clear()
        releaseBoundConnection(connection)
        callbacks.forEach { it.run() }
    }

    /** Rolls the innermost transaction level back one step. */
    fun rollBack() {
        val current = state().level
        if (current == 0) {
            throw CadenceException("There is no active transaction to roll back on connection [$name].")
        }
        rollBackTo(current - 1)
    }

    /**
     * Rolls back to [toLevel] (0 = full rollback of the outermost transaction).
     */
    fun rollBack(toLevel: Int) {
        val current = state().level
        if (current == 0) {
            throw CadenceException("There is no active transaction to roll back on connection [$name].")
        }
        require(toLevel in 0 until current) {
            "Rollback level must be between 0 and ${current - 1} (got $toLevel)."
        }
        rollBackTo(toLevel)
    }

    private fun rollBackTo(target: Int) {
        val txState = state()
        val connection = txState.bound
            ?: throw CadenceException("Cannot roll back: no JDBC connection is bound.")
        if (target == 0) {
            var rollbackError: SQLException? = null
            try {
                connection.rollback()
            } catch (e: SQLException) {
                rollbackError = e
            }
            txState.level = 0
            txState.savepoints.clear()
            txState.afterCommitCallbacks.clear()
            releaseBoundConnection(connection)
            if (rollbackError != null) {
                throw CadenceException(
                    "Failed to roll back transaction on connection [$name]: ${rollbackError.message}",
                    rollbackError,
                )
            }
            return
        }
        while (txState.level > target) {
            val savepoint = txState.savepoints.removeLast()
            try {
                connection.rollback(savepoint)
            } catch (e: SQLException) {
                throw CadenceException("Failed to roll back to savepoint on connection [$name]: ${e.message}", e)
            }
            txState.level -= 1
        }
        discardCallbacksDeeperThan(target)
    }

    /**
     * Registers work to run after the outermost transaction commits.
     * When not inside a transaction, [callback] runs immediately.
     */
    fun afterCommit(callback: Runnable) {
        if (transactionLevel() == 0) {
            callback.run()
            return
        }
        state().afterCommitCallbacks.add(AfterCommitCallback(transactionLevel(), callback))
    }

    /** Closes the underlying pool when the data source is closeable. */
    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    private fun handleTransactionException(error: Throwable, attempt: Int, maxAttempts: Int) {
        val txState = state()
        val current = txState.level
        if (ConcurrencyErrorDetector.causedByConcurrencyError(error) && current > 1) {
            // Engines such as MySQL roll back the whole transaction on deadlock; drop one level and rethrow.
            txState.level = current - 1
            if (txState.savepoints.isNotEmpty()) {
                txState.savepoints.removeLast()
            }
            discardCallbacksDeeperThan(txState.level)
            throw error
        }
        rollBack()
        if (ConcurrencyErrorDetector.causedByConcurrencyError(error) && attempt < maxAttempts) {
            return
        }
        throw error
    }

    private fun handleCommitException(error: Throwable, attempt: Int, maxAttempts: Int) {
        if (state().level > 0) {
            try {
                rollBack()
            } catch (_: Throwable) {
                // Prefer the original commit failure.
            }
        }
        if (ConcurrencyErrorDetector.causedByConcurrencyError(error) && attempt < maxAttempts) {
            return
        }
        throw error
    }

    private fun discardCallbacksDeeperThan(maxLevel: Int) {
        state().afterCommitCallbacks.removeAll { it.level > maxLevel }
    }

    private fun releaseBoundConnection(connection: Connection) {
        val txState = state()
        val previous = txState.previousAutoCommit
        txState.previousAutoCommit = null
        try {
            connection.autoCommit = previous ?: true
        } catch (_: SQLException) {
            // Connection may already be closed by the driver after a hard failure.
        }
        if (!txState.ownsBound) {
            return
        }
        txState.ownsBound = false
        txState.bound = null
        try {
            connection.close()
        } catch (_: SQLException) {
            // Returning a broken connection to the pool is best-effort.
        }
    }

    private fun <R> run(
        sql: String,
        bindings: List<Any?>,
        generatedKeys: Boolean = false,
        keyColumn: String = "id",
        action: (PreparedStatement) -> R,
    ): R {
        log.debug("[{}] {} {}", name, sql, bindings)
        return withConnection { connection ->
            try {
                val statement = if (generatedKeys) {
                    val named = grammar.generatedKeyColumns(keyColumn)
                    if (named != null) {
                        connection.prepareStatement(sql, named)
                    } else {
                        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                    }
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
            val bound = grammar.bindValue(value)
            when (bound) {
                null -> statement.setNull(position, Types.NULL)
                is Instant -> statement.setTimestamp(position, Timestamp.from(bound))
                is Enum<*> -> statement.setString(position, bound.name)
                else -> statement.setObject(position, bound)
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

    private data class AfterCommitCallback(val level: Int, val callback: Runnable)

    private class TxState {
        var bound: Connection? = null
        var ownsBound: Boolean = false
        var previousAutoCommit: Boolean? = null
        var level: Int = 0
        val savepoints = ArrayDeque<Savepoint>()
        val afterCommitCallbacks = mutableListOf<AfterCommitCallback>()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(DatabaseConnection::class.java)
    }
}
