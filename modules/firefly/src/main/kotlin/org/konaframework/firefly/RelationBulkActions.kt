package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.BulkAction
import org.konaframework.firefly.tables.BulkCounts
import java.util.function.Function
import java.util.function.Predicate

/**
 * A bulk action that runs a relation operation on the selected related rows of the owner
 * (relation manager tables only). Hidden when the manager does not offer [operation]; read-only
 * pages get no bulk actions. Each record is re-checked against the manager's `can…` predicates.
 */
open class RelationBulkAction internal constructor(name: String, val operation: RelationOperation) : BulkAction(name) {
    init {
        val verb = VERBS.getValue(operation)
        label("$verb selected")
        color(if (operation == RelationOperation.RESTORE) "gray" else "danger")
        requiresConfirmation()
        modalHeading("$verb selected")
        modalDescription(DESCRIPTIONS.getValue(operation))
        modalSubmitActionLabel(verb)
        successNotificationTitle(PAST.getValue(operation))
        failureNotificationTitle { counts: BulkCounts ->
            if (counts.successCount > 0) {
                "${counts.successCount} of ${counts.totalCount} ${PAST.getValue(operation).lowercase()}"
            } else {
                "Nothing was ${PAST.getValue(operation).lowercase()}"
            }
        }
        deselectRecordsAfterCompletion()
        fetchSelectedRecords(false)
        hidden(Predicate { _ -> !offered() })
        action { context ->
            val runtime = RelationScope.current() ?: throw FireflyException("Bulk action [$name] runs only on a relation manager table")
            runtime.manager.runBulk(runtime, operation, context.keys()) { failure ->
                context.reportBulkProcessingFailure(failure, Function { counts -> failureMessage(failure, counts) })
            }
        }
    }

    private fun offered(): Boolean {
        val runtime = RelationScope.current() ?: return true
        return runtime.manager.supports(operation, runtime)
    }

    companion object {
        /** Delete the selected related rows (pivot links first for many-to-many). */
        @JvmStatic
        @JvmOverloads
        fun delete(name: String = "delete"): RelationBulkAction = RelationBulkAction(name, RelationOperation.DELETE)

        private val VERBS = mapOf(
            RelationOperation.DELETE to "Delete",
            RelationOperation.DISSOCIATE to "Dissociate",
            RelationOperation.DETACH to "Detach",
            RelationOperation.RESTORE to "Restore",
            RelationOperation.FORCE_DELETE to "Force delete",
        )

        private val PAST = mapOf(
            RelationOperation.DELETE to "Deleted",
            RelationOperation.DISSOCIATE to "Dissociated",
            RelationOperation.DETACH to "Detached",
            RelationOperation.RESTORE to "Restored",
            RelationOperation.FORCE_DELETE to "Deleted",
        )

        private val DESCRIPTIONS = mapOf(
            RelationOperation.DELETE to "Are you sure you would like to do this?",
            RelationOperation.DISSOCIATE to "The selected records are unlinked from this owner but kept.",
            RelationOperation.DETACH to "The selected records are unlinked from this owner but kept.",
            RelationOperation.RESTORE to "Restore the selected records?",
            RelationOperation.FORCE_DELETE to "This permanently deletes the selected records. Are you sure?",
        )

        private fun failureMessage(failure: String, counts: BulkCounts): String {
            val one = counts.failureCount == 1
            return when (failure) {
                "missing" -> if (one) "One selected record is no longer related." else "${counts.failureCount} selected records are no longer related."
                "denied" -> if (one) "One selected record was not authorized." else "${counts.failureCount} selected records were not authorized."
                else -> if (one) "One selected record is not deleted." else "${counts.failureCount} selected records are not deleted."
            }
        }
    }
}

/** Unlink the selected rows from the owner (has-many / morph-many). */
class DissociateBulkAction private constructor(name: String) : RelationBulkAction(name, RelationOperation.DISSOCIATE) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun make(name: String = "dissociate"): DissociateBulkAction = DissociateBulkAction(name)
    }
}

/** Remove the selected rows' pivot links to the owner (belongs-to-many / morph-to-many). */
class DetachBulkAction private constructor(name: String) : RelationBulkAction(name, RelationOperation.DETACH) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun make(name: String = "detach"): DetachBulkAction = DetachBulkAction(name)
    }
}

/** Restore the selected soft-deleted rows. */
class RestoreBulkAction private constructor(name: String) : RelationBulkAction(name, RelationOperation.RESTORE) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun make(name: String = "restore"): RestoreBulkAction = RestoreBulkAction(name)
    }
}

/** Permanently delete the selected soft-deleted rows. */
class ForceDeleteBulkAction private constructor(name: String) : RelationBulkAction(name, RelationOperation.FORCE_DELETE) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun make(name: String = "forceDelete"): ForceDeleteBulkAction = ForceDeleteBulkAction(name)
    }
}
