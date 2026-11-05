package org.konaframework.broadcasting.reverb

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Netty websocket server speaking the Pusher protocol (Laravel Reverb class).
 *
 * Clients connect to `ws://host:port/app/{key}` (optional query `protocol=7`).
 */
class ReverbServer(
    private val hub: ReverbHub,
    private val appKey: String,
    private val host: String = "127.0.0.1",
    private val port: Int = 8080,
    private val transportPreference: String? = System.getProperty("kona.reverb.transport"),
) {
    private val log = LoggerFactory.getLogger(ReverbServer::class.java)
    private val running = AtomicBoolean(false)
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var channel: Channel? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val kind = ReverbTransport.resolve(transportPreference)
        val boss = ReverbTransport.bossGroup(kind)
        val workers = ReverbTransport.workerGroup(kind)
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(boss, workers)
                .channel(ReverbTransport.channelClass(kind))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            ch.pipeline()
                                .addLast(HttpServerCodec())
                                .addLast(HttpObjectAggregator(65_536))
                                .addLast(ReverbHandshakeHandler(hub, appKey))
                        }
                    },
                )
            val future = bootstrap.bind(host, port).sync()
            channel = future.channel()
            bossGroup = boss
            workerGroup = workers
            log.info("Reverb listening on ws://{}:{}/app/{}", host, port, appKey)
        } catch (t: Throwable) {
            running.set(false)
            boss.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
            workers.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
            throw t
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        channel?.close()?.syncUninterruptibly()
        channel = null
        bossGroup?.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS)?.syncUninterruptibly()
        workerGroup?.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS)?.syncUninterruptibly()
        bossGroup = null
        workerGroup = null
    }

    fun isRunning(): Boolean = running.get()

    fun port(): Int = port

    fun hub(): ReverbHub = hub
}

internal class ReverbHandshakeHandler(
    private val hub: ReverbHub,
    private val appKey: String,
) : SimpleChannelInboundHandler<Any>() {
    private var handshaker: WebSocketServerHandshaker? = null
    private var client: HubClient? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is FullHttpRequest -> handleHttp(ctx, msg)
            is WebSocketFrame -> handleFrame(ctx, msg)
            else -> {
                // ignore
            }
        }
    }

    private fun handleHttp(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        if (!req.decoderResult().isSuccess || req.method() != HttpMethod.GET) {
            sendHttp(ctx, HttpResponseStatus.BAD_REQUEST)
            return
        }
        val decoder = QueryStringDecoder(req.uri())
        val path = decoder.path()
        if (!path.startsWith("/app/")) {
            sendHttp(ctx, HttpResponseStatus.NOT_FOUND)
            return
        }
        val key = path.removePrefix("/app/").substringBefore('/')
        if (key != appKey) {
            sendHttp(ctx, HttpResponseStatus.FORBIDDEN)
            return
        }
        val wsFactory = WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true)
        handshaker = wsFactory.newHandshaker(req)
        val hs = handshaker
        if (hs == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
            return
        }
        hs.handshake(ctx.channel(), req).addListener(
            ChannelFutureListener { future ->
                if (!future.isSuccess) {
                    ctx.close()
                    return@ChannelFutureListener
                }
                val socketId = hub.nextSocketId()
                val hubClient = HubClient(socketId, ctx)
                client = hubClient
                hub.register(hubClient)
                hubClient.send(PusherProtocol.connectionEstablished(socketId))
            },
        )
    }

    private fun handleFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame) {
        when (frame) {
            is CloseWebSocketFrame -> {
                handshaker?.close(ctx.channel(), frame.retain())
            }
            is PingWebSocketFrame -> {
                ctx.writeAndFlush(PongWebSocketFrame(frame.content().retain()))
            }
            is TextWebSocketFrame -> {
                val socketId = client?.socketId() ?: return
                val reply = hub.handleClientMessage(socketId, frame.text())
                if (reply != null) {
                    ctx.writeAndFlush(TextWebSocketFrame(reply))
                }
            }
            else -> {
                // ignore binary / continuation
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        client?.let { hub.unregister(it.socketId()) }
        client = null
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }

    private fun sendHttp(ctx: ChannelHandlerContext, status: HttpResponseStatus) {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun getWebSocketLocation(req: FullHttpRequest): String {
        val location = req.headers().get(HttpHeaderNames.HOST) ?: "127.0.0.1"
        return "ws://$location${req.uri()}"
    }
}

private class HubClient(
    private val id: String,
    private val ctx: ChannelHandlerContext,
) : ReverbHub.Client {
    override fun send(message: String) {
        if (ctx.channel().isActive) {
            ctx.writeAndFlush(TextWebSocketFrame(message))
        }
    }

    override fun socketId(): String = id
}
