package org.konaframework.broadcasting

/** No-op broadcaster. */
class NullBroadcaster : Broadcaster {
    override fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>) {
        // intentionally empty
    }
}
