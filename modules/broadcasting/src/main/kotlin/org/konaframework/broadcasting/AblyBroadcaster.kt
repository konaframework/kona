package org.konaframework.broadcasting

import org.konaframework.support.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Optional Ably REST publish driver (`ably` connection).
 */
class AblyBroadcaster(
    private val key: String,
    private val sender: Consumer<HttpRequest> = liveSender(),
) : Broadcaster {
    data class Entry(val channels: List<String>, val event: String, val payload: Map<String, Any?>)

    private val sent = CopyOnWriteArrayList<Entry>()

    fun messages(): List<Entry> = sent.toList()

    fun clear() {
        sent.clear()
    }

    override fun broadcast(channels: List<Channel>, event: String, payload: Map<String, Any?>) {
        broadcast(channels, event, payload, null)
    }

    override fun broadcast(
        channels: List<Channel>,
        event: String,
        payload: Map<String, Any?>,
        excludeSocketId: String?,
    ) {
        val names = channels.map { it.name }
        sent += Entry(names, event, payload)
        val body =
            Json.stringify(
                mapOf(
                    "name" to event,
                    "data" to payload,
                ),
            )
        val auth = Base64.getEncoder().encodeToString(key.toByteArray())
        for (channel in names) {
            val uri = URI.create("https://rest.ably.io/channels/${encode(channel)}/messages")
            val request =
                HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic $auth")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
            sender.accept(request)
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    companion object {
        @JvmStatic
        fun liveSender(): Consumer<HttpRequest> {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            return Consumer { request ->
                client.send(request, HttpResponse.BodyHandlers.discarding())
            }
        }
    }
}
