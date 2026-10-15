package config

import dev.kona.core.ConfigFile

object Paths : ConfigFile {
    override fun key(): String = "paths"

    override fun config(): Map<String, Any?> = mapOf(
        "langRoot" to "src/main/kotlin",
        "resources" to "src/main/resources",
        "storage" to "storage",
        "public" to "src/main/resources/public",
    )
}
