package dev.kona.extract.relations

import dev.kona.extract.Extract

/**
 * Shared shape of [HasOne] and [HasMany]: related rows carry [foreignKey] pointing at the
 * parent's [localKey] (usually `user_id` → `users.id`).
 */
abstract class HasOneOrMany<R : Extract<R>, V>(
    parent: Extract<*>,
    related: R,
    val foreignKey: String,
    val localKey: String,
) : Relation<R, V>(parent, related) {
    protected val parentKey: Any? get() = parent.getRawAttribute(localKey)

    override fun addConstraints(): Boolean {
        val key = parentKey ?: return false
        query.where(qualified(related.table, foreignKey), key)
        return true
    }

    override fun addEagerConstraints(models: List<Extract<*>>) {
        query.whereIn(qualified(related.table, foreignKey), keys(models, localKey))
    }

    protected fun group(results: List<R>): Map<Any?, List<R>> =
        results.groupBy { normalizeKey(it.getRawAttribute(foreignKey)) }

    protected fun keyOf(model: Extract<*>): Any? = normalizeKey(model.getRawAttribute(localKey))

    /** A new, unsaved related model with [foreignKey] set to the parent's key. */
    fun make(attributes: Map<String, Any?>): R = related.newInstance(attributes).setAttribute(foreignKey, requireParentKey())

    /** Saves [model] after pointing its [foreignKey] at the parent. */
    fun save(model: R): R {
        model.setAttribute(foreignKey, requireParentKey())
        model.save()
        return model
    }

    /** Fills (mass-assignment rules apply), links, and saves a new related model. */
    open fun create(attributes: Map<String, Any?>): R = make(attributes).also { it.save() }

    private fun requireParentKey(): Any = parentKey
        ?: throw IllegalStateException("Parent [${parent.javaClass.simpleName}] has no [$localKey]; save it first")
}

/** One parent → many related rows. Resolves to a list (empty when there are none). */
class HasMany<R : Extract<R>>(
    parent: Extract<*>,
    related: R,
    foreignKey: String,
    localKey: String,
) : HasOneOrMany<R, List<R>>(parent, related, foreignKey, localKey) {
    override fun getResults(): List<R> = get()

    fun createMany(rows: List<Map<String, Any?>>): List<R> = rows.map { create(it) }

    fun saveMany(models: List<R>): List<R> = models.map { save(it) }

    override fun match(models: List<Extract<*>>, results: List<R>, relation: String) {
        val grouped = group(results)
        models.forEach { it.setRelation(relation, grouped[keyOf(it)] ?: emptyList<R>()) }
    }
}

/** One parent → at most one related row. Resolves to the model or null. */
class HasOne<R : Extract<R>>(
    parent: Extract<*>,
    related: R,
    foreignKey: String,
    localKey: String,
) : HasOneOrMany<R, R?>(parent, related, foreignKey, localKey) {
    override fun getResults(): R? = first()

    override fun match(models: List<Extract<*>>, results: List<R>, relation: String) {
        val grouped = group(results)
        models.forEach { it.setRelation(relation, grouped[keyOf(it)]?.first()) }
    }
}
