package dev.kona.broadcasting

import dev.kona.core.Config
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves named broadcasting drivers from config.
 *
 * - `broadcasting.default` — connection name (default `log`)
 * - `broadcasting.connections.{name}.driver` — `log`, `null`/`none`, `websocket`/`ws`
 */
class BroadcastManager(
    private val config: Config,
) {
    private val drivers = ConcurrentHashMap<String, Broadcaster>()
    private val logDriver = LogBroadcaster()
    private val websocketDriver = WebsocketBroadcaster()

    fun connection(): Broadcaster = connection(defaultConnection())

    fun connection(name: String): Broadcaster =
        drivers.getOrPut(name) { create(name) }

    fun defaultConnection(): String = config.getString("broadcasting.default", "log")

    fun log(): LogBroadcaster = logDriver

    fun websocket(): WebsocketBroadcaster = websocketDriver

    fun broadcast(event: ShouldBroadcast) {
        val channels = event.broadcastOn()
        if (channels.isEmpty()) return
        connection().broadcast(channels, event.broadcastAs(), event.broadcastWith())
    }

    private fun create(name: String): Broadcaster {
        val driver = config.getString("broadcasting.connections.$name.driver", name)
        return when (driver.lowercase()) {
            "log" -> logDriver
            "null", "none" -> NullBroadcaster()
            "websocket", "ws" -> websocketDriver
            else -> throw IllegalArgumentException("Unknown broadcast driver: $driver")
        }
    }
}
