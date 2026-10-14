package dev.kona.extract

/**
 * Static-style helpers for one model class. Use as a Kotlin companion
 * (`companion object : ExtractCompanion<User>(User::class.java)` → `User.find(1)`)
 * or from Java via `Extract.of(User.class)`.
 */
open class ExtractCompanion<T : Extract<T>>(
    val modelClass: Class<T>,
) {
    fun newInstance(): T = Extract.instantiate(modelClass)

    fun newInstance(attributes: Map<String, Any?>): T = newInstance().fill(attributes)

    fun query(): ModelBuilder<T> = newInstance().newQuery()

    fun all(): List<T> = query().get()

    /** Query that eager loads [relations] (`User.with("posts").get()`). */
    fun with(vararg relations: String): ModelBuilder<T> = query().with(*relations)

    fun first(): T? = query().first()

    fun find(id: Any?): T? = query().find(id)

    fun findOrFail(id: Any?): T = query().findOrFail(id)

    fun findMany(ids: Collection<*>): List<T> = query().findMany(ids)

    /** Fills (mass-assignment rules apply), saves, and returns a new model. */
    fun create(attributes: Map<String, Any?>): T {
        val model = newInstance(attributes)
        model.save()
        return model
    }

    fun where(column: String, value: Any?): ModelBuilder<T> = query().where(column, value)

    fun where(column: String, operator: String, value: Any?): ModelBuilder<T> = query().where(column, operator, value)

    fun whereIn(column: String, values: Collection<*>): ModelBuilder<T> = query().whereIn(column, values)

    fun withTrashed(): ModelBuilder<T> = query().withTrashed()

    fun onlyTrashed(): ModelBuilder<T> = query().onlyTrashed()

    fun count(): Long = query().count()

    @JvmOverloads
    fun paginate(perPage: Int = newInstance().perPage, page: Int = 1): Page<T> = query().paginate(perPage, page)

    /** Loads and deletes each model by key (soft-deleting where applicable); returns how many were deleted. */
    fun destroy(vararg ids: Any): Int = destroy(ids.asList())

    fun destroy(ids: Collection<*>): Int = findMany(ids).count { it.delete() }
}
