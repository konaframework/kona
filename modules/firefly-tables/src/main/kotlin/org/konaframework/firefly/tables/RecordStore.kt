package org.konaframework.firefly.tables

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.firefly.support.FireflyException
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

    fun constrain(query: Q, column: String, operator: String, value: Any?)

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

    override fun query(): MemoryQuery = MemoryQuery(rows)

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
            else -> query.where(column, operator, value)
        }
    }

    override fun search(query: ModelBuilder<T>, term: String, columns: List<String>) {
        if (term.isEmpty() || columns.isEmpty()) return
        query.where { group ->
            columns.forEachIndexed { index, column ->
                if (index == 0) {
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
        query.orderBy(column, normalized)
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

    override fun attributes(record: T): Map<String, Any?> = LinkedHashMap(record.getAttributes())

    private fun prototype(): T = try {
        val constructor = modelClass.getDeclaredConstructor()
        constructor.isAccessible = true
        constructor.newInstance()
    } catch (error: ReflectiveOperationException) {
        throw FireflyException("Cannot construct [${modelClass.name}]")
    }

    private fun coerce(key: String): Any = key.toLongOrNull() ?: key
}
