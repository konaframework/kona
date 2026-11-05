package org.konaframework.broadcasting

import org.konaframework.broadcasting.reverb.LocalScaleOutBus
import org.konaframework.broadcasting.reverb.RedisScaleOutBus
import org.konaframework.broadcasting.reverb.ReverbBroadcaster
import org.konaframework.broadcasting.reverb.ReverbHub
import org.konaframework.broadcasting.reverb.ScaleOutBus
import org.konaframework.cadence.DB
import org.konaframework.cadence.Database
import org.konaframework.core.Config
import org.konaframework.redis.RedisConnection
import org.konaframework.redis.RedisManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves named broadcasting drivers from config.
 *
 * - `broadcasting.default` — connection name (default `log`)
 * - `broadcasting.connections.{name}.driver` — `log`, `null`/`none`, `websocket`/`ws`/`reverb`,
 *   `pusher`, `ably`
 *
 * Events that also implement [ShouldBroadcastAfterCommit] are deferred until after the
 * outermost Cadence transaction commits.
 */
class BroadcastManager(
    private val config: Config,
    private val redisManager: RedisManager? = null,
) {
    private val drivers = ConcurrentHashMap<String, Broadcaster>()
    private val logDriver = LogBroadcaster()
    private val websocketDriver = WebsocketBroadcaster()
    private var reverbHub: ReverbHub? = null
    private var reverbBroadcaster: ReverbBroadcaster? = null

    fun connection(): Broadcaster = connection(defaultConnection())

    fun connection(name: String): Broadcaster =
        drivers.getOrPut(name) { create(name) }

    fun defaultConnection(): String = config.getString("broadcasting.default", "log")

    fun log(): LogBroadcaster = logDriver

    fun websocket(): WebsocketBroadcaster = websocketDriver

    fun hub(): ReverbHub = reverbHub ?: createHub().also { reverbHub = it }

    fun reverb(): ReverbBroadcaster = reverbBroadcaster ?: createReverb().also { reverbBroadcaster = it }

    fun broadcast(event: ShouldBroadcast) {
        if (shouldDeferUntilCommit(event)) {
            DB.afterCommit(Runnable { broadcastNow(event) })
            return
        }
        broadcastNow(event)
    }

    private fun shouldDeferUntilCommit(event: ShouldBroadcast): Boolean {
        if (event !is ShouldBroadcastAfterCommit) {
            return false
        }
        if (!Database.hasGlobal()) {
            return false
        }
        return DB.transactionLevel() > 0
    }

    private fun broadcastNow(event: ShouldBroadcast) {
        val channels = event.broadcastOn()
        if (channels.isEmpty()) return
        val exclude = Broadcast.currentSocketId()
        connection().broadcast(channels, event.broadcastAs(), event.broadcastWith(), exclude)
    }

    private fun create(name: String): Broadcaster {
        val driver = config.getString("broadcasting.connections.$name.driver", name)
        return when (driver.lowercase()) {
            "log" -> logDriver
            "null", "none" -> NullBroadcaster()
            "websocket", "ws" -> websocketDriver
            "reverb" -> reverb()
            "pusher" -> createPusher(name)
            "ably" -> createAbly(name)
            else -> throw IllegalArgumentException("Unknown broadcast driver: $driver")
        }
    }

    private fun createHub(): ReverbHub {
        val prefix = connectionPrefix()
        val key = config.getString("$prefix.key", "kona-key")
        val secret = config.getString("$prefix.secret", "kona-secret")
        return ReverbHub(key, secret)
    }

    private fun createReverb(): ReverbBroadcaster {
        val hub = hub()
        val scaleOut = resolveScaleOut()
        return ReverbBroadcaster(hub, scaleOut).also { it.startScaleOutListener() }
    }

    private fun resolveScaleOut(): ScaleOutBus? {
        val enabled = config.getString("broadcasting.reverb.scaling", "false")
        if (!enabled.equals("redis", ignoreCase = true) && enabled != "true") {
            return null
        }
        val redis = resolveRedis() ?: return LocalScaleOutBus()
        val channel = config.getString("broadcasting.reverb.scaling_channel", RedisScaleOutBus.DEFAULT_CHANNEL)
        return RedisScaleOutBus(redis, channel)
    }

    private fun resolveRedis(): RedisConnection? {
        val manager = redisManager ?: return null
        return try {
            manager.connection()
        } catch (_: Exception) {
            null
        }
    }

    private fun createPusher(name: String): Broadcaster {
        val prefix = "broadcasting.connections.$name"
        return PusherBroadcaster(
            appId = config.getString("$prefix.app_id", "app-id"),
            key = config.getString("$prefix.key", "key"),
            secret = config.getString("$prefix.secret", "secret"),
            host = config.getString("$prefix.options.host")
                ?: config.getString("$prefix.host", "api-mt1.pusher.com"),
            scheme = config.getString("$prefix.scheme", "https"),
            port = config.getInt("$prefix.port", 0).takeIf { it > 0 },
            sender = PusherBroadcaster.liveSender(),
        )
    }

    private fun createAbly(name: String): Broadcaster {
        val prefix = "broadcasting.connections.$name"
        return AblyBroadcaster(
            key = config.getString("$prefix.key", "ably-key"),
            sender = AblyBroadcaster.liveSender(),
        )
    }

    private fun connectionPrefix(): String {
        val name = defaultConnection()
        val driver = config.getString("broadcasting.connections.$name.driver", name).lowercase()
        return when (driver) {
            "reverb", "websocket", "ws" -> "broadcasting.connections.$name"
            else -> "broadcasting.connections.websocket"
        }
    }
}
