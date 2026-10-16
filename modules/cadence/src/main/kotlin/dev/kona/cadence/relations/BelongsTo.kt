package dev.kona.cadence.relations

import dev.kona.cadence.Cadence

/**
 * The child side of a one-to-many: the parent model (here called the child) holds
 * [foreignKey] pointing at the owner's [ownerKey] (`posts.user_id` → `users.id`).
 * Resolves to the owner or null.
 */
class BelongsTo<R : Cadence<R>>(
    child: Cadence<*>,
    related: R,
    val foreignKey: String,
    val ownerKey: String,
    /** Relation name used by [associate] / [dissociate] to keep the loaded value in sync. */
    val relationName: String,
) : Relation<R, R?>(child, related) {
    override fun getResults(): R? = first()

    override fun addConstraints(): Boolean {
        val key = parent.getRawAttribute(foreignKey) ?: return false
        query.where(qualified(related.table, ownerKey), key)
        return true
    }

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        query.whereIn(qualified(related.table, ownerKey), keys(models, foreignKey))
    }

    override fun match(models: List<Cadence<*>>, results: List<R>, relation: String) {
        val owners = results.associateBy { normalizeKey(it.getRawAttribute(ownerKey)) }
        models.forEach { it.setRelation(relation, owners[normalizeKey(it.getRawAttribute(foreignKey))]) }
    }

    /** Points the child at [owner] (unsaved until the child is saved). */
    fun associate(owner: R): Cadence<*> {
        parent.setAttribute(foreignKey, owner.getRawAttribute(ownerKey))
        parent.setRelation(relationName, owner)
        return parent
    }

    /** Clears the foreign key (unsaved until the child is saved). */
    fun dissociate(): Cadence<*> {
        parent.setAttribute(foreignKey, null)
        parent.setRelation(relationName, null)
        return parent
    }
}
