package org.konaframework.crema

import java.nio.file.Files
import java.nio.file.Path

/**
 * Canonical view name: path separators become dots (`posts/show` → `posts.show`).
 * Leading/trailing dots and slashes are trimmed.
 */
fun normalizeViewName(name: String): String =
    name.trim().trim('/').trim('.').replace('/', '.')

fun interface ViewFinder {
    /** Resolve a dotted (or slash) view name (e.g. `home` or `posts.show`) to template source. */
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
        val canonical = normalizeViewName(name)
        val relative = canonical.replace('.', '/')
        for (root in paths) {
            for (ext in extensions) {
                val file = root.resolve(relative + ext)
                if (Files.isRegularFile(file)) {
                    return Files.readString(file)
                }
            }
        }
        throw ViewNotFoundException(canonical)
    }
}

/** In-memory views for tests and programmatic registration. */
class MapViewFinder(
    private val templates: MutableMap<String, String> = linkedMapOf(),
) : ViewFinder {
    fun put(name: String, source: String): MapViewFinder {
        templates[normalizeViewName(name)] = source
        return this
    }

    override fun get(name: String): String {
        val canonical = normalizeViewName(name)
        return templates[canonical] ?: throw ViewNotFoundException(canonical)
    }
}

class ViewNotFoundException(name: String) :
    IllegalArgumentException("View [$name] not found.")
