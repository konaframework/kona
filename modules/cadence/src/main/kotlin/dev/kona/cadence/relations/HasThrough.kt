package dev.kona.cadence.relations

import dev.kona.cadence.Cadence

/**
 * Distant one-to-many: parent → intermediate → related
 * (e.g. `User` has many `Comment` through `Post`).
 */
class HasManyThrough<R : Cadence<R>, I : Cadence<I>>(
    parent: Cadence<*>,
    related: R,
    val through: I,
    val firstKey: String,
    val secondKey: String,
    val localKey: String,
    val secondLocalKey: String,
) : Relation<R, List<R>>(parent, related) {
    override fun getResults(): List<R> = get()

    override fun addConstraints(): Boolean {
        val key = parent.getRawAttribute(localKey) ?: return false
        joinThrough()
        query.where(qualified(through.table, firstKey), key)
        return true
    }

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        joinThrough()
        query.whereIn(qualified(through.table, firstKey), keys(models, localKey))
    }

    override fun getEager(): List<R> {
        query.select("${related.table}.*", "${through.table}.$firstKey as _through_parent_key")
        return query.get()
    }

    override fun match(models: List<Cadence<*>>, results: List<R>, relation: String) {
        val grouped = results.groupBy { normalizeKey(it.getRawAttribute("_through_parent_key")) }
        models.forEach { model ->
            val key = normalizeKey(model.getRawAttribute(localKey))
            model.setRelation(relation, grouped[key] ?: emptyList<R>())
        }
    }

    private fun joinThrough() {
        query.join(
            through.table,
            qualified(through.table, secondLocalKey),
            "=",
            qualified(related.table, secondKey),
        )
    }
}

/**
 * Distant one-to-one through an intermediate model (first matching related row).
 */
class HasOneThrough<R : Cadence<R>, I : Cadence<I>>(
    parent: Cadence<*>,
    related: R,
    val through: I,
    val firstKey: String,
    val secondKey: String,
    val localKey: String,
    val secondLocalKey: String,
) : Relation<R, R?>(parent, related) {
    override fun getResults(): R? = first()

    override fun addConstraints(): Boolean {
        val key = parent.getRawAttribute(localKey) ?: return false
        joinThrough()
        query.where(qualified(through.table, firstKey), key)
        return true
    }

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        joinThrough()
        query.whereIn(qualified(through.table, firstKey), keys(models, localKey))
    }

    override fun getEager(): List<R> {
        query.select("${related.table}.*", "${through.table}.$firstKey as _through_parent_key")
        return query.get()
    }

    override fun match(models: List<Cadence<*>>, results: List<R>, relation: String) {
        val grouped = results.groupBy { normalizeKey(it.getRawAttribute("_through_parent_key")) }
        models.forEach { model ->
            val key = normalizeKey(model.getRawAttribute(localKey))
            model.setRelation(relation, grouped[key]?.first())
        }
    }

    private fun joinThrough() {
        query.join(
            through.table,
            qualified(through.table, secondLocalKey),
            "=",
            qualified(related.table, secondKey),
        )
    }
}
