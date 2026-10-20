package org.konaframework.cadence.schema

import java.sql.Connection
import java.sql.DatabaseMetaData

/** Java-friendly table callback for [Schema.create] and [Schema.table]. */
fun interface TableDefinition {
    fun define(table: Blueprint)
}

/** One column as reported by JDBC metadata ([Schema.columns], `grind db:table`). */
data class SchemaColumn(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val defaultValue: String,
    val size: Int,
) {
    /** Driver type, with `(size)` appended for character types that report a length. */
    fun typeLabel(): String {
        val upper = type.uppercase()
        return if (size > 0 && (upper == "VARCHAR" || upper == "CHAR" || upper == "NVARCHAR" || upper == "VARCHAR2")) {
            "$type($size)"
        } else {
            type
        }
    }
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

    fun dropViewIfExists(view: String) {
        execute(listOf(grammar.compileDropViewIfExists(view)))
    }

    fun rename(from: String, to: String) {
        execute(listOf(grammar.compileRename(from, to)))
    }

    /** User tables in the current schema (excludes views, catalogs, and engine bookkeeping like `sqlite_sequence`). */
    fun tables(): List<String> = listObjects("TABLE", "BASE TABLE")

    /** User views in the current schema. */
    fun views(): List<String> = listObjects("VIEW")

    /** Columns of [table] as the driver reports them, or empty when the table is missing. */
    fun columns(table: String): List<SchemaColumn> =
        firstNonEmpty(table) { name ->
            connection.metaData.getColumns(null, connection.schema, name, "%").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SchemaColumn(
                                name = rs.getString("COLUMN_NAME"),
                                type = rs.getString("TYPE_NAME"),
                                nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                                defaultValue = rs.getString("COLUMN_DEF") ?: "",
                                size = rs.getInt("COLUMN_SIZE"),
                            ),
                        )
                    }
                }
            }
        }

    /** Primary-key column names of [table], in key order. */
    fun primaryKey(table: String): List<String> =
        firstNonEmpty(table) { name ->
            connection.metaData.getPrimaryKeys(null, connection.schema, name).use { rs ->
                buildList {
                    val keys = mutableListOf<Pair<Int, String>>()
                    while (rs.next()) {
                        keys += rs.getInt("KEY_SEQ") to rs.getString("COLUMN_NAME")
                    }
                    keys.sortedBy { it.first }.forEach { add(it.second) }
                }
            }
        }

    /**
     * Drops every user table on this connection. Foreign keys are disabled (or `CASCADE` is used)
     * according to [grammar]. Returns the names that were dropped.
     */
    fun dropAllTables(): List<String> {
        val names = tables()
        if (names.isEmpty()) {
            return emptyList()
        }
        execute(listOfNotNull(grammar.compileDisableForeignKeyConstraints()))
        try {
            val compiled = grammar.compileDropAllTables(names)
            if (compiled != null) {
                execute(compiled)
            } else {
                names.forEach { dropIfExists(it) }
            }
        } finally {
            execute(listOfNotNull(grammar.compileEnableForeignKeyConstraints()))
        }
        val reset = grammar.compileResetSequences()
        if (reset.isNotEmpty() && hasTable("sqlite_sequence")) {
            execute(reset)
        }
        return names
    }

    /** Drops every user view. Returns the names that were dropped. */
    fun dropAllViews(): List<String> {
        val names = views()
        names.forEach { dropViewIfExists(it) }
        return names
    }

    /**
     * Drops user-defined types (PostgreSQL enums, for example) when the grammar can list them.
     * No-op on engines that do not support `DROP TYPE`.
     */
    fun dropAllTypes(): List<String> {
        val sql = grammar.listUserTypesSql() ?: return emptyList()
        val names = connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        val name = rs.getString(1) ?: continue
                        add(name)
                    }
                }
            }
        }
        names.forEach { execute(listOf(grammar.compileDropTypeIfExists(it))) }
        return names
    }

    /**
     * Reconstructs `CREATE TABLE` (and extra indexes) from JDBC metadata. Accurate enough for a
     * portable schema dump; native `pg_dump` / `mysqldump` output is not used.
     */
    fun dump(): String {
        val tables = tables()
        if (tables.isEmpty()) {
            return ""
        }
        return tables.joinToString("\n\n") { dumpTable(it) } + "\n"
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

    private fun listObjects(vararg types: String): List<String> =
        connection.metaData.getTables(null, connection.schema, "%", types).use { rs ->
            buildList {
                while (rs.next()) {
                    val name = rs.getString("TABLE_NAME")
                    if (grammar.skipTable(name) || grammar.skipSchema(rs.getString("TABLE_SCHEM"))) {
                        continue
                    }
                    add(name)
                }
            }.distinct()
        }

    private fun <T> firstNonEmpty(table: String, read: (String) -> List<T>): List<T> =
        variants(table).firstNotNullOfOrNull { name -> read(name).takeIf { it.isNotEmpty() } } ?: emptyList()

    private fun dumpTable(table: String): String {
        val cols = columns(table)
        val pk = primaryKey(table)
        val body = cols.map { dumpColumn(it) } +
            if (pk.isNotEmpty()) {
                listOf("PRIMARY KEY (" + pk.joinToString(", ") { grammar.wrap(it) } + ")")
            } else {
                emptyList()
            }
        return "CREATE TABLE " + grammar.wrap(table) + " (\n    " + body.joinToString(",\n    ") + "\n);"
    }

    private fun dumpColumn(column: SchemaColumn): String {
        val nullSql = if (column.nullable) "NULL" else "NOT NULL"
        val defaultSql = if (column.defaultValue.isEmpty()) "" else " DEFAULT ${column.defaultValue}"
        return grammar.wrap(column.name) + " " + column.typeLabel() + " " + nullSql + defaultSql
    }

    private fun variants(name: String): List<String> = linkedSetOf(name, name.uppercase(), name.lowercase()).toList()
}
