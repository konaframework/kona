package io.konaframework.cadence.schema

/** Column kinds understood by every [SchemaGrammar]. */
enum class ColumnType {
    ID,
    STRING,
    TEXT,
    INTEGER,
    BIG_INTEGER,
    BOOLEAN,
    TIMESTAMP,
}

/** One column inside a [Blueprint]; modifiers return `this` so they chain from Kotlin and Java. */
class ColumnDefinition internal constructor(
    private val blueprint: Blueprint,
    val name: String,
    val type: ColumnType,
    val length: Int,
) {
    var isNullable: Boolean = false
        private set
    var hasDefault: Boolean = false
        private set
    var defaultValue: Any? = null
        private set
    var useCurrent: Boolean = false
        private set

    @JvmOverloads
    fun nullable(value: Boolean = true): ColumnDefinition = apply { isNullable = value }

    /** `DEFAULT value`; Java calls this `withDefault` (`default` is a Java keyword). */
    @JvmName("withDefault")
    fun default(value: Any?): ColumnDefinition = apply {
        hasDefault = true
        defaultValue = value
    }

    /** `DEFAULT CURRENT_TIMESTAMP` — meaningful on timestamp columns. */
    fun useCurrent(): ColumnDefinition = apply { useCurrent = true }

    fun unique(): ColumnDefinition = apply { blueprint.unique(name) }

    fun index(): ColumnDefinition = apply { blueprint.index(name) }

    /**
     * Adds a foreign key on this column. Without [table], the referenced table is derived from the
     * column name: `user_id` → `users`.
     */
    @JvmOverloads
    fun constrained(table: String? = null, column: String = "id"): ForeignKeyDefinition =
        blueprint.foreign(name).references(column).on(table ?: guessTable(name))

    private fun guessTable(column: String): String = column.removeSuffix("_id") + "s"
}

/** A `FOREIGN KEY (column) REFERENCES table (references)` constraint. */
class ForeignKeyDefinition internal constructor(val table: String, val column: String) {
    var referencesColumn: String = "id"
        private set
    var onTable: String = ""
        private set
    var onDeleteAction: String? = null
        private set
    var onUpdateAction: String? = null
        private set

    val name: String get() = "${table}_${column}_foreign"

    fun references(column: String): ForeignKeyDefinition = apply { referencesColumn = column }

    fun on(table: String): ForeignKeyDefinition = apply { onTable = table }

    /** `CASCADE`, `SET NULL`, `RESTRICT`, `NO ACTION`. */
    fun onDelete(action: String): ForeignKeyDefinition = apply { onDeleteAction = action.uppercase() }

    fun onUpdate(action: String): ForeignKeyDefinition = apply { onUpdateAction = action.uppercase() }

    fun cascadeOnDelete(): ForeignKeyDefinition = onDelete("cascade")

    fun nullOnDelete(): ForeignKeyDefinition = onDelete("set null")
}

/** A plain or unique index over one or more columns. */
data class IndexDefinition(val name: String, val columns: List<String>, val unique: Boolean)

/**
 * Describes the shape of a table: the columns, indexes, and foreign keys to create (inside
 * [Schema.create]) or to add and drop (inside [Schema.table]).
 */
class Blueprint(val table: String) {
    private val columnList = mutableListOf<ColumnDefinition>()
    private val indexList = mutableListOf<IndexDefinition>()
    private val foreignList = mutableListOf<ForeignKeyDefinition>()
    private val droppedColumns = mutableListOf<String>()
    private val droppedIndexes = mutableListOf<String>()

    val columns: List<ColumnDefinition> get() = columnList.toList()
    val indexes: List<IndexDefinition> get() = indexList.toList()
    val foreignKeys: List<ForeignKeyDefinition> get() = foreignList.toList()
    val dropColumns: List<String> get() = droppedColumns.toList()
    val dropIndexes: List<String> get() = droppedIndexes.toList()

    /** Auto-incrementing `BIGINT` primary key. */
    @JvmOverloads
    fun id(name: String = "id"): ColumnDefinition = add(name, ColumnType.ID)

    @JvmOverloads
    fun string(name: String, length: Int = 255): ColumnDefinition {
        require(length > 0) { "String column [$name] length must be positive" }
        return add(name, ColumnType.STRING, length)
    }

    fun text(name: String): ColumnDefinition = add(name, ColumnType.TEXT)

    fun integer(name: String): ColumnDefinition = add(name, ColumnType.INTEGER)

    fun bigInteger(name: String): ColumnDefinition = add(name, ColumnType.BIG_INTEGER)

    /** Java calls this `bool` (`boolean` is a Java keyword). */
    @JvmName("bool")
    fun boolean(name: String): ColumnDefinition = add(name, ColumnType.BOOLEAN)

    fun timestamp(name: String): ColumnDefinition = add(name, ColumnType.TIMESTAMP)

    /** `BIGINT` column meant to reference another table's `id`; chain [ColumnDefinition.constrained]. */
    fun foreignId(name: String): ColumnDefinition = add(name, ColumnType.BIG_INTEGER)

    /** Nullable `created_at` and `updated_at`. */
    fun timestamps() {
        timestamp("created_at").nullable()
        timestamp("updated_at").nullable()
    }

    /** Nullable `deleted_at` used by soft-deleting models. */
    @JvmOverloads
    fun softDeletes(name: String = "deleted_at"): ColumnDefinition = timestamp(name).nullable()

    /**
     * Polymorphic columns `{name}_type` (string) + `{name}_id` (bigint), matching Laravel `morphs`.
     */
    fun morphs(name: String) {
        morphs(name, null)
    }

    fun morphs(name: String, indexName: String?) {
        string("${name}_type")
        foreignId("${name}_id")
        index(listOf("${name}_type", "${name}_id"), indexName ?: "${table}_${name}_type_${name}_id_index")
    }

    /** Nullable variant of [morphs]. */
    fun nullableMorphs(name: String) {
        nullableMorphs(name, null)
    }

    fun nullableMorphs(name: String, indexName: String?) {
        string("${name}_type").nullable()
        foreignId("${name}_id").nullable()
        index(listOf("${name}_type", "${name}_id"), indexName ?: "${table}_${name}_type_${name}_id_index")
    }

    /** UUID morph id (`string(36)`) plus type column. */
    fun uuidMorphs(name: String) {
        uuidMorphs(name, null)
    }

    fun uuidMorphs(name: String, indexName: String?) {
        string("${name}_type")
        string("${name}_id", 36)
        index(listOf("${name}_type", "${name}_id"), indexName ?: "${table}_${name}_type_${name}_id_index")
    }

    fun index(vararg columns: String): IndexDefinition = index(columns.toList(), null)

    fun index(columns: List<String>, name: String?): IndexDefinition = addIndex(columns, name, unique = false)

    fun unique(vararg columns: String): IndexDefinition = unique(columns.toList(), null)

    fun unique(columns: List<String>, name: String?): IndexDefinition = addIndex(columns, name, unique = true)

    fun foreign(column: String): ForeignKeyDefinition =
        ForeignKeyDefinition(table, column).also { foreignList += it }

    fun dropColumn(vararg columns: String) {
        droppedColumns += columns
    }

    fun dropIndex(name: String) {
        droppedIndexes += name
    }

    private fun add(name: String, type: ColumnType, length: Int = 0): ColumnDefinition =
        ColumnDefinition(this, name, type, length).also { columnList += it }

    private fun addIndex(columns: List<String>, name: String?, unique: Boolean): IndexDefinition {
        require(columns.isNotEmpty()) { "An index on [$table] needs at least one column" }
        val suffix = if (unique) "unique" else "index"
        val index = IndexDefinition(name ?: "${table}_${columns.joinToString("_")}_$suffix", columns, unique)
        indexList += index
        return index
    }
}
