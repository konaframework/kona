package dev.kona.cadence.relations

import dev.kona.cadence.Cadence
import dev.kona.cadence.QueryBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 * Polymorphic morph alias map (`Relation::morphMap` shape).
 *
 * When empty, [morphAlias] falls back to the model's simple class name (and [resolveOrNull]
 * also tries `Class.forName` for fully-qualified names).
 */
object MorphMap {
    private val aliasToClass = ConcurrentHashMap<String, Class<out Cadence<*>>>()
    private val classToAlias = ConcurrentHashMap<Class<*>, String>()

    @JvmStatic
    fun morphMap(map: Map<String, Class<out Cadence<*>>>) {
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
    fun resolveOrNull(alias: String): Class<out Cadence<*>>? {
        aliasToClass[alias]?.let { return it }
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(alias) as Class<out Cadence<*>>
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}

/** Parent owns many polymorphic related rows (`comments` on Post/Video). */
class MorphMany<R : Cadence<R>>(
    parent: Cadence<*>,
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

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        super.addEagerConstraints(models)
        query.where(qualified(related.table, morphType), morphClass)
    }

    override fun getResults(): List<R> = get()

    override fun match(models: List<Cadence<*>>, results: List<R>, relation: String) {
        val grouped = group(results)
        models.forEach { it.setRelation(relation, grouped[keyOf(it)] ?: emptyList<R>()) }
    }

    override fun make(attributes: Map<String, Any?>): R =
        super.make(attributes).also { it.setAttribute(morphType, morphClass) }
}

/** Parent owns at most one polymorphic related row. */
class MorphOne<R : Cadence<R>>(
    parent: Cadence<*>,
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

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        super.addEagerConstraints(models)
        query.where(qualified(related.table, morphType), morphClass)
    }

    override fun getResults(): R? = first()

    override fun match(models: List<Cadence<*>>, results: List<R>, relation: String) {
        val grouped = group(results)
        models.forEach { it.setRelation(relation, grouped[keyOf(it)]?.first()) }
    }

    override fun make(attributes: Map<String, Any?>): R =
        super.make(attributes).also { it.setAttribute(morphType, morphClass) }
}

/** Placeholder related model for [MorphTo]'s type parameter (never queried as a table). */
class MorphToRelated : Cadence<MorphToRelated>() {
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
    parent: Cadence<*>,
    val typeColumn: String,
    val idColumn: String,
    val relationName: String,
) : Relation<MorphToRelated, Cadence<*>?>(parent, MorphToRelated()) {
    override fun getResults(): Cadence<*>? {
        val typeAttr = parent.getRawAttribute(typeColumn) ?: return null
        val type = typeAttr.toString()
        val id = parent.getRawAttribute(idColumn) ?: return null
        val relatedClass = MorphMap.resolveOrNull(type) ?: return null
        val instance = Cadence.instantiate(relatedClass as Class<Cadence<*>>)
        return instance.newQuery().where(instance.primaryKey, id).first()
    }

    override fun addConstraints(): Boolean {
        return parent.getRawAttribute(typeColumn) != null && parent.getRawAttribute(idColumn) != null
    }

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        // Eager loading is implemented in [eagerLoadOnto]; this hook stays no-op for Relation's default path.
        return
    }

    override fun match(models: List<Cadence<*>>, results: List<MorphToRelated>, relation: String) {
        // Matching is implemented in [eagerLoadOnto].
        return
    }

    override fun getEager(): List<MorphToRelated> = emptyList()

    override fun eagerLoadOnto(models: List<Cadence<*>>, name: String) {
        val byType = models.groupBy { it.getRawAttribute(typeColumn)?.toString() }
        val matched = HashMap<Cadence<*>, Cadence<*>?>()
        models.forEach { matched[it] = null }
        byType.forEach { (type, group) ->
            if (type == null) {
                return@forEach
            }
            val relatedClass = MorphMap.resolveOrNull(type) ?: return@forEach
            val instance = Cadence.instantiate(relatedClass as Class<Cadence<*>>)
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

    fun associate(owner: Cadence<*>): Cadence<*> {
        parent.setAttribute(idColumn, owner.getKey())
        parent.setAttribute(typeColumn, MorphMap.morphAlias(owner.javaClass))
        parent.setRelation(relationName, owner)
        return parent
    }

    fun dissociate(): Cadence<*> {
        parent.setAttribute(idColumn, null)
        parent.setAttribute(typeColumn, null)
        parent.setRelation(relationName, null)
        return parent
    }
}

/**
 * Polymorphic many-to-many (`post.tags()` via `taggables` with `taggable_type` / `taggable_id`).
 *
 * [inverse] is true for [Cadence.morphedByMany] (related model is the morph parent on the pivot).
 */
class MorphToMany<R : Cadence<R>>(
    parent: Cadence<*>,
    related: R,
    pivotTable: String,
    foreignPivotKey: String,
    relatedPivotKey: String,
    parentKey: String,
    relatedKey: String,
    val morphType: String,
    val morphClass: String,
) : BelongsToMany<R>(parent, related, pivotTable, foreignPivotKey, relatedPivotKey, parentKey, relatedKey) {
    init {
        withPivot(morphType)
    }

    override fun addConstraints(): Boolean {
        if (!super.addConstraints()) {
            return false
        }
        query.where(qualified(pivotTable, morphType), morphClass)
        return true
    }

    override fun addEagerConstraints(models: List<Cadence<*>>) {
        super.addEagerConstraints(models)
        query.where(qualified(pivotTable, morphType), morphClass)
    }

    override fun attach(ids: Collection<*>, attributes: Map<String, Any?>) {
        val withMorph = LinkedHashMap(attributes)
        withMorph.putIfAbsent(morphType, morphClass)
        super.attach(ids, withMorph)
    }

    override fun pivotForParent(): QueryBuilder =
        super.pivotForParent().where(morphType, morphClass)
}
