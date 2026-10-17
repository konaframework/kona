package org.konaframework.queue

/** Thread-local context set by [Worker] so chain continuations can re-dispatch. */
object WorkerContext {
    private val bus = ThreadLocal<Bus?>()
    private val connection = ThreadLocal<String?>()
    private val queue = ThreadLocal<String?>()

    fun bus(): Bus? = bus.get()

    fun connection(): String? = connection.get()

    fun queue(): String? = queue.get()

    fun bind(bus: Bus?, connection: String?, queue: String?) {
        this.bus.set(bus)
        this.connection.set(connection)
        this.queue.set(queue)
    }

    fun clear() {
        bus.remove()
        connection.remove()
        queue.remove()
    }
}
