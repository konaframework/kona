package dev.kona.support.packages

import java.nio.file.Files
import java.nio.file.Path

/**
 * Adds or updates an `implementation` dependency in a Gradle build script.
 *
 * Supports Kotlin DSL (`build.gradle.kts`) and Groovy (`build.gradle`).
 */
object GradleDependencyEditor {
    data class Result(
        val path: Path,
        val added: Boolean,
        val updated: Boolean,
        val alreadyPresent: Boolean,
    )

    /**
     * Ensures `implementation("group:artifact:version")` (or Groovy single quotes) is present.
     * Returns null when no Gradle build file exists under [projectRoot].
     */
    @JvmStatic
    fun ensureDependency(projectRoot: Path, group: String, artifact: String, version: String): Result? {
        val kts = projectRoot.resolve("build.gradle.kts")
        if (Files.isRegularFile(kts)) {
            return edit(kts, group, artifact, version, kotlinDsl = true)
        }
        val groovy = projectRoot.resolve("build.gradle")
        if (Files.isRegularFile(groovy)) {
            return edit(groovy, group, artifact, version, kotlinDsl = false)
        }
        return null
    }

    internal fun edit(
        buildFile: Path,
        group: String,
        artifact: String,
        version: String,
        kotlinDsl: Boolean,
    ): Result {
        val original = Files.readString(buildFile)
        val ga = "$group:$artifact"
        val existing =
            Regex("""implementation\s*\(\s*["']${Regex.escape(ga)}:([^"']+)["']\s*\)""")
                .find(original)
                ?: Regex("""implementation\s+["']${Regex.escape(ga)}:([^"']+)["']""")
                    .find(original)
        if (existing != null) {
            val currentVersion = existing.groupValues[1]
            if (currentVersion == version) {
                return Result(buildFile, added = false, updated = false, alreadyPresent = true)
            }
            val replacement =
                if (kotlinDsl) {
                    """implementation("$ga:$version")"""
                } else {
                    """implementation '$ga:$version'"""
                }
            val updated = original.replace(existing.value, replacement)
            Files.writeString(buildFile, updated)
            return Result(buildFile, added = false, updated = true, alreadyPresent = false)
        }

        val line =
            if (kotlinDsl) {
                """    implementation("$ga:$version")"""
            } else {
                """    implementation '$ga:$version'"""
            }
        val inserted = insertIntoDependenciesBlock(original, line)
            ?: error("Could not find a dependencies { } block in $buildFile")
        Files.writeString(buildFile, inserted)
        return Result(buildFile, added = true, updated = false, alreadyPresent = false)
    }

    /**
     * Inserts [line] before the closing brace of the first top-level `dependencies { }` block.
     */
    internal fun insertIntoDependenciesBlock(source: String, line: String): String? {
        val marker = Regex("""dependencies\s*\{""")
        val match = marker.find(source) ?: return null
        var depth = 0
        var i = match.range.first
        while (i < source.length) {
            val ch = source[i]
            if (ch == '{') {
                depth++
            }
            if (ch == '}') {
                depth--
                if (depth == 0) {
                    val before = source.substring(0, i)
                    val after = source.substring(i)
                    val body = before.trimEnd()
                    return "$body\n$line\n$after"
                }
            }
            i++
        }
        return null
    }

    /**
     * Best-effort: read a `dev.kona:…:version` already present in the app build file.
     */
    @JvmStatic
    fun detectKonaVersion(projectRoot: Path): String? {
        val files =
            listOf(
                projectRoot.resolve("build.gradle.kts"),
                projectRoot.resolve("build.gradle"),
            ).filter { Files.isRegularFile(it) }
        for (file in files) {
            val text = Files.readString(file)
            val match =
                Regex("""dev\.kona:[\w.-]+:([\w.+-]+)""")
                    .find(text)
            if (match == null) {
                continue
            }
            return match.groupValues[1]
        }
        return null
    }
}
