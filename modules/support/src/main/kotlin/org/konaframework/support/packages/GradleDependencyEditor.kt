package org.konaframework.support.packages

import java.nio.file.Files
import java.nio.file.Path

/**
 * Adds or updates a Gradle dependency (`implementation` by default, or
 * `runtimeOnly` / another configuration) in a build script.
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
     * Ensures `configuration("group:artifact:version")` (or Groovy single quotes) is present.
     * Returns null when no Gradle build file exists under [projectRoot].
     */
    @JvmStatic
    @JvmOverloads
    fun ensureDependency(
        projectRoot: Path,
        group: String,
        artifact: String,
        version: String,
        configuration: String = "implementation",
    ): Result? {
        val kts = projectRoot.resolve("build.gradle.kts")
        if (Files.isRegularFile(kts)) {
            return edit(kts, group, artifact, version, kotlinDsl = true, configuration = configuration)
        }
        val groovy = projectRoot.resolve("build.gradle")
        if (Files.isRegularFile(groovy)) {
            return edit(groovy, group, artifact, version, kotlinDsl = false, configuration = configuration)
        }
        return null
    }

    internal fun edit(
        buildFile: Path,
        group: String,
        artifact: String,
        version: String,
        kotlinDsl: Boolean,
        configuration: String,
    ): Result {
        val original = Files.readString(buildFile)
        val ga = "$group:$artifact"
        val config = Regex.escape(configuration)
        val existing =
            Regex("""(?<![A-Za-z])$config\s*\(\s*["']${Regex.escape(ga)}:([^"']+)["']\s*\)""")
                .find(original)
                ?: Regex("""(?<![A-Za-z])$config\s+["']${Regex.escape(ga)}:([^"']+)["']""")
                    .find(original)
        if (existing != null) {
            val currentVersion = existing.groupValues[1]
            if (currentVersion == version) {
                return Result(buildFile, added = false, updated = false, alreadyPresent = true)
            }
            val replacement =
                if (kotlinDsl) {
                    """$configuration("$ga:$version")"""
                } else {
                    """$configuration '$ga:$version'"""
                }
            val updated = original.replace(existing.value, replacement)
            Files.writeString(buildFile, updated)
            return Result(buildFile, added = false, updated = true, alreadyPresent = false)
        }
        val production =
            Regex(
                """(?<![A-Za-z])(?:implementation|api|compileOnly|runtimeOnly)\s*(?:\(|\s+)["']${Regex.escape(ga)}:([^"']+)["']""",
            )
        if (production.containsMatchIn(original)) {
            return Result(buildFile, added = false, updated = false, alreadyPresent = true)
        }

        val line =
            if (kotlinDsl) {
                """    $configuration("$ga:$version")"""
            } else {
                """    $configuration '$ga:$version'"""
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
     * Best-effort: read a `org.konaframework:…:version` already present in the app build file.
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
                Regex("""org\.konaframework:[\w.-]+:([\w.+-]+)""")
                    .find(text)
            if (match == null) {
                continue
            }
            return match.groupValues[1]
        }
        return null
    }
}
