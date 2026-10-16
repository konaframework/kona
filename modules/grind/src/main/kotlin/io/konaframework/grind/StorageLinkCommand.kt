package io.konaframework.grind

import java.nio.file.Files
import java.nio.file.Path

/**
 * `grind storage:link` — symlink `public/storage` → `storage/app/public` (Laravel shape).
 *
 * Prefers `src/main/resources/public` (scaffold web root); falls back to `public/`.
 */
class StorageLinkCommand @JvmOverloads constructor(
    private val workingDirectory: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        val base = if (options.containsKey("base")) {
            Path.of(options.getValue("base")).toAbsolutePath().normalize()
        } else {
            workingDirectory
        }
        val target = base.resolve("storage/app/public")
        Files.createDirectories(target)
        val linkParent = resolvePublicDir(base)
        Files.createDirectories(linkParent)
        val link = linkParent.resolve("storage")
        if (Files.exists(link)) {
            if (Files.isSymbolicLink(link) && Files.readSymbolicLink(link).normalize() == target.normalize()) {
                io.info("The [public/storage] link already exists.")
                return 0
            }
            throw GrindException("The [$link] path already exists.")
        }
        Files.createSymbolicLink(link, target)
        io.success("The [$link] link has been connected to [$target].")
        return 0
    }

    private fun resolvePublicDir(base: Path): Path {
        val resourcesPublic = base.resolve("src/main/resources/public")
        if (Files.isDirectory(resourcesPublic)) {
            return resourcesPublic
        }
        return base.resolve("public")
    }
}
