package org.konaframework.cadence

/**
 * Class-style lifecycle hooks for one model type (Eloquent `Observer`).
 *
 * Register with [Cadence.observe] / [CadenceCompanion.observe]. Override only the
 * events you care about; empty defaults are no-ops.
 */
open class ModelObserver<T : Cadence<T>> {
    open fun retrieved(model: T) {}

    open fun creating(model: T) {}

    open fun created(model: T) {}

    open fun updating(model: T) {}

    open fun updated(model: T) {}

    open fun saving(model: T) {}

    open fun saved(model: T) {}

    open fun deleting(model: T) {}

    open fun deleted(model: T) {}

    open fun forceDeleting(model: T) {}

    open fun forceDeleted(model: T) {}

    open fun restoring(model: T) {}

    open fun restored(model: T) {}

    open fun trashed(model: T) {}
}

/** Wires an [ModelObserver] into [ModelDispatcher] for models of [modelClass]. */
internal fun <T : Cadence<T>> registerObserver(modelClass: Class<T>, observer: ModelObserver<T>) {
    fun bind(event: ModelEvent, call: (T) -> Unit) {
        ModelDispatcher.listen(event) { model ->
            if (modelClass.isInstance(model)) {
                @Suppress("UNCHECKED_CAST")
                call(model as T)
            }
        }
    }
    bind(ModelEvent.RETRIEVED, observer::retrieved)
    bind(ModelEvent.CREATING, observer::creating)
    bind(ModelEvent.CREATED, observer::created)
    bind(ModelEvent.UPDATING, observer::updating)
    bind(ModelEvent.UPDATED, observer::updated)
    bind(ModelEvent.SAVING, observer::saving)
    bind(ModelEvent.SAVED, observer::saved)
    bind(ModelEvent.DELETING, observer::deleting)
    bind(ModelEvent.DELETED, observer::deleted)
    bind(ModelEvent.FORCE_DELETING, observer::forceDeleting)
    bind(ModelEvent.FORCE_DELETED, observer::forceDeleted)
    bind(ModelEvent.RESTORING, observer::restoring)
    bind(ModelEvent.RESTORED, observer::restored)
    bind(ModelEvent.TRASHED, observer::trashed)
}
