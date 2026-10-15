package dev.kona.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.kona.core.Log
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * JDK HttpServer + Virtual Threads. Thin adapter kept fully unit-testable via [HttpKernel].
 */
class KonaHttpServer(
    private val kernel: HttpKernel,
    private val host: String = "127.0.0.1",
    private val port: Int = 4000,
) {
    private var server: HttpServer? = null

    fun start() {
        if (server != null) {
            return
        }
        val http = HttpServer.create(InetSocketAddress(host, port), 0)
        http.executor = Executors.newVirtualThreadPerTaskExecutor()
        http.createContext("/") { exchange -> dispatch(exchange) }
        http.start()
        server = http
        Log.info("Kona server listening on http://$host:$port")
    }

    fun stop(delaySeconds: Int = 0) {
        server?.stop(delaySeconds)
        server = null
    }

    fun isRunning(): Boolean = server != null

    fun port(): Int = port

    internal fun dispatch(exchange: HttpExchange) {
        val request = readRequest(exchange)
        val response = kernel.handle(request)
        writeResponse(exchange, response, request.method.equals("HEAD", ignoreCase = true))
    }

    private fun readRequest(exchange: HttpExchange): Request {
        val method = exchange.requestMethod
        val uri = exchange.requestURI
        val path = HttpPaths.fromUriParts(uri.rawPath, uri.path)
        val query = parseQuery(uri.rawQuery)
        val headers = HeaderMaps.from(exchange.requestHeaders)
        val bodyBytes = exchange.requestBody.readBytes()
        return Request(method, path, headers, query, body = "", bodyBytes = bodyBytes)
    }

    private fun writeResponse(exchange: HttpExchange, response: Response, head: Boolean) {
        response.headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
        response.cookies.forEach { cookie ->
            exchange.responseHeaders.add("Set-Cookie", cookie.toSetCookieHeader())
        }
        val bytes = response.bodyBytes()
        if (head || response.status == 204) {
            exchange.sendResponseHeaders(response.status, -1)
            exchange.responseBody.close()
        } else {
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return raw.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) {
                    decode(pair) to ""
                } else {
                    decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
                }
            }
    }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8)
}
