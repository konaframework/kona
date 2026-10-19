package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

object Database : ConfigFile {
    override fun key(): String = "database"

    override fun config(): Map<String, Any?> = mapOf<String, Any?>(
        "default" to Env.getOrDefault("DB_CONNECTION", "main"),
        "connections" to mapOf(
            "main" to mapOf(
                "url" to Env.getOrDefault("DB_URL", "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"),
                "username" to Env.getOrDefault("DB_USERNAME", "sa"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
        ),
    )
}
