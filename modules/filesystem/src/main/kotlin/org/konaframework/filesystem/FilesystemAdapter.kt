package org.konaframework.filesystem

import java.io.InputStream
import java.nio.file.Path

/**
 * Disk adapter surface (named disks via [FilesystemManager]).
 *
 * Visibility values are [Visibility.PUBLIC] and [Visibility.PRIVATE].
 */
interface FilesystemAdapter {
    fun put(path: String, contents: ByteArray)

    fun put(path: String, contents: String)

    fun get(path: String): ByteArray?

    fun getString(path: String): String?

    fun exists(path: String): Boolean

    fun missing(path: String): Boolean

    fun delete(path: String): Boolean

    fun copy(from: String, to: String): Boolean

    fun move(from: String, to: String): Boolean

    fun size(path: String): Long

    fun lastModified(path: String): Long

    fun mimeType(path: String): String?

    fun checksum(path: String, algorithm: String = "SHA-256"): String?

    fun files(directory: String = ""): List<String>

    fun allFiles(directory: String = ""): List<String>

    fun directories(directory: String = ""): List<String>

    fun allDirectories(directory: String = ""): List<String>

    fun makeDirectory(path: String): Boolean

    fun deleteDirectory(path: String): Boolean

    fun url(path: String): String

    fun path(path: String): String

    fun append(path: String, data: String)

    fun prepend(path: String, data: String)

    fun readStream(path: String): InputStream?

    fun writeStream(path: String, input: InputStream)

    fun putFile(directory: String, source: Path): String

    fun putFileAs(directory: String, source: Path, name: String): String

    fun getVisibility(path: String): String

    fun setVisibility(path: String, visibility: String)

    fun providesTemporaryUrls(): Boolean

    fun temporaryUrl(path: String, expirationSeconds: Long): String
}

/** Public / private visibility tokens for [FilesystemAdapter.setVisibility]. */
object Visibility {
    const val PUBLIC: String = "public"
    const val PRIVATE: String = "private"
}
