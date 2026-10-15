package config

import dev.kona.core.ConfigFile

object Http : ConfigFile {
    override fun key(): String = "http"

    override fun config(): Map<String, Any?> = mapOf<String, Any?>(
        "trusted_proxies" to listOf("*"),
        "trusted_hosts" to emptyList<String>(),
    )
}
