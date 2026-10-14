package dev.kona.filesystem

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class LocalAdapter(
    private val root: Path,
    private val urlPrefix: String,
) : FilesystemAdapter {
    constructor(root: Path) : this(root, "")

    init {
        Files.createDirectories(root)
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
        val bytes = get(path)
        if (bytes == null) {
            return null
        }
        return bytes.toString(Charsets.UTF_8)
    }

    override fun exists(path: String): Boolean = resolve(path).exists()

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

    private fun resolve(path: String): Path {
        val normalized = path.replace('\\', '/').trimStart('/')
        val target = root.resolve(normalized).normalize()
        if (!target.startsWith(root.normalize())) {
            throw IllegalArgumentException("Path [$path] escapes storage root")
        }
        return target
    }
}
