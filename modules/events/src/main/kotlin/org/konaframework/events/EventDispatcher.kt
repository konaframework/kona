package org.konaframework.events

import org.konaframework.cadence.DB
import org.konaframework.cadence.Database
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Synchronous event bus (Laravel EventDispatcher shape).
 *
 * Listeners are invoked in registration order. Java callers use [EventListener] SAMs;
 * Kotlin can pass lambdas via [listen]. Events that implement [ShouldDispatchAfterCommit]
 * are deferred until after the outermost Cadence transaction commits.
 */
class EventDispatcher {
    private val listeners = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<EventListener<*>>>()

    fun <T : Any> listen(type: Class<T>, listener: EventListener<T>): EventDispatcher {
        @Suppress("UNCHECKED_CAST")
        listeners.getOrPut(type) { CopyOnWriteArrayList() }
            .add(listener as EventListener<*>)
        return this
    }

    inline fun <reified T : Any> listen(listener: EventListener<T>): EventDispatcher =
        listen(T::class.java, listener)

    fun dispatch(event: Any) {
        if (shouldDeferUntilCommit(event)) {
            DB.afterCommit(Runnable { dispatchNow(event) })
            return
        }
        dispatchNow(event)
    }

    fun forget(type: Class<*>) {
        listeners.remove(type)
    }

    fun flush() {
        listeners.clear()
    }

    fun hasListeners(type: Class<*>): Boolean = !listeners[type].isNullOrEmpty()

    fun listenerCount(type: Class<*>): Int = listeners[type]?.size ?: 0

    private fun shouldDeferUntilCommit(event: Any): Boolean {
        if (event !is ShouldDispatchAfterCommit) {
            return false
        }
        if (!Database.hasGlobal()) {
            return false
        }
        return DB.transactionLevel() > 0
    }

    private fun dispatchNow(event: Any) {
        val type = event.javaClass
        val handlers = listeners[type] ?: return
        for (handler in handlers) {
            @Suppress("UNCHECKED_CAST")
            (handler as EventListener<Any>).handle(event)
        }
    }
}
