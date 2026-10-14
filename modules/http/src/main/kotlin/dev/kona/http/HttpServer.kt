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
    private val port: Int = 8080,
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
        writeResponse(exchange, response)
    }

    private fun readRequest(exchange: HttpExchange): Request {
        val method = exchange.requestMethod
        val uri = exchange.requestURI
        val path = uri.rawPath
        val query = parseQuery(uri.rawQuery)
        val headers = HeaderMaps.from(exchange.requestHeaders)
        val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        return Request(method, path, headers, query, body)
    }

    private fun writeResponse(exchange: HttpExchange, response: Response) {
        response.headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(response.status, if (response.status == 204) -1 else bytes.size.toLong())
        if (response.status != 204) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.responseBody.close()
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
