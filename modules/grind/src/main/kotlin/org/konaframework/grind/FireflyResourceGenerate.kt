package org.konaframework.grind

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CastType
import org.konaframework.cadence.Database
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaColumn
import org.konaframework.cadence.schema.SchemaGrammar

/**
 * Builds form / table / infolist component lists for `make:firefly-resource`.
 *
 * Default (no `--generate`): a single `name` text field. With `--generate`: load
 * `app.models.{Model}`, introspect its table via [Schema.columns], and map columns
 * to Firefly components (#325).
 */
internal object FireflyResourceGenerate {
    fun stubName(): ResourceFieldSet =
        ResourceFieldSet(
            form = listOf(GeneratedComponent("TextInput", "org.konaframework.firefly.forms.TextInput", "name")),
            table = listOf(GeneratedComponent("TextColumn", "org.konaframework.firefly.tables.TextColumn", "name")),
            infolist = listOf(GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", "name")),
        )

    fun loadModel(loader: ClassLoader, modelName: String): Cadence<*> {
        val qualified = "app.models.$modelName"
        val loaded = try {
            Class.forName(qualified, true, loader)
        } catch (ex: ClassNotFoundException) {
            throw GrindException("Model [$qualified] not found. Create it first (e.g. ./grind make:model $modelName).")
        }
        if (!Cadence::class.java.isAssignableFrom(loaded)) {
            throw GrindException("[$qualified] is not a Cadence model.")
        }
        return try {
            instantiateCadence(loaded)
        } catch (ex: GrindException) {
            throw ex
        } catch (ex: Exception) {
            throw GrindException("Cannot instantiate model [$qualified]: ${ex.message}")
        }
    }

    private fun instantiateCadence(type: Class<*>): Cadence<*> {
        val ctor = type.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance() as Cadence<*>
    }

    fun fromModel(model: Cadence<*>, database: Database, connectionName: String? = null): ResourceFieldSet {
        val table = model.table
        val name = connectionName ?: model.connectionName
        val connection = if (name == null) database.connection() else database.connection(name)
        return connection.withConnection { jdbc ->
            val schema = Schema(jdbc, SchemaGrammar.forProduct(jdbc.metaData.databaseProductName, connection.grammar))
            if (!schema.hasTable(table)) {
                throw GrindException("Table [$table] for model [${model.javaClass.name}] does not exist. Run ./grind migrate first.")
            }
            val columns = schema.columns(table)
            val primary = schema.primaryKey(table).toSet()
            mapColumns(columns, model, primary)
        }
    }

    fun mapColumns(
        columns: List<SchemaColumn>,
        model: Cadence<*>,
        primaryKeys: Set<String>,
    ): ResourceFieldSet {
        val skipForm = skipFormColumns(model, primaryKeys)
        val casts = model.effectiveCasts()
        val fillable = model.fillable
        val form = ArrayList<GeneratedComponent>()
        val table = ArrayList<GeneratedComponent>()
        val infolist = ArrayList<GeneratedComponent>()
        for (column in columns) {
            val mapped = mapColumn(column, casts[column.name])
            if (column.name !in skipForm && (fillable.isEmpty() || column.name in fillable)) {
                form.add(mapped.form)
            }
            if (column.name !in primaryKeys) {
                table.add(mapped.table)
                infolist.add(mapped.entry)
            }
        }
        if (form.isEmpty()) {
            throw GrindException(
                "No form fields generated for [${model.javaClass.simpleName}] " +
                    "(all columns skipped as primary key / timestamps / non-fillable).",
            )
        }
        return ResourceFieldSet(form = form, table = table, infolist = infolist)
    }

    private fun skipFormColumns(model: Cadence<*>, primaryKeys: Set<String>): Set<String> {
        val skip = LinkedHashSet<String>()
        skip.addAll(primaryKeys)
        if (primaryKeys.isEmpty()) skip.add(model.primaryKey)
        if (model.timestamps) {
            skip.add(model.createdAtColumn)
            skip.add(model.updatedAtColumn)
        }
        if (model is SoftDeletes) {
            skip.add(model.deletedAtColumn)
        }
        return skip
    }

    private fun mapColumn(column: SchemaColumn, cast: Any?): MappedColumn {
        val name = column.name
        val kind = resolveKind(column, cast)
        return when (kind) {
            FieldKind.TOGGLE -> MappedColumn(
                form = GeneratedComponent("Toggle", "org.konaframework.firefly.forms.Toggle", name),
                table = GeneratedComponent(
                    "IconColumn",
                    "org.konaframework.firefly.tables.IconColumn",
                    name,
                    callSuffix = ".boolean()",
                ),
                entry = GeneratedComponent(
                    "IconEntry",
                    "org.konaframework.firefly.infolists.IconEntry",
                    name,
                    callSuffix = ".boolean()",
                ),
            )
            FieldKind.DATE -> MappedColumn(
                form = GeneratedComponent("DatePicker", "org.konaframework.firefly.forms.DatePicker", name),
                table = GeneratedComponent("TextColumn", "org.konaframework.firefly.tables.TextColumn", name),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
            FieldKind.DATETIME -> MappedColumn(
                form = GeneratedComponent("DateTimePicker", "org.konaframework.firefly.forms.DateTimePicker", name),
                table = GeneratedComponent("TextColumn", "org.konaframework.firefly.tables.TextColumn", name),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
            FieldKind.TEXTAREA -> MappedColumn(
                form = GeneratedComponent("Textarea", "org.konaframework.firefly.forms.Textarea", name),
                table = GeneratedComponent("TextColumn", "org.konaframework.firefly.tables.TextColumn", name),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
            FieldKind.TEXT -> MappedColumn(
                form = GeneratedComponent("TextInput", "org.konaframework.firefly.forms.TextInput", name),
                table = GeneratedComponent("TextColumn", "org.konaframework.firefly.tables.TextColumn", name),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
        }
    }

    private fun resolveKind(column: SchemaColumn, cast: Any?): FieldKind {
        if (cast == CastType.BOOLEAN) return FieldKind.TOGGLE
        if (cast == CastType.DATETIME || cast == CastType.INSTANT) return FieldKind.DATETIME
        if (cast == CastType.JSON) return FieldKind.TEXTAREA
        val type = column.type.uppercase()
        if (isBooleanSqlType(type, column.size)) return FieldKind.TOGGLE
        if (type == "DATE") return FieldKind.DATE
        if (isDateTimeSqlType(type)) return FieldKind.DATETIME
        if (isLongTextSqlType(type) || isWideCharSqlType(type, column.size)) return FieldKind.TEXTAREA
        return FieldKind.TEXT
    }

    /**
     * JDBC [SchemaColumn.type] names across Cadence engines (H2, Postgres, MySQL, SQLite,
     * SQL Server, Oracle): BOOLEAN/BOOL, BIT, TINYINT(1), NUMBER(1) / NUMERIC(1) / DECIMAL(1).
     */
    private fun isBooleanSqlType(type: String, size: Int): Boolean {
        if (type.contains("BOOL")) return true
        if (type == "BIT") return true
        if (type == "TINYINT" && size == 1) return true
        if (size == 1 && (type == "NUMBER" || type == "NUMERIC" || type == "DECIMAL")) return true
        return false
    }

    private fun isDateTimeSqlType(type: String): Boolean {
        if (type.contains("TIMESTAMP")) return true
        if (type.contains("DATETIME")) return true // DATETIME, DATETIME2
        return type == "TIMESTAMPTZ"
    }

    private fun isLongTextSqlType(type: String): Boolean {
        if (type.contains("TEXT")) return true
        if (type.contains("CLOB")) return true
        if (type.contains("CHARACTER LARGE")) return true
        if (type.contains("JSON")) return true
        return type.contains("BLOB")
    }

    private fun isWideCharSqlType(type: String, size: Int): Boolean {
        // CHARACTER VARYING / VARCHAR / CHAR all contain "CHAR".
        return size >= 10_000 && type.contains("CHAR")
    }
}

internal data class GeneratedComponent(
    val type: String,
    val importFqcn: String,
    val name: String,
    val callSuffix: String = "",
) {
    fun callExpr(): String = "$type.make(\"$name\")$callSuffix"
}

internal data class ResourceFieldSet(
    val form: List<GeneratedComponent>,
    val table: List<GeneratedComponent>,
    val infolist: List<GeneratedComponent>,
) {
    fun imports(): List<String> =
        (form + table + infolist).map { it.importFqcn }.distinct().sorted()
}

private data class MappedColumn(
    val form: GeneratedComponent,
    val table: GeneratedComponent,
    val entry: GeneratedComponent,
)

private enum class FieldKind { TEXT, TEXTAREA, TOGGLE, DATE, DATETIME }
