package dev.kona.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Framework entry facade.
 *
 * ```kotlin
 * Kona.create(args).providers(HttpServiceProvider()).configure { it.set("app.debug", true) }.boot()
 * ```
 *
 * `create` loads `.env` and config from `src/main/resources/config` (fallback: top-level `config/`)
 * and seeds `app.*` defaults before user configurers run; every provider registers before any
 * provider boots.
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
        app = null
        Env.clear()
    }

    private fun bootstrap(application: Application) {
        Env.load(application.path(".env"))
        val config = application.config
        config.set("app.name", Env.getOrDefault("APP_NAME", "Kona"))
        config.set("app.env", Env.getOrDefault("APP_ENV", "local"))
        config.set("app.debug", Config.toBoolean(Env.getOrDefault("APP_DEBUG", "false")) ?: false)
        config.set("app.url", Env.getOrDefault("APP_URL", "http://127.0.0.1:8080"))
        Env.get("APP_KEY")?.let { config.set("app.key", it) }
        val primary = application.configPath()
        val legacy = application.path("config")
        when {
            Files.isDirectory(primary) -> config.loadDirectory(primary)
            Files.isDirectory(legacy) -> config.loadDirectory(legacy)
        }
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
