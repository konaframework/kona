package org.konaframework.cadence

import org.konaframework.cadence.relations.BelongsTo
import org.konaframework.cadence.relations.BelongsToMany
import org.konaframework.cadence.relations.EagerLoader
import org.konaframework.cadence.relations.HasManyThrough
import org.konaframework.cadence.relations.HasOneOrMany
import org.konaframework.cadence.relations.HasOneThrough
import org.konaframework.cadence.relations.MorphMany
import org.konaframework.cadence.relations.MorphMap
import org.konaframework.cadence.relations.MorphOne
import org.konaframework.cadence.relations.MorphTo
import org.konaframework.cadence.relations.Relation
import org.konaframework.cadence.relations.RelationConstraint
import java.util.function.Consumer

/**
 * Model-aware query: the [QueryBuilder] constraints plus hydration into [T] instances.
 * For [SoftDeletes] models trashed rows are excluded unless [withTrashed] / [onlyTrashed] is called.
 */
class ModelBuilder<T : Cadence<T>> internal constructor(
    /** Prototype instance supplying table, key, and scope metadata. */
    val model: T,
    private val query: QueryBuilder,
) {
    private var trashed = Trashed.EXCLUDE
    private val eagerLoads = linkedMapOf<String, RelationConstraint?>()
    private val removedGlobalScopes = linkedSetOf<String>()
    private var withoutAllGlobalScopes = false
    private var exceptGlobalScopes: Set<String>? = null

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
            val subquery = existenceQuery(name, null, Aggregate.COUNT)
            query.selectRaw("(${subquery.toSql()}) as ${grammar.wrap(countAlias)}", subquery.getBindings())
        }
        return this
    }

    /**
     * Adds a `{relation}_exists` (or alias) boolean select subquery for each name.
     * Supports `posts as has_posts` aliases.
     */
    fun withExists(vararg relations: String): ModelBuilder<T> {
        if (!query.hasSelectColumns()) {
            query.select("${model.table}.*")
        }
        val grammar = model.connection().grammar
        for (spec in relations) {
            val (name, alias) = parseRelationAlias(spec)
            val existsAlias = alias ?: "${name}_exists"
            val subquery = existenceQuery(name, null, Aggregate.EXISTS)
            query.selectRaw(
                "case when exists (${subquery.toSql()}) then 1 else 0 end as ${grammar.wrap(existsAlias)}",
                subquery.getBindings(),
            )
        }
        return this
    }

    /**
     * Adds a `{relation}_sum_{column}` (or alias) select subquery.
     * [spec] is `relation:column` or `relation as alias:column`.
     */
    fun withSum(spec: String): ModelBuilder<T> {
        if (!query.hasSelectColumns()) {
            query.select("${model.table}.*")
        }
        val grammar = model.connection().grammar
        val (name, alias, column) = Cadence.parseSumSpec(spec)
        val subquery = existenceQuery(name, null, Aggregate.SUM, column)
        query.selectRaw("(${subquery.toSql()}) as ${grammar.wrap(alias)}", subquery.getBindings())
        return this
    }

    /** Convenience overload: `withSum("posts", "votes")` → alias `posts_sum_votes`. */
    fun withSum(relation: String, column: String): ModelBuilder<T> = withSum("$relation:$column")

    /** Adds `{relation}_avg_{column}` (or alias) select subquery. Spec: `relation:column` or `relation as alias:column`. */
    fun withAvg(spec: String): ModelBuilder<T> = withAggregate(Aggregate.AVG, spec)

    fun withAvg(relation: String, column: String): ModelBuilder<T> = withAvg("$relation:$column")

    /** Adds `{relation}_max_{column}` (or alias) select subquery. */
    fun withMax(spec: String): ModelBuilder<T> = withAggregate(Aggregate.MAX, spec)

    fun withMax(relation: String, column: String): ModelBuilder<T> = withMax("$relation:$column")

    /** Adds `{relation}_min_{column}` (or alias) select subquery. */
    fun withMin(spec: String): ModelBuilder<T> = withAggregate(Aggregate.MIN, spec)

    fun withMin(relation: String, column: String): ModelBuilder<T> = withMin("$relation:$column")

    private fun withAggregate(kind: Aggregate, spec: String): ModelBuilder<T> {
        if (!query.hasSelectColumns()) {
            query.select("${model.table}.*")
        }
        val grammar = model.connection().grammar
        val fn = kind.name.lowercase()
        val (name, alias, column) = Cadence.parseAggregateSpec(fn, spec)
        val subquery = existenceQuery(name, null, kind, column)
        query.selectRaw("(${subquery.toSql()}) as ${grammar.wrap(alias)}", subquery.getBindings())
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

    /** Drops one registered global scope for this query only. */
    fun withoutGlobalScope(name: String): ModelBuilder<T> = apply { removedGlobalScopes += name }

    /** Drops every registered global scope for this query only. */
    fun withoutGlobalScopes(): ModelBuilder<T> = apply {
        withoutAllGlobalScopes = true
        exceptGlobalScopes = null
    }

    /** Drops the named global scopes for this query only. */
    fun withoutGlobalScopes(names: Collection<String>): ModelBuilder<T> = apply {
        removedGlobalScopes.addAll(names)
    }

    /** Keeps only [names] among registered global scopes for this query. */
    fun withoutGlobalScopesExcept(names: Collection<String>): ModelBuilder<T> = apply {
        withoutAllGlobalScopes = false
        exceptGlobalScopes = names.toSet()
    }

    /** Underlying query with soft-delete and global scopes applied (a copy; safe to modify). */
    fun toBase(): QueryBuilder {
        val base = query.copy()
        val model = model
        if (model is SoftDeletes && trashed != Trashed.INCLUDE) {
            val column = "${model.table}.${model.deletedAtColumn}"
            base.groupWheres()
            if (trashed == Trashed.ONLY) base.whereNotNull(column) else base.whereNull(column)
        }
        applyGlobalScopes(base)
        return base
    }

    private fun applyGlobalScopes(base: QueryBuilder) {
        if (withoutAllGlobalScopes) {
            return
        }
        val registered = GlobalScopes.all(model.javaClass)
        if (registered.isEmpty()) {
            return
        }
        val applicator = ModelBuilder(model, base).also {
            it.withoutAllGlobalScopes = true
            it.trashed = Trashed.INCLUDE
        }
        val except = exceptGlobalScopes
        for ((name, scope) in registered) {
            if (except != null) {
                if (name !in except) continue
            } else if (name in removedGlobalScopes) {
                continue
            }
            scope.apply(applicator)
        }
    }

    /** The unscoped underlying query, for constraints not mirrored on this class. */
    fun getQuery(): QueryBuilder = query

    // ---- constraints (delegated) ----

    fun select(vararg columns: String): ModelBuilder<T> = apply { query.select(*columns) }

    fun addSelect(vararg columns: String): ModelBuilder<T> = apply { query.addSelect(*columns) }

    @JvmOverloads
    fun selectRaw(expression: String, bindings: List<Any?> = emptyList()): ModelBuilder<T> =
        apply { query.selectRaw(expression, bindings) }

    fun distinct(): ModelBuilder<T> = apply { query.distinct() }

    fun where(column: String, value: Any?): ModelBuilder<T> = apply { query.where(column, value) }

    fun where(column: String, operator: String, value: Any?): ModelBuilder<T> = apply { query.where(column, operator, value) }

    fun where(group: Consumer<QueryBuilder>): ModelBuilder<T> = apply { query.where(group) }

    fun orWhere(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhere(column, value) }

    fun orWhere(column: String, operator: String, value: Any?): ModelBuilder<T> = apply { query.orWhere(column, operator, value) }

    fun orWhere(group: Consumer<QueryBuilder>): ModelBuilder<T> = apply { query.orWhere(group) }

    /**
     * Parenthesized `and (…)` group whose callback receives a model builder, so relation
     * constraints (`whereHas`) compose inside the group (Eloquent `where(fn (Builder $q) => …)`).
     * Soft-delete and global scopes stay on the outer builder.
     */
    fun whereNested(group: Consumer<ModelBuilder<T>>): ModelBuilder<T> = apply {
        query.where { nested -> group.accept(nestedBuilder(nested)) }
    }

    /** `or (…)` counterpart of [whereNested]. */
    fun orWhereNested(group: Consumer<ModelBuilder<T>>): ModelBuilder<T> = apply {
        query.orWhere { nested -> group.accept(nestedBuilder(nested)) }
    }

    private fun nestedBuilder(nested: QueryBuilder): ModelBuilder<T> = ModelBuilder(model, nested).also {
        it.withoutAllGlobalScopes = true
        it.trashed = Trashed.INCLUDE
    }

    fun whereNull(column: String): ModelBuilder<T> = apply { query.whereNull(column) }

    fun orWhereNull(column: String): ModelBuilder<T> = apply { query.orWhereNull(column) }

    fun whereNotNull(column: String): ModelBuilder<T> = apply { query.whereNotNull(column) }

    fun orWhereNotNull(column: String): ModelBuilder<T> = apply { query.orWhereNotNull(column) }

    fun whereIn(column: String, values: Collection<*>): ModelBuilder<T> = apply { query.whereIn(column, values) }

    fun orWhereIn(column: String, values: Collection<*>): ModelBuilder<T> = apply { query.orWhereIn(column, values) }

    fun whereNotIn(column: String, values: Collection<*>): ModelBuilder<T> = apply { query.whereNotIn(column, values) }

    fun orWhereNotIn(column: String, values: Collection<*>): ModelBuilder<T> = apply { query.orWhereNotIn(column, values) }

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

    @JvmOverloads
    fun orWhereRaw(sql: String, bindings: List<Any?> = emptyList()): ModelBuilder<T> =
        apply { query.orWhereRaw(sql, bindings) }

    fun whereDate(column: String, value: Any?): ModelBuilder<T> = apply { query.whereDate(column, value) }

    fun whereDate(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.whereDate(column, operator, value) }

    fun orWhereDate(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhereDate(column, value) }

    fun orWhereDate(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.orWhereDate(column, operator, value) }

    fun whereDay(column: String, value: Any?): ModelBuilder<T> = apply { query.whereDay(column, value) }

    fun whereDay(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.whereDay(column, operator, value) }

    fun orWhereDay(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhereDay(column, value) }

    fun orWhereDay(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.orWhereDay(column, operator, value) }

    fun whereMonth(column: String, value: Any?): ModelBuilder<T> = apply { query.whereMonth(column, value) }

    fun whereMonth(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.whereMonth(column, operator, value) }

    fun orWhereMonth(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhereMonth(column, value) }

    fun orWhereMonth(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.orWhereMonth(column, operator, value) }

    fun whereYear(column: String, value: Any?): ModelBuilder<T> = apply { query.whereYear(column, value) }

    fun whereYear(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.whereYear(column, operator, value) }

    fun orWhereYear(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhereYear(column, value) }

    fun orWhereYear(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.orWhereYear(column, operator, value) }

    fun whereTime(column: String, value: Any?): ModelBuilder<T> = apply { query.whereTime(column, value) }

    fun whereTime(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.whereTime(column, operator, value) }

    fun orWhereTime(column: String, value: Any?): ModelBuilder<T> = apply { query.orWhereTime(column, value) }

    fun orWhereTime(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.orWhereTime(column, operator, value) }

    fun groupBy(vararg columns: String): ModelBuilder<T> = apply { query.groupBy(*columns) }

    fun having(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.having(column, operator, value) }

    fun orHaving(column: String, operator: String, value: Any?): ModelBuilder<T> =
        apply { query.orHaving(column, operator, value) }

    @JvmOverloads
    fun havingRaw(sql: String, bindings: List<Any?> = emptyList()): ModelBuilder<T> =
        apply { query.havingRaw(sql, bindings) }

    @JvmOverloads
    fun orHavingRaw(sql: String, bindings: List<Any?> = emptyList()): ModelBuilder<T> =
        apply { query.orHavingRaw(sql, bindings) }

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

    /** Runs [callback] with this builder and returns this (side-effect helper). */
    fun tap(callback: Consumer<ModelBuilder<T>>): ModelBuilder<T> {
        callback.accept(this)
        return this
    }

    /** Runs [callback] against this builder (named scope helper). */
    fun scope(callback: Consumer<ModelBuilder<T>>): ModelBuilder<T> {
        callback.accept(this)
        return this
    }

    fun lockForUpdate(): ModelBuilder<T> = apply { query.lockForUpdate() }

    fun sharedLock(): ModelBuilder<T> = apply { query.sharedLock() }

    fun withoutLock(): ModelBuilder<T> = apply { query.withoutLock() }

    // ---- relation existence ----

    @JvmOverloads
    fun whereHas(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        addHas(AND, relation, constraint, negate = false)

    @JvmOverloads
    fun orWhereHas(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        addHas(OR, relation, constraint, negate = false)

    /**
     * `exists (…)` SQL correlating this model to [relation], for composing OR groups
     * (e.g. Firefly table search across related columns) without a nested ModelBuilder where.
     */
    fun existsClause(relation: String): Pair<String, List<Any?>> = existsClause(relation, null)

    fun existsClause(relation: String, constraint: Consumer<ModelBuilder<*>>?): Pair<String, List<Any?>> {
        val subquery = existenceQuery(relation, constraint, Aggregate.EXISTS)
        return "exists (${subquery.toSql()})" to subquery.getBindings()
    }

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

    /** Hydrated results as an [CadenceCollection]. */
    fun getCollection(): CadenceCollection<T> = CadenceCollection(get())

    fun first(): T? = toBase().first()?.let { eagerLoadRelations(listOf(model.newFromBuilder(it))).first() }

    fun firstOrFail(): T = first() ?: throw ModelNotFoundException(model.javaClass)

    /**
     * Exactly one matching model. Throws [ModelNotFoundException] when none match and
     * [MultipleRecordsFoundException] when more than one matches.
     */
    fun sole(): T {
        val rows = limit(2).get()
        return when (rows.size) {
            0 -> throw ModelNotFoundException(model.javaClass)
            1 -> rows[0]
            else -> throw MultipleRecordsFoundException(model.javaClass, rows.size)
        }
    }

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

    /** Matching [id], or a new unsaved instance with the primary key set to [id]. */
    fun findOrNew(id: Any?): T {
        find(id)?.let { return it }
        val instance = model.newInstance()
        if (id != null) {
            instance.setAttribute(model.primaryKey, id)
        }
        return instance
    }

    fun findMany(ids: Collection<*>): List<T> = whereIn(qualifiedKey(), ids).get()

    fun value(column: String): Any? = toBase().value(column)

    fun pluck(column: String): List<Any?> = toBase().pluck(column)

    /** Keyed pluck: map of [key] → [column] (Laravel `pluck('name', 'id')`). */
    fun pluck(column: String, key: String): Map<Any?, Any?> = toBase().pluck(column, key)

    fun union(other: QueryBuilder): ModelBuilder<T> = apply { query.union(other) }

    fun unionAll(other: QueryBuilder): ModelBuilder<T> = apply { query.unionAll(other) }

    fun crossJoin(table: String): ModelBuilder<T> = apply { this.query.crossJoin(table) }

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

    @JvmOverloads
    fun simplePaginate(perPage: Int = model.perPage, page: Int = 1): SimplePage<T> =
        toBase().simplePaginate(perPage, page).map { model.newFromBuilder(it) }.also { eagerLoadRelations(it.data) }

    @JvmOverloads
    fun cursorPaginate(
        perPage: Int = model.perPage,
        cursor: String? = null,
        column: String = "id",
    ): CursorPage<T> =
        toBase().cursorPaginate(perPage, cursor, column)
            .map { model.newFromBuilder(it) }
            .also { eagerLoadRelations(it.data) }

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

    /** Lazy sequence of models (see [QueryBuilder.cursor]). */
    fun cursor(): Sequence<T> = lazy(100)

    /** [lazy] with a default page size of 1000. */
    fun lazy(): Sequence<T> = lazy(1000)

    /** Lazy sequence of models fetched in pages of [chunkSize]. */
    fun lazy(chunkSize: Int): Sequence<T> {
        require(chunkSize >= 1) { "Lazy chunk size must be at least 1, got $chunkSize" }
        return sequence {
            var page = 1
            while (true) {
                val models = eagerLoadRelations(hydrate(toBase().forPage(page, chunkSize).get()))
                if (models.isEmpty()) {
                    break
                }
                for (item in models) {
                    yield(item)
                }
                if (models.size < chunkSize) {
                    break
                }
                page += 1
            }
        }
    }

    /** Chunks by the model's primary key (see [QueryBuilder.chunkById]). */
    fun chunkById(count: Int, consumer: Consumer<List<T>>): Unit =
        chunkById(count, model.primaryKey, consumer)

    /** Chunks by ascending [column] using `where column > last` (see [QueryBuilder.chunkById]). */
    fun chunkById(count: Int, column: String, consumer: Consumer<List<T>>) {
        toBase().chunkById(count, column) { rows ->
            consumer.accept(eagerLoadRelations(hydrate(rows)))
        }
    }

    /** [lazyById] with page size 1000 on the primary key. */
    fun lazyById(): Sequence<T> = lazyById(1000, model.primaryKey)

    /** [lazyById] on the primary key. */
    fun lazyById(chunkSize: Int): Sequence<T> = lazyById(chunkSize, model.primaryKey)

    /** Lazy models via [chunkById] semantics. */
    fun lazyById(chunkSize: Int, column: String): Sequence<T> {
        require(chunkSize >= 1) { "Lazy chunk size must be at least 1, got $chunkSize" }
        return sequence {
            var lastId: Any? = null
            while (true) {
                val batch = toBase().withoutLock().copy().clearOrders().orderBy(column)
                if (lastId != null) {
                    batch.where(column, ">", lastId)
                }
                val models = eagerLoadRelations(hydrate(batch.limit(chunkSize).get()))
                if (models.isEmpty()) {
                    break
                }
                for (item in models) {
                    yield(item)
                }
                lastId = models.last().getRawAttribute(column)
                    ?: throw CadenceException("lazyById: last model missing column [$column]")
                if (models.size < chunkSize) {
                    break
                }
            }
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

    /** Empties the model's table (`TRUNCATE TABLE`). Ignores query constraints and soft-delete scopes. */
    fun truncate() {
        toBase().truncate()
    }

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
        val rel = EagerLoader.relationOf(model, relation)
        if (rel is MorphTo) {
            return addMorphToHas(boolean, rel, constraint, negate)
        }
        val subquery = existenceQuery(relation, constraint, Aggregate.EXISTS)
        val sql = if (negate) "not exists (${subquery.toSql()})" else "exists (${subquery.toSql()})"
        if (boolean == AND) {
            query.whereRaw(sql, subquery.getBindings())
        } else {
            query.orWhereRaw(sql, subquery.getBindings())
        }
        return this
    }

    /**
     * MorphTo existence: OR across registered [MorphMap] types (Laravel `whereHas` / `whereHasMorph`).
     * Requires at least one morph alias registration.
     */
    private fun addMorphToHas(
        boolean: String,
        rel: MorphTo,
        constraint: Consumer<ModelBuilder<*>>?,
        negate: Boolean,
    ): ModelBuilder<T> {
        val entries = MorphMap.entries()
        if (entries.isEmpty()) {
            throw CadenceException(
                "MorphTo [${rel.relationName}] whereHas requires MorphMap registrations " +
                    "(MorphMap.morphMap(...)) so candidate types are known.",
            )
        }
        val parts = mutableListOf<String>()
        val bindings = mutableListOf<Any?>()
        for ((alias, type) in entries) {
            @Suppress("UNCHECKED_CAST")
            val instance = Cadence.instantiate(type as Class<Cadence<*>>)
            val relatedQuery = instance.newQuery()
            @Suppress("UNCHECKED_CAST")
            constraint?.accept(relatedQuery as ModelBuilder<*>)
            val base = relatedQuery.toBase()
            base.whereColumn(
                "${instance.table}.${instance.primaryKey}",
                "${model.table}.${rel.idColumn}",
            )
            base.where("${model.table}.${rel.typeColumn}", alias)
            val reset = base.resetForAggregate().selectRaw("1")
            parts += "exists (${reset.toSql()})"
            bindings.addAll(reset.getBindings())
        }
        val combined = parts.joinToString(" or ")
        val sql = if (negate) "not ($combined)" else "($combined)"
        if (boolean == AND) {
            query.whereRaw(sql, bindings)
        } else {
            query.orWhereRaw(sql, bindings)
        }
        return this
    }

    private fun existenceQuery(
        relation: String,
        constraint: Consumer<ModelBuilder<*>>?,
        aggregate: Aggregate,
        sumColumn: String? = null,
    ): QueryBuilder {
        val rel = EagerLoader.relationOf(model, relation)
        @Suppress("UNCHECKED_CAST")
        constraint?.accept(rel.query as ModelBuilder<*>)
        val base = rel.query.toBase()
        correlate(base, rel)
        val reset = base.resetForAggregate()
        val grammar = model.connection().grammar
        return when (aggregate) {
            Aggregate.COUNT -> reset.selectRaw("count(*)")
            Aggregate.EXISTS -> reset.selectRaw("1")
            Aggregate.SUM -> {
                val wrapped = grammar.wrap("${rel.related.table}.${sumColumn!!}")
                reset.selectRaw("coalesce(sum($wrapped), 0)")
            }
            Aggregate.AVG -> {
                val wrapped = grammar.wrap("${rel.related.table}.${sumColumn!!}")
                reset.selectRaw("avg($wrapped)")
            }
            Aggregate.MAX -> {
                val wrapped = grammar.wrap("${rel.related.table}.${sumColumn!!}")
                reset.selectRaw("max($wrapped)")
            }
            Aggregate.MIN -> {
                val wrapped = grammar.wrap("${rel.related.table}.${sumColumn!!}")
                reset.selectRaw("min($wrapped)")
            }
        }
    }

    private fun correlate(base: QueryBuilder, rel: Relation<*, *>) {
        when (rel) {
            is MorphMany<*> -> {
                base.whereColumn(
                    "${rel.related.table}.${rel.foreignKey}",
                    "${model.table}.${rel.localKey}",
                )
                base.where("${rel.related.table}.${rel.morphType}", rel.morphClass)
            }
            is MorphOne<*> -> {
                base.whereColumn(
                    "${rel.related.table}.${rel.foreignKey}",
                    "${model.table}.${rel.localKey}",
                )
                base.where("${rel.related.table}.${rel.morphType}", rel.morphClass)
            }
            is MorphTo ->
                throw CadenceException(
                    "MorphTo uses whereHas MorphMap expansion; withCount/withSum are not supported on MorphTo.",
                )
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
            is HasManyThrough<*, *> -> {
                base.join(
                    rel.through.table,
                    "${rel.through.table}.${rel.secondLocalKey}",
                    "=",
                    "${rel.related.table}.${rel.secondKey}",
                )
                base.whereColumn(
                    "${rel.through.table}.${rel.firstKey}",
                    "${model.table}.${rel.localKey}",
                )
            }
            is HasOneThrough<*, *> -> {
                base.join(
                    rel.through.table,
                    "${rel.through.table}.${rel.secondLocalKey}",
                    "=",
                    "${rel.related.table}.${rel.secondKey}",
                )
                base.whereColumn(
                    "${rel.through.table}.${rel.firstKey}",
                    "${model.table}.${rel.localKey}",
                )
            }
            else ->
                throw CadenceException(
                    "Relationship [${rel.javaClass.name}] does not support whereHas / withCount.",
                )
        }
    }

    private fun qualifiedKey(): String = "${model.table}.${model.primaryKey}"

    private enum class Trashed { EXCLUDE, INCLUDE, ONLY }

    private enum class Aggregate { COUNT, EXISTS, SUM, AVG, MAX, MIN }

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
