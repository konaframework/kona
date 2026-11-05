package org.konaframework.http

import java.io.IOException
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Deferred body writer for [Response.stream] / [Response.streamDownload] / [Response.eventStream].
 * Netty flushes chunks as [writeTo] runs; [Response.bodyBytes] materializes into memory for tests.
 */
sealed class StreamedBody {
    abstract fun writeTo(out: OutputStream, context: StreamContext)

    /**
     * Runtime view of the live connection while a stream is writing.
     */
    class StreamContext(
        private val connectedCheck: () -> Boolean = { true },
        val lastEventId: String? = null,
        val heartbeat: Duration? = null,
        val timeout: Duration? = null,
        /** True for live Netty writes; false when materializing [Response.bodyBytes]. */
        val live: Boolean = false,
        private val startedAtNanos: Long = System.nanoTime(),
    ) {
        fun connected(): Boolean {
            if (!connectedCheck()) {
                return false
            }
            val limit = timeout ?: return true
            val elapsed = System.nanoTime() - startedAtNanos
            return elapsed < limit.toNanos()
        }

        fun withTimeout(timeout: Duration?): StreamContext =
            StreamContext(
                connectedCheck = connectedCheck,
                lastEventId = lastEventId,
                heartbeat = heartbeat,
                timeout = timeout,
                live = live,
                startedAtNanos = startedAtNanos,
            )

        fun withHeartbeat(heartbeat: Duration?): StreamContext =
            StreamContext(
                connectedCheck = connectedCheck,
                lastEventId = lastEventId,
                heartbeat = heartbeat,
                timeout = timeout,
                live = live,
                startedAtNanos = startedAtNanos,
            )

        fun withLastEventId(lastEventId: String?): StreamContext =
            StreamContext(
                connectedCheck = connectedCheck,
                lastEventId = lastEventId,
                heartbeat = heartbeat,
                timeout = timeout,
                live = live,
                startedAtNanos = startedAtNanos,
            )

        companion object {
            /** In-process materialization (tests / non-Netty adapters): no heartbeat, no timeout. */
            @JvmStatic
            @JvmOverloads
            fun materialize(lastEventId: String? = null): StreamContext =
                StreamContext(lastEventId = lastEventId, live = false)
        }
    }

    class Callback(
        val writer: Consumer<OutputStream>,
        val timeout: Duration? = null,
    ) : StreamedBody() {
        override fun writeTo(out: OutputStream, context: StreamContext) {
            val live = if (timeout != null) context.withTimeout(timeout) else context
            val gated = ConnectedOutputStream(out, live)
            writer.accept(gated)
            gated.flush()
        }
    }

    class EventStream(
        val callback: Consumer<SseEmitter>,
        val endStreamWith: String? = DEFAULT_END,
        val heartbeat: Duration = DEFAULT_HEARTBEAT,
        val timeout: Duration? = null,
        val lastEventId: String? = null,
    ) : StreamedBody() {
        override fun writeTo(out: OutputStream, context: StreamContext) {
            val effectiveHeartbeat = if (context.live) heartbeat else null
            val live =
                context
                    .withLastEventId(lastEventId ?: context.lastEventId)
                    .withHeartbeat(effectiveHeartbeat)
                    .let { if (timeout != null) it.withTimeout(timeout) else it }
            val emitter = DefaultSseEmitter(out, live)
            try {
                if (context.live) {
                    emitter.startHeartbeat()
                }
                callback.accept(emitter)
                if (live.connected() && !endStreamWith.isNullOrBlank()) {
                    emitter.emit("update", endStreamWith)
                }
            } finally {
                emitter.stopHeartbeat()
                out.flush()
            }
        }

        companion object {
            val DEFAULT_HEARTBEAT: Duration = Duration.ofSeconds(15)
            const val DEFAULT_END: String = "</stream>"
        }
    }
}

/**
 * Thrown when a client disconnects mid-stream or the stream deadline elapses.
 * Callbacks may catch it; Netty treats it as a clean end of the response.
 */
class StreamAbortedException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

private class ConnectedOutputStream(
    private val delegate: OutputStream,
    private val context: StreamedBody.StreamContext,
) : OutputStream() {
    override fun write(b: Int) {
        ensureConnected()
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) {
            return
        }
        ensureConnected()
        delegate.write(b, off, len)
    }

    override fun flush() {
        ensureConnected()
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }

    private fun ensureConnected() {
        if (!context.connected()) {
            throw StreamAbortedException("Client disconnected or stream timed out")
        }
    }
}

internal class DefaultSseEmitter(
    private val out: OutputStream,
    private val context: StreamedBody.StreamContext,
) : SseEmitter {
    private val lock = Any()
    private val lastWriteNanos = AtomicLong(System.nanoTime())
    private val heartbeatStarted = AtomicBoolean(false)
    private var heartbeatFuture: ScheduledFuture<*>? = null

    override fun lastEventId(): String? = context.lastEventId

    override fun connected(): Boolean = context.connected()

    override fun emit(data: Any?) = emit("update", data, null)

    override fun emit(event: String, data: Any?) = emit(event, data, null)

    override fun emit(event: StreamedEvent) = emit(event.event, event.data, null)

    override fun emit(event: String, data: Any?, id: String?) {
        ensureConnected()
        val payload = encodeData(data)
        synchronized(lock) {
            ensureConnected()
            if (id != null) {
                writeLine("id: $id")
            }
            writeLine("event: $event")
            payload.lineSequence().forEach { line ->
                writeLine("data: $line")
            }
            writeLine("")
            out.flush()
            lastWriteNanos.set(System.nanoTime())
        }
    }

    override fun comment(text: String) {
        ensureConnected()
        synchronized(lock) {
            ensureConnected()
            writeLine(": $text")
            writeLine("")
            out.flush()
            lastWriteNanos.set(System.nanoTime())
        }
    }

    fun startHeartbeat() {
        val interval = context.heartbeat ?: return
        if (interval.isZero || interval.isNegative) {
            return
        }
        if (heartbeatFuture != null) {
            return
        }
        heartbeatStarted.set(true)
        val nanos = interval.toNanos()
        heartbeatFuture =
            HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                {
                    if (!context.connected()) {
                        return@scheduleAtFixedRate
                    }
                    val idle = System.nanoTime() - lastWriteNanos.get()
                    if (idle >= nanos) {
                        runCatching { comment("heartbeat") }
                    }
                },
                nanos,
                nanos,
                TimeUnit.NANOSECONDS,
            )
    }

    fun stopHeartbeat() {
        heartbeatFuture?.cancel(false)
        heartbeatFuture = null
        heartbeatStarted.set(false)
    }

    private fun ensureConnected() {
        if (!context.connected()) {
            throw StreamAbortedException("Client disconnected or stream timed out")
        }
    }

    private fun writeLine(line: String) {
        out.write(line.toByteArray(Charsets.UTF_8))
        out.write('\n'.code)
    }

    private fun encodeData(data: Any?): String =
        when (data) {
            null -> ""
            is String -> data
            is Number, is Boolean -> data.toString()
            else -> JsonSupport.stringify(data)
        }

    companion object {
        private val HEARTBEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "kona-sse-heartbeat").apply { isDaemon = true }
            }
    }
}
