package org.konaframework.http

import org.konaframework.core.Log
import org.konaframework.support.console.ConsoleIO
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpServerExpectContinueHandler
import io.netty.handler.codec.http.HttpUtil
import io.netty.util.ReferenceCountUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Netty HTTP adapter + Virtual Threads. Thin adapter kept fully unit-testable via [HttpKernel].
 *
 * I/O uses Netty (epoll on Linux when available, otherwise NIO). Each request is handled on a
 * virtual thread so application code may block freely.
 */
class KonaHttpServer @JvmOverloads constructor(
    kernel: HttpKernel,
    private val host: String = "127.0.0.1",
    private val port: Int = 4000,
    private val maxContentLength: Int = DEFAULT_MAX_CONTENT_LENGTH,
    private val transportPreference: String? = System.getProperty("kona.http.transport"),
    private val console: ConsoleIO = ConsoleIO(),
) {
    private val kernelRef = AtomicReference(kernel)
    private val running = AtomicBoolean(false)
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var channel: Channel? = null
    private var requestExecutor: ExecutorService? = null

    /** Current kernel; [replaceKernel] swaps it without restarting Netty. */
    fun kernel(): HttpKernel = kernelRef.get()

    /**
     * Soft-reload hook: keep event loops and keep-alive connections, but dispatch new
     * requests through [next]. Existing in-flight work still finishes on the previous kernel.
     */
    fun replaceKernel(next: HttpKernel) {
        kernelRef.set(next)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val kind = NettyTransport.resolve(transportPreference)
        val boss = NettyTransport.bossGroup(kind)
        val workers = NettyTransport.workerGroup(kind)
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(boss, workers)
                .channel(NettyTransport.channelClass(kind))
                .option(ChannelOption.SO_BACKLOG, 8192)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(
                    ChannelOption.WRITE_BUFFER_WATER_MARK,
                    WriteBufferWaterMark(32 * 1024, 64 * 1024),
                )
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            ch.pipeline()
                                .addLast(HttpRequestDecoder())
                                .addLast(HttpResponseEncoder())
                                .addLast(HttpServerExpectContinueHandler())
                                .addLast(HttpObjectAggregator(maxContentLength))
                                .addLast(KonaNettyHandler(kernelRef.get(), executor, kernelRef::get))
                        }
                    },
                )
            val future = bootstrap.bind(host, port).sync()
            channel = future.channel()
            bossGroup = boss
            workerGroup = workers
            requestExecutor = executor
            val url = "http://$host:$port"
            Log.info("Kona server listening on $url")
            console.info("Kona server listening on $url")
        } catch (t: Throwable) {
            running.set(false)
            executor.close()
            boss.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
            workers.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
            throw t
        }
    }

    fun stop(delaySeconds: Int = 0) {
        if (!running.compareAndSet(true, false)) {
            return
        }
        if (delaySeconds > 0) {
            Thread.sleep(delaySeconds * 1000L)
        }
        val ch = channel
        val executor = requestExecutor
        val boss = bossGroup
        val workers = workerGroup
        channel = null
        requestExecutor = null
        bossGroup = null
        workerGroup = null
        ch!!.close().syncUninterruptibly()
        executor!!.close()
        boss!!.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).syncUninterruptibly()
        workers!!.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).syncUninterruptibly()
    }

    fun isRunning(): Boolean = running.get()

    fun port(): Int = port

    companion object {
        const val DEFAULT_MAX_CONTENT_LENGTH: Int = 10 * 1024 * 1024
    }
}

internal class KonaNettyHandler(
    kernel: HttpKernel,
    private val executor: ExecutorService,
    private val resolveKernel: () -> HttpKernel = { kernel },
) : SimpleChannelInboundHandler<FullHttpRequest>(false) {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        if (!msg.decoderResult().isSuccess) {
            ReferenceCountUtil.release(msg)
            val bad =
                NettyHttpBridge.toResponse(
                    Response.text("Bad Request", 400),
                    head = false,
                    keepAlive = false,
                )
            ctx.writeAndFlush(bad).addListener(ChannelFutureListener.CLOSE)
            return
        }
        val keepAlive = HttpUtil.isKeepAlive(msg)
        val head = NettyHttpBridge.isHead(msg)
        executor.execute {
            try {
                val request = NettyHttpBridge.toRequest(msg)
                val response = resolveKernel().handle(request)
                val netty = NettyHttpBridge.toResponse(response, head, keepAlive)
                writeResponse(ctx, netty, keepAlive)
            } catch (t: Throwable) {
                Log.error("Unhandled server error", t)
                writeError(ctx)
            } finally {
                ReferenceCountUtil.release(msg)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.error("HTTP channel error", cause)
        ctx.close()
    }

    companion object {
        internal fun writeResponse(
            ctx: ChannelHandlerContext,
            netty: io.netty.handler.codec.http.FullHttpResponse,
            keepAlive: Boolean,
        ) {
            ctx.channel().eventLoop().execute {
                val future = ctx.writeAndFlush(netty)
                if (!keepAlive) {
                    future.addListener(ChannelFutureListener.CLOSE)
                }
            }
        }

        internal fun writeError(ctx: ChannelHandlerContext) {
            ctx.channel().eventLoop().execute {
                if (!ctx.channel().isActive) {
                    return@execute
                }
                val err =
                    NettyHttpBridge.toResponse(
                        Response.text("Internal Server Error", 500),
                        head = false,
                        keepAlive = false,
                    )
                ctx.writeAndFlush(err).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }
}
