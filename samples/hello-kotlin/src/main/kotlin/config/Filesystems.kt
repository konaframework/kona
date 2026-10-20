package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Filesystem disks (`config/filesystems.php` shape).
 *
 * `default` selects the disk used by `Storage`. `links` is the map
 * `./grind storage:link` creates (public URL prefix → on-disk root).
 */
object Filesystems : ConfigFile {
    override fun key(): String = "filesystems"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Default Filesystem Disk
        |--------------------------------------------------------------------------
        |
        | Here you may specify the default filesystem disk that should be used
        | by the framework. The "local" disk, as well as a public disk that
        | is meant to be web-accessible, are configured below.
        |
        */
        "default" to Env.getOrDefault("FILESYSTEM_DISK", "local"),

        /*
        |--------------------------------------------------------------------------
        | Filesystem Disks
        |--------------------------------------------------------------------------
        |
        | Below you may configure as many filesystem disks as necessary, and you
        | may even configure multiple disks for the same driver. Examples for
        | most supported storage drivers are configured here for reference.
        |
        | Supported drivers: "local"
        |
        */
        "disks" to mapOf(
            "local" to mapOf(
                "driver" to "local",
                "root" to "storage/app",
            ),
            "public" to mapOf(
                "driver" to "local",
                "root" to "storage/app/public",
                "url" to "/storage",
                "visibility" to "public",
            ),
        ),

        /*
        |--------------------------------------------------------------------------
        | Symbolic Links
        |--------------------------------------------------------------------------
        |
        | Here you may configure the symbolic links that will be created when the
        | `storage:link` grind command is executed. The array keys should be
        | the locations of the links and the values should be their targets.
        |
        */
        "links" to mapOf(
            "src/main/resources/public/storage" to "storage/app/public",
        ),
    )
}
