package io.konaframework.http

import io.konaframework.filesystem.FilesystemAdapter
import java.security.SecureRandom
import java.util.HexFormat

/**
 * Uploaded file from a multipart form (Laravel UploadedFile shape).
 */
class UploadedFile(
    val clientOriginalName: String,
    val clientMimeType: String?,
    val contents: ByteArray,
    val error: Int = UPLOAD_ERR_OK,
) {
    val size: Long get() = contents.size.toLong()

    fun isValid(): Boolean = error == UPLOAD_ERR_OK && contents.isNotEmpty()

    fun isEmpty(): Boolean = !isValid()

    fun extension(): String {
        val name = clientOriginalName
        val idx = name.lastIndexOf('.')
        if (idx < 0 || idx == name.length - 1) {
            return ""
        }
        return name.substring(idx + 1).lowercase()
    }

    @JvmOverloads
    fun hashName(extension: String? = null): String {
        val ext = (extension ?: extension()).trimStart('.')
        val hex = HexFormat.of().formatHex(ByteArray(20).also { SecureRandom().nextBytes(it) })
        return if (ext.isEmpty()) hex else "$hex.$ext"
    }

    fun store(directory: String, disk: FilesystemAdapter): String =
        storeAs(directory, hashName(), disk)

    fun storeAs(directory: String, name: String, disk: FilesystemAdapter): String {
        val dir = directory.trim('/').trimEnd('/')
        val path = if (dir.isEmpty()) name else "$dir/$name"
        disk.put(path, contents)
        return path
    }

    companion object {
        const val UPLOAD_ERR_OK: Int = 0
        const val UPLOAD_ERR_NO_FILE: Int = 4
    }
}
