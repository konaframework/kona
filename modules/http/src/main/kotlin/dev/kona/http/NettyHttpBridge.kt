package dev.kona.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion

/**
 * Converts between Netty HTTP messages and Kona [Request] / [Response].
 */
internal object NettyHttpBridge {
    private val EMPTY_BODY = ByteArray(0)

    fun toRequest(msg: FullHttpRequest): Request {
        val path = pathOf(msg.uri())
        val query = HttpQuery.parse(queryRaw(msg.uri()))
        val headers = linkedMapOf<String, String>()
        msg.headers().names().forEach { name ->
            headers[name] = msg.headers().get(name)
        }
        val content = msg.content()
        val bodyBytes =
            if (!content.isReadable) {
                EMPTY_BODY
            } else {
                val bytes = ByteArray(content.readableBytes())
                content.getBytes(content.readerIndex(), bytes)
                bytes
            }
        return Request(
            method = msg.method().name(),
            path = path,
            headers = headers,
            query = query,
            body = "",
            bodyBytes = bodyBytes,
        )
    }

    fun toResponse(response: Response, head: Boolean, keepAlive: Boolean): FullHttpResponse {
        val bytes = response.bodyBytes()
        val status = statusOf(response.status)
        val omitBody = head || response.status == 204
        val content =
            if (omitBody) {
                Unpooled.EMPTY_BUFFER
            } else {
                Unpooled.wrappedBuffer(bytes)
            }
        val netty = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content)
        response.headers.forEach { (k, v) ->
            if (!k.equals("Transfer-Encoding", ignoreCase = true)) {
                netty.headers().set(k, v)
            }
        }
        response.cookies.forEach { cookie ->
            netty.headers().add(HttpHeaderNames.SET_COOKIE, cookie.toSetCookieHeader())
        }
        if (omitBody) {
            if (response.status == 204) {
                HttpUtil.setContentLength(netty, 0)
            } else {
                HttpUtil.setContentLength(netty, bytes.size.toLong())
            }
        } else {
            HttpUtil.setContentLength(netty, bytes.size.toLong())
        }
        HttpUtil.setKeepAlive(netty, keepAlive)
        return netty
    }

    fun isHead(msg: FullHttpRequest): Boolean = msg.method() == HttpMethod.HEAD

    fun statusOf(code: Int): HttpResponseStatus {
        val phrase = HttpStatus.reasonPhrase(code)
        return if (phrase != null) {
            HttpResponseStatus(code, phrase)
        } else {
            HttpResponseStatus.valueOf(code)
        }
    }

    /** Raw query substring after `?`, or null when absent. */
    fun queryRaw(uri: String): String? {
        val idx = uri.indexOf('?')
        if (idx < 0 || idx == uri.lastIndex) {
            return null
        }
        val fragment = uri.indexOf('#', startIndex = idx + 1)
        return if (fragment < 0) {
            uri.substring(idx + 1)
        } else {
            uri.substring(idx + 1, fragment)
        }
    }

    /**
     * Request-target path without query/fragment. Keeps percent-encoding (same shape as the
     * former JDK adapter’s `rawPath`).
     */
    fun pathOf(uri: String): String {
        val noFrag = uri.substringBefore('#')
        val pathPart = noFrag.substringBefore('?')
        return HttpPaths.normalize(pathPart.ifEmpty { "/" })
    }
}
