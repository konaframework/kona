package dev.kona.cadence.relations

import dev.kona.cadence.Cadence
import dev.kona.cadence.ModelBuilder
import java.util.function.Consumer

/**
 * A link from a [parent] model to [R] models. Calling a relation method on a model
 * (`user.posts()`) returns a fresh relation whose [query] can be constrained further before
 * [getResults] runs it. Eager loading reuses the same relation object for a whole list of
 * parents: [addEagerConstraints] adds one `IN (...)` constraint and [match] hands each parent its
 * slice of the results, so loading `posts` for 100 users costs one query, not 100.
 *
 * [V] is what the relation resolves to: `List<R>` for to-many relations, `R?` for to-one.
 */
abstract class Relation<R : Cadence<R>, V>(
    val parent: Cadence<*>,
    val related: R,
) {
    /** Query over the related table; soft-delete scopes of [R] apply. */
    val query: ModelBuilder<R> = related.newQuery()

    private var constrained: Boolean? = null

    /** Runs the relation for [parent] alone (lazy loading). */
    abstract fun getResults(): V

    /** Limits [query] to rows related to [parent]; false when the parent has no key yet (no rows). */
    protected abstract fun addConstraints(): Boolean

    private fun constrained(): Boolean = constrained ?: addConstraints().also { constrained = it }

    /**
     * Applies parent constraints to [query] once.
     * Returns false when the parent cannot constrain (for example, it has no key yet).
     */
    protected fun ensureConstraints(): Boolean = constrained()

    /** Every related row of [parent] (to-one relations included). */
    fun get(): List<R> = if (constrained()) getEager() else emptyList()

    /** The first related row of [parent], or null. */
    fun first(): R? {
        if (!constrained()) {
            return null
        }
        query.limit(1)
        return getEager().firstOrNull()
    }

    fun count(): Long = if (constrained()) query.count() else 0

    fun exists(): Boolean = count() > 0

    /** Constrains [query] to the related rows of every model in [models]. */
    abstract fun addEagerConstraints(models: List<Cadence<*>>)

    /** Stores each parent's results under [relation] (empty list / null when it has none). */
    abstract fun match(models: List<Cadence<*>>, results: List<R>, relation: String)

    /** Runs [query] as currently constrained and hydrates the rows (pivot relations attach `pivot` data). */
    open fun getEager(): List<R> = query.get()

    /** Adds constraints with access to the full [ModelBuilder] (`posts().constrain { it.where("published", true) }`). */
    fun constrain(constraint: Consumer<ModelBuilder<R>>): Relation<R, V> = apply { constraint.accept(query) }

    fun where(column: String, value: Any?): Relation<R, V> = apply { query.where(column, value) }

    fun where(column: String, operator: String, value: Any?): Relation<R, V> = apply { query.where(column, operator, value) }

    @JvmOverloads
    fun orderBy(column: String, direction: String = "asc"): Relation<R, V> = apply { query.orderBy(column, direction) }

    /** Eager loads [relations] on the related models this relation returns. */
    fun with(vararg relations: String): Relation<R, V> = apply { query.with(*relations) }

    internal open fun eagerLoadOnto(models: List<Cadence<*>>, name: String) {
        addEagerConstraints(models)
        match(models, getEager(), name)
    }

    protected fun qualified(table: String, column: String): String = "$table.$column"

    companion object {
        /** Keys compare as `Long` when numeric, so `INTEGER` and `BIGINT` columns still match. */
        @JvmStatic
        fun normalizeKey(value: Any?): Any? = if (value is Number) value.toLong() else value

        internal fun keys(models: List<Cadence<*>>, column: String): List<Any?> =
            models.mapNotNull { it.getRawAttribute(column) }.distinctBy { normalizeKey(it) }

        /** `BlogPost` → `blog_post`. */
        @JvmStatic
        fun snake(className: String): String = Regex("([a-z0-9])([A-Z])").replace(className, "$1_$2").lowercase()

        /** Register morph aliases (`Relation.morphMap(mapOf("posts" to Post::class.java))`). */
        @JvmStatic
        fun morphMap(map: Map<String, Class<out Cadence<*>>>) = MorphMap.morphMap(map)

        @JvmStatic
        fun clearMorphMap() = MorphMap.clear()
    }
}
