package org.konaframework.filesystem

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.BiFunction
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class LocalAdapter(
    private val root: Path,
    private val urlPrefix: String,
) : FilesystemAdapter {
    constructor(root: Path) : this(root, "")

    @Volatile
    private var temporaryUrlBuilder: BiFunction<String, Long, String>? = null

    private val visibilityOverrides = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        Files.createDirectories(root)
    }

    fun buildTemporaryUrlsUsing(builder: BiFunction<String, Long, String>) {
        temporaryUrlBuilder = builder
    }

    override fun put(path: String, contents: ByteArray) {
        val target = resolve(path)
        Files.createDirectories(target.parent)
        Files.write(target, contents)
    }

    override fun put(path: String, contents: String) {
        put(path, contents.toByteArray(Charsets.UTF_8))
    }

    override fun get(path: String): ByteArray? {
        val target = resolve(path)
        if (!Files.isRegularFile(target)) return null
        return Files.readAllBytes(target)
    }

    override fun getString(path: String): String? {
        val bytes = get(path) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    override fun exists(path: String): Boolean = resolve(path).exists()

    override fun missing(path: String): Boolean = !exists(path)

    override fun delete(path: String): Boolean {
        val target = resolve(path)
        return if (Files.isRegularFile(target)) Files.deleteIfExists(target) else false
    }

    override fun copy(from: String, to: String): Boolean {
        val source = resolve(from)
        if (!Files.isRegularFile(source)) return false
        val target = resolve(to)
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    override fun move(from: String, to: String): Boolean {
        val source = resolve(from)
        if (!Files.isRegularFile(source)) return false
        val target = resolve(to)
        Files.createDirectories(target.parent)
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    override fun size(path: String): Long = Files.size(resolve(path))

    override fun lastModified(path: String): Long = Files.getLastModifiedTime(resolve(path)).toMillis()

    override fun mimeType(path: String): String? {
        val target = resolve(path)
        if (!Files.isRegularFile(target)) return null
        return AdapterSupport.mimeFromFile(target, path)
    }

    override fun checksum(path: String, algorithm: String): String? {
        val bytes = get(path) ?: return null
        return AdapterSupport.checksum(bytes, algorithm)
    }

    override fun files(directory: String): List<String> {
        val dir = resolve(directory)
        if (!dir.isDirectory()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { it.isRegularFile() }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    override fun allFiles(directory: String): List<String> {
        val dir = resolve(directory)
        if (!dir.isDirectory()) return emptyList()
        val base = if (directory.isBlank()) "" else directory.replace('\\', '/').trim('/') + "/"
        return Files.walk(dir).use { walk ->
            walk.filter { it.isRegularFile() }
                .map { base + dir.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
    }

    override fun directories(directory: String): List<String> {
        val dir = resolve(directory)
        if (!dir.isDirectory()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { it.isDirectory() }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    override fun allDirectories(directory: String): List<String> {
        val dir = resolve(directory)
        if (!dir.isDirectory()) return emptyList()
        val base = if (directory.isBlank()) "" else directory.replace('\\', '/').trim('/') + "/"
        return Files.walk(dir).use { walk ->
            walk.filter { it.isDirectory() && it != dir }
                .map { base + dir.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
    }

    override fun makeDirectory(path: String): Boolean {
        Files.createDirectories(resolve(path))
        return true
    }

    override fun deleteDirectory(path: String): Boolean {
        val dir = resolve(path)
        if (!dir.isDirectory()) return false
        Files.walk(dir).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
        return true
    }

    override fun url(path: String): String {
        val normalized = path.trimStart('/')
        return if (urlPrefix.isBlank()) "/$normalized" else "${urlPrefix.trimEnd('/')}/$normalized"
    }

    override fun path(path: String): String = resolve(path).toAbsolutePath().toString()

    override fun append(path: String, data: String) = AdapterSupport.append(this, path, data)

    override fun prepend(path: String, data: String) = AdapterSupport.prepend(this, path, data)

    override fun readStream(path: String): InputStream? {
        val target = resolve(path)
        if (!Files.isRegularFile(target)) return null
        return Files.newInputStream(target)
    }

    override fun writeStream(path: String, input: InputStream) {
        val target = resolve(path)
        Files.createDirectories(target.parent)
        input.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    override fun putFile(directory: String, source: Path): String =
        AdapterSupport.putFile(this, directory, source)

    override fun putFileAs(directory: String, source: Path, name: String): String =
        AdapterSupport.putFileAs(this, directory, source, name)

    override fun getVisibility(path: String): String =
        visibilityOverrides[normalizeKey(path)] ?: Visibility.PRIVATE

    override fun setVisibility(path: String, visibility: String) {
        visibilityOverrides[normalizeKey(path)] = visibility
    }

    override fun providesTemporaryUrls(): Boolean = temporaryUrlBuilder != null

    override fun temporaryUrl(path: String, expirationSeconds: Long): String {
        val builder = temporaryUrlBuilder
            ?: throw IllegalStateException("This disk does not support creating temporary URLs.")
        return builder.apply(url(path), expirationSeconds)
    }

    private fun normalizeKey(path: String): String = path.replace('\\', '/').trimStart('/')

    private fun resolve(path: String): Path {
        val normalized = path.replace('\\', '/').trimStart('/')
        val target = root.resolve(normalized).normalize()
        if (!target.startsWith(root.normalize())) {
            throw IllegalArgumentException("Path [$path] escapes storage root")
        }
        return target
    }
}
