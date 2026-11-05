package org.konaframework.broadcasting.reverb

import org.konaframework.redis.RedisConnection
import org.konaframework.support.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Horizontal fan-out between Reverb nodes. */
interface ScaleOutBus {
    fun publish(payload: ScaleOutMessage)

    fun subscribe(handler: (ScaleOutMessage) -> Unit): AutoCloseable
}

data class ScaleOutMessage(
    val channels: List<String>,
    val event: String,
    val payload: Map<String, Any?>,
    val excludeSocketId: String? = null,
    val origin: String? = null,
)

/** Process-local bus used when Redis scaling is off (still useful for tests). */
class LocalScaleOutBus : ScaleOutBus {
    private val listeners = CopyOnWriteArrayList<(ScaleOutMessage) -> Unit>()

    override fun publish(payload: ScaleOutMessage) {
        for (listener in listeners) {
            listener(payload)
        }
    }

    override fun subscribe(handler: (ScaleOutMessage) -> Unit): AutoCloseable {
        listeners += handler
        return AutoCloseable { listeners.remove(handler) }
    }
}

/** Redis pub/sub scale-out (`kona:reverb` channel by default). */
class RedisScaleOutBus(
    private val redis: RedisConnection,
    private val channel: String = DEFAULT_CHANNEL,
) : ScaleOutBus {
    override fun publish(payload: ScaleOutMessage) {
        redis.publish(channel, Json.stringify(payload.toMap()))
    }

    override fun subscribe(handler: (ScaleOutMessage) -> Unit): AutoCloseable {
        val open = AtomicBoolean(true)
        val sub =
            redis.subscribe(channel) { _, message ->
                if (!open.get()) {
                    return@subscribe
                }
                handler(fromJson(message) ?: return@subscribe)
            }
        return AutoCloseable {
            open.set(false)
            sub.close()
        }
    }

    companion object {
        const val DEFAULT_CHANNEL = "kona:reverb"

        private fun ScaleOutMessage.toMap(): Map<String, Any?> =
            mapOf(
                "channels" to channels,
                "event" to event,
                "payload" to payload,
                "excludeSocketId" to excludeSocketId,
                "origin" to origin,
            )

        @Suppress("UNCHECKED_CAST")
        private fun fromJson(raw: String): ScaleOutMessage? {
            return try {
                val map = Json.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
                val channels = (map["channels"] as? List<*>)?.map { it.toString() } ?: return null
                val event = map["event"]?.toString() ?: return null
                val payload = (map["payload"] as? Map<String, Any?>).orEmpty()
                ScaleOutMessage(
                    channels = channels,
                    event = event,
                    payload = payload,
                    excludeSocketId = map["excludeSocketId"]?.toString(),
                    origin = map["origin"]?.toString(),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
