package org.konaframework.firefly

import org.konaframework.cadence.Cadence
import org.konaframework.firefly.tables.RecordStore
import org.konaframework.firefly.tables.RecordStores

/**
 * Default authorization of a relation manager: the policy of the related model decides.
 * [RelationManager.can] predicates override it per operation.
 *
 * Abilities: `viewAny` for the manager, `view` / `update` / `delete` / `restore` / `forceDelete` for a
 * row, `create`, `reorder`, and `deleteAny` for bulk actions. `associate`, `dissociate`, `attach` and
 * `detach` are asked only when the policy declares a method of that name, otherwise they are allowed.
 */
internal object RelationPolicies {
    private val ROW_LEVEL = setOf(
        RelationOperation.VIEW,
        RelationOperation.EDIT,
        RelationOperation.DELETE,
        RelationOperation.RESTORE,
        RelationOperation.FORCE_DELETE,
    )

    private val LINKING = setOf(
        RelationOperation.ASSOCIATE,
        RelationOperation.DISSOCIATE,
        RelationOperation.ATTACH,
        RelationOperation.DETACH,
    )

    fun allows(operation: RelationOperation, context: RelationContext, allowWithoutPolicy: Boolean?): Boolean {
        val model = context.relatedClass ?: return true
        val fallback = allowWithoutPolicy ?: Firefly.authorizesWithoutPolicy()
        val policy = Firefly.policyFor(model) ?: return fallback
        val subject = if (operation in ROW_LEVEL) rowOf(model, context.record) else null
        val ability = if (operation in ROW_LEVEL && subject == null) "viewAny" else abilityOf(operation)
        if (operation in LINKING && !FireflyPolicies.declares(policy, ability)) return true
        return FireflyPolicies.allows(model, ability, subject, fallback)
    }

    private fun abilityOf(operation: RelationOperation): String = when (operation) {
        RelationOperation.VIEW -> "view"
        RelationOperation.CREATE -> "create"
        RelationOperation.EDIT -> "update"
        RelationOperation.DELETE -> "delete"
        RelationOperation.RESTORE -> "restore"
        RelationOperation.FORCE_DELETE -> "forceDelete"
        RelationOperation.REORDER -> "reorder"
        RelationOperation.BULK -> "deleteAny"
        RelationOperation.ASSOCIATE -> "associate"
        RelationOperation.DISSOCIATE -> "dissociate"
        RelationOperation.ATTACH -> "attach"
        RelationOperation.DETACH -> "detach"
    }

    /** The related model a policy sees: the record itself, or the row's model loaded once per request. */
    private fun rowOf(model: Class<*>, record: Any?): Any? = when (record) {
        is Cadence<*> -> record
        is RowRecord -> FireflyAuth.remember("${model.name}:${record.key()}") {
            try {
                @Suppress("UNCHECKED_CAST")
                (RecordStores.open(model) as RecordStore<Any?, Any?>).find(record.key())
            } catch (_: RuntimeException) {
                null
            }
        }
        else -> null
    }
}
