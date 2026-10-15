package dev.kona.extract.relations

import dev.kona.extract.Extract
import java.util.concurrent.ConcurrentHashMap

/**
 * Polymorphic morph alias map (`Relation::morphMap` shape).
 *
 * When empty, [morphAlias] falls back to the model's simple class name (and [resolveOrNull]
 * also tries `Class.forName` for fully-qualified names).
 */
object MorphMap {
    private val aliasToClass = ConcurrentHashMap<String, Class<out Extract<*>>>()
    private val classToAlias = ConcurrentHashMap<Class<*>, String>()

    @JvmStatic
    fun morphMap(map: Map<String, Class<out Extract<*>>>) {
        map.forEach { (alias, type) ->
            aliasToClass[alias] = type
            classToAlias[type] = alias
        }
    }

    @JvmStatic
    fun clear() {
        aliasToClass.clear()
        classToAlias.clear()
    }

    @JvmStatic
    fun morphAlias(type: Class<*>): String =
        classToAlias[type] ?: type.simpleName

    @JvmStatic
    fun resolveOrNull(alias: String): Class<out Extract<*>>? {
        aliasToClass[alias]?.let { return it }
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(alias) as Class<out Extract<*>>
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}

/** Parent owns many polymorphic related rows (`comments` on Post/Video). */
class MorphMany<R : Extract<R>>(
    parent: Extract<*>,
    related: R,
    foreignKey: String,
    val morphType: String,
    val morphClass: String,
    localKey: String,
) : HasOneOrMany<R, List<R>>(parent, related, foreignKey, localKey) {
    override fun addConstraints(): Boolean {
        if (!super.addConstraints()) {
            return false
        }
        query.where(qualified(related.table, morphType), morphClass)
        return true
    }

    override fun addEagerConstraints(models: List<Extract<*>>) {
        super.addEagerConstraints(models)
        query.where(qualified(related.table, morphType), morphClass)
    }

    override fun getResults(): List<R> = get()

    override fun match(models: List<Extract<*>>, results: List<R>, relation: String) {
        val grouped = group(results)
        models.forEach { it.setRelation(relation, grouped[keyOf(it)] ?: emptyList<R>()) }
    }

    override fun make(attributes: Map<String, Any?>): R =
        super.make(attributes).also { it.setAttribute(morphType, morphClass) }
}

/** Parent owns at most one polymorphic related row. */
class MorphOne<R : Extract<R>>(
    parent: Extract<*>,
    related: R,
    foreignKey: String,
    val morphType: String,
    val morphClass: String,
    localKey: String,
) : HasOneOrMany<R, R?>(parent, related, foreignKey, localKey) {
    override fun addConstraints(): Boolean {
        if (!super.addConstraints()) {
            return false
        }
        query.where(qualified(related.table, morphType), morphClass)
        return true
    }

    override fun addEagerConstraints(models: List<Extract<*>>) {
        super.addEagerConstraints(models)
        query.where(qualified(related.table, morphType), morphClass)
    }

    override fun getResults(): R? = first()

    override fun match(models: List<Extract<*>>, results: List<R>, relation: String) {
        val grouped = group(results)
        models.forEach { it.setRelation(relation, grouped[keyOf(it)]?.first()) }
    }

    override fun make(attributes: Map<String, Any?>): R =
        super.make(attributes).also { it.setAttribute(morphType, morphClass) }
}

/** Placeholder related model for [MorphTo]'s type parameter (never queried as a table). */
class MorphToRelated : Extract<MorphToRelated>() {
    override val table = "_morph_to"
    override val timestamps = false
}

/**
 * Inverse polymorphic relation (`comment.commentable()` → Post or Video).
 *
 * Eager loading groups parents by morph type and issues one query per concrete class.
 */
@Suppress("UNCHECKED_CAST")
class MorphTo(
    parent: Extract<*>,
    val typeColumn: String,
    val idColumn: String,
    val relationName: String,
) : Relation<MorphToRelated, Extract<*>?>(parent, MorphToRelated()) {
    override fun getResults(): Extract<*>? {
        val typeAttr = parent.getRawAttribute(typeColumn) ?: return null
        val type = typeAttr.toString()
        val id = parent.getRawAttribute(idColumn) ?: return null
        val relatedClass = MorphMap.resolveOrNull(type) ?: return null
        val instance = Extract.instantiate(relatedClass as Class<Extract<*>>)
        return instance.newQuery().where(instance.primaryKey, id).first()
    }

    override fun addConstraints(): Boolean {
        return parent.getRawAttribute(typeColumn) != null && parent.getRawAttribute(idColumn) != null
    }

    override fun addEagerConstraints(models: List<Extract<*>>) {
        // Eager loading is implemented in [eagerLoadOnto]; this hook stays no-op for Relation's default path.
        return
    }

    override fun match(models: List<Extract<*>>, results: List<MorphToRelated>, relation: String) {
        // Matching is implemented in [eagerLoadOnto].
        return
    }

    override fun getEager(): List<MorphToRelated> = emptyList()

    override fun eagerLoadOnto(models: List<Extract<*>>, name: String) {
        val byType = models.groupBy { it.getRawAttribute(typeColumn)?.toString() }
        val matched = HashMap<Extract<*>, Extract<*>?>()
        models.forEach { matched[it] = null }
        byType.forEach { (type, group) ->
            if (type == null) {
                return@forEach
            }
            val relatedClass = MorphMap.resolveOrNull(type) ?: return@forEach
            val instance = Extract.instantiate(relatedClass as Class<Extract<*>>)
            val ids = keys(group, idColumn)
            if (ids.isEmpty()) {
                return@forEach
            }
            val found = instance.newQuery().whereIn(instance.primaryKey, ids).get()
            val byId = found.associateBy { normalizeKey(it.getRawAttribute(instance.primaryKey)) }
            group.forEach { model ->
                matched[model] = byId[normalizeKey(model.getRawAttribute(idColumn))]
            }
        }
        models.forEach { it.setRelation(name, matched[it]) }
    }

    fun associate(owner: Extract<*>): Extract<*> {
        parent.setAttribute(idColumn, owner.getKey())
        parent.setAttribute(typeColumn, MorphMap.morphAlias(owner.javaClass))
        parent.setRelation(relationName, owner)
        return parent
    }

    fun dissociate(): Extract<*> {
        parent.setAttribute(idColumn, null)
        parent.setAttribute(typeColumn, null)
        parent.setRelation(relationName, null)
        return parent
    }
}
