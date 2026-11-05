package org.konaframework.broadcasting

import org.konaframework.support.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Optional HTTP driver that triggers events on the Pusher Channels REST API
 * (or any Pusher-compatible host such as hosted Reverb / Soketi).
 */
class PusherBroadcaster(
    private val appId: String,
    private val key: String,
    private val secret: String,
    private val host: String = "api-mt1.pusher.com",
    private val scheme: String = "https",
    private val port: Int? = null,
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
                buildMap {
                    put("name", event)
                    put("channels", names)
                    put("data", Json.stringify(payload))
                    if (excludeSocketId != null) {
                        put("socket_id", excludeSocketId)
                    }
                },
            )
        val path = "/apps/$appId/events"
        val authQuery = signedQuery(path, body)
        val authority =
            if (port != null) {
                "$host:$port"
            } else {
                host
            }
        val uri = URI.create("$scheme://$authority$path?$authQuery")
        val request =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        sender.accept(request)
    }

    private fun signedQuery(path: String, body: String): String {
        val params =
            linkedMapOf(
                "auth_key" to key,
                "auth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
                "auth_version" to "1.0",
                "body_md5" to md5Hex(body),
            )
        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val stringToSign = "POST\n$path\n$query"
        val signature = hmacHex(secret, stringToSign)
        return "$query&auth_signature=$signature"
    }

    private fun md5Hex(value: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        return digest.digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmacHex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

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
