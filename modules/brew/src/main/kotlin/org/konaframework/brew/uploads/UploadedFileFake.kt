package org.konaframework.brew.uploads

import java.nio.file.Files
import java.nio.file.Path

/** Testing helper mirroring `UploadedFile::fake()` for Brew uploads. */
object UploadedFileFake {
    @JvmStatic
    fun create(root: Path): TemporaryUploadedFile =
        create("photo.jpg", ByteArray(10) { it.toByte() }, "image/jpeg", root)

    @JvmStatic
    fun create(name: String, root: Path): TemporaryUploadedFile =
        create(name, ByteArray(10) { it.toByte() }, "image/jpeg", root)

    @JvmStatic
    fun create(name: String, content: ByteArray, root: Path): TemporaryUploadedFile =
        create(name, content, "image/jpeg", root)

    @JvmStatic
    fun create(name: String, content: ByteArray, mime: String, root: Path): TemporaryUploadedFile {
        Files.createDirectories(root)
        val path = "fake/" + java.util.UUID.randomUUID() + "-" + name
        val abs = root.resolve(path)
        Files.createDirectories(abs.parent)
        Files.write(abs, content)
        return TemporaryUploadedFile(path, name, mime, content.size.toLong(), root)
    }

    @JvmStatic
    fun image(root: Path): TemporaryUploadedFile = image("photo.jpg", root)

    @JvmStatic
    fun image(name: String, root: Path): TemporaryUploadedFile =
        create(name, ByteArray(32) { 0xFF.toByte() }, "image/jpeg", root)
}
