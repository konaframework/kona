package org.konaframework.cadence.schema

import org.konaframework.support.str.Str

/** Column kinds understood by every [SchemaGrammar]. */
enum class ColumnType {
    ID,
    STRING,
    CHAR,
    TEXT,
    LONG_TEXT,
    JSON,
    UUID,
    ULID,
    ENUM,
    TINY_INTEGER,
    SMALL_INTEGER,
    INTEGER,
    BIG_INTEGER,
    DECIMAL,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    TIMESTAMP,
    DATE,
    DATE_TIME,
    TIME,
    YEAR,
    ;

    /** True for integer and fixed/floating point kinds — the ones that accept [ColumnDefinition.unsigned]. */
    val isNumeric: Boolean
        get() = this in NUMERIC_TYPES
}

private val NUMERIC_TYPES = setOf(
    ColumnType.TINY_INTEGER,
    ColumnType.SMALL_INTEGER,
    ColumnType.INTEGER,
    ColumnType.BIG_INTEGER,
    ColumnType.DECIMAL,
    ColumnType.FLOAT,
    ColumnType.DOUBLE,
)

/**
 * One column inside a [Blueprint]; modifiers return `this` so they chain from Kotlin and Java.
 *
 * [length] is the size of `STRING` / `CHAR` / `UUID` / `ULID` columns; [precision] and [scale] are the
 * total digits and digits after the point of `DECIMAL` (only [precision] for `FLOAT`); [allowed] holds
 * the values of an `ENUM` column.
 */
class ColumnDefinition internal constructor(
    private val blueprint: Blueprint,
    val name: String,
    val type: ColumnType,
    val length: Int,
    val precision: Int,
    val scale: Int,
    val allowed: List<String>,
) {
    var isNullable: Boolean = false
        private set
    var isUnsigned: Boolean = false
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

    /** Makes this column (alone) the table's primary key; see [Blueprint.primary] for composite keys. */
    fun primary(): ColumnDefinition = apply { blueprint.primary(name) }

    /**
     * Rejects negative values. MySQL / MariaDB integer columns get the native `UNSIGNED` attribute;
     * every other engine and type gets a `CHECK (column >= 0)` constraint. Only numeric columns.
     */
    fun unsigned(): ColumnDefinition = apply {
        require(type.isNumeric) { "Column [$name] of type $type cannot be unsigned" }
        isUnsigned = true
    }

    /**
     * Adds a foreign key on this column. Without [table], the referenced table is the English plural
     * of the column name minus `_{column}`: `user_id` → `users`, `currency_id` → `currencies`.
     */
    @JvmOverloads
    fun constrained(table: String? = null, column: String = "id"): ForeignKeyDefinition =
        blueprint.foreign(name).references(column).on(table ?: Str.plural(Str.beforeLast(name, "_$column")))

    /** Starts a foreign key on this column pointing at [column]; finish with [ForeignKeyDefinition.on]. */
    fun references(column: String): ForeignKeyDefinition = blueprint.foreign(name).references(column)
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

/** A `PRIMARY KEY` over one or more columns, declared with [Blueprint.primary]. */
data class PrimaryKeyDefinition(val name: String, val columns: List<String>)

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

    /** The table-level primary key declared with [primary], or null. */
    var primaryKey: PrimaryKeyDefinition? = null
        private set

    /** Auto-incrementing `BIGINT` primary key. */
    @JvmOverloads
    fun id(name: String = "id"): ColumnDefinition = add(name, ColumnType.ID)

    @JvmOverloads
    fun string(name: String, length: Int = 255): ColumnDefinition {
        require(length > 0) { "String column [$name] length must be positive" }
        return add(name, ColumnType.STRING, length)
    }

    /** Fixed-length text (`CHAR(length)`; `NCHAR` on SQL Server). Java calls this `charColumn`. */
    @JvmOverloads
    @JvmName("charColumn")
    fun char(name: String, length: Int = 255): ColumnDefinition {
        require(length > 0) { "Char column [$name] length must be positive" }
        return add(name, ColumnType.CHAR, length)
    }

    /** UUID text column (`CHAR(36)`), so string ids round-trip unchanged on every engine. */
    @JvmOverloads
    fun uuid(name: String = "uuid"): ColumnDefinition = add(name, ColumnType.UUID, 36)

    /** ULID text column (`CHAR(26)`). */
    @JvmOverloads
    fun ulid(name: String = "ulid"): ColumnDefinition = add(name, ColumnType.ULID, 26)

    /** [uuid] column meant to reference another table's UUID key; chain [ColumnDefinition.constrained]. */
    fun foreignUuid(name: String): ColumnDefinition = uuid(name)

    /** [ulid] column meant to reference another table's ULID key; chain [ColumnDefinition.constrained]. */
    fun foreignUlid(name: String): ColumnDefinition = ulid(name)

    /**
     * Text column restricted to [values]: native `ENUM(...)` on MySQL / MariaDB, a `VARCHAR(255)`
     * with `CHECK (column IN (...))` on every other engine. Java calls this `enumColumn`.
     */
    @JvmName("enumColumn")
    fun enum(name: String, values: List<String>): ColumnDefinition {
        require(values.isNotEmpty()) { "Enum column [$name] needs at least one allowed value" }
        return add(name, ColumnType.ENUM, 255, allowed = values.toList())
    }

    /**
     * Exact fixed-point number with [precision] total digits, [scale] of them after the decimal
     * point (`DECIMAL(precision, scale)`). Use this — not [float] / [double] — for money.
     */
    @JvmOverloads
    fun decimal(name: String, precision: Int = 8, scale: Int = 2): ColumnDefinition {
        require(precision > 0) { "Decimal column [$name] precision must be positive" }
        require(scale in 0..precision) { "Decimal column [$name] scale must be between 0 and $precision" }
        return add(name, ColumnType.DECIMAL, precision = precision, scale = scale)
    }

    /** [decimal] that rejects negative values (see [ColumnDefinition.unsigned]). */
    @JvmOverloads
    fun unsignedDecimal(name: String, precision: Int = 8, scale: Int = 2): ColumnDefinition =
        decimal(name, precision, scale).unsigned()

    /** Approximate floating point with [precision] binary digits (`FLOAT(precision)`). Java: `floatColumn`. */
    @JvmOverloads
    @JvmName("floatColumn")
    fun float(name: String, precision: Int = 53): ColumnDefinition {
        require(precision > 0) { "Float column [$name] precision must be positive" }
        return add(name, ColumnType.FLOAT, precision = precision)
    }

    /** Double-precision floating point (`DOUBLE PRECISION` / `DOUBLE`). Java calls this `doubleColumn`. */
    @JvmName("doubleColumn")
    fun double(name: String): ColumnDefinition = add(name, ColumnType.DOUBLE)

    /** `TINYINT` where the engine has it (`SMALLINT` on PostgreSQL and standard SQL). */
    fun tinyInteger(name: String): ColumnDefinition = add(name, ColumnType.TINY_INTEGER)

    fun smallInteger(name: String): ColumnDefinition = add(name, ColumnType.SMALL_INTEGER)

    fun unsignedTinyInteger(name: String): ColumnDefinition = tinyInteger(name).unsigned()

    fun unsignedSmallInteger(name: String): ColumnDefinition = smallInteger(name).unsigned()

    fun unsignedInteger(name: String): ColumnDefinition = integer(name).unsigned()

    fun unsignedBigInteger(name: String): ColumnDefinition = bigInteger(name).unsigned()

    fun text(name: String): ColumnDefinition = add(name, ColumnType.TEXT)

    /** Unbounded long text (`LONGTEXT` on MySQL; `TEXT` elsewhere). */
    fun longText(name: String): ColumnDefinition = add(name, ColumnType.LONG_TEXT)

    /** JSON document column (`JSON` on MySQL/Postgres; `TEXT` / `CHARACTER VARYING` elsewhere). */
    fun json(name: String): ColumnDefinition = add(name, ColumnType.JSON)

    fun integer(name: String): ColumnDefinition = add(name, ColumnType.INTEGER)

    fun bigInteger(name: String): ColumnDefinition = add(name, ColumnType.BIG_INTEGER)

    /** Java calls this `bool` (`boolean` is a Java keyword). */
    @JvmName("bool")
    fun boolean(name: String): ColumnDefinition = add(name, ColumnType.BOOLEAN)

    fun timestamp(name: String): ColumnDefinition = add(name, ColumnType.TIMESTAMP)

    /** Calendar date without a time of day (`DATE`). */
    fun date(name: String): ColumnDefinition = add(name, ColumnType.DATE)

    /** Date and time (`DATETIME` / `TIMESTAMP` depending on the engine). */
    fun dateTime(name: String): ColumnDefinition = add(name, ColumnType.DATE_TIME)

    /** Time of day without a date (`TIME`; `DATE` on Oracle, which has no time-only type). */
    fun time(name: String): ColumnDefinition = add(name, ColumnType.TIME)

    /** Four-digit year (`YEAR` on MySQL / MariaDB, an integer elsewhere). */
    fun year(name: String): ColumnDefinition = add(name, ColumnType.YEAR)

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

    /** Primary key over [columns] — pass several for a composite key (`primary("order_id", "line")`). */
    fun primary(vararg columns: String): PrimaryKeyDefinition = primary(columns.toList(), null)

    /** Primary key over [columns] named [name] (default `{table}_{columns}_primary`). One per table. */
    fun primary(columns: List<String>, name: String?): PrimaryKeyDefinition {
        require(columns.isNotEmpty()) { "A primary key on [$table] needs at least one column" }
        check(primaryKey == null) { "Table [$table] already declares a primary key" }
        val key = PrimaryKeyDefinition(name ?: "${table}_${columns.joinToString("_")}_primary", columns.toList())
        primaryKey = key
        return key
    }

    fun foreign(column: String): ForeignKeyDefinition =
        ForeignKeyDefinition(table, column).also { foreignList += it }

    fun dropColumn(vararg columns: String) {
        droppedColumns += columns
    }

    fun dropIndex(name: String) {
        droppedIndexes += name
    }

    private fun add(
        name: String,
        type: ColumnType,
        length: Int = 0,
        precision: Int = 0,
        scale: Int = 0,
        allowed: List<String> = emptyList(),
    ): ColumnDefinition = ColumnDefinition(this, name, type, length, precision, scale, allowed).also { columnList += it }

    private fun addIndex(columns: List<String>, name: String?, unique: Boolean): IndexDefinition {
        require(columns.isNotEmpty()) { "An index on [$table] needs at least one column" }
        val suffix = if (unique) "unique" else "index"
        val index = IndexDefinition(name ?: "${table}_${columns.joinToString("_")}_$suffix", columns, unique)
        indexList += index
        return index
    }
}
