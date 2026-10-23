package org.konaframework.core

import java.nio.file.Path

/***********************************************************************************************************************
 * Framework entry facade.
 * -----------------------------------------------------------------------------------------------------------------------
 * This is the main entry point for starting a Kona application. It provides methods to create and start an [Application] instance.
 *
 * Usage:
 * ```kotlin
 * Kona.create(args)
 *     .configs(config.App, config.Database)
 *     .providers(HttpServiceProvider())
 *     .configure { it.set("app.debug", true) }
 *     .boot()
 * ```
 *
 * `create` loads `.env` and seeds `app.*` defaults; registered [ConfigFile] modules and
 * `configure` callbacks merge next (see [Application.Builder.build]).
 */
object Kona {
    @Volatile
    private var app: Application? = null

    @JvmStatic
    fun create(args: Array<String>): Application.Builder = create(resolveBasePath(args))

    @JvmStatic
    fun create(basePath: Path): Application.Builder {
        val builder = Application.Builder(basePath.toAbsolutePath().normalize())
        builder.bootstrappers.add(::bootstrap)
        builder.onBooted.add { app = it }
        return builder
    }

    @JvmStatic
    fun create(): Application.Builder = create(emptyArray())

    @JvmStatic
    fun start(args: Array<String>): Application = create(args).boot()

    @JvmStatic
    fun start(basePath: Path): Application = create(basePath).boot()

    @JvmStatic
    fun app(): Application =
        app ?: throw IllegalStateException("Kona has not been started. Call Kona.start(...) first.")

    @JvmStatic
    fun reset() {
        Log.unbind()
        app = null
        Env.clear()
    }

    private fun bootstrap(application: Application) {
        Env.load(application.path(".env"))
        val config = application.config
        config.set("app.name", Env.getOrDefault("APP_NAME", "Kona"))
        config.set("app.env", Env.getOrDefault("APP_ENV", "local"))
        config.set("app.debug", Config.toBoolean(Env.getOrDefault("APP_DEBUG", "false")) ?: false)
        config.set("app.url", Env.getOrDefault("APP_URL", "http://127.0.0.1:4000"))
        config.set("app.timezone", Env.getOrDefault("APP_TIMEZONE", "UTC"))
        config.set("app.locale", Env.getOrDefault("APP_LOCALE", "en"))
        config.set("app.fallback_locale", Env.getOrDefault("APP_FALLBACK_LOCALE", "en"))
        config.set("app.faker_locale", Env.getOrDefault("APP_FAKER_LOCALE", "en_US"))
        config.set("app.cipher", Env.getOrDefault("APP_CIPHER", "AES-256-GCM"))
        Env.get("APP_KEY")?.let { config.set("app.key", it) }
        config.set("app.previous_keys", Env.csv("APP_PREVIOUS_KEYS"))
        config.set("app.maintenance.driver", Env.getOrDefault("APP_MAINTENANCE_DRIVER", "file"))
        config.set("app.maintenance.store", Env.getOrDefault("APP_MAINTENANCE_STORE", "database"))
        config.set(
            "logging.to_file",
            Config.toBoolean(Env.getOrDefault("LOG_TO_FILE", "true")) ?: true,
        )
        config.set("logging.file", Env.getOrDefault("LOG_FILE", FileLogging.DEFAULT_FILE_NAME))
        config.set("logging.level", Env.getOrDefault("LOG_LEVEL", "info"))
        Env.get("LOG_REQUESTS")?.let { raw ->
            Config.toBoolean(raw)?.let { config.set("logging.requests", it) }
        }
        LogManager.seedDefaults(config)
        application.refreshPathLayout()
    }

    /**
     * Resolves the base path for the application.
     *
     * The base path can be specified via the `--base` command-line argument. If not provided, the current working directory is used.
     *
     * @param args The command-line arguments.
     * @return The resolved base path as a [Path].
     */
    private fun resolveBasePath(args: Array<String>): Path {
        val fromArg = args.firstOrNull { it.startsWith("--base=") }
            ?.removePrefix("--base=")
        if (fromArg != null) {
            return Path.of(fromArg).toAbsolutePath().normalize()
        }
        return Path.of("").toAbsolutePath().normalize()
    }
}
