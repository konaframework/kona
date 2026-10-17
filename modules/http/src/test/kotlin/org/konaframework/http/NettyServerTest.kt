package org.konaframework.http

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NettyServerTest {
    @Test
    fun startStopWithNioTransportAndDelay() {
        val router = Router()
        router.get("/ping") { Response.text("pong") }
        val kernel = HttpKernel(router)
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server =
            KonaHttpServer(
                kernel,
                "127.0.0.1",
                free,
                transportPreference = "nio",
            )
        assertFalse(server.isRunning())
        server.start()
        server.start()
        assertTrue(server.isRunning())
        assertEquals(free, server.port())
        val client = HttpClient.newHttpClient()
        val resp =
            client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/ping")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, resp.statusCode())
        assertEquals("pong", resp.body())
        server.stop(1)
        assertFalse(server.isRunning())
        server.stop(0)
        assertFalse(server.isRunning())
    }

    @Test
    fun bindFailureCleansUp() {
        val blocker = java.net.ServerSocket(0)
        try {
            val port = blocker.localPort
            val server =
                KonaHttpServer(
                    HttpKernel(Router()),
                    "127.0.0.1",
                    port,
                    transportPreference = "nio",
                )
            assertThrows(Exception::class.java) { server.start() }
            assertFalse(server.isRunning())
        } finally {
            blocker.close()
        }
    }

    @Test
    fun epollPreferenceWhenAvailable() {
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server =
            KonaHttpServer(
                HttpKernel(Router().also { it.get("/") { Response.text("ok") } }),
                "127.0.0.1",
                free,
                transportPreference = "epoll",
            )
        server.start()
        try {
            assertTrue(server.isRunning())
            val client = HttpClient.newHttpClient()
            assertEquals(
                200,
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                ).statusCode(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun handlerRejectsDecoderFailuresAndClosesOnException() {
        val router = Router()
        router.get("/") { Response.text("ok") }
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val badChannel = EmbeddedChannel(KonaNettyHandler(HttpKernel(router), executor))
            val bad =
                DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.GET,
                    "/",
                    Unpooled.EMPTY_BUFFER,
                )
            bad.setDecoderResult(DecoderResult.failure(IllegalStateException("boom")))
            badChannel.writeInbound(bad)
            val out = badChannel.readOutbound<FullHttpResponse>()
            assertEquals(400, out.status().code())
            ReferenceCountUtil.release(out)
            assertFalse(badChannel.isActive)
            badChannel.finishAndReleaseAll()

            val closeChannel = EmbeddedChannel(KonaNettyHandler(HttpKernel(router), executor))
            closeChannel.pipeline().fireExceptionCaught(RuntimeException("x"))
            assertFalse(closeChannel.isActive)
        } finally {
            executor.close()
        }
    }

    @Test
    fun handlerRecoversWhenExceptionHandlerThrows() {
        val router = Router()
        router.get("/") { throw RuntimeException("boom") }
        val exploding =
            ExceptionHandler { _, _ ->
                throw IllegalStateException("handler-fail")
            }
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val channel = EmbeddedChannel(KonaNettyHandler(HttpKernel(router, exceptionHandler = exploding), executor))
            val req =
                DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.GET,
                    "/",
                    Unpooled.EMPTY_BUFFER,
                )
            channel.writeInbound(req)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            var out: FullHttpResponse? = null
            while (System.nanoTime() < deadline && out == null) {
                channel.runPendingTasks()
                out = channel.readOutbound()
                if (out == null) {
                    Thread.sleep(5)
                }
            }
            assertEquals(500, out!!.status().code())
            ReferenceCountUtil.release(out)
            channel.finishAndReleaseAll()
        } finally {
            executor.close()
        }
    }

    @Test
    fun writeHelpersCoverKeepAliveAndInactiveChannel() {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val handler = KonaNettyHandler(HttpKernel(Router()), executor)
            val channel = EmbeddedChannel(handler)
            val ctx = channel.pipeline().context(handler)
            val payload =
                NettyHttpBridge.toResponse(Response.text("x"), head = false, keepAlive = false)
            KonaNettyHandler.writeResponse(ctx, payload, keepAlive = false)
            channel.runPendingTasks()
            val out = channel.readOutbound<FullHttpResponse>()
            assertEquals(200, out.status().code())
            ReferenceCountUtil.release(out)

            channel.close().syncUninterruptibly()
            KonaNettyHandler.writeError(ctx)
            channel.runPendingTasks()
        } finally {
            executor.close()
        }
    }

    @Test
    fun binaryViaNetty(@TempDir base: Path) {
        val app = org.konaframework.core.Application(base)
        app.register(HttpServiceProvider()).boot()
        val router = app.resolve(Router::class.java)
        router.get("/bin") { Response.binary(byteArrayOf(1, 2, 3), "application/octet-stream") }
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server = KonaHttpServer(app.resolve(HttpKernel::class.java), "127.0.0.1", free)
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            val resp =
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/bin")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                )
            assertEquals(200, resp.statusCode())
            assertEquals(listOf(1.toByte(), 2.toByte(), 3.toByte()), resp.body().toList())
        } finally {
            server.stop()
        }
    }
}
