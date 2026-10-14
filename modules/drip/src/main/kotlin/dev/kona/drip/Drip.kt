package dev.kona.drip

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class DripResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, List<String>>,
) {
    fun successful(): Boolean = status in 200..299
    fun json(): Map<*, *> = jacksonObjectMapper().readValue(body, Map::class.java)
}

/**
 * Fluent HTTP client.
 */
class Drip(
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    private var baseUrl: String = ""
    private val defaultHeaders = linkedMapOf<String, String>()

    fun baseUrl(url: String): Drip {
        baseUrl = url.trimEnd('/')
        return this
    }

    fun withHeaders(headers: Map<String, String>): Drip {
        defaultHeaders.putAll(headers)
        return this
    }

    fun get(url: String): DripResponse = get(url, emptyMap())

    fun get(url: String, query: Map<String, String>): DripResponse =
        send("GET", url, query, null)

    fun post(url: String): DripResponse = post(url, null)

    fun post(url: String, body: Any?): DripResponse = send("POST", url, emptyMap(), body)

    fun put(url: String): DripResponse = put(url, null)

    fun put(url: String, body: Any?): DripResponse = send("PUT", url, emptyMap(), body)

    fun patch(url: String): DripResponse = patch(url, null)

    fun patch(url: String, body: Any?): DripResponse = send("PATCH", url, emptyMap(), body)

    fun delete(url: String): DripResponse = send("DELETE", url, emptyMap(), null)

    private fun send(method: String, url: String, query: Map<String, String>, body: Any?): DripResponse {
        val absolute = if (url.startsWith("http")) url else "$baseUrl/${url.trimStart('/')}"
        val uri = if (query.isEmpty()) {
            URI.create(absolute)
        } else {
            val q = query.entries.joinToString("&") { "${it.key}=${it.value}" }
            val sep = if (absolute.contains("?")) "&" else "?"
            URI.create(absolute + sep + q)
        }
        val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
        defaultHeaders.forEach { (k, v) -> builder.header(k, v) }
        val publisher = when (body) {
            null -> HttpRequest.BodyPublishers.noBody()
            is String -> {
                builder.header("Content-Type", "text/plain; charset=utf-8")
                HttpRequest.BodyPublishers.ofString(body)
            }
            else -> {
                builder.header("Content-Type", "application/json")
                HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
            }
        }
        builder.method(method, publisher)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return DripResponse(response.statusCode(), response.body(), response.headers().map())
    }
}
