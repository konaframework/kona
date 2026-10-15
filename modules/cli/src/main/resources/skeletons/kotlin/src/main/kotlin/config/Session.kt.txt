package config

import dev.kona.core.Config
import dev.kona.core.ConfigFile
import dev.kona.core.Env

object Session : ConfigFile {
    override fun key(): String = "session"

    override fun config(): Map<String, Any?> = mapOf(
        "driver" to Env.getOrDefault("SESSION_DRIVER", "cookie"),
        "lifetime" to Env.getOrDefault("SESSION_LIFETIME", "120").toIntOrNull() ?: 120,
        "cookie" to Env.getOrDefault("SESSION_COOKIE", "kona_session"),
        "path" to Env.getOrDefault("SESSION_PATH", "/"),
        "encrypt" to (Config.toBoolean(Env.getOrDefault("SESSION_ENCRYPT", "false")) ?: false),
        "http_only" to (Config.toBoolean(Env.getOrDefault("SESSION_HTTP_ONLY", "true")) ?: true),
        "same_site" to Env.getOrDefault("SESSION_SAME_SITE", "Lax"),
    )
}
