package config

import org.konaframework.core.Config
import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Log channels (`config/logging.php` shape).
 *
 * Supported drivers: `stack`, `single`, `daily`, `stderr`, `stdout`, `console`,
 * `syslog`, `errorlog`, `null`.
 */
object Logging : ConfigFile {
    override fun key(): String = "logging"

    override fun config(): Map<String, Any?> {
        val level = Env.getOrDefault("LOG_LEVEL", "debug")
        val path = Env.getOrDefault("LOG_FILE", "storage/logs/kona.log")
        return mapOf(
            /*
            |--------------------------------------------------------------------------
            | Default Log Channel
            |--------------------------------------------------------------------------
            |
            | This option defines the default log channel that is utilized to write
            | messages to your logs. The value provided here should match one of
            | the channels present in the list of "channels" configured below.
            |
            */
            "default" to Env.getOrDefault("LOG_CHANNEL", "stack"),

            "file" to path,
            "level" to level,
            "to_file" to (Config.toBoolean(Env.getOrDefault("LOG_TO_FILE", "true")) ?: true),

            /*
            |--------------------------------------------------------------------------
            | Deprecations Log Channel
            |--------------------------------------------------------------------------
            |
            | This option controls the log channel that should be used to log
            | warnings regarding deprecated library features.
            |
            */
            "deprecations" to mapOf(
                "channel" to Env.getOrDefault("LOG_DEPRECATIONS_CHANNEL", "null"),
                "trace" to false,
            ),

            /*
            |--------------------------------------------------------------------------
            | Log Channels
            |--------------------------------------------------------------------------
            |
            | Here you may configure the log channels for your application.
            |
            | Available drivers: "stack", "single", "daily", "stderr", "stdout",
            |                    "console", "syslog", "errorlog", "null"
            |
            */
            "channels" to mapOf(
                "stack" to mapOf(
                    "driver" to "stack",
                    "channels" to Env.getOrDefault("LOG_STACK", "single"),
                    "ignore_exceptions" to false,
                ),
                "single" to mapOf(
                    "driver" to "single",
                    "path" to path,
                    "level" to level,
                ),
                "daily" to mapOf(
                    "driver" to "daily",
                    "path" to path,
                    "level" to level,
                    "days" to (Env.getOrDefault("LOG_DAILY_DAYS", "14").toIntOrNull() ?: 14),
                ),
                "stderr" to mapOf(
                    "driver" to "stderr",
                    "level" to level,
                ),
                "stdout" to mapOf(
                    "driver" to "stdout",
                    "level" to level,
                ),
                "console" to mapOf(
                    "driver" to "console",
                    "level" to level,
                ),
                "syslog" to mapOf(
                    "driver" to "syslog",
                    "level" to level,
                    "host" to Env.getOrDefault("LOG_SYSLOG_HOST", "localhost"),
                    "facility" to Env.getOrDefault("LOG_SYSLOG_FACILITY", "USER"),
                ),
                "errorlog" to mapOf(
                    "driver" to "errorlog",
                    "level" to level,
                ),
                "null" to mapOf(
                    "driver" to "null",
                ),
                "emergency" to mapOf(
                    "path" to path,
                ),
            ),
        )
    }
}
