package org.konaframework.brew.uploads

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * A file held in Brew's temporary upload disk until [store] / [storeAs] / [storePublicly]
 * (`Livewire\Features\SupportFileUploads\TemporaryUploadedFile`).
 */
class TemporaryUploadedFile(
    val diskPath: String,
    val originalName: String,
    val mimeType: String,
    val size: Long,
    private val root: Path,
) {
    fun exists(): Boolean = Files.exists(absolutePath())

    fun absolutePath(): Path = root.resolve(diskPath)

    fun get(): InputStream = Files.newInputStream(absolutePath())

    fun contents(): ByteArray = Files.readAllBytes(absolutePath())

    fun temporaryUrl(): String = "/brew/preview/$diskPath"

    fun isPreviewable(): Boolean =
        mimeType.startsWith("image/") || mimeType in PREVIEW_MIMES

    /** Move into [directory] on the temp root (or a sibling store path). */
    fun store(): String = store("", null)

    fun store(directory: String): String = store(directory, null)

    fun store(directory: String, name: String?): String {
        val targetDir = if (directory.isBlank()) root.resolve("stored") else root.resolve(directory)
        Files.createDirectories(targetDir)
        val fileName = name ?: (UUID.randomUUID().toString() + extension())
        val dest = targetDir.resolve(fileName)
        Files.move(absolutePath(), dest)
        return targetDir.relativize(dest).toString().replace('\\', '/')
    }

    fun storeAs(directory: String, name: String): String = store(directory, name)

    fun storePublicly(): String = store("public")

    fun storePublicly(directory: String): String = store(directory)

    fun delete() {
        Files.deleteIfExists(absolutePath())
    }

    fun extension(): String {
        val i = originalName.lastIndexOf('.')
        return if (i < 0) "" else originalName.substring(i)
    }

    fun toMeta(): Map<String, Any?> = linkedMapOf(
        "path" to diskPath,
        "name" to originalName,
        "type" to mimeType,
        "size" to size,
    )

    companion object {
        private val PREVIEW_MIMES = setOf("application/pdf", "video/mp4", "audio/mpeg")

        @JvmStatic
        fun fromMeta(meta: Map<String, Any?>, root: Path): TemporaryUploadedFile {
            val rawName = meta["name"]
            val rawType = meta["type"]
            return TemporaryUploadedFile(
                diskPath = meta["path"].toString(),
                originalName = if (rawName == null) "file" else rawName.toString(),
                mimeType = if (rawType == null) "application/octet-stream" else rawType.toString(),
                size = (meta["size"] as? Number)?.toLong() ?: 0L,
                root = root,
            )
        }
    }
}
