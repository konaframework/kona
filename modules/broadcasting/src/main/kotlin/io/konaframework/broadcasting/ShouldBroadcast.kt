package io.konaframework.broadcasting

/**
 * Marker for events that should be broadcast when dispatched via [Broadcast].
 */
interface ShouldBroadcast {
    fun broadcastOn(): List<Channel>
    fun broadcastAs(): String = this::class.java.simpleName
    fun broadcastWith(): Map<String, Any?> = emptyMap()
}
