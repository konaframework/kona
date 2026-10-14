package dev.kona.core

import java.nio.file.Path

/**
 * Framework entry facade — `Kona.start(args)`.
 */
object Kona {
    @Volatile
    private var app: Application? = null

    @JvmStatic
    fun start(args: Array<String>): Application {
        val base = resolveBasePath(args)
        Env.load(base.resolve(".env"))
        val application = Application(base)
        application.config.set("app.name", Env.getOrDefault("APP_NAME", "Kona"))
        application.config.set("app.env", Env.getOrDefault("APP_ENV", "local"))
        application.config.set("app.debug", Env.getOrDefault("APP_DEBUG", "true").toBoolean())
        application.boot()
        app = application
        return application
    }

    @JvmStatic
    fun start(basePath: Path): Application = start(arrayOf("--base=$basePath"))

    @JvmStatic
    fun app(): Application =
        app ?: throw IllegalStateException("Kona has not been started. Call Kona.start(...) first.")

    @JvmStatic
    fun reset() {
        app = null
        Env.clear()
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
