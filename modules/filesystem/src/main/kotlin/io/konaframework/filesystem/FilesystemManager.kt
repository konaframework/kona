package io.konaframework.filesystem

import io.konaframework.core.Config
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

/**
 * Resolves named disks from config.
 *
 * - `filesystems.default` — disk name (default `local`)
 * - `filesystems.disks.{name}.driver` — `local`, `s3`, `memory`/`s3-memory`, `scoped`
 * - local: `root`, `url` (`public` defaults to `storage/app/public` + `/storage`)
 * - s3: `endpoint`, `bucket`, `key`, `secret`, `region`, `url`, optional `prefix`
 * - scoped: `disk` (parent name) + `prefix`
 * - any disk: `read-only` = true wraps the adapter
 */
class FilesystemManager(
    private val config: Config,
    private val basePath: Path,
) {
    private val disks = ConcurrentHashMap<String, FilesystemAdapter>()
    private val temporaryUrlBuilders = ConcurrentHashMap<String, BiFunction<String, Long, String>>()
    private val creating = ThreadLocal.withInitial { mutableSetOf<String>() }

    fun disk(): FilesystemAdapter = disk(defaultDisk())

    fun disk(name: String): FilesystemAdapter =
        disks.getOrPut(name) { create(name) }

    fun defaultDisk(): String = config.getString("filesystems.default", "local")

    /** Replace a named disk (tests and custom wiring). */
    fun set(name: String, adapter: FilesystemAdapter) {
        disks[name] = adapter
        applyTemporaryUrlBuilder(name, adapter)
    }

    /**
     * Mount a local temp-rooted disk under [name] for tests (clears prior instance).
     */
    @JvmOverloads
    fun fake(name: String = defaultDisk()): FilesystemAdapter {
        val root = Files.createTempDirectory("kona-fs-fake-$name")
        val adapter = LocalAdapter(root, "")
        adapter.buildTemporaryUrlsUsing { url, exp -> "$url?expires=$exp" }
        set(name, adapter)
        return adapter
    }

    /**
     * Register how temporary URLs are built for [disk] (typically `UrlGenerator.signed`).
     */
    fun buildTemporaryUrlsUsing(disk: String, builder: BiFunction<String, Long, String>) {
        temporaryUrlBuilders[disk] = builder
        disks[disk]?.let { applyTemporaryUrlBuilder(disk, it) }
    }

    fun providesTemporaryUrls(disk: String = defaultDisk()): Boolean =
        temporaryUrlBuilders.containsKey(disk) || disk(disk).providesTemporaryUrls()

    /**
     * Signed temporary URL for [path] on [disk].
     * Prefers [buildTemporaryUrlsUsing], then the adapter's own builder, else [signer].
     */
    @JvmOverloads
    fun temporaryUrl(
        path: String,
        expirationSeconds: Long,
        signer: BiFunction<String, Long, String>,
        disk: String = defaultDisk(),
    ): String {
        val adapter = disk(disk)
        temporaryUrlBuilders[disk]?.let { return it.apply(adapter.url(path), expirationSeconds) }
        if (adapter.providesTemporaryUrls()) {
            return adapter.temporaryUrl(path, expirationSeconds)
        }
        return signer.apply(adapter.url(path), expirationSeconds)
    }

    /** Temporary URL using a registered builder or the adapter's own builder. */
    fun temporaryUrl(path: String, expirationSeconds: Long, disk: String): String {
        val adapter = disk(disk)
        temporaryUrlBuilders[disk]?.let { return it.apply(adapter.url(path), expirationSeconds) }
        if (adapter.providesTemporaryUrls()) {
            return adapter.temporaryUrl(path, expirationSeconds)
        }
        throw IllegalStateException("Disk [$disk] does not support creating temporary URLs.")
    }

    private fun create(name: String): FilesystemAdapter {
        val stack = creating.get()
        if (!stack.add(name)) {
            throw IllegalStateException("Circular disk reference involving [$name]")
        }
        try {
            val adapter = wrapReadOnly(name, resolveDriver(name))
            applyTemporaryUrlBuilder(name, adapter)
            return adapter
        } finally {
            stack.remove(name)
        }
    }

    private fun resolveDriver(name: String): FilesystemAdapter {
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
                val root = config.getString("filesystems.disks.$name.root", defaultRoot)
                val url = config.getString("filesystems.disks.$name.url", defaultUrl)
                LocalAdapter(resolveRoot(root), url)
            }
            "memory", "s3-memory" -> {
                val bucket = config.getString("filesystems.disks.$name.bucket", "kona")
                val url = config.getString("filesystems.disks.$name.url", "")
                val prefix = config.getString("filesystems.disks.$name.prefix", "")
                S3Adapter(MemoryS3ClientAdapter(), bucket, url, prefix)
            }
            "s3" -> {
                val endpoint = config.getString("filesystems.disks.$name.endpoint")
                    ?: throw IllegalStateException("S3 disk [$name] requires endpoint")
                val bucket = config.getString("filesystems.disks.$name.bucket", "kona")
                val key = config.getString("filesystems.disks.$name.key", "")
                val secret = config.getString("filesystems.disks.$name.secret", "")
                val region = config.getString("filesystems.disks.$name.region", "us-east-1")
                val url = config.getString("filesystems.disks.$name.url", "")
                val prefix = config.getString("filesystems.disks.$name.prefix", "")
                S3Adapter(
                    HttpS3Client(endpoint, bucket, key, secret, region),
                    bucket,
                    url,
                    prefix,
                )
            }
            "scoped" -> {
                val parent = config.getString("filesystems.disks.$name.disk")
                    ?: throw IllegalStateException("Scoped disk [$name] requires disk")
                val prefix = config.getString("filesystems.disks.$name.prefix", "")
                ScopedAdapter(disk(parent), prefix)
            }
            else -> throw IllegalArgumentException("Disk [$name] is not defined.")
        }
    }

    private fun wrapReadOnly(name: String, adapter: FilesystemAdapter): FilesystemAdapter {
        val readOnly = config.getBoolean("filesystems.disks.$name.read-only", false) ||
            config.getBoolean("filesystems.disks.$name.read_only", false)
        return if (readOnly) ReadOnlyAdapter(adapter) else adapter
    }

    private fun applyTemporaryUrlBuilder(name: String, adapter: FilesystemAdapter) {
        val builder = temporaryUrlBuilders[name] ?: return
        when (adapter) {
            is LocalAdapter -> adapter.buildTemporaryUrlsUsing(builder)
            is S3Adapter -> adapter.buildTemporaryUrlsUsing(builder)
            else -> Unit
        }
    }

    private fun resolveRoot(root: String): Path {
        val path = Path.of(root)
        return if (path.isAbsolute) path else basePath.resolve(path).normalize()
    }
}
