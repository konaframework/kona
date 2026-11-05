package org.konaframework.http

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Push-based [OutputStream] that writes Netty `HttpContent` chunks, waiting when the channel
 * write buffer is above the high water mark (backpressure).
 */
internal class NettyChunkedOutputStream(
    private val ctx: ChannelHandlerContext,
    private val hooks: Hooks = Hooks(),
) : OutputStream() {
    @Volatile
    private var closed = false

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) {
            return
        }
        ensureOpen()
        awaitWritable()
        ensureOpen()
        val content = DefaultHttpContent(Unpooled.copiedBuffer(b, off, len))
        flushMessage(content)
    }

    override fun flush() {
        ensureOpen()
    }

    override fun close() {
        closed = true
    }

    fun finish() {
        if (!hooks.active()) {
            close()
            return
        }
        flushMessage(LastHttpContent.EMPTY_LAST_CONTENT)
        close()
    }

    private fun flushMessage(msg: Any) {
        val error = AtomicReference<Throwable?>()
        val done = CountDownLatch(1)
        hooks.execute {
            if (!hooks.active()) {
                error.set(StreamAbortedException("Client disconnected"))
                done.countDown()
                return@execute
            }
            hooks.writeAndFlush(msg) { success, cause ->
                if (!success) {
                    error.set(cause ?: StreamAbortedException("Chunk write failed"))
                }
                done.countDown()
            }
        }
        if (!done.await(hooks.awaitSeconds, TimeUnit.SECONDS)) {
            throw StreamAbortedException("Timed out waiting for chunk write")
        }
        error.get()?.let { cause ->
            if (cause is StreamAbortedException) {
                throw cause
            }
            throw StreamAbortedException("Chunk write failed", cause)
        }
    }

    private fun awaitWritable() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(hooks.awaitSeconds)
        while (hooks.active() && !hooks.writable()) {
            if (System.nanoTime() >= deadline) {
                throw StreamAbortedException("Timed out waiting for channel writability")
            }
            hooks.sleep(1)
        }
        if (!hooks.active()) {
            throw StreamAbortedException("Client disconnected")
        }
    }

    private fun ensureOpen() {
        if (closed || !hooks.active()) {
            throw StreamAbortedException("Client disconnected")
        }
    }

    /** Test seams for disconnect / backpressure / write failures. */
    class Hooks(
        val awaitSeconds: Long = 60,
        val active: () -> Boolean = { true },
        val writable: () -> Boolean = { true },
        val sleep: (Long) -> Unit = { Thread.sleep(it) },
        val execute: (Runnable) -> Unit = { it.run() },
        val writeAndFlush: (Any, (Boolean, Throwable?) -> Unit) -> Unit = { _, cb -> cb(true, null) },
    ) {
        companion object {
            fun forContext(ctx: ChannelHandlerContext): Hooks =
                Hooks(
                    active = { ctx.channel().isActive },
                    writable = { ctx.channel().isWritable },
                    execute = { task -> ctx.channel().eventLoop().execute(task) },
                    writeAndFlush = { msg, cb ->
                        ctx.writeAndFlush(msg).addListener { future ->
                            cb(future.isSuccess, future.cause())
                        }
                    },
                )
        }
    }

    companion object {
        fun writeHeaders(
            ctx: ChannelHandlerContext,
            response: Response,
            head: Boolean,
            keepAlive: Boolean,
            hooks: HeaderHooks = HeaderHooks.forContext(ctx),
        ) {
            val status = NettyHttpBridge.statusOf(response.status)
            val netty = DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
            response.headers.forEach { (k, v) ->
                if (!k.equals("Transfer-Encoding", ignoreCase = true) &&
                    !k.equals("Content-Length", ignoreCase = true)
                ) {
                    netty.headers().set(k, v)
                }
            }
            response.cookies.forEach { cookie ->
                netty.headers().add(HttpHeaderNames.SET_COOKIE, cookie.toSetCookieHeader())
            }
            if (head) {
                HttpUtil.setContentLength(netty, 0)
            } else {
                HttpUtil.setTransferEncodingChunked(netty, true)
            }
            HttpUtil.setKeepAlive(netty, keepAlive)
            val done = CountDownLatch(1)
            val error = AtomicReference<Throwable?>()
            hooks.execute {
                hooks.writeAndFlush(netty) { success, cause ->
                    if (!success) {
                        error.set(cause ?: StreamAbortedException("Failed to write headers"))
                    }
                    done.countDown()
                }
            }
            if (!done.await(hooks.awaitSeconds, TimeUnit.SECONDS) || error.get() != null) {
                throw StreamAbortedException("Failed to write streamed response headers", error.get())
            }
        }

        fun writeEmptyBody(
            ctx: ChannelHandlerContext,
            keepAlive: Boolean,
            hooks: HeaderHooks = HeaderHooks.forContext(ctx),
        ) {
            val done = CountDownLatch(1)
            hooks.execute {
                hooks.writeAndFlush(DefaultLastHttpContent()) { _, _ ->
                    if (!keepAlive) {
                        hooks.close()
                    }
                    done.countDown()
                }
            }
            done.await(hooks.awaitSeconds, TimeUnit.SECONDS)
        }
    }

    class HeaderHooks(
        val awaitSeconds: Long = 60,
        val execute: (Runnable) -> Unit = { it.run() },
        val writeAndFlush: (Any, (Boolean, Throwable?) -> Unit) -> Unit = { _, cb -> cb(true, null) },
        val close: () -> Unit = {},
    ) {
        companion object {
            fun forContext(ctx: ChannelHandlerContext): HeaderHooks =
                HeaderHooks(
                    execute = { task -> ctx.channel().eventLoop().execute(task) },
                    writeAndFlush = { msg, cb ->
                        ctx.writeAndFlush(msg).addListener { future ->
                            cb(future.isSuccess, future.cause())
                        }
                    },
                    close = { ctx.close() },
                )
        }
    }
}
