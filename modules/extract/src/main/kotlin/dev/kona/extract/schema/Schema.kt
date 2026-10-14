package dev.kona.extract.schema

import java.sql.Connection

/** Java-friendly table callback for [Schema.create] and [Schema.table]. */
fun interface TableDefinition {
    fun define(table: Blueprint)
}

/**
 * Runs DDL against one JDBC [connection]. Kotlin callers use the receiver DSL
 * (`schema.create("users") { id(); string("email").unique() }`); Java callers pass a
 * [TableDefinition] (`schema.create("users", t -> { t.id(); t.string("email").unique(); })`).
 */
class Schema @JvmOverloads constructor(
    val connection: Connection,
    val grammar: SchemaGrammar = SchemaGrammar.forProduct(connection.metaData.databaseProductName),
) {
    fun create(table: String, definition: TableDefinition) {
        execute(grammar.compileCreate(Blueprint(table).also { definition.define(it) }))
    }

    @JvmSynthetic
    fun create(table: String, block: Blueprint.() -> Unit) {
        create(table, TableDefinition { it.block() })
    }

    /** Alters an existing table: add or drop columns, indexes, and foreign keys. */
    fun table(table: String, definition: TableDefinition) {
        execute(grammar.compileAlter(Blueprint(table).also { definition.define(it) }))
    }

    @JvmSynthetic
    fun table(table: String, block: Blueprint.() -> Unit) {
        table(table, TableDefinition { it.block() })
    }

    fun drop(table: String) {
        execute(listOf(grammar.compileDrop(table)))
    }

    fun dropIfExists(table: String) {
        execute(listOf(grammar.compileDropIfExists(table)))
    }

    fun rename(from: String, to: String) {
        execute(listOf(grammar.compileRename(from, to)))
    }

    /** True when [table] exists in the connection's current schema (matched as written, upper-, or lower-case). */
    fun hasTable(table: String): Boolean = variants(table).any { name ->
        connection.metaData.getTables(null, connection.schema, name, null).use { it.next() }
    }

    fun hasColumn(table: String, column: String): Boolean = variants(table).any { name ->
        variants(column).any { col ->
            connection.metaData.getColumns(null, connection.schema, name, col).use { it.next() }
        }
    }

    /** Runs raw SQL statements in order. */
    fun execute(statements: List<String>) {
        connection.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
    }

    private fun variants(name: String): List<String> = linkedSetOf(name, name.uppercase(), name.lowercase()).toList()
}
