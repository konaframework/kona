package dev.kona.testing

import dev.kona.extract.Database
import java.util.function.Function

/**
 * Database testing helpers (refresh / transactional).
 */
object DatabaseTest {
    @JvmStatic
    fun <R> transactional(database: Database, block: Function<Database, R>): R =
        database.connection().transaction(Function { _ -> block.apply(database) })

    @JvmStatic
    fun refresh(database: Database, tables: List<String>) {
        val connection = database.connection()
        tables.forEach { table ->
            connection.statement("DELETE FROM $table")
        }
    }
}
