package org.konaframework.core

import java.nio.file.Path

/**
 * Framework entry facade.
 *
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
        Env.get("APP_KEY")?.let { config.set("app.key", it) }
        config.set(
            "logging.to_file",
            Config.toBoolean(Env.getOrDefault("LOG_TO_FILE", "true")) ?: true,
        )
        config.set("logging.file", Env.getOrDefault("LOG_FILE", FileLogging.DEFAULT_FILE_NAME))
        config.set("logging.level", Env.getOrDefault("LOG_LEVEL", "info"))
        LogManager.seedDefaults(config)
        application.refreshPathLayout()
    }

    private fun resolveBasePath(args: Array<String>): Path {
        val fromArg = args.firstOrNull { it.startsWith("--base=") }
            ?.removePrefix("--base=")
        if (fromArg != null) {
            return Path.of(fromArg).toAbsolutePath().normalize()
        }
        return Path.of("").toAbsolutePath().normalize()
    }
}
