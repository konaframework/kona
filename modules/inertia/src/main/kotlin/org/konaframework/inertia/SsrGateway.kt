package org.konaframework.inertia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Result from a Node Inertia SSR server (`POST /render`).
 */
data class SsrResponse(
    val head: String,
    val body: String,
)

/**
 * Optional SSR bridge — posts the page object to a Node SSR server and returns
 * head/body HTML. Failures return `null` so the manager falls back to CSR.
 */
class SsrGateway(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build(),
) {
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var url: String = "http://127.0.0.1:13714"

    fun dispatch(page: Map<String, Any?>): SsrResponse? {
        if (!enabled) {
            return null
        }
        return try {
            val endpoint = renderUrl()
            val body = mapper.writeValueAsString(page)
            val request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (!isSuccessStatus(response.statusCode())) {
                return null
            }
            parse(response.body())
        } catch (_: Exception) {
            null
        }
    }

    fun isHealthy(): Boolean {
        if (!enabled) {
            return false
        }
        return try {
            val request = HttpRequest.newBuilder(URI.create(healthUrl()))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            isSuccessStatus(response.statusCode())
        } catch (_: Exception) {
            false
        }
    }

    internal fun isSuccessStatus(status: Int): Boolean = status in 200..299

    internal fun parse(json: String): SsrResponse? {
        val tree = mapper.readTree(json)
        val headNode = tree.get("head")
        val bodyNode = tree.get("body")
        val head = when {
            headNode == null || headNode.isNull -> ""
            headNode.isArray -> headNode.map { it.asText() }.joinToString("\n")
            else -> headNode.asText()
        }
        val body = if (bodyNode == null || bodyNode.isNull) "" else bodyNode.asText()
        return SsrResponse(head = head, body = body)
    }

    private fun renderUrl(): String = join(url, "/render")

    private fun healthUrl(): String = join(url, "/health")

    companion object {
        internal fun join(base: String, path: String): String {
            val trimmed = base.trimEnd('/')
            val suffix = if (path.startsWith("/")) path else "/$path"
            return trimmed + suffix
        }
    }
}
