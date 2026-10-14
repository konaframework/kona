package dev.kona.filesystem

/**
 * Low-level disk adapter (Laravel FilesystemAdapter subset).
 */
interface FilesystemAdapter {
    fun put(path: String, contents: ByteArray)

    fun put(path: String, contents: String)

    fun get(path: String): ByteArray?

    fun getString(path: String): String?

    fun exists(path: String): Boolean

    fun delete(path: String): Boolean

    fun copy(from: String, to: String): Boolean

    fun move(from: String, to: String): Boolean

    fun size(path: String): Long

    fun lastModified(path: String): Long

    fun files(directory: String = ""): List<String>

    fun directories(directory: String = ""): List<String>

    fun makeDirectory(path: String): Boolean

    fun deleteDirectory(path: String): Boolean

    fun url(path: String): String

    fun path(path: String): String
}
