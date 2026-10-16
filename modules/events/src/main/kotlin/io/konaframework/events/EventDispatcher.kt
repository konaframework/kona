package io.konaframework.events

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Synchronous event bus (Laravel EventDispatcher shape).
 *
 * Listeners are invoked in registration order. Java callers use [EventListener] SAMs;
 * Kotlin can pass lambdas via [listen].
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
        val type = event.javaClass
        val handlers = listeners[type] ?: return
        for (handler in handlers) {
            @Suppress("UNCHECKED_CAST")
            (handler as EventListener<Any>).handle(event)
        }
    }

    fun forget(type: Class<*>) {
        listeners.remove(type)
    }

    fun flush() {
        listeners.clear()
    }

    fun hasListeners(type: Class<*>): Boolean = !listeners[type].isNullOrEmpty()

    fun listenerCount(type: Class<*>): Int = listeners[type]?.size ?: 0
}
