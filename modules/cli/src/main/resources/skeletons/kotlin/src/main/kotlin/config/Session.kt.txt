package config

import org.konaframework.core.Config
import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

object Session : ConfigFile {
    override fun key(): String = "session"

    override fun config(): Map<String, Any?> {
        val lifetime = Env.getOrDefault("SESSION_LIFETIME", "120").toIntOrNull() ?: 120
        return mapOf(
            "driver" to Env.getOrDefault("SESSION_DRIVER", "cookie"),
            "lifetime" to lifetime,
            "cookie" to Env.getOrDefault("SESSION_COOKIE", "kona_session"),
            "path" to Env.getOrDefault("SESSION_PATH", "/"),
            "encrypt" to (Config.toBoolean(Env.getOrDefault("SESSION_ENCRYPT", "false")) ?: false),
            "http_only" to (Config.toBoolean(Env.getOrDefault("SESSION_HTTP_ONLY", "true")) ?: true),
            "same_site" to Env.getOrDefault("SESSION_SAME_SITE", "Lax"),
        )
    }
}
