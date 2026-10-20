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
            | Session Database Connection
            |--------------------------------------------------------------------------
            |
            | When using the "database" or "redis" session drivers, you may specify a
            | connection that should be used to manage these sessions. This should
            | correspond to a connection in your Redis configuration.
            |
            */
            "connection" to Env.get("SESSION_CONNECTION"),

            "connection_prefix" to Env.getOrDefault("SESSION_CONNECTION_PREFIX", "kona_session:"),

            /*
            |--------------------------------------------------------------------------
            | Session Cache Store
            |--------------------------------------------------------------------------
            |
            | When using a cache-backed session driver, you may define the cache store
            | that should be used to store the session data between requests.
            |
            */
            "store" to Env.get("SESSION_STORE"),

            /*
            |--------------------------------------------------------------------------
            | Session Cache Table
            |--------------------------------------------------------------------------
            |
            | When using the "database" session driver, you may specify the table we
            | should use to manage the sessions. Of course, a sensible default is
            | provided for you; however, you're welcome to change this as needed.
            |
            */
            "table" to Env.getOrDefault("SESSION_TABLE", "sessions"),

            /*
            |--------------------------------------------------------------------------
            | Session Sweeping Lottery
            |--------------------------------------------------------------------------
            |
            | Some session drivers must manually sweep their storage location to get
            | rid of old sessions from storage. Here are the chances that it will
            | happen on a given request. By default, the odds are 2 out of 100.
            |
            */
            "lottery" to listOf(
                Env.getOrDefault("SESSION_LOTTERY_ODDS", "2").toIntOrNull() ?: 2,
                Env.getOrDefault("SESSION_LOTTERY_MAX", "100").toIntOrNull() ?: 100,
            ),

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

            "partitioned" to (Config.toBoolean(Env.getOrDefault("SESSION_PARTITIONED_COOKIE", "false")) ?: false),
        )
    }
}
