package dev.kona.broadcasting

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process websocket-style broadcaster: keeps channel → [Connection] listeners and fans out
 * payloads locally (no network socket). Enough for tests, Java API, and a future Netty upgrade.
 */
class WebsocketBroadcaster : Broadcaster {
    fun interface Connection {
        fun onBroadcast(channels: List<String>, event: String, payload: Map<String, Any?>)
    }

    data class Entry(val channels: List<String>, val event: String, val payload: Map<String, Any?>)

    private val log = LoggerFactory.getLogger(WebsocketBroadcaster::class.java)
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<Connection>>()
    private val entries = CopyOnWriteArrayList<Entry>()

    /** Registers [connection] for [channel]; returns a handle that unsubscribes. */
    fun subscribe(channel: String, connection: Connection): AutoCloseable {
        listeners.getOrPut(channel) { CopyOnWriteArrayList() }.add(connection)
        return AutoCloseable { unsubscribe(channel, connection) }
    }

    fun unsubscribe(channel: String, connection: Connection) {
        val bucket = listeners[channel] ?: return
        bucket.remove(connection)
        if (bucket.isEmpty()) {
            listeners.remove(channel, bucket)
        }
    }

    fun listenerCount(channel: String): Int = listeners[channel]?.size ?: 0

    fun messages(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
    }

    override fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>) {
        val names = channels.map { it.name }
        entries += Entry(names, event, payload)
        log.info("websocket broadcast event={} channels={}", event, names)
        val delivered = linkedSetOf<Connection>()
        for (name in names) {
            val bucket = listeners[name] ?: continue
            for (connection in bucket) {
                if (delivered.add(connection)) {
                    connection.onBroadcast(names, event, payload)
                }
            }
        }
    }
}
