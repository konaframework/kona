package dev.kona.filesystem

import java.io.InputStream
import java.nio.file.Path

/**
 * Prefixes every path with [prefix] before delegating to [inner].
 */
class ScopedAdapter(
    private val inner: FilesystemAdapter,
    private val prefix: String,
) : FilesystemAdapter {
    private val root = prefix.replace('\\', '/').trim('/').trimEnd('/')

    private fun scoped(path: String): String {
        val relative = path.replace('\\', '/').trimStart('/')
        return if (root.isEmpty()) relative else if (relative.isEmpty()) root else "$root/$relative"
    }

    private fun unscoped(path: String): String {
        if (root.isEmpty()) return path
        val full = path.replace('\\', '/').trimStart('/')
        return when {
            full == root -> ""
            full.startsWith("$root/") -> full.removePrefix("$root/")
            else -> full
        }
    }

    override fun put(path: String, contents: ByteArray) = inner.put(scoped(path), contents)

    override fun put(path: String, contents: String) = inner.put(scoped(path), contents)

    override fun get(path: String): ByteArray? = inner.get(scoped(path))

    override fun getString(path: String): String? = inner.getString(scoped(path))

    override fun exists(path: String): Boolean = inner.exists(scoped(path))

    override fun missing(path: String): Boolean = !exists(path)

    override fun delete(path: String): Boolean = inner.delete(scoped(path))

    override fun copy(from: String, to: String): Boolean = inner.copy(scoped(from), scoped(to))

    override fun move(from: String, to: String): Boolean = inner.move(scoped(from), scoped(to))

    override fun size(path: String): Long = inner.size(scoped(path))

    override fun lastModified(path: String): Long = inner.lastModified(scoped(path))

    override fun mimeType(path: String): String? = inner.mimeType(scoped(path))

    override fun checksum(path: String, algorithm: String): String? = inner.checksum(scoped(path), algorithm)

    override fun files(directory: String): List<String> = inner.files(scoped(directory))

    override fun allFiles(directory: String): List<String> =
        inner.allFiles(scoped(directory)).map { unscoped(it) }

    override fun directories(directory: String): List<String> = inner.directories(scoped(directory))

    override fun allDirectories(directory: String): List<String> =
        inner.allDirectories(scoped(directory)).map { unscoped(it) }

    override fun makeDirectory(path: String): Boolean = inner.makeDirectory(scoped(path))

    override fun deleteDirectory(path: String): Boolean = inner.deleteDirectory(scoped(path))

    override fun url(path: String): String = inner.url(scoped(path))

    override fun path(path: String): String = inner.path(scoped(path))

    override fun append(path: String, data: String) = inner.append(scoped(path), data)

    override fun prepend(path: String, data: String) = inner.prepend(scoped(path), data)

    override fun readStream(path: String): InputStream? = inner.readStream(scoped(path))

    override fun writeStream(path: String, input: InputStream) = inner.writeStream(scoped(path), input)

    override fun putFile(directory: String, source: Path): String {
        val stored = inner.putFile(scoped(directory), source)
        return unscoped(stored)
    }

    override fun putFileAs(directory: String, source: Path, name: String): String {
        val stored = inner.putFileAs(scoped(directory), source, name)
        return unscoped(stored)
    }

    override fun getVisibility(path: String): String = inner.getVisibility(scoped(path))

    override fun setVisibility(path: String, visibility: String) = inner.setVisibility(scoped(path), visibility)

    override fun providesTemporaryUrls(): Boolean = inner.providesTemporaryUrls()

    override fun temporaryUrl(path: String, expirationSeconds: Long): String =
        inner.temporaryUrl(scoped(path), expirationSeconds)
}
