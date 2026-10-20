package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication defaults, guards, providers, and password brokers
 * ({@code config/auth.php} shape).
 */
public final class Auth implements ConfigFile {
    @Override
    public String key() {
        return "auth";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("guard", Env.getOrDefault("AUTH_GUARD", "web"));
        defaults.put("passwords", Env.getOrDefault("AUTH_PASSWORD_BROKER", "users"));

        Map<String, Object> web = new LinkedHashMap<>();
        web.put("driver", "session");
        web.put("provider", "users");

        Map<String, Object> api = new LinkedHashMap<>();
        api.put("driver", Env.getOrDefault("AUTH_API_GUARD_DRIVER", "token"));
        api.put("provider", "users");

        Map<String, Object> guards = new LinkedHashMap<>();
        guards.put("web", web);
        guards.put("api", api);

        Map<String, Object> usersProvider = new LinkedHashMap<>();
        usersProvider.put("driver", Env.getOrDefault("AUTH_PROVIDER_DRIVER", "cadence"));
        usersProvider.put("model", Env.getOrDefault("AUTH_MODEL", "app.models.User"));

        Map<String, Object> providers = new LinkedHashMap<>();
        providers.put("users", usersProvider);

        Map<String, Object> usersPasswords = new LinkedHashMap<>();
        usersPasswords.put("provider", "users");
        usersPasswords.put("table", Env.getOrDefault("AUTH_PASSWORD_RESET_TABLE", "password_reset_tokens"));
        usersPasswords.put("expire", envInt("AUTH_PASSWORD_EXPIRE", 60));
        usersPasswords.put("throttle", envInt("AUTH_PASSWORD_THROTTLE", 60));

        Map<String, Object> passwords = new LinkedHashMap<>();
        passwords.put("driver", Env.getOrDefault("AUTH_PASSWORD_DRIVER", "array"));
        passwords.put("table", Env.getOrDefault("AUTH_PASSWORD_RESET_TABLE", "password_reset_tokens"));
        passwords.put("users", usersPasswords);

        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("driver", Env.getOrDefault("AUTH_TOKEN_DRIVER", "array"));
        tokens.put("table", Env.getOrDefault("AUTH_TOKEN_TABLE", "personal_access_tokens"));

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Authentication Defaults
        |--------------------------------------------------------------------------
        |
        | This option defines the default authentication "guard" and password
        | reset "broker" for your application. You may change these values
        | as required, but they're a perfect start for most applications.
        |
        */
        values.put("defaults", defaults);

        /*
        |--------------------------------------------------------------------------
        | Authentication Guards
        |--------------------------------------------------------------------------
        |
        | Next, you may define every authentication guard for your application.
        | The "session" driver uses request session state; "token" uses the
        | personal-access token table configured below.
        |
        | Supported: "session", "token"
        |
        */
        values.put("guards", guards);

        /*
        |--------------------------------------------------------------------------
        | User Providers
        |--------------------------------------------------------------------------
        |
        | All authentication guards have a user provider, which defines how the
        | users are actually retrieved out of your database or other storage
        | system used by the application.
        |
        | Supported: "cadence" (the default Cadence user provider)
        |
        */
        values.put("providers", providers);

        /*
        |--------------------------------------------------------------------------
        | Resetting Passwords
        |--------------------------------------------------------------------------
        |
        | These configuration options specify the behavior of Kona's password
        | reset functionality, including the table utilized for token storage
        | and the user provider that is invoked to actually retrieve users.
        |
        */
        values.put("passwords", passwords);

        /*
        |--------------------------------------------------------------------------
        | Personal Access Tokens
        |--------------------------------------------------------------------------
        |
        | Configure how API / Sanctum-style personal access tokens are stored.
        |
        */
        values.put("tokens", tokens);

        /*
        |--------------------------------------------------------------------------
        | Password Confirmation Timeout
        |--------------------------------------------------------------------------
        |
        | Here you may define the number of seconds before a password confirmation
        | window expires and users are asked to re-enter their password via the
        | confirmation screen. By default, the timeout lasts for three hours.
        |
        */
        values.put("password_timeout", envInt("AUTH_PASSWORD_TIMEOUT", 10800));
        return values;
    }

    private static int envInt(String key, int fallback) {
        try {
            return Integer.parseInt(Env.getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
