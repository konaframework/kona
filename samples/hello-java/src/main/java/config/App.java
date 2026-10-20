package config;

import org.konaframework.core.Config;
import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application configuration ({@code config/app.php} shape).
 *
 * <p>Values are read from the environment so each machine can differ without
 * changing this file. Prefer {@code app.getConfig().getString("app.name")} in
 * application code rather than calling {@link Env} directly.
 */
public final class App implements ConfigFile {
    @Override
    public String key() {
        return "app";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> values = new LinkedHashMap<>();

        /*
        |--------------------------------------------------------------------------
        | Application Name
        |--------------------------------------------------------------------------
        |
        | This value is the name of your application, which will be used when the
        | framework needs to place the application's name in a notification or
        | other UI elements where an application name needs to be displayed.
        |
        */
        values.put("name", Env.getOrDefault("APP_NAME", "hello-java"));

        /*
        |--------------------------------------------------------------------------
        | Application Environment
        |--------------------------------------------------------------------------
        |
        | This value determines the "environment" your application is currently
        | running in. This may determine how you prefer to configure various
        | services the application utilizes. Set this in your ".env" file.
        |
        */
        values.put("env", Env.getOrDefault("APP_ENV", "local"));

        /*
        |--------------------------------------------------------------------------
        | Application Debug Mode
        |--------------------------------------------------------------------------
        |
        | When your application is in debug mode, detailed error messages with
        | stack traces will be shown on every error that occurs within your
        | application. If disabled, a simple generic error page is shown.
        |
        */
        values.put("debug", Config.toBoolean(Env.getOrDefault("APP_DEBUG", "true")));

        /*
        |--------------------------------------------------------------------------
        | Application URL
        |--------------------------------------------------------------------------
        |
        | This URL is used by grind and the URL generator when building absolute
        | links. You should set this to the root of the application.
        |
        */
        values.put("url", Env.getOrDefault("APP_URL", "http://127.0.0.1:4000"));

        /*
        |--------------------------------------------------------------------------
        | Application Timezone
        |--------------------------------------------------------------------------
        |
        | Here you may specify the default timezone for your application, which
        | will be used by date helpers (KonaDate). The timezone is set to "UTC"
        | by default as it is suitable for most use cases.
        |
        */
        values.put("timezone", Env.getOrDefault("APP_TIMEZONE", "UTC"));

        /*
        |--------------------------------------------------------------------------
        | Application Locale Configuration
        |--------------------------------------------------------------------------
        |
        | The application locale determines the default locale that will be used
        | by Kona's translation / localization methods. This option can be
        | set to any locale for which you plan to have translation strings.
        |
        */
        values.put("locale", Env.getOrDefault("APP_LOCALE", "en"));
        values.put("fallback_locale", Env.getOrDefault("APP_FALLBACK_LOCALE", "en"));
        values.put("faker_locale", Env.getOrDefault("APP_FAKER_LOCALE", "en_US"));

        /*
        |--------------------------------------------------------------------------
        | Encryption Key
        |--------------------------------------------------------------------------
        |
        | This key is utilized by Kona's encryption services and should be set
        | to a random 32-byte value encoded as base64:… (`./grind key:generate`).
        | You should do this prior to deploying the application.
        |
        | Cipher is AES-256-GCM (payload shape matches Laravel's encrypter JSON).
        | previous_keys are tried on decrypt after a key rotation.
        |
        */
        values.put("cipher", Env.getOrDefault("APP_CIPHER", "AES-256-GCM"));
        values.put("key", Env.get("APP_KEY", null));
        values.put("previous_keys", Env.csv("APP_PREVIOUS_KEYS"));

        /*
        |--------------------------------------------------------------------------
        | Maintenance Mode Driver
        |--------------------------------------------------------------------------
        |
        | These configuration options determine the driver used to determine and
        | manage Kona's "maintenance mode" status. The "file" driver writes
        | storage/framework/down (`./grind down` / `./grind up`). The "cache"
        | driver will allow maintenance mode to be controlled across multiple
        | machines when a cache store is configured.
        |
        | Supported drivers: "file", "cache"
        |
        */
        Map<String, Object> maintenance = new LinkedHashMap<>();
        maintenance.put("driver", Env.getOrDefault("APP_MAINTENANCE_DRIVER", "file"));
        maintenance.put("store", Env.getOrDefault("APP_MAINTENANCE_STORE", "database"));
        values.put("maintenance", maintenance);

        return values;
    }
}
