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
        | Supported drivers: "local", "s3", "memory"
        |
        */
        "disks" to mapOf(
            "local" to mapOf(
                "driver" to "local",
                "root" to "storage/app",
                "throw" to false,
            ),
            "public" to mapOf(
                "driver" to "local",
                "root" to "storage/app/public",
                "url" to "/storage",
                "visibility" to "public",
                "throw" to false,
            ),
            "s3" to mapOf(
                "driver" to "s3",
                "key" to Env.get("AWS_ACCESS_KEY_ID"),
                "secret" to Env.get("AWS_SECRET_ACCESS_KEY"),
                "region" to Env.getOrDefault("AWS_DEFAULT_REGION", "us-east-1"),
                "bucket" to Env.get("AWS_BUCKET"),
                "url" to Env.get("AWS_URL"),
                "endpoint" to Env.get("AWS_ENDPOINT"),
                "prefix" to Env.getOrDefault("AWS_PREFIX", ""),
                "throw" to false,
            ),
            "memory" to mapOf(
                "driver" to "memory",
                "bucket" to Env.getOrDefault("FILESYSTEM_MEMORY_BUCKET", "kona"),
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
            "public/storage" to "storage/app/public",
        ),
    )
}
