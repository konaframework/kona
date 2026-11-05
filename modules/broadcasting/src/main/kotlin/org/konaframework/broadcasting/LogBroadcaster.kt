package org.konaframework.broadcasting

import java.util.concurrent.CopyOnWriteArrayList

/** Records broadcasts for tests / local logging. */
class LogBroadcaster : Broadcaster {
    data class Entry(
        val channels: List<String>,
        val event: String,
        val payload: Map<String, Any?>,
        val excludeSocketId: String? = null,
    )

    private val entries = CopyOnWriteArrayList<Entry>()

    override fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>) {
        broadcast(channels, event, payload, null)
    }

    override fun broadcast(
        channels: List<Channel>,
        event: String,
        payload: Map<String, Any?>,
        excludeSocketId: String?,
    ) {
        entries += Entry(channels.map { it.name }, event, payload, excludeSocketId)
    }

    fun messages(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
    }
}
