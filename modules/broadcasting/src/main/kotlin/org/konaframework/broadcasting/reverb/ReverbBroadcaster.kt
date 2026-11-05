package org.konaframework.broadcasting.reverb

import org.konaframework.broadcasting.Broadcaster
import org.konaframework.broadcasting.Channel
import java.util.UUID

/**
 * Publishes events into a local [ReverbHub] and optionally onto a [ScaleOutBus]
 * so sibling Reverb nodes can fan out to their websocket clients.
 */
class ReverbBroadcaster(
    private val hub: ReverbHub,
    private val scaleOut: ScaleOutBus? = null,
    private val originId: String = UUID.randomUUID().toString(),
) : Broadcaster {
    private var scaleOutSubscription: AutoCloseable? = null

    fun startScaleOutListener() {
        val bus = scaleOut ?: return
        if (scaleOutSubscription != null) {
            return
        }
        scaleOutSubscription =
            bus.subscribe { message ->
                if (message.origin == originId) {
                    return@subscribe
                }
                for (channel in message.channels) {
                    hub.publish(channel, message.event, message.payload, message.excludeSocketId)
                }
            }
    }

    fun stopScaleOutListener() {
        scaleOutSubscription?.close()
        scaleOutSubscription = null
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
        val names = channels.map { it.pusherName() }
        for (name in names) {
            hub.publish(name, event, payload, excludeSocketId)
        }
        scaleOut?.publish(
            ScaleOutMessage(
                channels = names,
                event = event,
                payload = payload,
                excludeSocketId = excludeSocketId,
                origin = originId,
            ),
        )
    }
}
