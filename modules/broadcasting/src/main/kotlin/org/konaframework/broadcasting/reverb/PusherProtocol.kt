package org.konaframework.broadcasting.reverb

import org.konaframework.support.json.Json

/** Pusher Channels websocket message helpers. */
object PusherProtocol {
    fun connectionEstablished(socketId: String, activityTimeout: Int = 120): String =
        envelope("pusher:connection_established", mapOf("socket_id" to socketId, "activity_timeout" to activityTimeout))

    fun subscriptionSucceeded(channel: String, data: Map<String, Any?> = emptyMap()): String =
        envelope("pusher_internal:subscription_succeeded", data, channel)

    fun memberAdded(channel: String, userId: String, userInfo: Map<String, Any?> = emptyMap()): String =
        envelope(
            "pusher_internal:member_added",
            mapOf("user_id" to userId, "user_info" to userInfo),
            channel,
        )

    fun memberRemoved(channel: String, userId: String): String =
        envelope("pusher_internal:member_removed", mapOf("user_id" to userId), channel)

    fun pong(): String = envelope("pusher:pong", emptyMap())

    fun error(code: Int, message: String): String =
        envelope("pusher:error", mapOf("code" to code, "message" to message))

    fun event(channel: String, event: String, data: Any?): String {
        val encoded =
            when (data) {
                is String -> data
                else -> Json.stringify(data ?: emptyMap<String, Any?>())
            }
        return Json.stringify(
            mapOf(
                "event" to event,
                "channel" to channel,
                "data" to encoded,
            ),
        )
    }

    fun envelope(event: String, data: Map<String, Any?>, channel: String? = null): String {
        val payload = linkedMapOf<String, Any?>("event" to event, "data" to Json.stringify(data))
        if (channel != null) {
            payload["channel"] = channel
        }
        return Json.stringify(payload)
    }

    @Suppress("UNCHECKED_CAST")
    fun parse(raw: String): Map<String, Any?> {
        return try {
            Json.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            emptyMap()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun dataObject(message: Map<String, Any?>): Map<String, Any?> {
        val data = message["data"] ?: return emptyMap()
        return when (data) {
            is Map<*, *> -> data as Map<String, Any?>
            is String ->
                try {
                    Json.mapper.readValue(data, Map::class.java) as Map<String, Any?>
                } catch (_: Exception) {
                    emptyMap()
                }
            else -> emptyMap()
        }
    }
}
