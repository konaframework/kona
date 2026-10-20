package org.konaframework.grind

import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaGrammar
import java.nio.file.Files
import java.sql.Connection
import java.sql.DatabaseMetaData

/**
 * `grind db:show`, `db:table <name>`, `schema:dump [--path=]`.
 *
 * Boots the application in-process and inspects the default (or `--database=`) connection.
 */
class DatabaseInspectCommand(private val action: Action) {
    enum class Action { SHOW, TABLE, DUMP }

    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        GrindLogging.hushConsole(io.verbosity == Verbosity.VERBOSE)
        MigrateCommand.withApplication(options) { app, _ ->
            MigrateCommand.withDatabase(app) { database ->
                val name = options["database"]
                val connection = if (name == null) database.connection() else database.connection(name)
                connection.withConnection { jdbc ->
                    val schema = Schema(jdbc, SchemaGrammar.forProduct(jdbc.metaData.databaseProductName, connection.grammar))
                    when (action) {
                        Action.SHOW -> show(jdbc, schema, connection.name, io)
                        Action.TABLE -> table(positional, schema, io)
                        Action.DUMP -> dump(options, schema, io)
                    }
                }
            }
        }
        return 0
    }

    private fun show(jdbc: Connection, schema: Schema, connectionName: String, io: GrindIO) {
        val meta: DatabaseMetaData = jdbc.metaData
        val tables = schema.tables()
        io.title("Database")
        io.twoColumn("Connection", connectionName)
        io.twoColumn("Driver", meta.databaseProductName)
        io.twoColumn("Version", meta.databaseProductVersion)
        io.twoColumn("URL", meta.url)
        io.twoColumn("Username", meta.userName)
        io.twoColumn("Tables", tables.size.toString())
        if (tables.isNotEmpty()) {
            io.title("Tables")
            io.table(
                listOf("Table", "Columns"),
                tables.map { name -> listOf(name, schema.columns(name).size.toString()) },
            )
        }
    }

    private fun table(positional: List<String>, schema: Schema, io: GrindIO) {
        val name = positional.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: throw GrindException("Specify a table: grind db:table <name>")
        if (!schema.hasTable(name)) {
            throw GrindException("Table [$name] does not exist.")
        }
        val columns = schema.columns(name)
        io.title(name)
        io.table(
            listOf("Column", "Type", "Nullable", "Default"),
            columns.map { column ->
                listOf(
                    column.name,
                    column.typeLabel(),
                    if (column.nullable) "YES" else "NO",
                    column.defaultValue,
                )
            },
        )
    }

    private fun dump(options: Map<String, String>, schema: Schema, io: GrindIO) {
        val sql = schema.dump()
        val relative = options["path"]?.takeIf { it.isNotEmpty() } ?: DEFAULT_DUMP_PATH
        val dest = Names.base(options).resolve(relative).toAbsolutePath()
        Files.createDirectories(dest.parent)
        Files.writeString(dest, sql)
        io.info("Schema dumped to $relative")
    }

    companion object {
        const val DEFAULT_DUMP_PATH = "database/schema.sql"
    }
}
