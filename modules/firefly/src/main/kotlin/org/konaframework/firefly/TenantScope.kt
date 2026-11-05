package org.konaframework.firefly

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceException
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.relations.BelongsTo
import org.konaframework.cadence.relations.BelongsToMany
import org.konaframework.cadence.relations.EagerLoader
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordScope
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** How one model belongs to a tenant: the query constraint and the stamp for new records. */
internal interface OwnershipLink {
    fun constrain(tenant: Cadence<*>, query: ModelBuilder<*>)

    fun before(tenant: Cadence<*>, record: Cadence<*>)

    fun after(tenant: Cadence<*>, record: Cadence<*>)
}

/** A `belongsTo` ownership relationship: rows carry the tenant's key in [foreignKey]. */
private class BelongsToLink(private val table: String, private val foreignKey: String, private val ownerKey: String) : OwnershipLink {
    override fun constrain(tenant: Cadence<*>, query: ModelBuilder<*>) {
        query.where("$table.$foreignKey", tenant.getRawAttribute(ownerKey))
    }

    override fun before(tenant: Cadence<*>, record: Cadence<*>) {
        record.setAttribute(foreignKey, tenant.getRawAttribute(ownerKey))
    }

    override fun after(tenant: Cadence<*>, record: Cadence<*>) = Unit
}

/** A `belongsToMany` ownership relationship: rows are linked to the tenant through the pivot table. */
private class BelongsToManyLink(
    private val name: String,
    private val relatedTable: String,
    private val relatedKey: String,
) : OwnershipLink {
    override fun constrain(tenant: Cadence<*>, query: ModelBuilder<*>) {
        query.whereHas(name) { related -> related.where("$relatedTable.$relatedKey", tenant.getRawAttribute(relatedKey)) }
    }

    override fun before(tenant: Cadence<*>, record: Cadence<*>) = Unit

    override fun after(tenant: Cadence<*>, record: Cadence<*>) {
        (EagerLoader.relationOf(record, name) as BelongsToMany<*>).attach(listOf(tenant.getRawAttribute(relatedKey)))
    }
}

/**
 * Finds, per model, the relationship that ties its rows to the tenant model: [defaultName] unless a
 * resource names another one. A model without that relationship is not owned by a tenant.
 */
internal class Ownerships(private val tenantModel: Class<*>, private val defaultName: String) {
    private val names = ConcurrentHashMap<Class<*>, String>()
    private val links = ConcurrentHashMap<Class<*>, Optional<OwnershipLink>>()

    /** Resources that name their own ownership relationship register it for their model. */
    fun name(model: Class<*>, relationship: String) {
        names[model] = relationship
        links.remove(model)
    }

    /** The link for [model], or null when [model] has no such relationship. */
    fun link(model: Class<*>): OwnershipLink? = links.computeIfAbsent(model) { Optional.ofNullable(resolve(it)) }.orElse(null)

    private fun resolve(model: Class<*>): OwnershipLink? {
        val override = names[model]
        val name = if (override == null) defaultName else override
        val constructor = model.getDeclaredConstructor()
        constructor.isAccessible = true
        val prototype = constructor.newInstance() as Cadence<*>
        val relation = try {
            EagerLoader.relationOf(prototype, name)
        } catch (_: CadenceException) {
            return null
        }
        if (!relation.related.javaClass.isAssignableFrom(tenantModel)) {
            throw FireflyException("Ownership relationship [$name] of [${model.name}] must relate to [${tenantModel.name}]")
        }
        return when (relation) {
            is BelongsTo<*> -> BelongsToLink(prototype.table, relation.foreignKey, relation.ownerKey)
            is BelongsToMany<*> -> BelongsToManyLink(name, relation.related.table, relation.relatedKey)
            else -> throw FireflyException("Ownership relationship [$name] of [${model.name}] must be belongsTo or belongsToMany")
        }
    }
}

/**
 * The tenant constraint of one request: every Cadence store query over an owned model is limited to
 * [tenant]'s rows, and a new row of an owned model is stamped with [tenant] when it is saved.
 * Models in [unscoped] (resources with `scopedToTenant(false)`) are left alone.
 */
internal class TenantScope(
    private val tenant: Cadence<*>,
    private val ownerships: Ownerships,
    private val unscoped: Set<Class<*>>,
) : RecordScope {
    override fun constrain(model: Class<*>, query: ModelBuilder<*>) {
        link(model)?.constrain(tenant, query)
    }

    override fun create(record: Cadence<*>, save: Runnable) {
        val link = link(record.javaClass)
        if (link == null) {
            save.run()
            return
        }
        link.before(tenant, record)
        save.run()
        link.after(tenant, record)
    }

    private fun link(model: Class<*>): OwnershipLink? = if (model in unscoped) null else ownerships.link(model)
}
