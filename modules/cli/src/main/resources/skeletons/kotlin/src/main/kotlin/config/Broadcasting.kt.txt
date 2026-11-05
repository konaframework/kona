package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Broadcast connections (`config/broadcasting.php` shape).
 *
 * Supported drivers: `reverb`, `websocket`, `pusher`, `ably`, `log`, `null`.
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
        | Supported: "reverb", "websocket", "pusher", "ably", "log", "null"
        |
        */
        "default" to Env.getOrDefault("BROADCAST_CONNECTION", "log"),

        /*
        |--------------------------------------------------------------------------
        | Reverb Scaling
        |--------------------------------------------------------------------------
        |
        | Set to "redis" to fan events across Reverb nodes via Redis pub/sub.
        |
        */
        "reverb" to mapOf(
            "scaling" to Env.getOrDefault("REVERB_SCALING", "false"),
            "scaling_channel" to Env.getOrDefault("REVERB_SCALING_CHANNEL", "kona:reverb"),
        ),

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
            "reverb" to mapOf(
                "driver" to "reverb",
                "key" to Env.get("REVERB_APP_KEY"),
                "secret" to Env.get("REVERB_APP_SECRET"),
                "app_id" to Env.get("REVERB_APP_ID"),
                "host" to Env.getOrDefault("REVERB_HOST", "127.0.0.1"),
                "port" to (Env.getOrDefault("REVERB_PORT", "8080").toIntOrNull() ?: 8080),
                "scheme" to Env.getOrDefault("REVERB_SCHEME", "http"),
            ),
            "websocket" to mapOf(
                "driver" to "websocket",
                "key" to Env.get("REVERB_APP_KEY"),
                "secret" to Env.get("REVERB_APP_SECRET"),
                "app_id" to Env.get("REVERB_APP_ID"),
                "host" to Env.getOrDefault("REVERB_HOST", "127.0.0.1"),
                "port" to (Env.getOrDefault("REVERB_PORT", "8080").toIntOrNull() ?: 8080),
                "scheme" to Env.getOrDefault("REVERB_SCHEME", "http"),
            ),
            "pusher" to mapOf(
                "driver" to "pusher",
                "key" to Env.get("PUSHER_APP_KEY"),
                "secret" to Env.get("PUSHER_APP_SECRET"),
                "app_id" to Env.get("PUSHER_APP_ID"),
                "host" to Env.getOrDefault("PUSHER_HOST", "api-mt1.pusher.com"),
                "port" to (Env.getOrDefault("PUSHER_PORT", "443").toIntOrNull() ?: 443),
                "scheme" to Env.getOrDefault("PUSHER_SCHEME", "https"),
            ),
            "ably" to mapOf(
                "driver" to "ably",
                "key" to Env.get("ABLY_KEY"),
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
