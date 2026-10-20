package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Authentication defaults, guards, providers, and password brokers
 * (`config/auth.php` shape).
 */
object Auth : ConfigFile {
    override fun key(): String = "auth"

    override fun config(): Map<String, Any?> = mapOf(
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
        "defaults" to mapOf(
            "guard" to Env.getOrDefault("AUTH_GUARD", "web"),
            "passwords" to Env.getOrDefault("AUTH_PASSWORD_BROKER", "users"),
        ),

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
        "guards" to mapOf(
            "web" to mapOf(
                "driver" to "session",
                "provider" to "users",
            ),
            "api" to mapOf(
                "driver" to Env.getOrDefault("AUTH_API_GUARD_DRIVER", "token"),
                "provider" to "users",
            ),
        ),

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
        "providers" to mapOf(
            "users" to mapOf(
                "driver" to Env.getOrDefault("AUTH_PROVIDER_DRIVER", "cadence"),
                "model" to Env.getOrDefault("AUTH_MODEL", "app.models.User"),
            ),
        ),

        /*
        |--------------------------------------------------------------------------
        | Resetting Passwords
        |--------------------------------------------------------------------------
        |
        | These configuration options specify the behavior of Kona's password
        | reset functionality, including the table utilized for token storage
        | and the user provider that is invoked to actually retrieve users.
        |
        | The expiry time is the number of minutes that each reset token will
        | be considered valid. This security feature keeps tokens short-lived
        | so they have less time to be guessed. You may change this as needed.
        |
        | The throttle setting is the number of seconds a user must wait before
        | generating more password reset tokens. This prevents the user from
        | quickly generating a very large amount of password reset tokens.
        |
        */
        "passwords" to mapOf(
            "driver" to Env.getOrDefault("AUTH_PASSWORD_DRIVER", "array"),
            "table" to Env.getOrDefault("AUTH_PASSWORD_RESET_TABLE", "password_reset_tokens"),
            "users" to mapOf(
                "provider" to "users",
                "table" to Env.getOrDefault("AUTH_PASSWORD_RESET_TABLE", "password_reset_tokens"),
                "expire" to (Env.getOrDefault("AUTH_PASSWORD_EXPIRE", "60").toIntOrNull() ?: 60),
                "throttle" to (Env.getOrDefault("AUTH_PASSWORD_THROTTLE", "60").toIntOrNull() ?: 60),
            ),
        ),

        /*
        |--------------------------------------------------------------------------
        | Personal Access Tokens
        |--------------------------------------------------------------------------
        |
        | Configure how API / Sanctum-style personal access tokens are stored.
        |
        */
        "tokens" to mapOf(
            "driver" to Env.getOrDefault("AUTH_TOKEN_DRIVER", "array"),
            "table" to Env.getOrDefault("AUTH_TOKEN_TABLE", "personal_access_tokens"),
        ),

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
        "password_timeout" to (Env.getOrDefault("AUTH_PASSWORD_TIMEOUT", "10800").toIntOrNull() ?: 10800),
    )
}
