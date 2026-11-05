package org.konaframework.broadcasting

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process websocket-style broadcaster: keeps channel → [Connection] listeners and fans out
 * payloads locally. Also the fan-out surface used by the Netty Reverb server for embedded mode.
 */
class WebsocketBroadcaster : Broadcaster {
    fun interface Connection {
        fun onBroadcast(channels: List<String>, event: String, payload: Map<String, Any?>)
    }

    data class Entry(
        val channels: List<String>,
        val event: String,
        val payload: Map<String, Any?>,
        val excludeSocketId: String? = null,
    )

    private val log = LoggerFactory.getLogger(WebsocketBroadcaster::class.java)
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<Connection>>()
    private val socketIds = ConcurrentHashMap<Connection, String>()
    private val entries = CopyOnWriteArrayList<Entry>()

    /** Registers [connection] for [channel]; returns a handle that unsubscribes. */
    @JvmOverloads
    fun subscribe(channel: String, connection: Connection, socketId: String? = null): AutoCloseable {
        listeners.getOrPut(channel) { CopyOnWriteArrayList() }.add(connection)
        if (socketId != null) {
            socketIds[connection] = socketId
        }
        return AutoCloseable { unsubscribe(channel, connection) }
    }

    fun unsubscribe(channel: String, connection: Connection) {
        val bucket = listeners[channel] ?: return
        bucket.remove(connection)
        if (bucket.isEmpty()) {
            listeners.remove(channel, bucket)
        }
        if (listeners.values.none { it.contains(connection) }) {
            socketIds.remove(connection)
        }
    }

    fun listenerCount(channel: String): Int = listeners[channel]?.size ?: 0

    fun messages(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
    }

    override fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>) {
        broadcast(channels, event, payload, null)
    }

    override fun broadcast(
        channels: List<Channel>,
        event: String,
        payload: Map<String, Any?>,
        excludeSocketId: String?,
    ) {
        val names = channels.map { it.name }
        // In-process listeners subscribe to the logical name; Reverb uses [Channel.pusherName].
        entries += Entry(names, event, payload, excludeSocketId)
        log.info("websocket broadcast event={} channels={}", event, names)
        val delivered = linkedSetOf<Connection>()
        for (name in names) {
            val bucket = listeners[name] ?: continue
            for (connection in bucket) {
                if (excludeSocketId != null && socketIds[connection] == excludeSocketId) {
                    continue
                }
                if (delivered.add(connection)) {
                    connection.onBroadcast(names, event, payload)
                }
            }
        }
    }
}
