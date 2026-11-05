package org.konaframework.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpResponseEncoder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
// AtomicBoolean used for mid-flush disconnect coverage

class NettyChunkedOutputStreamTest {
    @Test
    fun hooksCoverDisconnectBackpressureAndWriteFailures() {
        val channel = EmbeddedChannel(HttpResponseEncoder())
        val ctx = channel.pipeline().firstContext()

        val inactive =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    active = { false },
                    writable = { true },
                ),
            )
        assertThrows(StreamAbortedException::class.java) { inactive.write(1) }
        inactive.finish()

        val writes = AtomicInteger(0)
        val writable = AtomicBoolean(false)
        val active = AtomicBoolean(true)
        val stream =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    awaitSeconds = 1,
                    active = { active.get() },
                    writable = { writable.get() },
                    sleep = {
                        writes.incrementAndGet()
                        if (writes.get() >= 2) {
                            writable.set(true)
                        }
                    },
                    execute = { it.run() },
                    writeAndFlush = { _, cb -> cb(true, null) },
                ),
            )
        stream.write(byteArrayOf(1, 2, 3))
        stream.write(ByteArray(0))
        stream.flush()
        stream.finish()
        stream.close()
        assertThrows(StreamAbortedException::class.java) { stream.write(9) }

        val timed =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    awaitSeconds = 0,
                    active = { true },
                    writable = { false },
                    sleep = {},
                ),
            )
        assertThrows(StreamAbortedException::class.java) { timed.write(1) }

        val disconnectWait =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    awaitSeconds = 1,
                    active = {
                        if (writes.get() > 10) false else true.also { writable.set(false); writes.incrementAndGet() }
                    },
                    writable = { false },
                    sleep = {},
                ),
            )
        assertThrows(StreamAbortedException::class.java) { disconnectWait.write(1) }

        val failWrite =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    execute = { it.run() },
                    writeAndFlush = { _, cb -> cb(false, IllegalStateException("boom")) },
                ),
            )
        assertThrows(StreamAbortedException::class.java) { failWrite.write(1) }

        val abortWrite =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    execute = { it.run() },
                    writeAndFlush = { _, cb -> cb(false, StreamAbortedException("gone")) },
                ),
            )
        assertThrows(StreamAbortedException::class.java) { abortWrite.write(1) }

        val nullCause =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    execute = { it.run() },
                    writeAndFlush = { _, cb -> cb(false, null) },
                ),
            )
        assertThrows(StreamAbortedException::class.java) { nullCause.write(1) }

        val inactiveFinish =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(active = { false }),
            )
        inactiveFinish.finish()

        val disconnectMidFlush =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    active = { true },
                    writable = { true },
                    execute = { task ->
                        // Simulate channel going away on the event-loop task.
                        val failing =
                            NettyChunkedOutputStream.Hooks(
                                active = { false },
                                execute = { it.run() },
                                writeAndFlush = { _, cb -> cb(true, null) },
                            )
                        // Directly exercise the inactive branch via a dedicated stream:
                        NettyChunkedOutputStream(ctx, failing).finish()
                        task.run()
                    },
                    writeAndFlush = { _, cb -> cb(true, null) },
                ),
            )
        disconnectMidFlush.write(1)

        // Inactive inside flushMessage execute:
        val activeFlag = AtomicBoolean(true)
        val mid =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    active = { activeFlag.get() },
                    writable = { true },
                    execute = { task ->
                        activeFlag.set(false)
                        task.run()
                    },
                    writeAndFlush = { _, cb -> cb(true, null) },
                ),
            )
        assertThrows(StreamAbortedException::class.java) { mid.write(1) }

        val slowAwait =
            NettyChunkedOutputStream(
                ctx,
                NettyChunkedOutputStream.Hooks(
                    awaitSeconds = 0,
                    active = { true },
                    writable = { true },
                    execute = { /* never runs callback */ },
                    writeAndFlush = { _, _ -> },
                ),
            )
        assertThrows(StreamAbortedException::class.java) { slowAwait.write(1) }

        channel.finishAndReleaseAll()
    }

    @Test
    fun headerHooksCoverSuccessFailureCookiesAndHead() {
        val channel = EmbeddedChannel(HttpResponseEncoder())
        val ctx = channel.pipeline().firstContext()
        val response =
            Response.stream({ out -> out.write(1) }, "text/plain")
                .cookie(Cookie("a", "b"))
                .header("Transfer-Encoding", "identity")
                .header("Content-Length", "99")

        NettyChunkedOutputStream.writeHeaders(
            ctx,
            response,
            head = false,
            keepAlive = true,
            hooks = NettyChunkedOutputStream.HeaderHooks(),
        )
        NettyChunkedOutputStream.writeHeaders(
            ctx,
            response,
            head = true,
            keepAlive = false,
            hooks = NettyChunkedOutputStream.HeaderHooks(),
        )
        assertThrows(StreamAbortedException::class.java) {
            NettyChunkedOutputStream.writeHeaders(
                ctx,
                response,
                head = false,
                keepAlive = true,
                hooks =
                    NettyChunkedOutputStream.HeaderHooks(
                        writeAndFlush = { _, cb -> cb(false, null) },
                    ),
            )
        }
        assertThrows(StreamAbortedException::class.java) {
            NettyChunkedOutputStream.writeHeaders(
                ctx,
                response,
                head = false,
                keepAlive = true,
                hooks =
                    NettyChunkedOutputStream.HeaderHooks(
                        writeAndFlush = { _, cb -> cb(false, IllegalStateException("hdr")) },
                    ),
            )
        }
        assertThrows(StreamAbortedException::class.java) {
            NettyChunkedOutputStream.writeHeaders(
                ctx,
                response,
                head = false,
                keepAlive = true,
                hooks =
                    NettyChunkedOutputStream.HeaderHooks(
                        awaitSeconds = 0,
                        execute = {},
                        writeAndFlush = { _, _ -> },
                    ),
            )
        }

        var closed = false
        NettyChunkedOutputStream.writeEmptyBody(
            ctx,
            keepAlive = false,
            hooks =
                NettyChunkedOutputStream.HeaderHooks(
                    close = { closed = true },
                ),
        )
        assertTrue(closed)
        NettyChunkedOutputStream.writeEmptyBody(
            ctx,
            keepAlive = true,
            hooks = NettyChunkedOutputStream.HeaderHooks(),
        )
        channel.finishAndReleaseAll()
    }

    @Test
    fun streamContextHelpersAndBodyBytesAbort() {
        val base = StreamedBody.StreamContext.materialize()
        assertEquals(null, base.lastEventId)
        val withId = StreamedBody.StreamContext.materialize("5")
        assertEquals("5", withId.lastEventId)
        val timed =
            StreamedBody.StreamContext(
                timeout = java.time.Duration.ofMillis(1),
                live = true,
            )
        Thread.sleep(5)
        assertTrue(!timed.connected())

        val response =
            Response.stream(
                { out ->
                    Thread.sleep(20)
                    out.write(1)
                },
                timeout = java.time.Duration.ofMillis(1),
            )
        // Materialize swallows StreamAbortedException and returns whatever was written.
        assertArrayEquals(ByteArray(0), response.bodyBytes())

        val boom =
            Response.stream({ _ -> throw IllegalStateException("nope") })
        assertThrows(IllegalStateException::class.java) { boom.bodyBytes() }

        val buffer = ByteArrayOutputStream()
        Response.stream({ out -> out.write("z".toByteArray()) })
            .writeStreamedBody(buffer)
        assertEquals("z", buffer.toString())
    }
}
