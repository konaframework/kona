package dev.kona.extract

import dev.kona.extract.relations.EagerLoader
import dev.kona.extract.relations.RelationConstraint
import java.util.function.Consumer

/**
 * Model-aware query: the [QueryBuilder] constraints plus hydration into [T] instances.
 * For [SoftDeletes] models trashed rows are excluded unless [withTrashed] / [onlyTrashed] is called.
 */
class ModelBuilder<T : Extract<T>> internal constructor(
    /** Prototype instance supplying table, key, and scope metadata. */
    val model: T,
    private val query: QueryBuilder,
) {
    private var trashed = Trashed.EXCLUDE
    private val eagerLoads = linkedMapOf<String, RelationConstraint?>()

    // ---- eager loading ----

    /** Relations to load for every result with one query each (`with("posts", "posts.comments")`). */
    fun with(vararg relations: String): ModelBuilder<T> = apply { relations.forEach { eagerLoads[it] = null } }

    /** Eager loads [relation], applying [constraint] to its query first. */
    fun with(relation: String, constraint: RelationConstraint?): ModelBuilder<T> = apply { eagerLoads[relation] = constraint }

    fun getEagerLoads(): Map<String, RelationConstraint?> = LinkedHashMap(eagerLoads)

    /** Applies this builder's [with] relations to already hydrated [models]. */
    fun eagerLoadRelations(models: List<T>): List<T> =
        if (eagerLoads.isEmpty()) models else EagerLoader.load(models, eagerLoads)

    fun withTrashed(): ModelBuilder<T> {
        trashed = Trashed.INCLUDE
        return this
    }

    fun onlyTrashed(): ModelBuilder<T> {
        trashed = Trashed.ONLY
        return this
    }

    /** Underlying query with the soft-delete scope applied (a copy; safe to modify). */
    fun toBase(): QueryBuilder {
        val base = query.copy()
        val model = model
        if (model is SoftDeletes && trashed != Trashed.INCLUDE) {
            val column = "${model.table}.${model.deletedAtColumn}"
            base.groupWheres()
            if (trashed == Trashed.ONLY) base.whereNotNull(column) else base.whereNull(column)
        }
        return base
    }

    /** The unscoped underlying query, for constraints not mirrored on this class. */
    fun getQuery(): QueryBuilder = query

    // ---- constraints (delegated) ----

    fun select(vararg columns: String): ModelBuilder<T> = apply { query.select(*columns) }

    fun where(column: String, value: Any?): ModelBuilder<T> = apply { query.where(column, value) }

    fun where(column: String, operator: String, value: Any?): ModelBuilder<T> = apply { query.where(column, operator, value) }

    fun where(group: Consumer<QueryBuilder>): ModelBuilder<T> = apply { query.where(group) }

    fun orWhere(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhere(column, value) }

    fun orWhere(column: String, operator: String, value: Any?): ModelBuilder<T> = apply { query.orWhere(column, operator, value) }

    fun orWhere(group: Consumer<QueryBuilder>): ModelBuilder<T> = apply { query.orWhere(group) }

    fun whereNull(column: String): ModelBuilder<T> = apply { query.whereNull(column) }

    fun whereNotNull(column: String): ModelBuilder<T> = apply { query.whereNotNull(column) }

    fun whereIn(column: String, values: Collection<*>): ModelBuilder<T> = apply { query.whereIn(column, values) }

    fun whereNotIn(column: String, values: Collection<*>): ModelBuilder<T> = apply { query.whereNotIn(column, values) }

    fun whereBetween(column: String, from: Any?, to: Any?): ModelBuilder<T> = apply { query.whereBetween(column, from, to) }

    @JvmOverloads
    fun whereRaw(sql: String, bindings: List<Any?> = emptyList()): ModelBuilder<T> = apply { query.whereRaw(sql, bindings) }

    fun join(table: String, first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.join(table, first, operator, second) }

    fun leftJoin(table: String, first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.leftJoin(table, first, operator, second) }

    @JvmOverloads
    fun orderBy(column: String, direction: String = "asc"): ModelBuilder<T> = apply { query.orderBy(column, direction) }

    fun orderByDesc(column: String): ModelBuilder<T> = apply { query.orderByDesc(column) }

    @JvmOverloads
    fun latest(column: String = model.createdAtColumn): ModelBuilder<T> = apply { query.latest(column) }

    @JvmOverloads
    fun oldest(column: String = model.createdAtColumn): ModelBuilder<T> = apply { query.oldest(column) }

    fun limit(value: Int): ModelBuilder<T> = apply { query.limit(value) }

    fun take(value: Int): ModelBuilder<T> = limit(value)

    fun offset(value: Int): ModelBuilder<T> = apply { query.offset(value) }

    fun skip(value: Int): ModelBuilder<T> = offset(value)

    fun forPage(page: Int, perPage: Int): ModelBuilder<T> = apply { query.forPage(page, perPage) }

    // ---- reads ----

    fun get(): List<T> = eagerLoadRelations(hydrate(toBase().get()))

    fun first(): T? = toBase().first()?.let { eagerLoadRelations(listOf(model.newFromBuilder(it))).first() }

    fun firstOrFail(): T = first() ?: throw ModelNotFoundException(model.javaClass)

    fun find(id: Any?): T? = where(qualifiedKey(), id).first()

    fun findOrFail(id: Any?): T = find(id) ?: throw ModelNotFoundException(model.javaClass, listOf(id))

    fun findMany(ids: Collection<*>): List<T> = whereIn(qualifiedKey(), ids).get()

    fun value(column: String): Any? = toBase().value(column)

    fun pluck(column: String): List<Any?> = toBase().pluck(column)

    fun count(): Long = toBase().count()

    fun exists(): Boolean = toBase().exists()

    fun doesntExist(): Boolean = toBase().doesntExist()

    @JvmOverloads
    fun paginate(perPage: Int = model.perPage, page: Int = 1): Page<T> =
        toBase().paginate(perPage, page).map { model.newFromBuilder(it) }.also { eagerLoadRelations(it.data) }

    fun hydrate(rows: List<Map<String, Any?>>): List<T> = rows.map { model.newFromBuilder(it) }

    // ---- writes ----

    /** Mass update; touches `updated_at` when the model uses timestamps. */
    fun update(values: Map<String, Any?>): Int {
        val withTimestamp = LinkedHashMap(values)
        if (model.timestamps && !withTimestamp.containsKey(model.updatedAtColumn)) {
            withTimestamp[model.updatedAtColumn] = model.freshTimestamp()
        }
        return toBase().update(withTimestamp)
    }

    /** Deletes matching rows, or trashes them for [SoftDeletes] models. */
    fun delete(): Int {
        val model = model
        if (model is SoftDeletes) {
            return update(mapOf(model.deletedAtColumn to model.freshTimestamp()))
        }
        return toBase().delete()
    }

    /** Permanently deletes matching rows (respecting the current trashed scope). */
    fun forceDelete(): Int = toBase().delete()

    /** Clears `deleted_at` on matching rows (trashed included); 0 for models without [SoftDeletes]. */
    fun restore(): Int {
        val model = model
        if (model !is SoftDeletes) {
            return 0
        }
        withTrashed()
        return update(mapOf(model.deletedAtColumn to null))
    }

    fun toSql(): String = toBase().toSql()

    fun getBindings(): List<Any?> = toBase().getBindings()

    private fun qualifiedKey(): String = "${model.table}.${model.primaryKey}"

    private enum class Trashed { EXCLUDE, INCLUDE, ONLY }
}
