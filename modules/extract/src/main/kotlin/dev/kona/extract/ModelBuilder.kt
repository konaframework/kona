package dev.kona.extract

import dev.kona.extract.relations.BelongsTo
import dev.kona.extract.relations.BelongsToMany
import dev.kona.extract.relations.EagerLoader
import dev.kona.extract.relations.HasOneOrMany
import dev.kona.extract.relations.Relation
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

    /**
     * Adds a `{relation}_count` (or alias) select subquery for each name.
     * Supports `posts as published_posts_count` aliases.
     */
    fun withCount(vararg relations: String): ModelBuilder<T> {
        if (!query.hasSelectColumns()) {
            query.select("${model.table}.*")
        }
        val grammar = model.connection().grammar
        for (spec in relations) {
            val (name, alias) = parseRelationAlias(spec)
            val countAlias = alias ?: "${name}_count"
            val subquery = existenceQuery(name, null, forCount = true)
            query.selectRaw("(${subquery.toSql()}) as ${grammar.wrap(countAlias)}", subquery.getBindings())
        }
        return this
    }

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

    fun orWhereBetween(column: String, from: Any?, to: Any?): ModelBuilder<T> =
        apply { query.orWhereBetween(column, from, to) }

    fun whereNotBetween(column: String, from: Any?, to: Any?): ModelBuilder<T> =
        apply { query.whereNotBetween(column, from, to) }

    fun orWhereNotBetween(column: String, from: Any?, to: Any?): ModelBuilder<T> =
        apply { query.orWhereNotBetween(column, from, to) }

    fun whereColumn(first: String, second: String): ModelBuilder<T> = apply { query.whereColumn(first, second) }

    fun whereColumn(first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.whereColumn(first, operator, second) }

    fun orWhereColumn(first: String, second: String): ModelBuilder<T> = apply { query.orWhereColumn(first, second) }

    fun orWhereColumn(first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.orWhereColumn(first, operator, second) }

    @JvmOverloads
    fun whereRaw(sql: String, bindings: List<Any?> = emptyList()): ModelBuilder<T> = apply { query.whereRaw(sql, bindings) }

    fun join(table: String, first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.join(table, first, operator, second) }

    fun leftJoin(table: String, first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.leftJoin(table, first, operator, second) }

    fun rightJoin(table: String, first: String, operator: String, second: String): ModelBuilder<T> =
        apply { query.rightJoin(table, first, operator, second) }

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

    fun `when`(condition: Boolean, callback: Consumer<ModelBuilder<T>>): ModelBuilder<T> {
        if (condition) {
            callback.accept(this)
        }
        return this
    }

    fun unless(condition: Boolean, callback: Consumer<ModelBuilder<T>>): ModelBuilder<T> = `when`(!condition, callback)

    /** Runs [callback] against this builder (named scope helper). */
    fun scope(callback: Consumer<ModelBuilder<T>>): ModelBuilder<T> {
        callback.accept(this)
        return this
    }

    // ---- relation existence ----

    @JvmOverloads
    fun whereHas(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        addHas(AND, relation, constraint, negate = false)

    @JvmOverloads
    fun orWhereHas(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        addHas(OR, relation, constraint, negate = false)

    @JvmOverloads
    fun whereDoesntHave(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        addHas(AND, relation, constraint, negate = true)

    @JvmOverloads
    fun orWhereDoesntHave(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        addHas(OR, relation, constraint, negate = true)

    fun has(relation: String): ModelBuilder<T> = whereHas(relation)

    fun doesntHave(relation: String): ModelBuilder<T> = whereDoesntHave(relation)

    // ---- reads ----

    fun get(): List<T> = eagerLoadRelations(hydrate(toBase().get()))

    /** Hydrated results as an [ExtractCollection]. */
    fun getCollection(): ExtractCollection<T> = ExtractCollection(get())

    fun first(): T? = toBase().first()?.let { eagerLoadRelations(listOf(model.newFromBuilder(it))).first() }

    fun firstOrFail(): T = first() ?: throw ModelNotFoundException(model.javaClass)

    /** First matching [attributes], or a new unsaved instance filled with them. */
    fun firstOrNew(attributes: Map<String, Any?>): T {
        attributes.forEach { (column, value) -> where(column, value) }
        return first() ?: model.newInstance(attributes)
    }

    /** First matching [attributes], or creates one with [attributes] + [values]. */
    @JvmOverloads
    fun firstOrCreate(attributes: Map<String, Any?>, values: Map<String, Any?> = emptyMap()): T {
        attributes.forEach { (column, value) -> where(column, value) }
        val found = first()
        if (found != null) {
            return found
        }
        val created = model.newInstance()
        created.fill(LinkedHashMap(attributes).also { it.putAll(values) })
        created.save()
        return created
    }

    /** Updates the first matching [attributes] with [values], or creates `attributes + values`. */
    @JvmOverloads
    fun updateOrCreate(attributes: Map<String, Any?>, values: Map<String, Any?> = emptyMap()): T {
        attributes.forEach { (column, value) -> where(column, value) }
        val found = first()
        if (found != null) {
            found.fill(values)
            found.save()
            return found
        }
        val created = model.newInstance()
        created.fill(LinkedHashMap(attributes).also { it.putAll(values) })
        created.save()
        return created
    }

    fun find(id: Any?): T? = where(qualifiedKey(), id).first()

    fun findOrFail(id: Any?): T = find(id) ?: throw ModelNotFoundException(model.javaClass, listOf(id))

    fun findMany(ids: Collection<*>): List<T> = whereIn(qualifiedKey(), ids).get()

    fun value(column: String): Any? = toBase().value(column)

    fun pluck(column: String): List<Any?> = toBase().pluck(column)

    fun count(): Long = toBase().count()

    fun max(column: String): Any? = toBase().max(column)

    fun min(column: String): Any? = toBase().min(column)

    fun sum(column: String): Any? = toBase().sum(column)

    fun avg(column: String): Any? = toBase().avg(column)

    fun exists(): Boolean = toBase().exists()

    fun doesntExist(): Boolean = toBase().doesntExist()

    @JvmOverloads
    fun paginate(perPage: Int = model.perPage, page: Int = 1): Page<T> =
        toBase().paginate(perPage, page).map { model.newFromBuilder(it) }.also { eagerLoadRelations(it.data) }

    fun hydrate(rows: List<Map<String, Any?>>): List<T> = rows.map { model.newFromBuilder(it) }

    fun chunk(count: Int, consumer: Consumer<List<T>>) {
        require(count >= 1) { "Chunk size must be at least 1, got $count" }
        var page = 1
        while (true) {
            val models = eagerLoadRelations(hydrate(toBase().forPage(page, count).get()))
            if (models.isEmpty()) {
                break
            }
            consumer.accept(models)
            if (models.size < count) {
                break
            }
            page += 1
        }
    }

    // ---- writes ----

    /** Mass update; touches `updated_at` when the model uses timestamps. */
    fun update(values: Map<String, Any?>): Int {
        val withTimestamp = LinkedHashMap(values)
        if (model.timestamps && !withTimestamp.containsKey(model.updatedAtColumn)) {
            withTimestamp[model.updatedAtColumn] = model.freshTimestamp()
        }
        return toBase().update(withTimestamp)
    }

    @JvmOverloads
    fun updateOrInsert(attributes: Map<String, Any?>, values: Map<String, Any?> = emptyMap()): Boolean =
        toBase().updateOrInsert(attributes, values)

    fun upsert(rows: List<Map<String, Any?>>, uniqueBy: Collection<String>): Int =
        toBase().upsert(rows, uniqueBy)

    fun upsert(rows: List<Map<String, Any?>>, uniqueBy: Collection<String>, update: Collection<String>?): Int =
        toBase().upsert(rows, uniqueBy, update)

    fun insertOrIgnore(rows: List<Map<String, Any?>>, uniqueBy: Collection<String>): Int =
        toBase().insertOrIgnore(rows, uniqueBy)

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

    private fun addHas(
        boolean: String,
        relation: String,
        constraint: Consumer<ModelBuilder<*>>?,
        negate: Boolean,
    ): ModelBuilder<T> {
        val subquery = existenceQuery(relation, constraint, forCount = false)
        val sql = if (negate) "not exists (${subquery.toSql()})" else "exists (${subquery.toSql()})"
        if (boolean == AND) {
            query.whereRaw(sql, subquery.getBindings())
        } else {
            query.orWhereRaw(sql, subquery.getBindings())
        }
        return this
    }

    private fun existenceQuery(
        relation: String,
        constraint: Consumer<ModelBuilder<*>>?,
        forCount: Boolean,
    ): QueryBuilder {
        val rel = EagerLoader.relationOf(model, relation)
        @Suppress("UNCHECKED_CAST")
        constraint?.accept(rel.query as ModelBuilder<*>)
        val base = rel.query.toBase()
        correlate(base, rel)
        return if (forCount) {
            base.resetForAggregate().selectRaw("count(*)")
        } else {
            base.resetForAggregate().selectRaw("1")
        }
    }

    private fun correlate(base: QueryBuilder, rel: Relation<*, *>) {
        when (rel) {
            is HasOneOrMany<*, *> ->
                base.whereColumn(
                    "${rel.related.table}.${rel.foreignKey}",
                    "${model.table}.${rel.localKey}",
                )
            is BelongsTo<*> ->
                base.whereColumn(
                    "${rel.related.table}.${rel.ownerKey}",
                    "${model.table}.${rel.foreignKey}",
                )
            is BelongsToMany<*> ->
                base.whereColumn(
                    "${rel.pivotTable}.${rel.foreignPivotKey}",
                    "${model.table}.${rel.parentKey}",
                )
            else ->
                throw ExtractException(
                    "Relationship [${rel.javaClass.name}] does not support whereHas / withCount.",
                )
        }
    }

    private fun qualifiedKey(): String = "${model.table}.${model.primaryKey}"

    private enum class Trashed { EXCLUDE, INCLUDE, ONLY }

    private companion object {
        const val AND = "and"
        const val OR = "or"

        fun parseRelationAlias(spec: String): Pair<String, String?> {
            val match = ALIAS.find(spec.trim())
            return if (match == null) {
                spec.trim() to null
            } else {
                spec.trim().substring(0, match.range.first).trim() to
                    spec.trim().substring(match.range.last + 1).trim()
            }
        }

        private val ALIAS = Regex("\\s+as\\s+", RegexOption.IGNORE_CASE)
    }
}
