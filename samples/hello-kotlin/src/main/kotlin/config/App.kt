package config

import dev.kona.core.Config
import dev.kona.core.ConfigFile
import dev.kona.core.Env

object App : ConfigFile {
    override fun key(): String = "app"

    override fun config(): Map<String, Any?> = mapOf<String, Any?>(
        "name" to Env.getOrDefault("APP_NAME", "hello-kotlin"),
        "env" to Env.getOrDefault("APP_ENV", "local"),
        "debug" to (Config.toBoolean(Env.getOrDefault("APP_DEBUG", "true")) ?: true),
        "url" to Env.getOrDefault("APP_URL", "http://127.0.0.1:4000"),
        "key" to Env.get("APP_KEY"),
    )
}
