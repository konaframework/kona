package dev.kona.crema

import java.nio.file.Files
import java.nio.file.Path

fun interface ViewFinder {
    /** Resolve a dotted view name (e.g. `home` or `posts.show`) to template source. */
    fun get(name: String): String
}

/**
 * Loads `*.crema.html` (and optional `*.crema.kt` as plain text templates) from view paths.
 */
class FileViewFinder(
    private val paths: List<Path>,
    private val extensions: List<String> = listOf(".crema.html", ".crema.kt"),
) : ViewFinder {
    constructor(path: Path, extensions: List<String> = listOf(".crema.html", ".crema.kt")) :
        this(listOf(path), extensions)

    override fun get(name: String): String {
        val relative = name.replace('.', '/')
        for (root in paths) {
            for (ext in extensions) {
                val file = root.resolve(relative + ext)
                if (Files.isRegularFile(file)) {
                    return Files.readString(file)
                }
            }
        }
        throw ViewNotFoundException(name)
    }
}

/** In-memory views for tests and programmatic registration. */
class MapViewFinder(
    private val templates: MutableMap<String, String> = linkedMapOf(),
) : ViewFinder {
    fun put(name: String, source: String): MapViewFinder {
        templates[name] = source
        return this
    }

    override fun get(name: String): String =
        templates[name] ?: throw ViewNotFoundException(name)
}

class ViewNotFoundException(name: String) :
    IllegalArgumentException("View [$name] not found.")
