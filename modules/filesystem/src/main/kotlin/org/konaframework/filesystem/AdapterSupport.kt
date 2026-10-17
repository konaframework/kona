package org.konaframework.filesystem

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

internal object AdapterSupport {
    private val extensionMime = mapOf(
        "txt" to "text/plain",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "json" to "application/json",
        "xml" to "application/xml",
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "zip" to "application/zip",
        "bin" to "application/octet-stream",
        "mp4" to "video/mp4",
    )

    fun join(directory: String, name: String): String {
        val dir = directory.replace('\\', '/').trim('/').trimEnd('/')
        val file = name.replace('\\', '/').trimStart('/')
        return if (dir.isEmpty()) file else "$dir/$file"
    }

    fun extensionOf(source: Path): String {
        val fileName = source.fileName?.toString().orEmpty()
        val dot = fileName.lastIndexOf('.')
        return if (dot >= 0) fileName.substring(dot) else ""
    }

    fun uniqueName(source: Path): String = UUID.randomUUID().toString() + extensionOf(source)

    fun mimeFromPath(path: String, probe: (() -> String?)? = null): String? {
        if (probe != null) {
            val probed = probe()
            if (probed != null) return probed
        }
        val name = path.replace('\\', '/').substringAfterLast('/')
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extensionMime[ext]
    }

    fun mimeFromFile(file: Path, logicalPath: String): String? =
        mimeFromPath(logicalPath) { Files.probeContentType(file) }

    fun checksum(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun putFile(adapter: FilesystemAdapter, directory: String, source: Path): String {
        val name = uniqueName(source)
        return putFileAs(adapter, directory, source, name)
    }

    fun putFileAs(adapter: FilesystemAdapter, directory: String, source: Path, name: String): String {
        val relative = join(directory, name)
        Files.newInputStream(source).use { adapter.writeStream(relative, it) }
        return relative
    }

    fun append(adapter: FilesystemAdapter, path: String, data: String) {
        val existing = adapter.get(path) ?: ByteArray(0)
        adapter.put(path, existing + data.toByteArray(Charsets.UTF_8))
    }

    fun prepend(adapter: FilesystemAdapter, path: String, data: String) {
        val existing = adapter.get(path) ?: ByteArray(0)
        adapter.put(path, data.toByteArray(Charsets.UTF_8) + existing)
    }

    fun writeStream(adapter: FilesystemAdapter, path: String, input: InputStream) {
        adapter.put(path, input.readAllBytes())
    }
}
