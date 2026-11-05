package org.konaframework.broadcasting

import org.konaframework.broadcasting.reverb.ReverbServer
import org.konaframework.grind.ConsoleCommand

/**
 * `./grind reverb:start` — run the first-party Pusher-protocol websocket server.
 */
class ReverbStartCommand : ConsoleCommand() {
    override val signature: String =
        "reverb:start {--host= : Bind host} {--port= : Bind port} {--once : Bind then return (tests)}"

    override val description: String = "Start the Kona Reverb websocket server"

    override fun handle(): Int {
        val config = app.config
        val connection = config.getString("broadcasting.default", "websocket")
        val prefix = "broadcasting.connections.$connection"
        val key = config.getString("$prefix.key")
            ?: config.getString("broadcasting.connections.websocket.key", "kona-key")
        val secret = config.getString("$prefix.secret")
            ?: config.getString("broadcasting.connections.websocket.secret", "kona-secret")
        val host = option("host")
            ?: config.getString("$prefix.host")
            ?: config.getString("broadcasting.connections.websocket.host", "127.0.0.1")
        val port =
            option("port")?.toIntOrNull()
                ?: config.getInt("$prefix.port", 0).takeIf { it > 0 }
                ?: config.getInt("broadcasting.connections.websocket.port", 8080)

        // Ensure Reverb hub / scale-out listener are warm before binding.
        config.set("broadcasting.connections.reverb.driver", "reverb")
        config.set("broadcasting.connections.reverb.key", key)
        config.set("broadcasting.connections.reverb.secret", secret)
        val manager = app.resolve(BroadcastManager::class.java)
        val hub = manager.hub()
        manager.reverb()

        val server = ReverbServer(hub, key, host, port)
        server.start()
        info("Reverb running on ws://$host:$port/app/$key")
        if (optionBool("once")) {
            server.stop()
            return SUCCESS
        }
        try {
            Thread.currentThread().join()
        } finally {
            server.stop()
        }
        return SUCCESS
    }
}
