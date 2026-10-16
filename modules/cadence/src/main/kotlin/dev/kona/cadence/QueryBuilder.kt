package dev.kona.cadence

import java.util.function.Consumer

/**
 * Fluent, mutable SQL query builder. Values are always bound as `PreparedStatement`
 * parameters; only identifiers (quoted through the connection's [Grammar]) appear in the SQL.
 *
 * ```kotlin
 * db.table("users").where("votes", ">", 100).orWhere { it.where("name", "Ada").whereNotNull("email") }
 *     .orderBy("name").limit(10).get()
 * ```
 */
class QueryBuilder(val connection: DatabaseConnection) {
    private val grammar: Grammar get() = connection.grammar

    /** Test hook: force [nativeUpsert] to compile SQL for this JDBC product name. */
    @JvmField
    internal var upsertProductOverride: String? = null

    private var from: String? = null
    private var distinct = false
    private val columns = mutableListOf<String>()
    private val selectBindings = mutableListOf<Any?>()
    private val joins = mutableListOf<String>()
    private val wheres = mutableListOf<Clause>()
    private val groups = mutableListOf<String>()
    private val havings = mutableListOf<Clause>()
    private val orders = mutableListOf<String>()
    private val unions = mutableListOf<UnionClause>()
    private var limit: Int? = null
    private var offset: Int? = null
    /** Appended after limit/offset: ` for update` or ` for share` (empty when unlocked). */
    private var lock: String = ""

    fun from(table: String): QueryBuilder {
        from = grammar.wrap(table)
        return this
    }

    fun table(table: String): QueryBuilder = from(table)

    // ---- select ----

    /** Replaces the selected columns (default `*`). */
    fun select(vararg columns: String): QueryBuilder {
        this.columns.clear()
        selectBindings.clear()
        return addSelect(*columns)
    }

    fun addSelect(vararg columns: String): QueryBuilder {
        columns.forEach { this.columns.add(grammar.wrap(it)) }
        return this
    }

    /** Adds a raw select expression such as `count(*) as total`; [bindings] fill its `?` markers. */
    @JvmOverloads
    fun selectRaw(expression: String, bindings: List<Any?> = emptyList()): QueryBuilder {
        columns.add(expression)
        selectBindings.addAll(bindings)
        return this
    }

    fun distinct(): QueryBuilder {
        distinct = true
        return this
    }

    // ---- where ----

    fun where(column: String, value: Any?): QueryBuilder = where(column, "=", value)

    fun where(column: String, operator: String, value: Any?): QueryBuilder = addBasic(AND, column, operator, value)

    /** Parenthesized group: `where (a = ? or b = ?)`. */
    fun where(group: Consumer<QueryBuilder>): QueryBuilder = addNested(AND, group)

    fun orWhere(column: String, value: Any?): QueryBuilder = orWhere(column, "=", value)

    fun orWhere(column: String, operator: String, value: Any?): QueryBuilder = addBasic(OR, column, operator, value)

    fun orWhere(group: Consumer<QueryBuilder>): QueryBuilder = addNested(OR, group)

    fun whereNull(column: String): QueryBuilder = addNull(AND, column, not = false)

    fun orWhereNull(column: String): QueryBuilder = addNull(OR, column, not = false)

    fun whereNotNull(column: String): QueryBuilder = addNull(AND, column, not = true)

    fun orWhereNotNull(column: String): QueryBuilder = addNull(OR, column, not = true)

    fun whereIn(column: String, values: Collection<*>): QueryBuilder = addIn(AND, column, values, not = false)

    fun orWhereIn(column: String, values: Collection<*>): QueryBuilder = addIn(OR, column, values, not = false)

    fun whereNotIn(column: String, values: Collection<*>): QueryBuilder = addIn(AND, column, values, not = true)

    fun orWhereNotIn(column: String, values: Collection<*>): QueryBuilder = addIn(OR, column, values, not = true)

    fun whereBetween(column: String, from: Any?, to: Any?): QueryBuilder {
        wheres.add(Clause(AND, "${grammar.wrap(column)} between ? and ?", listOf(from, to)))
        return this
    }

    fun orWhereBetween(column: String, from: Any?, to: Any?): QueryBuilder {
        wheres.add(Clause(OR, "${grammar.wrap(column)} between ? and ?", listOf(from, to)))
        return this
    }

    fun whereNotBetween(column: String, from: Any?, to: Any?): QueryBuilder {
        wheres.add(Clause(AND, "${grammar.wrap(column)} not between ? and ?", listOf(from, to)))
        return this
    }

    fun orWhereNotBetween(column: String, from: Any?, to: Any?): QueryBuilder {
        wheres.add(Clause(OR, "${grammar.wrap(column)} not between ? and ?", listOf(from, to)))
        return this
    }

    /** Column-to-column comparison (no value bindings). */
    fun whereColumn(first: String, second: String): QueryBuilder = whereColumn(first, "=", second)

    fun whereColumn(first: String, operator: String, second: String): QueryBuilder =
        addColumnCompare(AND, first, operator, second)

    fun orWhereColumn(first: String, second: String): QueryBuilder = orWhereColumn(first, "=", second)

    fun orWhereColumn(first: String, operator: String, second: String): QueryBuilder =
        addColumnCompare(OR, first, operator, second)

    @JvmOverloads
    fun whereRaw(sql: String, bindings: List<Any?> = emptyList()): QueryBuilder {
        wheres.add(Clause(AND, sql, bindings))
        return this
    }

    @JvmOverloads
    fun orWhereRaw(sql: String, bindings: List<Any?> = emptyList()): QueryBuilder {
        wheres.add(Clause(OR, sql, bindings))
        return this
    }

    /** Compare the date part of [column] (`cast(col as date)`). */
    fun whereDate(column: String, value: Any?): QueryBuilder = whereDate(column, "=", value)

    fun whereDate(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(AND, "date", column, operator, value)

    fun orWhereDate(column: String, value: Any?): QueryBuilder = orWhereDate(column, "=", value)

    fun orWhereDate(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(OR, "date", column, operator, value)

    fun whereDay(column: String, value: Any?): QueryBuilder = whereDay(column, "=", value)

    fun whereDay(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(AND, "day", column, operator, value)

    fun orWhereDay(column: String, value: Any?): QueryBuilder = orWhereDay(column, "=", value)

    fun orWhereDay(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(OR, "day", column, operator, value)

    fun whereMonth(column: String, value: Any?): QueryBuilder = whereMonth(column, "=", value)

    fun whereMonth(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(AND, "month", column, operator, value)

    fun orWhereMonth(column: String, value: Any?): QueryBuilder = orWhereMonth(column, "=", value)

    fun orWhereMonth(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(OR, "month", column, operator, value)

    fun whereYear(column: String, value: Any?): QueryBuilder = whereYear(column, "=", value)

    fun whereYear(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(AND, "year", column, operator, value)

    fun orWhereYear(column: String, value: Any?): QueryBuilder = orWhereYear(column, "=", value)

    fun orWhereYear(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(OR, "year", column, operator, value)

    fun whereTime(column: String, value: Any?): QueryBuilder = whereTime(column, "=", value)

    fun whereTime(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(AND, "time", column, operator, value)

    fun orWhereTime(column: String, value: Any?): QueryBuilder = orWhereTime(column, "=", value)

    fun orWhereTime(column: String, operator: String, value: Any?): QueryBuilder =
        addDatePart(OR, "time", column, operator, value)

    /** Applies [callback] only when [condition] is true. */
    fun `when`(condition: Boolean, callback: Consumer<QueryBuilder>): QueryBuilder {
        if (condition) {
            callback.accept(this)
        }
        return this
    }

    /** Applies [callback] only when [condition] is false. */
    fun unless(condition: Boolean, callback: Consumer<QueryBuilder>): QueryBuilder = `when`(!condition, callback)

    /** Runs [callback] with this builder and returns this (side-effect helper). */
    fun tap(callback: Consumer<QueryBuilder>): QueryBuilder {
        callback.accept(this)
        return this
    }

    // ---- joins ----

    fun join(table: String, first: String, operator: String, second: String): QueryBuilder =
        addJoin("inner", table, first, operator, second)

    fun join(table: String, first: String, second: String): QueryBuilder = join(table, first, "=", second)

    fun leftJoin(table: String, first: String, operator: String, second: String): QueryBuilder =
        addJoin("left", table, first, operator, second)

    fun leftJoin(table: String, first: String, second: String): QueryBuilder = leftJoin(table, first, "=", second)

    fun rightJoin(table: String, first: String, operator: String, second: String): QueryBuilder =
        addJoin("right", table, first, operator, second)

    fun rightJoin(table: String, first: String, second: String): QueryBuilder = rightJoin(table, first, "=", second)

    /** Cartesian product join (`CROSS JOIN table`). */
    fun crossJoin(table: String): QueryBuilder {
        joins.add("cross join ${grammar.wrap(table)}")
        return this
    }

    /**
     * Appends `UNION` with [query]'s select. Both sides must be compatible column lists.
     * Order / limit / offset on this builder apply to the combined result.
     */
    fun union(query: QueryBuilder): QueryBuilder {
        unions.add(UnionClause(all = false, query = query.copy()))
        return this
    }

    /** Like [union] but keeps duplicate rows (`UNION ALL`). */
    fun unionAll(query: QueryBuilder): QueryBuilder {
        unions.add(UnionClause(all = true, query = query.copy()))
        return this
    }

    /** True when at least one select column / expression has been set (not the default `*`). */
    fun hasSelectColumns(): Boolean = columns.isNotEmpty()

    // ---- grouping / ordering / paging ----

    fun groupBy(vararg columns: String): QueryBuilder {
        columns.forEach { groups.add(grammar.wrap(it)) }
        return this
    }

    fun having(column: String, operator: String, value: Any?): QueryBuilder {
        havings.add(Clause(AND, "${grammar.wrap(column)} ${operatorOf(operator)} ?", listOf(value)))
        return this
    }

    fun orHaving(column: String, operator: String, value: Any?): QueryBuilder {
        havings.add(Clause(OR, "${grammar.wrap(column)} ${operatorOf(operator)} ?", listOf(value)))
        return this
    }

    @JvmOverloads
    fun havingRaw(sql: String, bindings: List<Any?> = emptyList()): QueryBuilder {
        havings.add(Clause(AND, sql, bindings))
        return this
    }

    @JvmOverloads
    fun orHavingRaw(sql: String, bindings: List<Any?> = emptyList()): QueryBuilder {
        havings.add(Clause(OR, sql, bindings))
        return this
    }

    @JvmOverloads
    fun orderBy(column: String, direction: String = "asc"): QueryBuilder {
        val dir = direction.trim().lowercase()
        require(dir == "asc" || dir == "desc") { "Order direction must be \"asc\" or \"desc\", got [$direction]" }
        orders.add("${grammar.wrap(column)} $dir")
        return this
    }

    fun orderByDesc(column: String): QueryBuilder = orderBy(column, "desc")

    @JvmOverloads
    fun latest(column: String = "created_at"): QueryBuilder = orderBy(column, "desc")

    @JvmOverloads
    fun oldest(column: String = "created_at"): QueryBuilder = orderBy(column, "asc")

    /** A negative value removes the limit. */
    fun limit(value: Int): QueryBuilder {
        limit = if (value >= 0) value else null
        return this
    }

    fun take(value: Int): QueryBuilder = limit(value)

    fun offset(value: Int): QueryBuilder {
        offset = maxOf(value, 0)
        return this
    }

    fun skip(value: Int): QueryBuilder = offset(value)

    fun forPage(page: Int, perPage: Int): QueryBuilder = offset((page - 1) * perPage).limit(perPage)

    /** Row lock for the select: `… for update` (exclusive). */
    fun lockForUpdate(): QueryBuilder {
        lock = " for update"
        return this
    }

    /** Shared row lock for the select: `… for share` (PostgreSQL; driver may reject on H2/SQLite). */
    fun sharedLock(): QueryBuilder {
        lock = " for share"
        return this
    }

    /** Clears any [lockForUpdate] / [sharedLock] clause. */
    fun withoutLock(): QueryBuilder {
        lock = ""
        return this
    }

    // ---- reads ----

    fun get(): List<Map<String, Any?>> = connection.select(toSql(), getBindings())

    fun first(): Map<String, Any?>? = copy().limit(1).get().firstOrNull()

    /**
     * Exactly one row, or throws [CadenceException] when zero or many match
     * (map-level sole; prefer [ModelBuilder.sole] for models).
     */
    fun sole(): Map<String, Any?> {
        val tableLabel = from ?: "?"
        val rows = copy().limit(2).get()
        return when (rows.size) {
            0 -> throw CadenceException("No query results for table [$tableLabel]")
            1 -> rows[0]
            else -> throw CadenceException("Query returned ${rows.size} results for table [$tableLabel]; expected exactly one.")
        }
    }

    @JvmOverloads
    fun find(id: Any?, column: String = "id"): Map<String, Any?>? = copy().where(column, id).first()

    /** First row's [column] value, or null when there is no row. */
    fun value(column: String): Any? = copy().select(column).first()?.let { it.values.first() }

    fun pluck(column: String): List<Any?> = copy().select(column).get().map { it.values.first() }

    /**
     * Keyed pluck: map of [key] column → [column] value (last row wins on duplicate keys).
     * Laravel `pluck('name', 'id')` shape.
     */
    fun pluck(column: String, key: String): Map<Any?, Any?> {
        val probe = copy()
        if (!probe.hasSelectColumns()) {
            probe.select(key, column)
        }
        val rows = probe.get()
        val result = linkedMapOf<Any?, Any?>()
        for (row in rows) {
            val keyValue = if (row.containsKey(key)) row[key] else row.values.firstOrNull()
            val value = if (row.containsKey(column)) row[column] else row.values.lastOrNull()
            result[keyValue] = value
        }
        return result
    }

    fun count(): Long {
        if (groups.isEmpty() && !distinct) {
            return (aggregate("count", "*") as Number).toLong()
        }
        val sql = "select count(*) as aggregate from (${toSql()}) temp_table"
        return (connection.select(sql, getBindings()).first().values.first() as Number).toLong()
    }

    fun max(column: String): Any? = aggregate("max", column)

    fun min(column: String): Any? = aggregate("min", column)

    fun sum(column: String): Any? = aggregate("sum", column)

    fun avg(column: String): Any? = aggregate("avg", column)

    fun exists(): Boolean {
        val probe = copy().resetForAggregate().selectRaw("1").limit(1)
        return probe.get().isNotEmpty()
    }

    fun doesntExist(): Boolean = !exists()

    /** Runs [count] and fetches [page] (1-based; values below 1 mean page 1). */
    @JvmOverloads
    fun paginate(perPage: Int = 15, page: Int = 1): Page<Map<String, Any?>> {
        require(perPage >= 1) { "perPage must be at least 1, got $perPage" }
        val current = maxOf(page, 1)
        val total = count()
        return Page(copy().forPage(current, perPage).get(), total, perPage, current)
    }

    /** Page without a total count (fetches `perPage + 1` to detect more pages). */
    @JvmOverloads
    fun simplePaginate(perPage: Int = 15, page: Int = 1): SimplePage<Map<String, Any?>> {
        require(perPage >= 1) { "perPage must be at least 1, got $perPage" }
        val current = maxOf(page, 1)
        val rows = copy().offset((current - 1) * perPage).limit(perPage + 1).get()
        val hasMore = rows.size > perPage
        return SimplePage(rows.take(perPage), perPage, current, hasMore)
    }

    /**
     * Cursor pagination ordered by [column] ascending.
     * Pass [cursor] from a previous [CursorPage.nextCursor] to continue.
     */
    @JvmOverloads
    fun cursorPaginate(
        perPage: Int = 15,
        cursor: String? = null,
        column: String = "id",
    ): CursorPage<Map<String, Any?>> {
        require(perPage >= 1) { "perPage must be at least 1, got $perPage" }
        val q = copy().orderBy(column)
        if (cursor != null) {
            q.where(column, ">", CursorPage.decode(cursor))
        }
        val rows = q.limit(perPage + 1).get()
        val hasMore = rows.size > perPage
        val page = rows.take(perPage)
        val next = if (hasMore) CursorPage.encode(page.last()[column]) else null
        val prev = if (cursor != null && page.isNotEmpty()) CursorPage.encode(page.first()[column]) else null
        return CursorPage(page, perPage, next, prev)
    }

    // ---- writes ----

    fun insert(values: Map<String, Any?>): Boolean = insert(listOf(values))

    /** Multi-row insert; columns come from the first row (missing keys bind null). */
    fun insert(rows: List<Map<String, Any?>>): Boolean {
        if (rows.isEmpty()) {
            return true
        }
        val columns = rows.first().keys.toList()
        return connection.insert(compileInsert(columns, rows.size), rows.flatMap { row -> columns.map { row[it] } })
    }

    /** Inserts one row and returns the generated key (null when the driver reports none). */
    fun insertGetId(values: Map<String, Any?>): Any? {
        val columns = values.keys.toList()
        return connection.insertGetId(compileInsert(columns, 1), columns.map { values[it] })
    }

    fun update(values: Map<String, Any?>): Int {
        if (values.isEmpty()) {
            return 0
        }
        return runUpdate(values.map { (column, value) -> "${grammar.wrap(column)} = ?" to value })
    }

    @JvmOverloads
    fun increment(column: String, amount: Number = 1): Int =
        runUpdate(listOf("${grammar.wrap(column)} = ${grammar.wrap(column)} + ?" to amount))

    @JvmOverloads
    fun decrement(column: String, amount: Number = 1): Int =
        runUpdate(listOf("${grammar.wrap(column)} = ${grammar.wrap(column)} - ?" to amount))

    fun delete(): Int = connection.delete("delete from ${requireTable()}" + clauses(" where ", wheres), bindingsOf(wheres))

    fun truncate() {
        connection.statement("truncate table ${requireTable()}")
    }

    /**
     * Updates rows matching [attributes] with [values], or inserts `attributes + values` when none match.
     * Returns true when a row was updated or inserted.
     */
    @JvmOverloads
    fun updateOrInsert(attributes: Map<String, Any?>, values: Map<String, Any?> = emptyMap()): Boolean {
        val matcher = copy()
        attributes.forEach { (column, value) -> matcher.where(column, value) }
        if (matcher.exists()) {
            if (values.isNotEmpty()) {
                val updater = copy()
                attributes.forEach { (column, value) -> updater.where(column, value) }
                updater.update(values)
            }
            return true
        }
        val row = LinkedHashMap(attributes)
        row.putAll(values)
        insert(row)
        return true
    }

    /**
     * Portable per-row upsert: for each row, update when [uniqueBy] columns match, otherwise insert.
     * Updates every non-unique key on match.
     */
    fun upsert(rows: List<Map<String, Any?>>, uniqueBy: Collection<String>): Int = upsert(rows, uniqueBy, null)

    /**
     * Portable per-row upsert: for each row, update when [uniqueBy] columns match, otherwise insert.
     * When [update] is null, every non-unique key is updated; when empty, matching rows are left unchanged.
     * Returns the number of rows inserted or updated.
     */
    fun upsert(rows: List<Map<String, Any?>>, uniqueBy: Collection<String>, update: Collection<String>?): Int {
        if (rows.isEmpty()) {
            return 0
        }
        require(uniqueBy.isNotEmpty()) { "upsert requires at least one uniqueBy column" }
        nativeUpsert(rows, uniqueBy, update)?.let { return it }
        var affected = 0
        for (row in rows) {
            val matcher = copy()
            uniqueBy.forEach { matcher.where(it, row[it]) }
            if (matcher.exists()) {
                val keys = update ?: row.keys.filter { it !in uniqueBy }
                if (keys.isNotEmpty()) {
                    val values = keys.associateWith { row[it] }
                    val updater = copy()
                    uniqueBy.forEach { updater.where(it, row[it]) }
                    affected += updater.update(values)
                }
            } else {
                insert(row)
                affected += 1
            }
        }
        return affected
    }

    /**
     * Dialect-specific multi-row upsert when the driver supports it; null → portable path.
     * MySQL / MariaDB: `ON DUPLICATE KEY UPDATE`. PostgreSQL / H2: `ON CONFLICT … DO UPDATE`.
     */
    private fun nativeUpsert(
        rows: List<Map<String, Any?>>,
        uniqueBy: Collection<String>,
        update: Collection<String>?,
    ): Int? {
        val columns = rows.first().keys.toList()
        if (columns.isEmpty()) {
            return 0
        }
        if (!NativeUpsert.rowsHaveUniformKeys(rows)) {
            return null
        }
        val updateCols = (update ?: columns.filter { it !in uniqueBy }).toList()
        val product = upsertProductOverride
            ?: connection.withConnection { it.metaData.databaseProductName.lowercase() }
        val table = requireTable()
        val sql = NativeUpsert.compileSql(product, grammar, table, columns, uniqueBy.toList(), updateCols, rows.size)
            ?: return null
        val bindings = rows.flatMap { row -> columns.map { row[it] } }
        return try {
            connection.update(sql, bindings)
        } catch (_: Exception) {
            null
        }
    }

    /** Inserts each row only when no existing row matches [uniqueBy]; returns how many were inserted. */
    fun insertOrIgnore(rows: List<Map<String, Any?>>, uniqueBy: Collection<String>): Int {
        if (rows.isEmpty()) {
            return 0
        }
        require(uniqueBy.isNotEmpty()) { "insertOrIgnore requires at least one uniqueBy column" }
        var inserted = 0
        for (row in rows) {
            val matcher = copy()
            uniqueBy.forEach { matcher.where(it, row[it]) }
            if (!matcher.exists()) {
                insert(row)
                inserted += 1
            }
        }
        return inserted
    }

    /**
     * Pages through results [count] at a time and passes each page to [consumer].
     * Uses existing order clauses when present; otherwise limit/offset alone.
     */
    fun chunk(count: Int, consumer: Consumer<List<Map<String, Any?>>>) {
        require(count >= 1) { "Chunk size must be at least 1, got $count" }
        var page = 1
        while (true) {
            val rows = copy().forPage(page, count).get()
            if (rows.isEmpty()) {
                break
            }
            consumer.accept(rows)
            if (rows.size < count) {
                break
            }
            page += 1
        }
    }

    /**
     * Lazy sequence of rows (internal batches of 100 via limit/offset).
     * Prefer [lazyById] / [chunkById] for large tables.
     */
    fun cursor(): Sequence<Map<String, Any?>> = lazy(100)

    /** [lazy] with a default page size of 1000. */
    fun lazy(): Sequence<Map<String, Any?>> = lazy(1000)

    /**
     * Lazy sequence of individual rows, fetched in pages of [chunkSize].
     * Pair with a stable `orderBy` so pages stay consistent.
     */
    fun lazy(chunkSize: Int): Sequence<Map<String, Any?>> {
        require(chunkSize >= 1) { "Lazy chunk size must be at least 1, got $chunkSize" }
        return sequence {
            var page = 1
            while (true) {
                val rows = copy().forPage(page, chunkSize).get()
                if (rows.isEmpty()) {
                    break
                }
                for (row in rows) {
                    yield(row)
                }
                if (rows.size < chunkSize) {
                    break
                }
                page += 1
            }
        }
    }

    /** [chunkById] on the primary-key-style column `id`. */
    fun chunkById(count: Int, consumer: Consumer<List<Map<String, Any?>>>): Unit =
        chunkById(count, "id", consumer)

    /**
     * Like [chunk], but advances with `where column > lastId` so inserts/deletes mid-scan
     * do not skip or duplicate rows. Requires an ordered comparable [column].
     * [consumer] is last so Kotlin can use a trailing lambda.
     */
    fun chunkById(
        count: Int,
        column: String,
        consumer: Consumer<List<Map<String, Any?>>>,
    ) {
        require(count >= 1) { "Chunk size must be at least 1, got $count" }
        var lastId: Any? = null
        while (true) {
            val batch = copy().withoutLock().clearOrders().orderBy(column)
            if (lastId != null) {
                batch.where(column, ">", lastId)
            }
            val rows = batch.limit(count).get()
            if (rows.isEmpty()) {
                break
            }
            consumer.accept(rows)
            lastId = rows.last()[column]
                ?: throw CadenceException("chunkById: last row missing column [$column]")
            if (rows.size < count) {
                break
            }
        }
    }

    /** [lazyById] with page size 1000 on column `id`. */
    fun lazyById(): Sequence<Map<String, Any?>> = lazyById(1000, "id")

    /** [lazyById] on column `id`. */
    fun lazyById(chunkSize: Int): Sequence<Map<String, Any?>> = lazyById(chunkSize, "id")

    /** Lazy rows via [chunkById] semantics (keyed by [column]). */
    fun lazyById(chunkSize: Int, column: String): Sequence<Map<String, Any?>> {
        require(chunkSize >= 1) { "Lazy chunk size must be at least 1, got $chunkSize" }
        return sequence {
            var lastId: Any? = null
            while (true) {
                val batch = copy().withoutLock().clearOrders().orderBy(column)
                if (lastId != null) {
                    batch.where(column, ">", lastId)
                }
                val rows = batch.limit(chunkSize).get()
                if (rows.isEmpty()) {
                    break
                }
                for (row in rows) {
                    yield(row)
                }
                lastId = rows.last()[column]
                    ?: throw CadenceException("lazyById: last row missing column [$column]")
                if (rows.size < chunkSize) {
                    break
                }
            }
        }
    }

    internal fun clearOrders(): QueryBuilder {
        orders.clear()
        return this
    }

    // ---- debugging ----

    fun toSql(): String {
        val head = StringBuilder("select ")
        if (distinct) {
            head.append("distinct ")
        }
        head.append(if (columns.isEmpty()) "*" else columns.joinToString(", "))
        val base = head.toString() + tailWithoutOrderLimit()
        if (unions.isEmpty()) {
            return base + orderLimitLock()
        }
        val sql = StringBuilder(base)
        for (union in unions) {
            sql.append(if (union.all) " union all " else " union ")
            sql.append(union.query.toSqlWithoutUnionsOrderLimit())
        }
        sql.append(orderLimitLock())
        return sql.toString()
    }

    fun getBindings(): List<Any?> {
        val base = selectBindings + bindingsOf(wheres) + bindingsOf(havings)
        if (unions.isEmpty()) {
            return base
        }
        return base + unions.flatMap { it.query.getBindings() }
    }

    /** Independent copy; changes to either builder do not affect the other. */
    fun copy(): QueryBuilder {
        val copy = QueryBuilder(connection)
        copy.from = from
        copy.distinct = distinct
        copy.columns.addAll(columns)
        copy.selectBindings.addAll(selectBindings)
        copy.joins.addAll(joins)
        copy.wheres.addAll(wheres)
        copy.groups.addAll(groups)
        copy.havings.addAll(havings)
        copy.orders.addAll(orders)
        copy.unions.addAll(unions.map { UnionClause(it.all, it.query.copy()) })
        copy.limit = limit
        copy.offset = offset
        copy.lock = lock
        return copy
    }

    /** Folds every existing where into one parenthesized group so appended scopes keep precedence. */
    internal fun groupWheres(): QueryBuilder {
        if (wheres.isNotEmpty()) {
            val group = Clause(AND, "(${body(wheres)})", bindingsOf(wheres))
            wheres.clear()
            wheres.add(group)
        }
        return this
    }

    private fun addDatePart(
        boolean: String,
        part: String,
        column: String,
        operator: String,
        value: Any?,
    ): QueryBuilder {
        val op = operatorOf(operator)
        val wrapped = grammar.wrap(column)
        val expression = when (part) {
            "date" -> "cast($wrapped as date)"
            "time" -> "cast($wrapped as time)"
            else -> "extract($part from $wrapped)"
        }
        wheres.add(Clause(boolean, "$expression $op ?", listOf(value)))
        return this
    }

    private fun addBasic(boolean: String, column: String, operator: String, value: Any?): QueryBuilder {
        val op = operatorOf(operator)
        if (value == null && op == "=") {
            return addNull(boolean, column, not = false)
        }
        if (value == null && (op == "!=" || op == "<>")) {
            return addNull(boolean, column, not = true)
        }
        wheres.add(Clause(boolean, "${grammar.wrap(column)} $op ?", listOf(value)))
        return this
    }

    private fun addNested(boolean: String, group: Consumer<QueryBuilder>): QueryBuilder {
        val nested = QueryBuilder(connection)
        group.accept(nested)
        if (nested.wheres.isNotEmpty()) {
            wheres.add(Clause(boolean, "(${body(nested.wheres)})", bindingsOf(nested.wheres)))
        }
        return this
    }

    private fun addNull(boolean: String, column: String, not: Boolean): QueryBuilder {
        wheres.add(Clause(boolean, "${grammar.wrap(column)} is ${if (not) "not " else ""}null", emptyList()))
        return this
    }

    private fun addIn(boolean: String, column: String, values: Collection<*>, not: Boolean): QueryBuilder {
        val clause = if (values.isEmpty()) {
            Clause(boolean, if (not) "1 = 1" else "0 = 1", emptyList())
        } else {
            val keyword = if (not) "not in" else "in"
            Clause(boolean, "${grammar.wrap(column)} $keyword (${values.joinToString(", ") { "?" }})", values.toList())
        }
        wheres.add(clause)
        return this
    }

    private fun addJoin(type: String, table: String, first: String, operator: String, second: String): QueryBuilder {
        joins.add("$type join ${grammar.wrap(table)} on ${grammar.wrap(first)} ${operatorOf(operator)} ${grammar.wrap(second)}")
        return this
    }

    private fun addColumnCompare(boolean: String, first: String, operator: String, second: String): QueryBuilder {
        wheres.add(Clause(boolean, "${grammar.wrap(first)} ${operatorOf(operator)} ${grammar.wrap(second)}", emptyList()))
        return this
    }

    private fun aggregate(function: String, column: String): Any? {
        val query = copy().resetForAggregate()
        val sql = "select $function(${grammar.wrap(column)}) as aggregate" + query.tail()
        return connection.select(sql, query.getBindings()).first().values.first()
    }

    /** Clears select / order / limit so a copy can become `select 1 from … where …` for EXISTS / counts. */
    internal fun resetForAggregate(): QueryBuilder {
        columns.clear()
        selectBindings.clear()
        orders.clear()
        limit = null
        offset = null
        lock = ""
        return this
    }

    private fun compileInsert(columns: List<String>, rows: Int): String {
        val table = requireTable()
        if (columns.isEmpty()) {
            return "insert into $table default values"
        }
        val row = columns.joinToString(", ", "(", ")") { "?" }
        return "insert into $table (${grammar.columnize(columns)}) values " + List(rows) { row }.joinToString(", ")
    }

    private fun runUpdate(sets: List<Pair<String, Any?>>): Int {
        val sql = "update ${requireTable()} set " + sets.joinToString(", ") { it.first } + clauses(" where ", wheres)
        return connection.update(sql, sets.map { it.second } + bindingsOf(wheres))
    }

    private fun tail(): String = tailWithoutOrderLimit() + orderLimitLock()

    private fun tailWithoutOrderLimit(): String {
        val sql = StringBuilder(" from ").append(requireTable())
        joins.forEach { sql.append(' ').append(it) }
        sql.append(clauses(" where ", wheres))
        if (groups.isNotEmpty()) {
            sql.append(" group by ").append(groups.joinToString(", "))
        }
        sql.append(clauses(" having ", havings))
        return sql.toString()
    }

    private fun orderLimitLock(): String {
        val sql = StringBuilder()
        if (orders.isNotEmpty()) {
            sql.append(" order by ").append(orders.joinToString(", "))
        }
        limit?.let { sql.append(" limit ").append(it) }
        offset?.let { sql.append(" offset ").append(it) }
        sql.append(lock)
        return sql.toString()
    }

    /** Select SQL without nested unions / order / limit (used as a union arm). */
    private fun toSqlWithoutUnionsOrderLimit(): String {
        val head = StringBuilder("select ")
        if (distinct) {
            head.append("distinct ")
        }
        head.append(if (columns.isEmpty()) "*" else columns.joinToString(", "))
        return head.toString() + tailWithoutOrderLimit()
    }

    private fun requireTable(): String = from ?: throw CadenceException("No table set on query; call from(table) first.")

    private fun clauses(keyword: String, clauses: List<Clause>): String =
        if (clauses.isEmpty()) "" else keyword + body(clauses)

    private fun body(clauses: List<Clause>): String =
        clauses.withIndex().joinToString(" ") { (index, clause) ->
            if (index == 0) clause.sql else "${clause.boolean} ${clause.sql}"
        }

    private fun bindingsOf(clauses: List<Clause>): List<Any?> = clauses.flatMap { it.bindings }

    private class Clause(val boolean: String, val sql: String, val bindings: List<Any?>)

    private class UnionClause(val all: Boolean, val query: QueryBuilder)

    companion object {
        private const val AND = "and"
        private const val OR = "or"
        private val OPERATORS = setOf("=", "<", ">", "<=", ">=", "<>", "!=", "like", "not like", "ilike", "not ilike")

        private fun operatorOf(operator: String): String {
            val op = operator.trim().lowercase()
            require(op in OPERATORS) { "Illegal operator [$operator]" }
            return op
        }

    }
}
