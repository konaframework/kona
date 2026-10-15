package config

import dev.kona.core.ConfigFile
import dev.kona.core.Env

object Filesystems : ConfigFile {
    override fun key(): String = "filesystems"

    override fun config(): Map<String, Any?> = mapOf(
        "default" to Env.getOrDefault("FILESYSTEM_DISK", "local"),
        "disks" to mapOf(
            "local" to mapOf(
                "driver" to "local",
                "root" to "storage/app",
            ),
            "public" to mapOf(
                "driver" to "local",
                "root" to "storage/app/public",
                "url" to "/storage",
            ),
        ),
    )
}
