package org.konaframework.firefly.tables

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.relations.BelongsTo
import org.konaframework.cadence.relations.EagerLoader
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer
import java.util.function.Function

enum class Summary {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
}

interface RecordStore<T, Q> {
    fun query(): Q

    fun find(key: String): T?

    fun keyOf(record: T): String

    fun keyName(): String

    fun newRecord(): T

    fun fill(record: T, attributes: Map<String, Any?>): T

    fun save(record: T): T

    fun delete(record: T)

    fun supportsSoftDeletes(): Boolean

    fun <R> transaction(block: Function<RecordStore<T, Q>, R>): R

    /**
     * Add one `and` constraint. Operators: `=`, `!=`, `<>`, `>`, `>=`, `<`, `<=`, `like`,
     * `not like`, `null`, `not-null`, `in`, `not in` (value is a collection).
     */
    fun constrain(query: Q, column: String, operator: String, value: Any?)

    /**
     * Run [block] inside a parenthesized `and (…)` / `or (…)` group so `or` clauses added
     * by one filter cannot leak into other constraints. Stores without grouping run [block] inline.
     */
    fun group(query: Q, or: Boolean, block: Consumer<Q>) {
        block.accept(query)
    }

    /** Compare a date part: [part] is `date`, `day`, `month`, or `year`. */
    fun constrainDate(query: Q, column: String, part: String, operator: String, value: Any?) {
        throw FireflyException("Record store does not support date constraints")
    }

    /**
     * Relationship existence: [keys] null means "has any related record" (or none when [negate]);
     * otherwise "related to one of [keys]".
     */
    fun constrainRelationship(query: Q, relation: String, keys: Collection<*>?, negate: Boolean) {
        throw FireflyException("Record store does not support relationship constraints")
    }

    /**
     * Related record key → [titleAttribute] label, ordered by title (relationship Select filters).
     * [keys] limits the result to those related keys (indicator labels).
     */
    fun relationshipOptions(
        relation: String,
        titleAttribute: String,
        modify: Consumer<Any>?,
        search: String?,
        limit: Int?,
        keys: Collection<*>?,
    ): Map<String, String> {
        throw FireflyException("Record store does not support relationship options")
    }

    fun search(query: Q, term: String, columns: List<String>)

    fun sort(query: Q, column: String, direction: String)

    fun paginate(query: Q, page: Int, perPage: Int): TablePage<T>

    fun list(query: Q): List<T>

    fun summarize(query: Q, column: String, operation: Summary): Any?

    fun trashed(query: Q, mode: String)

    fun set(key: String, column: String, value: Any?)

    fun reorder(column: String, orderedKeys: List<String>)

    fun attributes(record: T): Map<String, Any?>
}

object RecordStores {
    @JvmStatic
    fun open(model: Class<*>): RecordStore<*, *> {
        if (isDocument(model)) {
            throw FireflyException(
                "Document model [${model.name}] needs kona-firefly-document on the classpath",
            )
        }
        if (Cadence::class.java.isAssignableFrom(model)) {
            return cadence(model)
        }
        throw FireflyException("No record store for [${model.name}]. Custom data uses Table.memory")
    }

    private fun isDocument(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null) {
            if (current.name == "org.konaframework.cadence.document.Document") return true
            current = current.superclass
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Cadence<T>> cadence(model: Class<*>): CadenceRecordStore<T> =
        CadenceRecordStore(model as Class<T>)
}

open class MemoryRecordStore(
    rows: List<Map<String, Any?>> = emptyList(),
    private val key: String = "id",
    private val softDeletes: Boolean = false,
) : RecordStore<Map<String, Any?>, MemoryQuery> {
    private val rows = rows.map { LinkedHashMap(it) }.toMutableList()
    private var sequence = this.rows.mapNotNull { row ->
        val value = row[key] ?: return@mapNotNull null
        value.toString().toLongOrNull()
    }.maxOrNull() ?: 0L

    /** Soft-delete stores hide `deleted_at` rows by default, like a Cadence `SoftDeletes` model. */
    override fun query(): MemoryQuery {
        val query = MemoryQuery(rows)
        if (softDeletes) query.trashed("without")
        return query
    }

    override fun find(key: String): Map<String, Any?>? = rows.find { keyOf(it) == key }

    override fun keyOf(record: Map<String, Any?>): String {
        val value = record[key] ?: throw FireflyException("Record is missing [$key]")
        return value.toString()
    }

    override fun keyName(): String = key

    override fun newRecord(): Map<String, Any?> = LinkedHashMap()

    override fun fill(record: Map<String, Any?>, attributes: Map<String, Any?>): Map<String, Any?> {
        val mutable = record as MutableMap<String, Any?>
        mutable.putAll(attributes)
        return mutable
    }

    override fun save(record: Map<String, Any?>): Map<String, Any?> {
        val stored = LinkedHashMap(record)
        if (stored[key] == null) {
            sequence += 1
            stored[key] = sequence.toString()
        }
        val id = keyOf(stored)
        val index = rows.indexOfFirst { keyOf(it) == id }
        if (index < 0) {
            rows.add(stored)
        } else {
            rows[index] = stored
        }
        val mutable = record as MutableMap<String, Any?>
        mutable.clear()
        mutable.putAll(stored)
        return mutable
    }

    override fun delete(record: Map<String, Any?>) {
        val id = keyOf(record)
        val index = rows.indexOfFirst { keyOf(it) == id }
        if (index < 0) throw FireflyException("Unknown record [$id]")
        if (softDeletes) {
            (rows[index] as MutableMap<String, Any?>)["deleted_at"] = "now"
        } else {
            rows.removeAt(index)
        }
    }

    override fun supportsSoftDeletes(): Boolean = softDeletes

    override fun <R> transaction(block: Function<RecordStore<Map<String, Any?>, MemoryQuery>, R>): R {
        val snapshot = rows.map { LinkedHashMap(it) }
        val previous = sequence
        return try {
            block.apply(this)
        } catch (error: RuntimeException) {
            rows.clear()
            rows.addAll(snapshot.map { LinkedHashMap(it) })
            sequence = previous
            throw error
        }
    }

    override fun constrain(query: MemoryQuery, column: String, operator: String, value: Any?) {
        query.where(column, operator, value)
    }

    override fun group(query: MemoryQuery, or: Boolean, block: Consumer<MemoryQuery>) {
        if (or) query.orWhereNested(block) else query.whereNested(block)
    }

    override fun constrainDate(query: MemoryQuery, column: String, part: String, operator: String, value: Any?) {
        when (part) {
            "date" -> query.whereDate(column, operator, value)
            "day" -> query.whereDay(column, operator, value)
            "month" -> query.whereMonth(column, operator, value)
            "year" -> query.whereYear(column, operator, value)
            else -> throw FireflyException("Unknown date part [$part]")
        }
    }

    /**
     * Memory relations are nested maps (`project` → `{id, name}`) or lists of maps; a
     * `{relation}_id` column also matches when the nested value is absent.
     */
    override fun constrainRelationship(query: MemoryQuery, relation: String, keys: Collection<*>?, negate: Boolean) {
        val wanted = keys?.map { it.toString() }?.toSet()
        query.whereRow { row ->
            val related = relatedKeys(row, relation)
            val matched = if (wanted == null) related.isNotEmpty() else related.any { it in wanted }
            if (negate) !matched else matched
        }
    }

    override fun relationshipOptions(
        relation: String,
        titleAttribute: String,
        modify: Consumer<Any>?,
        search: String?,
        limit: Int?,
        keys: Collection<*>?,
    ): Map<String, String> {
        val wanted = keys?.map { it.toString() }?.toSet()
        val found = LinkedHashMap<String, String>()
        for (row in rows) {
            for (related in relatedRecords(row[relation])) {
                val title = (related[titleAttribute] ?: continue).toString()
                val id = (related[key] ?: title).toString()
                if (wanted == null || id in wanted) found[id] = title
            }
        }
        val needle = search?.lowercase().orEmpty()
        var entries = found.entries.filter { needle.isEmpty() || it.value.lowercase().contains(needle) }
            .sortedBy { it.value.lowercase() }
        if (limit != null) entries = entries.take(limit)
        return entries.associateTo(LinkedHashMap()) { it.key to it.value }
    }

    private fun relatedKeys(row: Map<String, Any?>, relation: String): List<String> {
        val nested = relatedRecords(row[relation])
        if (nested.isNotEmpty()) return nested.mapNotNull { it[key]?.toString() }
        val foreign = row["${relation}_id"] ?: return emptyList()
        return listOf(foreign.toString())
    }

    private fun relatedRecords(value: Any?): List<Map<*, *>> = when (value) {
        is Map<*, *> -> listOf(value)
        is Collection<*> -> value.filterIsInstance<Map<*, *>>()
        else -> emptyList()
    }

    override fun search(query: MemoryQuery, term: String, columns: List<String>) {
        query.search(term, columns)
    }

    override fun sort(query: MemoryQuery, column: String, direction: String) {
        query.orderBy(column, direction)
    }

    override fun paginate(query: MemoryQuery, page: Int, perPage: Int): TablePage<Map<String, Any?>> =
        query.paginate(page, perPage)

    override fun list(query: MemoryQuery): List<Map<String, Any?>> = query.get()

    override fun summarize(query: MemoryQuery, column: String, operation: Summary): Any? = when (operation) {
        Summary.COUNT -> query.count()
        Summary.SUM -> query.sum(column)
        Summary.AVG -> query.avg(column)
        Summary.MIN -> query.min(column)
        Summary.MAX -> query.max(column)
    }

    override fun trashed(query: MemoryQuery, mode: String) {
        query.trashed(mode)
    }

    override fun set(key: String, column: String, value: Any?) {
        val row = find(key) ?: throw FireflyException("Unknown record [$key]")
        (row as MutableMap<String, Any?>)[column] = value
    }

    override fun reorder(column: String, orderedKeys: List<String>) {
        orderedKeys.forEachIndexed { position, id ->
            val row = find(id) ?: throw FireflyException("Unknown record [$id]")
            (row as MutableMap<String, Any?>)[column] = position
        }
    }

    override fun attributes(record: Map<String, Any?>): Map<String, Any?> = LinkedHashMap(record)
}

open class CadenceRecordStore<T : Cadence<T>>(
    private val modelClass: Class<T>,
) : RecordStore<T, ModelBuilder<T>> {
    override fun query(): ModelBuilder<T> = prototype().newQuery()

    override fun find(key: String): T? = try {
        query().where(keyName(), coerce(key)).first()
    } catch (_: org.konaframework.cadence.CadenceException) {
        null
    }

    override fun keyOf(record: T): String {
        val key = record.getKey() ?: throw FireflyException("Record is missing [${record.primaryKey}]")
        return key.toString()
    }

    override fun keyName(): String = prototype().primaryKey

    override fun newRecord(): T = prototype()

    override fun fill(record: T, attributes: Map<String, Any?>): T = record.fill(attributes)

    override fun save(record: T): T = record.apply { save() }

    override fun delete(record: T) {
        if (record.getKey() == null) throw FireflyException("Record is missing [${record.primaryKey}]")
        record.delete()
    }

    override fun supportsSoftDeletes(): Boolean =
        org.konaframework.cadence.SoftDeletes::class.java.isAssignableFrom(modelClass)

    override fun <R> transaction(block: Function<RecordStore<T, ModelBuilder<T>>, R>): R =
        prototype().connection().transaction { block.apply(this) }

    override fun constrain(query: ModelBuilder<T>, column: String, operator: String, value: Any?) {
        when (operator) {
            "null" -> query.whereNull(column)
            "not-null" -> query.whereNotNull(column)
            "in" -> query.whereIn(column, asCollection(value))
            "not in" -> query.whereNotIn(column, asCollection(value))
            else -> query.where(column, operator, value)
        }
    }

    override fun group(query: ModelBuilder<T>, or: Boolean, block: Consumer<ModelBuilder<T>>) {
        if (or) query.orWhereNested(block) else query.whereNested(block)
    }

    override fun constrainDate(query: ModelBuilder<T>, column: String, part: String, operator: String, value: Any?) {
        when (part) {
            "date" -> query.whereDate(column, operator, value)
            "day" -> query.whereDay(column, operator, value)
            "month" -> query.whereMonth(column, operator, value)
            "year" -> query.whereYear(column, operator, value)
            else -> throw FireflyException("Unknown date part [$part]")
        }
    }

    override fun constrainRelationship(query: ModelBuilder<T>, relation: String, keys: Collection<*>?, negate: Boolean) {
        if (keys == null) {
            if (negate) query.whereDoesntHave(relation) else query.whereHas(relation)
            return
        }
        val rel = EagerLoader.relationOf(prototype(), relation)
        if (rel is BelongsTo<*> && !negate) {
            query.whereIn("${prototype().table}.${rel.foreignKey}", coerceKeys(keys))
            return
        }
        val relatedKey = relatedKeyColumn(rel)
        val constraint = Consumer<ModelBuilder<*>> { related -> related.whereIn(relatedKey, coerceKeys(keys)) }
        if (negate) query.whereDoesntHave(relation, constraint) else query.whereHas(relation, constraint)
    }

    override fun relationshipOptions(
        relation: String,
        titleAttribute: String,
        modify: Consumer<Any>?,
        search: String?,
        limit: Int?,
        keys: Collection<*>?,
    ): Map<String, String> {
        val rel = EagerLoader.relationOf(prototype(), relation)
        val related = rel.related.newQuery()
        modify?.accept(related)
        val keyName = if (rel is BelongsTo<*>) rel.ownerKey else rel.related.primaryKey
        if (keys != null) related.whereIn(keyName, coerceKeys(keys))
        if (!search.isNullOrEmpty()) related.where(titleAttribute, "like", "%$search%")
        related.orderBy(titleAttribute)
        if (limit != null) related.limit(limit)
        val out = LinkedHashMap<String, String>()
        for (record in related.get()) {
            out[record.getAttribute(keyName).toString()] = record.getAttribute(titleAttribute)?.toString().orEmpty()
        }
        return out
    }

    private fun relatedKeyColumn(rel: org.konaframework.cadence.relations.Relation<*, *>): String {
        val name = if (rel is BelongsTo<*>) rel.ownerKey else rel.related.primaryKey
        return "${rel.related.table}.$name"
    }

    private fun asCollection(value: Any?): Collection<*> = when (value) {
        is Collection<*> -> value
        null -> emptyList<Any?>()
        else -> listOf(value)
    }

    private fun coerceKeys(keys: Collection<*>): List<Any?> = keys.map { key -> key.toString().toLongOrNull() ?: key }

    override fun search(query: ModelBuilder<T>, term: String, columns: List<String>) {
        if (term.isEmpty() || columns.isEmpty()) return
        // Group ORs so filters stay AND-ed: WHERE … AND (name LIKE ? OR exists (project…)).
        query.where { group ->
            columns.forEachIndexed { index, column ->
                if (column.contains('.')) {
                    val (sql, bindings) = relationExistsClause(query, column, term)
                    if (index == 0) group.whereRaw(sql, bindings) else group.orWhereRaw(sql, bindings)
                } else if (index == 0) {
                    group.where(column, "like", "%$term%")
                } else {
                    group.orWhere(column, "like", "%$term%")
                }
            }
        }
    }

    override fun sort(query: ModelBuilder<T>, column: String, direction: String) {
        val normalized = direction.lowercase()
        if (normalized != "asc" && normalized != "desc") {
            throw FireflyException("Sort direction must be asc or desc")
        }
        if (!column.contains('.')) {
            query.orderBy(column, normalized)
            return
        }
        sortRelated(query, column, normalized)
    }

    /**
     * `project.name` → exists (select 1 from projects where … and name like ?).
     * Nested paths nest whereHas inside the exists constraint (#366).
     */
    private fun relationExistsClause(
        query: ModelBuilder<T>,
        column: String,
        term: String,
    ): Pair<String, List<Any?>> {
        val relationPath = column.substringBeforeLast('.')
        val attribute = column.substringAfterLast('.')
        val parts = relationPath.split('.')
        return query.existsClause(parts[0]) { related ->
            constrainRelationSearch(related, parts.drop(1), attribute, term)
        }
    }

    private fun constrainRelationSearch(
        query: ModelBuilder<*>,
        remainingRelation: List<String>,
        attribute: String,
        term: String,
    ) {
        if (remainingRelation.isEmpty()) {
            query.where(attribute, "like", "%$term%")
            return
        }
        val head = remainingRelation.first()
        val tail = remainingRelation.drop(1)
        query.whereHas(head) { nested -> constrainRelationSearch(nested, tail, attribute, term) }
    }

    /** Left-join BelongsTo chain and order by the related attribute (#366). */
    private fun sortRelated(query: ModelBuilder<T>, column: String, direction: String) {
        val parts = column.split('.')
        val attribute = parts.last()
        val relationNames = parts.dropLast(1)
        var parent: Cadence<*> = prototype()
        var leftTable = parent.table
        for (relationName in relationNames) {
            val rel = EagerLoader.relationOf(parent, relationName)
            if (rel !is BelongsTo<*>) {
                throw FireflyException(
                    "Sortable nested column [$column] requires BelongsTo relations " +
                        "(got ${rel.javaClass.simpleName} for [$relationName])",
                )
            }
            val rightTable = rel.related.table
            query.leftJoin(
                rightTable,
                "$leftTable.${rel.foreignKey}",
                "=",
                "$rightTable.${rel.ownerKey}",
            )
            leftTable = rightTable
            parent = rel.related
        }
        if (!query.toBase().hasSelectColumns()) {
            query.select("${prototype().table}.*")
        }
        query.orderBy("$leftTable.$attribute", direction)
    }

    override fun paginate(query: ModelBuilder<T>, page: Int, perPage: Int): TablePage<T> {
        if (page < 1) throw FireflyException("Page must be at least 1")
        if (perPage < 1) throw FireflyException("Page size must be at least 1")
        val result = query.paginate(perPage, page)
        return TablePage(result.data, result.total, result.perPage, result.currentPage)
    }

    override fun list(query: ModelBuilder<T>): List<T> = query.get()

    override fun summarize(query: ModelBuilder<T>, column: String, operation: Summary): Any? = when (operation) {
        Summary.COUNT -> query.count()
        Summary.SUM -> query.sum(column)
        Summary.AVG -> query.avg(column)
        Summary.MIN -> query.min(column)
        Summary.MAX -> query.max(column)
    }

    override fun trashed(query: ModelBuilder<T>, mode: String) {
        when (mode) {
            "with" -> query.withTrashed()
            "only" -> query.onlyTrashed()
            "without", "" -> Unit
            else -> throw FireflyException("Unknown trashed mode [$mode]")
        }
    }

    override fun set(key: String, column: String, value: Any?) {
        val record = find(key) ?: throw FireflyException("Unknown record [$key]")
        record.forceFill(mapOf(column to value))
        record.save()
    }

    override fun reorder(column: String, orderedKeys: List<String>) {
        orderedKeys.forEachIndexed { position, id ->
            val record = find(id) ?: throw FireflyException("Unknown record [$id]")
            record.forceFill(mapOf(column to position))
            record.save()
        }
    }

    override fun attributes(record: T): Map<String, Any?> {
        val out = LinkedHashMap(record.getAttributes())
        // Expose eager-loaded relations so TextColumn.relationship / dotted cells can resolve titles.
        for ((name, value) in record.getRelations()) {
            out[name] = relationAttribute(value)
        }
        return out
    }

    private fun relationAttribute(value: Any?, seen: MutableSet<Int> = HashSet()): Any? {
        return when (value) {
            null -> null
            is Cadence<*> -> {
                val id = System.identityHashCode(value)
                // Break cycles (belongsTo back-references) so nested dig paths stay finite (#350).
                if (!seen.add(id)) {
                    LinkedHashMap(value.getAttributes())
                } else {
                    val out = LinkedHashMap(value.getAttributes())
                    for ((name, nested) in value.getRelations()) {
                        out[name] = relationAttribute(nested, seen)
                    }
                    out
                }
            }
            is Collection<*> -> value.map { item ->
                if (item is Cadence<*>) relationAttribute(item, seen) else item
            }
            else -> value
        }
    }

    private fun prototype(): T = try {
        val constructor = modelClass.getDeclaredConstructor()
        constructor.isAccessible = true
        constructor.newInstance()
    } catch (error: ReflectiveOperationException) {
        throw FireflyException("Cannot construct [${modelClass.name}]")
    }

    private fun coerce(key: String): Any = key.toLongOrNull() ?: key
}
