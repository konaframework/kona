package config

import org.konaframework.core.Config
import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * HTTP session configuration (`config/session.php` shape).
 *
 * Supported drivers: {@code cookie}, {@code file}, {@code redis}, {@code array}/{@code memory}.
 */
object Session : ConfigFile {
    override fun key(): String = "session"

    override fun config(): Map<String, Any?> {
        val lifetime = Env.getOrDefault("SESSION_LIFETIME", "120").toIntOrNull() ?: 120
        return mapOf(
            /*
            |--------------------------------------------------------------------------
            | Default Session Driver
            |--------------------------------------------------------------------------
            |
            | This option determines the default session driver that is utilized for
            | incoming requests. Cookie storage is a good zero-dependency default;
            | file and redis persist across processes.
            |
            | Supported: "cookie", "file", "redis", "array", "memory"
            |
            */
            "driver" to Env.getOrDefault("SESSION_DRIVER", "cookie"),

            /*
            |--------------------------------------------------------------------------
            | Session Lifetime
            |--------------------------------------------------------------------------
            |
            | Here you may specify the number of minutes that you wish the session
            | to be allowed to remain idle before it expires. If you want them
            | to expire immediately when the browser is closed then you may
            | indicate that via the expire_on_close configuration option.
            |
            */
            "lifetime" to lifetime,

            "expire_on_close" to (Config.toBoolean(Env.getOrDefault("SESSION_EXPIRE_ON_CLOSE", "false")) ?: false),

            /*
            |--------------------------------------------------------------------------
            | Session Encryption
            |--------------------------------------------------------------------------
            |
            | This option allows you to easily specify that all of your session data
            | should be encrypted before it's stored. Requires a valid APP_KEY.
            |
            */
            "encrypt" to (Config.toBoolean(Env.getOrDefault("SESSION_ENCRYPT", "false")) ?: false),

            /*
            |--------------------------------------------------------------------------
            | Session File Location
            |--------------------------------------------------------------------------
            |
            | When utilizing the "file" session driver, the session files are placed
            | on disk. The default storage location is defined here; however, you
            | are free to provide another location where they should be stored.
            |
            */
            "files" to Env.getOrDefault("SESSION_FILES", "storage/framework/sessions"),

            /*
            |--------------------------------------------------------------------------
            | Session Cookie
            |--------------------------------------------------------------------------
            |
            | Here you may change the name, path, domain, and security flags of the
            | session cookie that is created by the framework for every request.
            |
            */
            "cookie" to Env.getOrDefault("SESSION_COOKIE", "kona_session"),

            "path" to Env.getOrDefault("SESSION_PATH", "/"),

            "domain" to Env.get("SESSION_DOMAIN"),

            "secure" to (Config.toBoolean(Env.getOrDefault("SESSION_SECURE_COOKIE", "false")) ?: false),

            "http_only" to (Config.toBoolean(Env.getOrDefault("SESSION_HTTP_ONLY", "true")) ?: true),

            "same_site" to Env.getOrDefault("SESSION_SAME_SITE", "Lax"),
        )
    }
}
