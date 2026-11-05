package org.konaframework.broadcasting.reverb

import org.konaframework.broadcasting.PusherSigner
import org.konaframework.support.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/** In-memory connection and channel registry for a Reverb node. */
class ReverbHub(
    private val appKey: String,
    private val appSecret: String,
) {
    interface Client {
        fun send(message: String)

        fun socketId(): String
    }

    data class PresenceMember(val userId: String, val userInfo: Map<String, Any?>)

    private val socketSeq = AtomicLong()
    private val clients = ConcurrentHashMap<String, Client>()
    private val subscriptions = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val presence = ConcurrentHashMap<String, ConcurrentHashMap<String, PresenceMember>>()
    private val clientPresence = ConcurrentHashMap<String, ConcurrentHashMap<String, PresenceMember>>()

    fun nextSocketId(): String {
        val a = socketSeq.incrementAndGet()
        val b = UUID.randomUUID().mostSignificantBits.ushr(1)
        return "$a.$b"
    }

    fun register(client: Client) {
        clients[client.socketId()] = client
    }

    fun unregister(socketId: String) {
        clients.remove(socketId)
        val channels = subscriptions.filter { (_, sockets) -> sockets.contains(socketId) }.keys.toList()
        for (channel in channels) {
            unsubscribe(socketId, channel)
        }
    }

    fun subscribe(
        socketId: String,
        channel: String,
        auth: String?,
        channelData: String?,
    ): String {
        if (channel.startsWith("private-") || channel.startsWith("presence-")) {
            if (!PusherSigner.verify(appKey, appSecret, socketId, channel, auth, channelData)) {
                return PusherProtocol.error(4009, "Connection is unauthorized")
            }
        }
        subscriptions.getOrPut(channel) { CopyOnWriteArrayList() }.let { bucket ->
            if (!bucket.contains(socketId)) {
                bucket.add(socketId)
            }
        }
        if (channel.startsWith("presence-")) {
            val member = parsePresence(channelData)
            if (member != null) {
                presence.getOrPut(channel) { ConcurrentHashMap() }[socketId] = member
                clientPresence.getOrPut(socketId) { ConcurrentHashMap() }[channel] = member
                broadcastRaw(channel, PusherProtocol.memberAdded(channel, member.userId, member.userInfo), excludeSocketId = socketId)
            }
            val members = presence[channel]?.values?.toList().orEmpty()
            val payload =
                mapOf(
                    "presence" to
                        mapOf(
                            "ids" to members.map { it.userId },
                            "hash" to members.associate { it.userId to it.userInfo },
                            "count" to members.size,
                        ),
                )
            return PusherProtocol.subscriptionSucceeded(channel, payload)
        }
        return PusherProtocol.subscriptionSucceeded(channel)
    }

    fun unsubscribe(socketId: String, channel: String) {
        subscriptions[channel]?.remove(socketId)
        if (subscriptions[channel]?.isEmpty() == true) {
            subscriptions.remove(channel)
        }
        val member = presence[channel]?.remove(socketId)
        clientPresence[socketId]?.remove(channel)
        if (presence[channel]?.isEmpty() == true) {
            presence.remove(channel)
        }
        if (member != null) {
            broadcastRaw(channel, PusherProtocol.memberRemoved(channel, member.userId), excludeSocketId = socketId)
        }
        if (clientPresence[socketId]?.isEmpty() == true) {
            clientPresence.remove(socketId)
        }
    }

    fun publish(channel: String, event: String, payload: Map<String, Any?>, excludeSocketId: String? = null) {
        broadcastRaw(channel, PusherProtocol.event(channel, event, payload), excludeSocketId)
    }

    fun broadcastRaw(channel: String, message: String, excludeSocketId: String? = null) {
        val sockets = subscriptions[channel] ?: return
        for (socketId in sockets) {
            if (excludeSocketId != null && socketId == excludeSocketId) {
                continue
            }
            clients[socketId]?.send(message)
        }
    }

    fun handleClientMessage(socketId: String, raw: String): String? {
        val message = PusherProtocol.parse(raw)
        val event = message["event"]?.toString() ?: return PusherProtocol.error(4000, "Event required")
        val data = PusherProtocol.dataObject(message)
        return when (event) {
            "pusher:ping" -> PusherProtocol.pong()
            "pusher:pong" -> null
            "pusher:subscribe" -> {
                val channel = data["channel"]?.toString() ?: return PusherProtocol.error(4001, "Channel required")
                val auth = data["auth"]?.toString()
                val channelData = data["channel_data"]?.toString()
                subscribe(socketId, channel, auth, channelData)
            }
            "pusher:unsubscribe" -> {
                val channel = data["channel"]?.toString() ?: return PusherProtocol.error(4001, "Channel required")
                unsubscribe(socketId, channel)
                null
            }
            else -> {
                if (event.startsWith("client-")) {
                    val channel = message["channel"]?.toString() ?: data["channel"]?.toString()
                    if (channel != null) {
                        broadcastRaw(channel, raw, excludeSocketId = socketId)
                    }
                    null
                } else {
                    PusherProtocol.error(4002, "Unknown event: $event")
                }
            }
        }
    }

    fun forEachClient(action: Consumer<Client>) {
        clients.values.forEach(action)
    }

    fun connectionCount(): Int = clients.size

    fun listenerCount(channel: String): Int = subscriptions[channel]?.size ?: 0

    private fun parsePresence(channelData: String?): PresenceMember? {
        if (channelData.isNullOrBlank()) {
            return null
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = Json.mapper.readValue(channelData, Map::class.java) as Map<String, Any?>
            val userId = map["user_id"]?.toString() ?: return null
            @Suppress("UNCHECKED_CAST")
            val info = (map["user_info"] as? Map<String, Any?>).orEmpty()
            PresenceMember(userId, info)
        } catch (_: Exception) {
            null
        }
    }
}
