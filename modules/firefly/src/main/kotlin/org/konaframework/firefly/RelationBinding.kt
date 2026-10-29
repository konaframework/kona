package org.konaframework.firefly

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceException
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.relations.BelongsToMany
import org.konaframework.cadence.relations.EagerLoader
import org.konaframework.cadence.relations.HasMany
import org.konaframework.cadence.relations.HasManyThrough
import org.konaframework.cadence.relations.HasOneOrMany
import org.konaframework.cadence.relations.MorphMany
import org.konaframework.cadence.relations.MorphToMany
import org.konaframework.cadence.relations.Relation
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStore
import org.konaframework.firefly.tables.RecordStores

/**
 * One relation of one owner record. Scopes related queries to the owner and performs the
 * Cadence writes behind create / associate / dissociate / attach / detach.
 */
internal class RelationBinding(val owner: Cadence<*>, val name: String) {
    private val probe: Relation<*, *> = open()
    val kind: RelationKind = kindOf(probe)
    val related: Cadence<*> = probe.related
    val relatedClass: Class<*> = related.javaClass
    private val relatedTable = ident(related.table)

    fun store(): RecordStore<Any?, Any?> {
        @Suppress("UNCHECKED_CAST")
        return RecordStores.open(relatedClass) as RecordStore<Any?, Any?>
    }

    fun softDeletes(): Boolean = SoftDeletes::class.java.isAssignableFrom(relatedClass)

    /** Limit [query] (a related-model builder) to the owner's related rows; selects [pivotColumns]. */
    fun scope(query: ModelBuilder<*>, pivotColumns: List<String>) {
        when (val relation = probe) {
            is HasOneOrMany<*, *> -> {
                val key = owner.getRawAttribute(relation.localKey)
                if (key == null) {
                    query.whereRaw("1 = 0")
                    return
                }
                query.where("$relatedTable.${ident(relation.foreignKey)}", key)
                val morph = morphOf(relation)
                if (morph != null) query.where("$relatedTable.${ident(morph.first)}", morph.second)
            }
            is BelongsToMany<*> -> {
                val (sql, bindings) = pivotMembership(relation)
                query.whereRaw("$relatedTable.${ident(relation.relatedKey)} in ($sql)", bindings)
                if (pivotColumns.isNotEmpty()) selectPivot(query, relation, pivotColumns)
            }
            else -> {
                val through = probe as HasManyThrough<*, *>
                val throughTable = ident(through.through.table)
                val key = owner.getRawAttribute(through.localKey)
                query.whereRaw(
                    "$relatedTable.${ident(through.secondKey)} in (select $throughTable.${ident(through.secondLocalKey)} " +
                        "from $throughTable where $throughTable.${ident(through.firstKey)} = ?)",
                    listOf(key),
                )
            }
        }
    }

    /**
     * Rows a picker may offer: not yet associated / attached to the owner, searched by
     * [title] and [searchColumns], ordered by title.
     */
    fun candidates(
        operation: RelationOperation,
        title: String,
        searchColumns: List<String>,
        search: String,
        limit: Int,
        duplicates: Boolean,
        modify: (ModelBuilder<*>) -> Unit,
    ): List<Cadence<*>> {
        val query = related.newQuery()
        val relation = probe
        if (operation == RelationOperation.ASSOCIATE) {
            val one = requireOneToMany(relation)
            val fk = "$relatedTable.${ident(one.foreignKey)}"
            val morph = morphOf(one)
            val key = owner.getRawAttribute(one.localKey)
            if (morph == null) {
                query.whereRaw("($fk is null or $fk <> ?)", listOf(key))
            } else {
                val type = "$relatedTable.${ident(morph.first)}"
                query.whereRaw("($fk is null or $fk <> ? or $type is null or $type <> ?)", listOf(key, morph.second))
            }
        } else {
            val many = requireManyToMany(relation)
            if (!duplicates) {
                val (sql, bindings) = pivotMembership(many)
                query.whereRaw("$relatedTable.${ident(many.relatedKey)} not in ($sql)", bindings)
            }
        }
        if (search.isNotBlank()) {
            val columns = listOf(title) + searchColumns
            query.where { group ->
                columns.forEachIndexed { index, column ->
                    if (index == 0) group.where(column, "like", "%$search%") else group.orWhere(column, "like", "%$search%")
                }
            }
        }
        modify(query)
        query.orderBy(title)
        query.limit(limit)
        return query.get()
    }

    /** A related record in the owner's scope; [withTrashed] also finds soft-deleted rows. */
    fun find(key: String, withTrashed: Boolean, modify: (ModelBuilder<*>) -> Unit): Cadence<*>? {
        val query = related.newQuery()
        if (withTrashed) query.withTrashed()
        scope(query, emptyList())
        modify(query)
        query.where("$relatedTable.${ident(related.primaryKey)}", coerce(key))
        return query.first()
    }

    fun create(attributes: Map<String, Any?>, pivot: Map<String, Any?>): Cadence<*> {
        val relation = open()
        if (relation is HasOneOrMany<*, *>) return relation.create(attributes)
        val store = store()
        val model = store.newRecord()
        store.fill(model, attributes)
        store.save(model)
        (relation as BelongsToMany<*>).attach(listOf(model), pivot)
        return model as Cadence<*>
    }

    /** Point [record] at the owner (has-many foreign key, plus the morph type for morph-many). */
    fun associate(record: Cadence<*>, attributes: Map<String, Any?>) {
        val one = requireOneToMany(probe)
        if (attributes.isNotEmpty()) record.fill(attributes)
        record.setAttribute(one.foreignKey, owner.getRawAttribute(one.localKey))
        val morph = morphOf(one)
        if (morph != null) record.setAttribute(morph.first, morph.second)
        record.save()
    }

    fun dissociate(record: Cadence<*>) {
        val one = requireOneToMany(probe)
        record.setAttribute(one.foreignKey, null)
        val morph = morphOf(one)
        if (morph != null) record.setAttribute(morph.first, null)
        record.save()
    }

    fun attach(keys: List<String>, pivot: Map<String, Any?>) {
        requireManyToMany(probe)
        (open() as BelongsToMany<*>).attach(keys.map { coerce(it) }, pivot)
    }

    fun detach(keys: List<String>): Int {
        requireManyToMany(probe)
        return (open() as BelongsToMany<*>).detach(keys.map { coerce(it) })
    }

    /** Update the owner's pivot row for [key]; [pivot] is only non-empty for many-to-many managers. */
    fun updatePivot(key: String, pivot: Map<String, Any?>) {
        if (pivot.isEmpty()) return
        (open() as BelongsToMany<*>).updateExistingPivot(coerce(key), pivot)
    }

    /**
     * Delete a related record. Many-to-many links go first, except for soft deletes: the trashed
     * row stays linked so it can be restored from the owner.
     */
    fun delete(record: Cadence<*>) {
        if (kind.isManyToMany() && record !is SoftDeletes) detach(listOf(record.getKey().toString()))
        record.delete()
    }

    /** Permanently delete a soft-deleted related record (and its pivot links). */
    fun forceDelete(record: Cadence<*>) {
        if (kind.isManyToMany()) detach(listOf(record.getKey().toString()))
        (record as SoftDeletes).forceDelete()
    }

    private fun open(): Relation<*, *> = try {
        EagerLoader.relationOf(owner, name)
    } catch (error: CadenceException) {
        throw FireflyException("Relation manager [$name]: ${error.message}")
    }

    /** `select related_pivot_key from pivot where foreign_pivot_key = ? [and morph_type = ?]`. */
    private fun pivotMembership(relation: BelongsToMany<*>): Pair<String, List<Any?>> {
        val pivot = ident(relation.pivotTable)
        val bindings = ArrayList<Any?>()
        bindings.add(owner.getRawAttribute(relation.parentKey))
        val sql = StringBuilder(
            "select $pivot.${ident(relation.relatedPivotKey)} from $pivot where $pivot.${ident(relation.foreignPivotKey)} = ?",
        )
        if (relation is MorphToMany<*>) {
            sql.append(" and $pivot.${ident(relation.morphType)} = ?")
            bindings.add(relation.morphClass)
        }
        return sql.toString() to bindings
    }

    /** Pivot columns read per row as plain attributes (`role` beside the related columns). */
    private fun selectPivot(query: ModelBuilder<*>, relation: BelongsToMany<*>, columns: List<String>) {
        val pivot = ident(relation.pivotTable)
        query.select("$relatedTable.*")
        for (column in columns) {
            val (membership, bindings) = pivotMembership(relation)
            val match = membership.substringAfter(" where ")
            query.selectRaw(
                "(select $pivot.${ident(column)} from $pivot where $match and $pivot.${ident(relation.relatedPivotKey)} = " +
                    "$relatedTable.${ident(relation.relatedKey)} limit 1) as ${ident(column)}",
                bindings,
            )
        }
    }

    /** Callers check [RelationKind.isOneToMany] first (the manager only offers supported operations). */
    private fun requireOneToMany(relation: Relation<*, *>): HasOneOrMany<*, *> = relation as HasOneOrMany<*, *>

    /** Callers check [RelationKind.isManyToMany] first. */
    private fun requireManyToMany(relation: Relation<*, *>): BelongsToMany<*> = relation as BelongsToMany<*>

    private fun morphOf(relation: HasOneOrMany<*, *>): Pair<String, String>? =
        if (relation is MorphMany<*>) relation.morphType to relation.morphClass else null

    private fun kindOf(relation: Relation<*, *>): RelationKind = when (relation) {
        is MorphToMany<*> -> RelationKind.MORPH_TO_MANY
        is BelongsToMany<*> -> RelationKind.BELONGS_TO_MANY
        is MorphMany<*> -> RelationKind.MORPH_MANY
        is HasMany<*> -> RelationKind.HAS_MANY
        is HasManyThrough<*, *> -> RelationKind.HAS_MANY_THROUGH
        else -> throw FireflyException(
            "Relation manager [$name] needs HasMany, MorphMany, BelongsToMany, MorphToMany, or HasManyThrough " +
                "(got ${relation.javaClass.simpleName})",
        )
    }

    companion object {
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

        fun ident(value: String): String {
            if (!IDENTIFIER.matches(value)) throw FireflyException("Unsafe SQL identifier [$value]")
            return value
        }

        fun coerce(key: String): Any = key.toLongOrNull() ?: key
    }
}
