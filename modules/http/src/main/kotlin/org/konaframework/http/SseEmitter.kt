package org.konaframework.http

/**
 * Writes Server-Sent Events to an open stream (Laravel `eventStream` callback surface).
 *
 * [lastEventId] is the client's `Last-Event-ID` header when the response was built from a
 * [Request], otherwise null. [connected] becomes false when the client disconnects or the
 * stream times out — stop emitting and return from the callback.
 */
interface SseEmitter {
    /** Value of `Last-Event-ID` for reconnect replay, or null. */
    fun lastEventId(): String?

    /** False once the client has gone away or the stream deadline elapsed. */
    fun connected(): Boolean

    /** Emit an `update` event with [data] (JSON-encoded when not a string/number). */
    fun emit(data: Any?)

    /** Emit a named event with [data]. */
    fun emit(event: String, data: Any?)

    /** Emit [event]'s name and data. */
    fun emit(event: StreamedEvent)

    /** Emit a named event with optional SSE `id:` field (drives the next `Last-Event-ID`). */
    fun emit(event: String, data: Any?, id: String?)

    /** Write an SSE comment line (default heartbeat). Ignored by EventSource clients. */
    fun comment(text: String)

    /** Write the default `: heartbeat` comment. */
    fun comment() = comment("heartbeat")
}
