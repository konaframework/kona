package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Broadcast connections (`config/broadcasting.php` shape).
 *
 * Supported drivers: `log`, `null`, `websocket`.
 */
object Broadcasting : ConfigFile {
    override fun key(): String = "broadcasting"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Default Broadcaster
        |--------------------------------------------------------------------------
        |
        | This option controls the default broadcaster that will be used by the
        | framework when an event needs to be broadcast. You may set this to
        | any of the connections defined in the "connections" array below.
        |
        | Supported: "websocket", "log", "null"
        |
        */
        "default" to Env.getOrDefault("BROADCAST_CONNECTION", "log"),

        /*
        |--------------------------------------------------------------------------
        | Broadcast Connections
        |--------------------------------------------------------------------------
        |
        | Here you may define all of the broadcast connections that will be used
        | to broadcast events to other systems or over WebSockets. Samples of
        | each available type of connection are provided inside this array.
        |
        */
        "connections" to mapOf(
            "websocket" to mapOf(
                "driver" to "websocket",
                "key" to Env.get("REVERB_APP_KEY"),
                "secret" to Env.get("REVERB_APP_SECRET"),
                "app_id" to Env.get("REVERB_APP_ID"),
                "host" to Env.getOrDefault("REVERB_HOST", "127.0.0.1"),
                "port" to (Env.getOrDefault("REVERB_PORT", "8080").toIntOrNull() ?: 8080),
                "scheme" to Env.getOrDefault("REVERB_SCHEME", "http"),
            ),
            "log" to mapOf(
                "driver" to "log",
            ),
            "null" to mapOf(
                "driver" to "null",
            ),
        ),
    )
}
