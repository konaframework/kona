package dev.kona.cadence

import java.util.function.Consumer

/**
 * Static-style helpers for one model class. Use as a Kotlin companion
 * (`companion object : CadenceCompanion<User>(User::class.java)` → `User.find(1)`)
 * or from Java via `Cadence.of(User.class)`.
 */
open class CadenceCompanion<T : Cadence<T>>(
    val modelClass: Class<T>,
) {
    fun newInstance(): T = Cadence.instantiate(modelClass)

    fun newInstance(attributes: Map<String, Any?>): T = newInstance().fill(attributes)

    fun query(): ModelBuilder<T> = newInstance().newQuery()

    /** Registers a [ModelObserver] for this model class (Eloquent `Model::observe`). */
    fun observe(observer: ModelObserver<T>): CadenceCompanion<T> = apply {
        registerObserver(modelClass, observer)
    }

    /**
     * Registers a named global scope applied to every [query] for this model.
     * Call from the companion `init` block (Kotlin) or once at app boot (Java).
     */
    fun addGlobalScope(name: String, scope: Consumer<ModelBuilder<T>>): CadenceCompanion<T> = apply {
        GlobalScopes.add(modelClass, name, Scope { builder ->
            @Suppress("UNCHECKED_CAST")
            scope.accept(builder as ModelBuilder<T>)
        })
    }

    /** Registers a [Scope] instance; the identifier is the scope's concrete class name. */
    fun addGlobalScope(scope: Scope): CadenceCompanion<T> = apply {
        GlobalScopes.add(modelClass, scope)
    }

    fun hasGlobalScope(name: String): Boolean = GlobalScopes.has(modelClass, name)

    fun getGlobalScope(name: String): Scope? = GlobalScopes.get(modelClass, name)

    fun getGlobalScopes(): Map<String, Scope> = GlobalScopes.all(modelClass)

    fun removeGlobalScope(name: String): CadenceCompanion<T> = apply {
        GlobalScopes.remove(modelClass, name)
    }

    fun withoutGlobalScope(name: String): ModelBuilder<T> = query().withoutGlobalScope(name)

    fun withoutGlobalScopes(): ModelBuilder<T> = query().withoutGlobalScopes()

    fun withoutGlobalScopes(names: Collection<String>): ModelBuilder<T> = query().withoutGlobalScopes(names)

    fun withoutGlobalScopesExcept(names: Collection<String>): ModelBuilder<T> =
        query().withoutGlobalScopesExcept(names)

    fun all(): List<T> = query().get()

    /** Query that eager loads [relations] (`User.with("posts").get()`). */
    fun with(vararg relations: String): ModelBuilder<T> = query().with(*relations)

    fun withCount(vararg relations: String): ModelBuilder<T> = query().withCount(*relations)

    fun withExists(vararg relations: String): ModelBuilder<T> = query().withExists(*relations)

    fun withSum(spec: String): ModelBuilder<T> = query().withSum(spec)

    fun withSum(relation: String, column: String): ModelBuilder<T> = query().withSum(relation, column)

    fun withAvg(spec: String): ModelBuilder<T> = query().withAvg(spec)

    fun withAvg(relation: String, column: String): ModelBuilder<T> = query().withAvg(relation, column)

    fun withMax(spec: String): ModelBuilder<T> = query().withMax(spec)

    fun withMax(relation: String, column: String): ModelBuilder<T> = query().withMax(relation, column)

    fun withMin(spec: String): ModelBuilder<T> = query().withMin(spec)

    fun withMin(relation: String, column: String): ModelBuilder<T> = query().withMin(relation, column)

    fun first(): T? = query().first()

    fun sole(): T = query().sole()

    fun find(id: Any?): T? = query().find(id)

    fun findOrFail(id: Any?): T = query().findOrFail(id)

    /** Matching [id], or a new unsaved instance with the primary key set to [id]. */
    fun findOrNew(id: Any?): T = query().findOrNew(id)

    fun findMany(ids: Collection<*>): List<T> = query().findMany(ids)

    /** Fills (mass-assignment rules apply), saves, and returns a new model. */
    fun create(attributes: Map<String, Any?>): T {
        val model = newInstance(attributes)
        model.save()
        return model
    }

    fun firstOrNew(attributes: Map<String, Any?>): T = query().firstOrNew(attributes)

    @JvmOverloads
    fun firstOrCreate(attributes: Map<String, Any?>, values: Map<String, Any?> = emptyMap()): T =
        query().firstOrCreate(attributes, values)

    @JvmOverloads
    fun updateOrCreate(attributes: Map<String, Any?>, values: Map<String, Any?> = emptyMap()): T =
        query().updateOrCreate(attributes, values)

    fun where(column: String, value: Any?): ModelBuilder<T> = query().where(column, value)

    fun where(column: String, operator: String, value: Any?): ModelBuilder<T> = query().where(column, operator, value)

    fun whereIn(column: String, values: Collection<*>): ModelBuilder<T> = query().whereIn(column, values)

    @JvmOverloads
    fun whereHas(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        query().whereHas(relation, constraint)

    @JvmOverloads
    fun orWhereHas(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        query().orWhereHas(relation, constraint)

    @JvmOverloads
    fun whereDoesntHave(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        query().whereDoesntHave(relation, constraint)

    @JvmOverloads
    fun orWhereDoesntHave(relation: String, constraint: Consumer<ModelBuilder<*>>? = null): ModelBuilder<T> =
        query().orWhereDoesntHave(relation, constraint)

    fun has(relation: String): ModelBuilder<T> = query().has(relation)

    fun doesntHave(relation: String): ModelBuilder<T> = query().doesntHave(relation)

    fun withTrashed(): ModelBuilder<T> = query().withTrashed()

    fun onlyTrashed(): ModelBuilder<T> = query().onlyTrashed()

    fun count(): Long = query().count()

    @JvmOverloads
    fun paginate(perPage: Int = newInstance().perPage, page: Int = 1): Page<T> = query().paginate(perPage, page)

    @JvmOverloads
    fun simplePaginate(perPage: Int = newInstance().perPage, page: Int = 1): SimplePage<T> =
        query().simplePaginate(perPage, page)

    @JvmOverloads
    fun cursorPaginate(
        perPage: Int = newInstance().perPage,
        cursor: String? = null,
        column: String = "id",
    ): CursorPage<T> = query().cursorPaginate(perPage, cursor, column)

    /** Loads and deletes each model by key (soft-deleting where applicable); returns how many were deleted. */
    fun destroy(vararg ids: Any): Int = destroy(ids.asList())

    fun destroy(ids: Collection<*>): Int = findMany(ids).count { it.delete() }

    /** Empties this model's table (`TRUNCATE TABLE`). */
    fun truncate() {
        query().truncate()
    }
}
