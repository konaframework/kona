package org.konaframework.broadcasting

/** Delivers an event payload to one or more [Channel]s. */
fun interface Broadcaster {
    fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>)

    /** Optional socket-id exclusion (`toOthers`). Default ignores the hint. */
    fun broadcast(
        channels: List<Channel>,
        event: String,
        payload: Map<String, Any?>,
        excludeSocketId: String?,
    ) {
        broadcast(channels, event, payload)
    }
}
