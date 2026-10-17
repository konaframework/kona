package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

object Database : ConfigFile {
    override fun key(): String = "database"

    override fun config(): Map<String, Any?> = mapOf<String, Any?>(
        "default" to Env.getOrDefault("DB_CONNECTION", "main"),
        "connections" to mapOf(
            "main" to mapOf(
                "url" to Env.getOrDefault("DB_URL", "jdbc:h2:mem:kona;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"),
                "username" to Env.getOrDefault("DB_USERNAME", "sa"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
        ),
    )
}
