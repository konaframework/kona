package dev.kona.cadence.relations

import dev.kona.cadence.Cadence
import dev.kona.cadence.QueryBuilder

/** Ids added and removed by [BelongsToMany.sync]. */
data class SyncResult(val attached: List<Any?>, val detached: List<Any?>)

/**
 * Many-to-many through a [pivotTable] holding [foreignPivotKey] (→ parent [parentKey]) and
 * [relatedPivotKey] (→ related [relatedKey]); for `users` ↔ `roles` that is
 * `role_user(user_id, role_id)`. Each returned model carries its pivot row as the `pivot`
 * relation (a `Map<String, Any?>` of the pivot keys plus [withPivot] columns).
 */
open class BelongsToMany<R : Cadence<R>>(
    parent: Cadence<*>,
    related: R,
    val pivotTable: String,
    val foreignPivotKey: String,
    val relatedPivotKey: String,
    val parentKey: String,
    val relatedKey: String,
) : Relation<R, List<R>>(parent, related) {
    private val pivotColumns = linkedSetOf(foreignPivotKey, relatedPivotKey)
    private var pivotTimestamps = false

    init {
        query.getQuery()
            .select(qualified(related.table, "*"))
            .join(pivotTable, qualified(pivotTable, relatedPivotKey), "=", qualified(related.table, relatedKey))
    }

    /** Extra pivot columns to read into each model's `pivot` map. */
    fun withPivot(vararg columns: String): BelongsToMany<R> = apply { pivotColumns += columns }

    /** Fills `created_at` / `updated_at` on [attach] and reads them into `pivot`. */
    fun withTimestamps(): BelongsToMany<R> = apply {
        pivotTimestamps = true
        withPivot(CREATED_AT, UPDATED_AT)
    }

    override fun getResults(): List<R> = get()

    open override fun addConstraints(): Boolean {
        val key = parent.getRawAttribute(parentKey) ?: return false
        query.where(qualified(pivotTable, foreignPivotKey), key)
        return true
    }

    open override fun addEagerConstraints(models: List<Cadence<*>>) {
        query.whereIn(qualified(pivotTable, foreignPivotKey), keys(models, parentKey))
    }

    override fun getEager(): List<R> {
        val base = query.toBase()
        pivotColumns.forEach { base.addSelect("${qualified(pivotTable, it)} as $PREFIX$it") }
        val models = base.get().map { row ->
            val attributes = linkedMapOf<String, Any?>()
            val pivot = linkedMapOf<String, Any?>()
            row.forEach { (column, value) ->
                if (column.startsWith(PREFIX)) pivot[column.removePrefix(PREFIX)] = value else attributes[column] = value
            }
            related.newFromBuilder(attributes).setRelation(PIVOT, pivot)
        }
        query.eagerLoadRelations(models)
        return models
    }

    override fun match(models: List<Cadence<*>>, results: List<R>, relation: String) {
        val grouped = results.groupBy { pivotOf(it)[foreignPivotKey].let(::normalizeKey) }
        models.forEach { it.setRelation(relation, grouped[normalizeKey(it.getRawAttribute(parentKey))] ?: emptyList<R>()) }
    }

    /** Inserts pivot rows linking the parent to [ids] (keys or [R] models), with extra [attributes]. */
    @JvmOverloads
    open fun attach(ids: Collection<*>, attributes: Map<String, Any?> = emptyMap()) {
        val key = requireParentKey()
        val rows = ids.map { id ->
            val row = linkedMapOf<String, Any?>(foreignPivotKey to key, relatedPivotKey to idOf(id))
            if (pivotTimestamps) {
                val now = related.freshTimestamp()
                row[CREATED_AT] = now
                row[UPDATED_AT] = now
            }
            row.putAll(attributes)
            row
        }
        pivot().insert(rows)
    }

    fun attach(id: Any): Unit = attach(listOf(id))

    /** Removes pivot rows for [ids]; returns how many were deleted. */
    open fun detach(ids: Collection<*>): Int {
        if (ids.isEmpty()) {
            return 0
        }
        return pivotForParent().whereIn(relatedPivotKey, ids.map { idOf(it) }).delete()
    }

    fun detach(id: Any): Int = detach(listOf(id))

    /** Removes every pivot row of the parent. */
    open fun detach(): Int = pivotForParent().delete()

    /** Makes the parent linked to exactly [ids]: attaches missing ones, detaches the rest. */
    fun sync(ids: Collection<*>): SyncResult {
        val wanted = ids.map { idOf(it) }
        val current = pivotForParent().pluck(relatedPivotKey)
        val currentKeys = current.map { normalizeKey(it) }.toSet()
        val wantedKeys = wanted.map { normalizeKey(it) }.toSet()
        val detached = current.filter { normalizeKey(it) !in wantedKeys }
        val attached = wanted.filter { normalizeKey(it) !in currentKeys }.distinctBy { normalizeKey(it) }
        detach(detached)
        if (attached.isNotEmpty()) {
            attach(attached)
        }
        return SyncResult(attached, detached)
    }

    /** Attaches missing [ids] without detaching existing pivot rows. */
    fun syncWithoutDetaching(ids: Collection<*>): SyncResult {
        val wanted = ids.map { idOf(it) }
        val currentKeys = pivotForParent().pluck(relatedPivotKey).map { normalizeKey(it) }.toSet()
        val attached = wanted.filter { normalizeKey(it) !in currentKeys }.distinctBy { normalizeKey(it) }
        if (attached.isNotEmpty()) {
            attach(attached)
        }
        return SyncResult(attached, emptyList())
    }

    /** Attaches missing [ids] and detaches ones already present; returns both lists. */
    fun toggle(ids: Collection<*>): SyncResult {
        val wanted = ids.map { idOf(it) }.distinctBy { normalizeKey(it) }
        val currentKeys = pivotForParent().pluck(relatedPivotKey).map { normalizeKey(it) }.toSet()
        val detached = wanted.filter { normalizeKey(it) in currentKeys }
        val attached = wanted.filter { normalizeKey(it) !in currentKeys }
        detach(detached)
        if (attached.isNotEmpty()) {
            attach(attached)
        }
        return SyncResult(attached, detached)
    }

    fun wherePivot(column: String, value: Any?): BelongsToMany<R> =
        apply { query.where(qualified(pivotTable, column), value) }

    fun wherePivot(column: String, operator: String, value: Any?): BelongsToMany<R> =
        apply { query.where(qualified(pivotTable, column), operator, value) }

    fun wherePivotIn(column: String, values: Collection<*>): BelongsToMany<R> =
        apply { query.whereIn(qualified(pivotTable, column), values) }

    /**
     * First related model matching [attributes] (already attached), or creates one from
     * [attributes] + [values], attaches it, and returns it (Eloquent `BelongsToMany::firstOrCreate`).
     */
    fun firstOrCreate(attributes: Map<String, Any?>): R = firstOrCreate(attributes, emptyMap())

    fun firstOrCreate(attributes: Map<String, Any?>, values: Map<String, Any?>): R {
        requireParentKey()
        ensureConstraints()
        attributes.forEach { (column, value) -> query.where(column, value) }
        val existing = query.first()
        if (existing != null) {
            return existing
        }
        val created = related.newInstance()
        created.fill(LinkedHashMap(attributes).also { it.putAll(values) })
        created.save()
        attach(created)
        return created
    }

    /** Updates extra columns on one existing pivot row. */
    fun updateExistingPivot(id: Any, attributes: Map<String, Any?>): Int {
        val values = LinkedHashMap(attributes)
        if (pivotTimestamps) {
            values[UPDATED_AT] = related.freshTimestamp()
        }
        return pivotForParent().where(relatedPivotKey, idOf(id)).update(values)
    }

    protected fun pivot(): QueryBuilder = parent.connection().table(pivotTable)

    protected open fun pivotForParent(): QueryBuilder = pivot().where(foreignPivotKey, requireParentKey())

    protected fun idOf(id: Any?): Any? = if (id is Cadence<*>) id.getRawAttribute(relatedKey) else id

    protected fun requireParentKey(): Any = parent.getRawAttribute(parentKey)
        ?: throw IllegalStateException("Parent [${parent.javaClass.simpleName}] has no [$parentKey]; save it first")

    @Suppress("UNCHECKED_CAST")
    private fun pivotOf(model: R): Map<String, Any?> = model.getRelation(PIVOT) as Map<String, Any?>

    companion object {
        /** Relation name under which each related model stores its pivot row. */
        const val PIVOT = "pivot"
        private const val PREFIX = "pivot_"
        private const val CREATED_AT = "created_at"
        private const val UPDATED_AT = "updated_at"
    }
}
