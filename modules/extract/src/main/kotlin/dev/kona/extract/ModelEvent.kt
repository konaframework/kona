package dev.kona.extract

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
    fun handle(model: Extract<*>)
}

/**
 * Global model event dispatcher (Laravel Event shape for Eloquent lifecycle).
 */
object ModelDispatcher {
    private val listeners = linkedMapOf<ModelEvent, MutableList<ModelEventListener>>()

    @JvmStatic
    fun listen(event: ModelEvent, listener: ModelEventListener) {
        listeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    @JvmStatic
    fun listen(event: ModelEvent, listener: (Extract<*>) -> Unit) {
        listen(event, ModelEventListener { listener(it) })
    }

    @JvmStatic
    fun dispatch(event: ModelEvent, model: Extract<*>) {
        val bag = listeners[event] ?: return
        for (listener in bag.toList()) {
            listener.handle(model)
        }
    }

    @JvmStatic
    fun flush() {
        listeners.clear()
    }
}
