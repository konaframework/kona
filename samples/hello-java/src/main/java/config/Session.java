package config;

import org.konaframework.core.Config;
import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP session configuration ({@code config/session.php} shape).
 *
 * <p>Supported drivers: {@code cookie}, {@code file}, {@code redis}, {@code array}/{@code memory}.
 */
public final class Session implements ConfigFile {
    @Override
    public String key() {
        return "session";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> values = new LinkedHashMap<>();

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
        values.put("driver", Env.getOrDefault("SESSION_DRIVER", "cookie"));

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
        try {
            values.put("lifetime", Integer.parseInt(Env.getOrDefault("SESSION_LIFETIME", "120")));
        } catch (NumberFormatException e) {
            values.put("lifetime", 120);
        }
        values.put("expire_on_close", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("SESSION_EXPIRE_ON_CLOSE", "false"))));

        /*
        |--------------------------------------------------------------------------
        | Session Encryption
        |--------------------------------------------------------------------------
        |
        | This option allows you to easily specify that all of your session data
        | should be encrypted before it's stored. Requires a valid APP_KEY.
        |
        */
        values.put("encrypt", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("SESSION_ENCRYPT", "false"))));

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
        values.put("files", Env.getOrDefault("SESSION_FILES", "storage/framework/sessions"));

        /*
        |--------------------------------------------------------------------------
        | Session Cookie
        |--------------------------------------------------------------------------
        |
        | Here you may change the name, path, domain, and security flags of the
        | session cookie that is created by the framework for every request.
        |
        */
        values.put("cookie", Env.getOrDefault("SESSION_COOKIE", "kona_session"));
        values.put("path", Env.getOrDefault("SESSION_PATH", "/"));
        values.put("domain", Env.get("SESSION_DOMAIN", null));
        values.put("secure", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("SESSION_SECURE_COOKIE", "false"))));
        values.put("http_only", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("SESSION_HTTP_ONLY", "true"))));
        values.put("same_site", Env.getOrDefault("SESSION_SAME_SITE", "Lax"));
        return values;
    }
}
