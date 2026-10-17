package org.konaframework.support.packages

import java.nio.file.Files
import java.nio.file.Path

/**
 * Maintains the app-local `META-INF/kona/providers` discovery file
 * (same format packages ship inside their JARs).
 */
object ProviderManifestEditor {
    const val RELATIVE = "src/main/resources/META-INF/kona/providers"

    data class Result(
        val path: Path,
        val added: List<String>,
        val alreadyPresent: List<String>,
    )

    @JvmStatic
    fun ensureProviders(projectRoot: Path, providers: List<String>): Result? {
        if (providers.isEmpty()) {
            return null
        }
        val path = projectRoot.resolve(RELATIVE)
        Files.createDirectories(path.parent)
        val existing =
            if (Files.isRegularFile(path)) {
                Files.readString(path)
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toMutableSet()
            } else {
                linkedSetOf()
            }
        val added = mutableListOf<String>()
        val already = mutableListOf<String>()
        for (provider in providers) {
            if (existing.contains(provider)) {
                already += provider
            } else {
                existing += provider
                added += provider
            }
        }
        if (added.isEmpty()) {
            return Result(path, added = emptyList(), alreadyPresent = already)
        }
        val header = "# Managed by kona install / grind install — package service providers\n"
        val body = existing.joinToString("\n", postfix = "\n")
        Files.writeString(path, header + body)
        return Result(path, added = added, alreadyPresent = already)
    }
}
