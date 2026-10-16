package io.konaframework.broadcasting

/** Driver that delivers a broadcast payload to channels. */
fun interface Broadcaster {
    fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>)
}
