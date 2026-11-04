package org.konaframework.cadence

import java.util.function.Function

/**
 * Static facade over the global [Database] (`Database.setAsGlobal()` or `CadenceServiceProvider`).
 *
 * ```java
 * List<Map<String, Object>> users = DB.table("users").where("active", true).get();
 * ```
 */
object DB {
    @JvmStatic
    fun connection(): DatabaseConnection = Database.global().connection()

    @JvmStatic
    fun connection(name: String): DatabaseConnection = Database.global().connection(name)

    @JvmStatic
    fun table(table: String): QueryBuilder = connection().table(table)

    @JvmStatic
    @JvmOverloads
    fun select(sql: String, bindings: List<Any?> = emptyList()): List<Map<String, Any?>> = connection().select(sql, bindings)

    @JvmStatic
    @JvmOverloads
    fun insert(sql: String, bindings: List<Any?> = emptyList()): Boolean = connection().insert(sql, bindings)

    @JvmStatic
    @JvmOverloads
    fun update(sql: String, bindings: List<Any?> = emptyList()): Int = connection().update(sql, bindings)

    @JvmStatic
    @JvmOverloads
    fun delete(sql: String, bindings: List<Any?> = emptyList()): Int = connection().delete(sql, bindings)

    @JvmStatic
    @JvmOverloads
    fun statement(sql: String, bindings: List<Any?> = emptyList()): Boolean = connection().statement(sql, bindings)

    /** Runs [block] in a transaction on the default connection. */
    @JvmStatic
    fun <R> transaction(block: Function<DatabaseConnection, R>): R = connection().transaction(block)

    /**
     * Runs [block] in a transaction on the default connection.
     * Pass [attempts] greater than 1 to retry on deadlock / serialization failure.
     */
    @JvmStatic
    fun <R> transaction(block: Function<DatabaseConnection, R>, attempts: Int): R =
        connection().transaction(block, attempts)

    /** Registers work that runs after the outermost transaction commits (or immediately if none). */
    @JvmStatic
    fun afterCommit(callback: Runnable) {
        connection().afterCommit(callback)
    }

    /** Current transaction nesting depth on the default connection. */
    @JvmStatic
    fun transactionLevel(): Int = connection().transactionLevel()

    @JvmStatic
    fun beginTransaction() {
        connection().beginTransaction()
    }

    @JvmStatic
    fun commit() {
        connection().commit()
    }

    @JvmStatic
    fun rollBack() {
        connection().rollBack()
    }

    @JvmStatic
    fun rollBack(toLevel: Int) {
        connection().rollBack(toLevel)
    }
}
