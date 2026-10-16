package dev.kona.cadence

/**
 * Lifecycle events fired around persistence (Laravel Eloquent model events).
 */
enum class ModelEvent {
    RETRIEVED,
    CREATING,
    CREATED,
    UPDATING,
    UPDATED,
    SAVING,
    SAVED,
    DELETING,
    DELETED,
    FORCE_DELETING,
    FORCE_DELETED,
    RESTORING,
    RESTORED,
    TRASHED,
}

fun interface ModelEventListener {
    fun handle(model: Cadence<*>)
}

/**
 * Global model event dispatcher (Laravel Event shape for Eloquent lifecycle).
 */
object ModelDispatcher {
    private val listeners = linkedMapOf<ModelEvent, MutableList<ModelEventListener>>()
    private val muteDepth = ThreadLocal.withInitial { 0 }

    @JvmStatic
    fun listen(event: ModelEvent, listener: ModelEventListener) {
        listeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    @JvmStatic
    fun listen(event: ModelEvent, listener: (Cadence<*>) -> Unit) {
        listen(event, ModelEventListener { listener(it) })
    }

    @JvmStatic
    fun dispatch(event: ModelEvent, model: Cadence<*>) {
        if (muteDepth.get() > 0) {
            return
        }
        val bag = listeners[event] ?: return
        for (listener in bag.toList()) {
            listener.handle(model)
        }
    }

    /** Runs [action] with event dispatch muted (nestable). */
    @JvmStatic
    fun withoutEvents(action: Runnable) {
        withoutEvents<Unit> { action.run() }
    }

    /** Runs [block] with event dispatch muted (nestable); returns [block]'s result. */
    @JvmStatic
    fun <R> withoutEvents(block: () -> R): R {
        muteDepth.set(muteDepth.get() + 1)
        try {
            return block()
        } finally {
            muteDepth.set(muteDepth.get() - 1)
        }
    }

    @JvmStatic
    fun flush() {
        listeners.clear()
        muteDepth.remove()
    }
}
