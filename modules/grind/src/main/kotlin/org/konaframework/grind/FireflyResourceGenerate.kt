package org.konaframework.grind

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CastType
import org.konaframework.cadence.Database
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaColumn
import org.konaframework.cadence.schema.SchemaForeignKey
import org.konaframework.cadence.schema.SchemaGrammar

/**
 * Builds form / table / infolist component lists for `make:firefly-resource`.
 *
 * Default (no `--generate`): a single `name` text field. With `--generate`: load
 * `app.models.{Model}`, introspect its table via [Schema.columns], and map columns
 * to Firefly components (#325 / #334).
 */
internal object FireflyResourceGenerate {
    private val TITLE_COLUMN_NAMES = setOf("name", "title", "label", "subject", "caption")

    /** Common boolean column names when the driver only reports INTEGER (SQLite). */
    private val BOOLEAN_NAME_HINTS = setOf(
        "active", "enabled", "disabled", "published", "featured", "verified", "approved",
        "archived", "hidden", "visible", "public", "private", "locked", "sticky", "draft",
    )

    fun stubName(): ResourceFieldSet =
        ResourceFieldSet(
            form = listOf(GeneratedComponent("TextInput", "org.konaframework.firefly.forms.TextInput", "name")),
            table = listOf(
                GeneratedComponent(
                    "TextColumn",
                    "org.konaframework.firefly.tables.TextColumn",
                    "name",
                    callSuffix = ".searchable().sortable()",
                ),
            ),
            infolist = listOf(GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", "name")),
            formColumns = 2,
            infolistColumns = 2,
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
            val foreign = schema.foreignKeys(table).associateBy { it.column }
            mapColumns(columns, model, primary, foreign)
        }
    }

    fun mapColumns(
        columns: List<SchemaColumn>,
        model: Cadence<*>,
        primaryKeys: Set<String>,
        foreignKeys: Map<String, SchemaForeignKey> = emptyMap(),
    ): ResourceFieldSet {
        val skipForm = skipFormColumns(model, primaryKeys)
        val casts = model.effectiveCasts()
        val fillable = model.fillable
        val form = ArrayList<GeneratedComponent>()
        val table = ArrayList<GeneratedComponent>()
        val infolist = ArrayList<GeneratedComponent>()
        val timestampNames = timestampColumnNames(model)
        for (column in columns) {
            val fk = foreignKeys[column.name] ?: guessForeignKey(column.name, primaryKeys)
            val mapped = mapColumn(column, casts[column.name], fk, timestampNames)
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
        return ResourceFieldSet(
            form = form,
            table = markSearchableSortable(table),
            infolist = infolist,
            formColumns = 2,
            infolistColumns = 2,
        )
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

    private fun timestampColumnNames(model: Cadence<*>): Set<String> {
        val names = LinkedHashSet<String>()
        if (model.timestamps) {
            names.add(model.createdAtColumn)
            names.add(model.updatedAtColumn)
        }
        if (model is SoftDeletes) {
            names.add(model.deletedAtColumn)
        }
        return names
    }

    private fun mapColumn(
        column: SchemaColumn,
        cast: Any?,
        foreign: SchemaForeignKey?,
        timestampNames: Set<String>,
    ): MappedColumn {
        val name = column.name
        if (foreign != null) {
            val relation = relationName(foreign)
            return MappedColumn(
                form = GeneratedComponent(
                    "Select",
                    "org.konaframework.firefly.forms.Select",
                    name,
                    callSuffix = ".relationship(\"$relation\", \"name\")",
                ),
                table = GeneratedComponent("TextColumn", "org.konaframework.firefly.tables.TextColumn", name),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
        }
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
                table = textColumn(name, toggleHidden = false),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
            FieldKind.DATETIME -> MappedColumn(
                form = GeneratedComponent("DateTimePicker", "org.konaframework.firefly.forms.DateTimePicker", name),
                table = textColumn(name, toggleHidden = true),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
            FieldKind.TEXTAREA -> MappedColumn(
                form = GeneratedComponent("Textarea", "org.konaframework.firefly.forms.Textarea", name),
                table = textColumn(name, toggleHidden = false),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
            FieldKind.TEXT -> MappedColumn(
                form = GeneratedComponent("TextInput", "org.konaframework.firefly.forms.TextInput", name),
                table = textColumn(name, toggleHidden = name in timestampNames),
                entry = GeneratedComponent("TextEntry", "org.konaframework.firefly.infolists.TextEntry", name),
            )
        }
    }

    private fun textColumn(name: String, toggleHidden: Boolean): GeneratedComponent {
        val suffix = if (toggleHidden) ".toggleable().toggledHiddenByDefault()" else ""
        return GeneratedComponent(
            "TextColumn",
            "org.konaframework.firefly.tables.TextColumn",
            name,
            callSuffix = suffix,
        )
    }

    private fun markSearchableSortable(columns: List<GeneratedComponent>): List<GeneratedComponent> {
        val titleIndex = columns.indexOfFirst { it.name.lowercase() in TITLE_COLUMN_NAMES }
        val index = if (titleIndex >= 0) titleIndex else 0
        return columns.mapIndexed { i, column ->
            if (i != index) column
            else column.copy(callSuffix = column.callSuffix + ".searchable().sortable()")
        }
    }

    private fun resolveKind(column: SchemaColumn, cast: Any?): FieldKind {
        if (cast == CastType.BOOLEAN) return FieldKind.TOGGLE
        if (cast == CastType.DATETIME || cast == CastType.INSTANT) return FieldKind.DATETIME
        if (cast == CastType.JSON) return FieldKind.TEXTAREA
        val type = column.type.uppercase()
        if (isBooleanSqlType(type, column.size) || looksBooleanName(column.name, type)) return FieldKind.TOGGLE
        if (type == "DATE") return FieldKind.DATE
        if (isDateTimeSqlType(type)) return FieldKind.DATETIME
        if (isLongTextSqlType(type) || isWideCharSqlType(type, column.size)) return FieldKind.TEXTAREA
        return FieldKind.TEXT
    }

    /**
     * JDBC [SchemaColumn.type] names across Cadence engines (H2, Postgres, MySQL, SQLite,
     * SQL Server, Oracle): BOOLEAN/BOOL, BIT, TINYINT(1), NUMBER(1) / NUMERIC(1) / DECIMAL(1).
     * SQLite stores Cadence booleans as INTEGER — use [looksBooleanName] as well.
     */
    private fun isBooleanSqlType(type: String, size: Int): Boolean {
        if (type.contains("BOOL")) return true
        if (type == "BIT") return true
        if (type == "TINYINT" && size == 1) return true
        if (size == 1 && (type == "NUMBER" || type == "NUMERIC" || type == "DECIMAL")) return true
        return false
    }

    /** SQLite / affinity: INTEGER column with a boolean-ish name. */
    private fun looksBooleanName(name: String, type: String): Boolean {
        if (type != "INTEGER" && type != "INT" && type != "BIGINT") return false
        val lower = name.lowercase()
        if (lower in BOOLEAN_NAME_HINTS) return true
        return lower.startsWith("is_") ||
            lower.startsWith("has_") ||
            lower.startsWith("can_") ||
            lower.startsWith("was_") ||
            lower.startsWith("should_") ||
            lower.endsWith("_flag")
    }

    private fun isDateTimeSqlType(type: String): Boolean {
        if (type.contains("TIMESTAMP")) return true
        if (type.contains("DATETIME")) return true
        return type == "TIMESTAMPTZ"
    }

    private fun isLongTextSqlType(type: String): Boolean {
        if (type.contains("TEXT")) return true
        if (type.contains("CLOB")) return true
        if (type.contains("CHARACTER LARGE")) return true
        if (type.contains("JSON")) return true
        return type.contains("BLOB")
    }

    private fun isWideCharSqlType(type: String, size: Int): Boolean =
        size >= 10_000 && type.contains("CHAR")

    private fun guessForeignKey(column: String, primaryKeys: Set<String>): SchemaForeignKey? {
        if (column in primaryKeys) return null
        if (!column.endsWith("_id")) return null
        val stem = column.removeSuffix("_id")
        if (stem.isEmpty()) return null
        return SchemaForeignKey(column, referencedTable = stem + "s", referencedColumn = "id")
    }

    private fun relationName(foreign: SchemaForeignKey): String {
        val fromColumn = foreign.column
        if (fromColumn.endsWith("_id") && fromColumn.length > 3) {
            return fromColumn.removeSuffix("_id")
        }
        val table = foreign.referencedTable
        return if (table.length > 1 && table.endsWith("s")) table.dropLast(1) else table
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
    val formColumns: Int = 2,
    val infolistColumns: Int = 2,
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
