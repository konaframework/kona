package dev.kona.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NettyHttpBridgeTest {
    @Test
    fun toRequestParsesPathQueryHeadersAndBody() {
        val content = Unpooled.copiedBuffer("a=b", Charsets.UTF_8)
        val msg =
            DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/echo//x/?q=1&lonely&x=y#frag",
                content,
            )
        msg.headers().add("X-A", "1")
        msg.headers().add("X-A", "2")
        msg.headers().add("Content-Type", "application/x-www-form-urlencoded")
        val request = NettyHttpBridge.toRequest(msg)
        msg.release()
        assertEquals("POST", request.method)
        assertEquals("/echo/x", request.path)
        assertEquals("1", request.query["q"])
        assertEquals("", request.query["lonely"])
        assertEquals("y", request.query["x"])
        assertEquals("1", request.header("X-A"))
        assertEquals("a=b", request.body)
    }

    @Test
    fun toRequestEmptyBodyAndRootPath() {
        val msg = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        val request = NettyHttpBridge.toRequest(msg)
        msg.release()
        assertEquals("/", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals(0, request.bodyBytes.size)
    }

    @Test
    fun toResponseWritesCookiesHeadAndNoContent() {
        val withBody =
            NettyHttpBridge.toResponse(
                Response.text("pong").cookie("sid", "1").cookie(Cookie("x", "y", httpOnly = false)),
                head = false,
                keepAlive = true,
            )
        assertEquals(200, withBody.status().code())
        assertEquals(2, withBody.headers().getAll("Set-Cookie").size)
        assertTrue(HttpUtil.isKeepAlive(withBody))
        withBody.release()

        val head =
            NettyHttpBridge.toResponse(Response.text("pong"), head = true, keepAlive = false)
        assertEquals(0, head.content().readableBytes())
        assertEquals("4", head.headers().get("Content-Length"))
        assertFalse(HttpUtil.isKeepAlive(head))
        head.release()

        val noContent =
            NettyHttpBridge.toResponse(Response.noContent(), head = false, keepAlive = true)
        assertEquals(204, noContent.status().code())
        assertEquals("0", noContent.headers().get("Content-Length"))
        noContent.release()

        val skipTe =
            NettyHttpBridge.toResponse(
                Response.text("x").header("Transfer-Encoding", "chunked"),
                head = false,
                keepAlive = true,
            )
        assertTrue(skipTe.headers().get("Transfer-Encoding") == null)
        skipTe.release()
    }

    @Test
    fun statusOfUsesKnownPhraseAndFallsBack() {
        assertEquals("Unprocessable Entity", NettyHttpBridge.statusOf(422).reasonPhrase())
        assertEquals("OK", NettyHttpBridge.statusOf(200).reasonPhrase())
        assertTrue(NettyHttpBridge.statusOf(599).reasonPhrase().isNotBlank())
    }

    @Test
    fun queryRawAndPathOfEdges() {
        assertNull(NettyHttpBridge.queryRaw("/ping"))
        assertNull(NettyHttpBridge.queryRaw("/ping?"))
        assertEquals("a=1", NettyHttpBridge.queryRaw("/ping?a=1"))
        assertEquals("a=1", NettyHttpBridge.queryRaw("/ping?a=1#x"))
        assertEquals("/files/a%20b", NettyHttpBridge.pathOf("/files/a%20b?x=1#y"))
        assertEquals("/", NettyHttpBridge.pathOf(""))
        assertEquals("/", NettyHttpBridge.pathOf("?a=1"))
    }

    @Test
    fun isHeadDetectsMethod() {
        val head = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/")
        val get = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        assertTrue(NettyHttpBridge.isHead(head))
        assertFalse(NettyHttpBridge.isHead(get))
        head.release()
        get.release()
    }

    @Test
    fun httpQueryParse() {
        assertTrue(HttpQuery.parse(null).isEmpty())
        assertTrue(HttpQuery.parse("").isEmpty())
        assertTrue(HttpQuery.parse("   ").isEmpty())
        assertEquals("", HttpQuery.parse("lonely")["lonely"])
        assertEquals("b", HttpQuery.parse("a=b")["a"])
    }
}
