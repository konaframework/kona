package org.konaframework.filesystem

import java.io.InputStream
import java.nio.file.Path

/**
 * Rejects every mutating operation on [inner].
 */
class ReadOnlyAdapter(
    private val inner: FilesystemAdapter,
) : FilesystemAdapter {
    private fun deny(): Nothing =
        throw UnsupportedOperationException("This disk is read-only.")

    override fun put(path: String, contents: ByteArray) = deny()

    override fun put(path: String, contents: String) = deny()

    override fun get(path: String): ByteArray? = inner.get(path)

    override fun getString(path: String): String? = inner.getString(path)

    override fun exists(path: String): Boolean = inner.exists(path)

    override fun missing(path: String): Boolean = inner.missing(path)

    override fun delete(path: String): Boolean = deny()

    override fun copy(from: String, to: String): Boolean = deny()

    override fun move(from: String, to: String): Boolean = deny()

    override fun size(path: String): Long = inner.size(path)

    override fun lastModified(path: String): Long = inner.lastModified(path)

    override fun mimeType(path: String): String? = inner.mimeType(path)

    override fun checksum(path: String, algorithm: String): String? = inner.checksum(path, algorithm)

    override fun files(directory: String): List<String> = inner.files(directory)

    override fun allFiles(directory: String): List<String> = inner.allFiles(directory)

    override fun directories(directory: String): List<String> = inner.directories(directory)

    override fun allDirectories(directory: String): List<String> = inner.allDirectories(directory)

    override fun makeDirectory(path: String): Boolean = deny()

    override fun deleteDirectory(path: String): Boolean = deny()

    override fun url(path: String): String = inner.url(path)

    override fun path(path: String): String = inner.path(path)

    override fun append(path: String, data: String) = deny()

    override fun prepend(path: String, data: String) = deny()

    override fun readStream(path: String): InputStream? = inner.readStream(path)

    override fun writeStream(path: String, input: InputStream) = deny()

    override fun putFile(directory: String, source: Path): String = deny()

    override fun putFileAs(directory: String, source: Path, name: String): String = deny()

    override fun getVisibility(path: String): String = inner.getVisibility(path)

    override fun setVisibility(path: String, visibility: String) = deny()

    override fun providesTemporaryUrls(): Boolean = inner.providesTemporaryUrls()

    override fun temporaryUrl(path: String, expirationSeconds: Long): String =
        inner.temporaryUrl(path, expirationSeconds)
}
