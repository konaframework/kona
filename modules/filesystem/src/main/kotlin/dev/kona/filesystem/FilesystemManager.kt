package dev.kona.filesystem

import dev.kona.core.Config
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

/**
 * Resolves named disks from config (Laravel Storage / FilesystemManager).
 *
 * - `filesystems.default` — disk name (default `local`)
 * - `filesystems.disks.{name}.driver` — `local`, `s3`, `memory`/`s3-memory`
 * - local: `root`, `url` (`public` defaults to `storage/app/public` + `/storage`)
 * - s3: `endpoint`, `bucket`, `key`, `secret`, `region`, `url`
 */
class FilesystemManager(
    private val config: Config,
    private val basePath: Path,
) {
    private val disks = ConcurrentHashMap<String, FilesystemAdapter>()

    fun disk(): FilesystemAdapter = disk(defaultDisk())

    fun disk(name: String): FilesystemAdapter =
        disks.getOrPut(name) { create(name) }

    fun defaultDisk(): String = config.getString("filesystems.default", "local")

    /**
     * Signed temporary URL for [path] on [disk] (Laravel `Storage::temporaryUrl` shape).
     * [signer] receives `(diskUrl, expirationSeconds)` and returns the signed URL
     * (typically `UrlGenerator.signed`).
     */
    @JvmOverloads
    fun temporaryUrl(
        path: String,
        expirationSeconds: Long,
        signer: BiFunction<String, Long, String>,
        disk: String = defaultDisk(),
    ): String = signer.apply(disk(disk).url(path), expirationSeconds)

    private fun create(name: String): FilesystemAdapter {
        val configured = config.getString("filesystems.disks.$name.driver")
        val driver = when {
            configured != null -> configured
            name.equals("local", true) || name.equals("public", true) || name.equals("s3", true) ||
                name.equals("memory", true) || name.equals("s3-memory", true) ->
                if (name.equals("public", true)) "local" else name
            else -> "local"
        }
        return when (driver.lowercase()) {
            "local" -> {
                val defaultRoot = if (name.equals("public", true)) {
                    basePath.resolve("storage/app/public").toString()
                } else {
                    basePath.resolve("storage/app").toString()
                }
                val defaultUrl = if (name.equals("public", true)) "/storage" else ""
                val root = config.getString("filesystems.disks.$name.root") ?: defaultRoot
                val url = config.getString("filesystems.disks.$name.url") ?: defaultUrl
                LocalAdapter(resolveRoot(root), url)
            }
            "memory", "s3-memory" -> {
                val bucket = config.getString("filesystems.disks.$name.bucket", "kona")
                val url = config.getString("filesystems.disks.$name.url", "")
                S3Adapter(MemoryS3ClientAdapter(), bucket, url)
            }
            "s3" -> {
                val endpoint = config.getString("filesystems.disks.$name.endpoint")
                    ?: throw IllegalStateException("S3 disk [$name] requires endpoint")
                val bucket = config.getString("filesystems.disks.$name.bucket", "kona")
                val key = config.getString("filesystems.disks.$name.key", "")
                val secret = config.getString("filesystems.disks.$name.secret", "")
                val region = config.getString("filesystems.disks.$name.region", "us-east-1")
                val url = config.getString("filesystems.disks.$name.url", "")
                S3Adapter(
                    HttpS3Client(endpoint, bucket, key, secret, region),
                    bucket,
                    url,
                )
            }
            else -> throw IllegalArgumentException("Disk [$name] is not defined.")
        }
    }

    private fun resolveRoot(root: String): Path {
        val path = Path.of(root)
        return if (path.isAbsolute) path else basePath.resolve(path).normalize()
    }
}
