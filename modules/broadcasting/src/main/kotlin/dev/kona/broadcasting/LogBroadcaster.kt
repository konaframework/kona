package dev.kona.broadcasting

import java.util.concurrent.CopyOnWriteArrayList

/** Records broadcasts for tests / local logging. */
class LogBroadcaster : Broadcaster {
    data class Entry(val channels: List<String>, val event: String, val payload: Map<String, Any?>)

    private val entries = CopyOnWriteArrayList<Entry>()

    override fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>) {
        entries += Entry(channels.map { it.name }, event, payload)
    }

    fun messages(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
    }
}
