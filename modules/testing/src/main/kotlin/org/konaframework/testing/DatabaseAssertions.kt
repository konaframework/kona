package org.konaframework.testing

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.Database
import org.konaframework.cadence.QueryBuilder
import org.konaframework.cadence.SoftDeletes

/**
 * Database assertions (Laravel `assertDatabaseHas` family) over [database]'s default connection,
 * or [connection] when named. Rows are read through the connection the test runs on, so rows
 * written inside a [TestDatabase] transaction are visible. Failures throw [IllegalArgumentException].
 *
 * Column maps match rows by equality; a `null` value matches `IS NULL`.
 */
class DatabaseAssertions(
    private val database: Database,
    private val connection: String?,
) {
    constructor(database: Database) : this(database, null)
    /** Assert [table] has a row matching every entry of [data]. */
    fun assertDatabaseHas(table: String, data: Map<String, Any?>): DatabaseAssertions = apply {
        require(matching(table, data).exists()) {
            "Expected table [$table] to have a row matching $data. ${describe(table)}"
        }
    }

    /** Assert [table] has no row matching [data]. */
    fun assertDatabaseMissing(table: String, data: Map<String, Any?>): DatabaseAssertions = apply {
        require(!matching(table, data).exists()) { "Expected table [$table] to have no row matching $data" }
    }

    /** Assert [table] has exactly [expected] rows. */
    fun assertDatabaseCount(table: String, expected: Int): DatabaseAssertions = apply {
        val actual = query(table).count()
        require(actual == expected.toLong()) { "Expected table [$table] to have $expected rows but it has $actual" }
    }

    /** Assert [table] has no rows. */
    fun assertDatabaseEmpty(table: String): DatabaseAssertions = assertDatabaseCount(table, 0)

    /** Assert the row of [model] (by its primary key) exists, trashed or not. */
    fun assertModelExists(model: Cadence<*>): DatabaseAssertions = assertDatabaseHas(model.table, key(model))

    /** Assert the row of [model] (by its primary key) is gone. */
    fun assertModelMissing(model: Cadence<*>): DatabaseAssertions = assertDatabaseMissing(model.table, key(model))

    /** Assert a row of [table] matching [data] is soft deleted (its `deleted_at` is set). */
    fun assertSoftDeleted(table: String, data: Map<String, Any?>): DatabaseAssertions =
        assertSoftDeleted(table, data, "deleted_at")

    fun assertSoftDeleted(table: String, data: Map<String, Any?>, column: String): DatabaseAssertions = apply {
        require(matching(table, data).whereNotNull(column).exists()) {
            "Expected a soft deleted row in [$table] matching $data. ${describe(table)}"
        }
    }

    /** Assert the row of [model] is soft deleted. */
    fun assertSoftDeleted(model: Cadence<*>): DatabaseAssertions =
        assertSoftDeleted(model.table, key(model), deletedAt(model))

    /** Assert a row of [table] matching [data] exists and is not soft deleted. */
    fun assertNotSoftDeleted(table: String, data: Map<String, Any?>): DatabaseAssertions =
        assertNotSoftDeleted(table, data, "deleted_at")

    fun assertNotSoftDeleted(table: String, data: Map<String, Any?>, column: String): DatabaseAssertions = apply {
        require(matching(table, data).whereNull(column).exists()) {
            "Expected a row in [$table] matching $data that is not soft deleted. ${describe(table)}"
        }
    }

    /** Assert the row of [model] exists and is not soft deleted. */
    fun assertNotSoftDeleted(model: Cadence<*>): DatabaseAssertions =
        assertNotSoftDeleted(model.table, key(model), deletedAt(model))

    private fun query(table: String): QueryBuilder =
        (if (connection == null) database.connection() else database.connection(connection)).table(table)

    private fun matching(table: String, data: Map<String, Any?>): QueryBuilder {
        var query = query(table)
        for ((column, value) in data) {
            query = if (value == null) query.whereNull(column) else query.where(column, value)
        }
        return query
    }

    private fun key(model: Cadence<*>): Map<String, Any?> = mapOf(model.primaryKey to model.getKey())

    private fun deletedAt(model: Cadence<*>): String =
        if (model is SoftDeletes) model.deletedAtColumn else "deleted_at"

    private fun describe(table: String): String {
        val rows = query(table).limit(5).get()
        return if (rows.isEmpty()) "The table is empty." else "First rows: $rows"
    }
}
