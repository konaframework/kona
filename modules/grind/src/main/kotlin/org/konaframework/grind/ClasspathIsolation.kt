package org.konaframework.grind

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps soft-reload [java.net.URLClassLoader]s off Gradle-managed build/libs jars.
 *
 * `./grind serve` watch mode runs `gradlew classes`, which for `includeBuild` / composite apps
 * often rewrites those jars in place while a previous loader (and the grind JVM itself) still
 * has [java.util.zip.ZipFile] handles mapped. That surfaces as
 * `ZipFile invalid LOC header (bad signature)` on the next reload.
 *
 * Strategy (same idea as Spring Boot DevTools / Quarkus reload):
 * 1. Prefer exploded build/classes + build/resources when a jar lives under build/libs.
 * 2. Otherwise copy mutable project build/libs jars into a per-generation directory before opening them.
 * 3. Leave immutable cache jars (Maven/Gradle caches) on their original paths.
 */
internal data class StagedClasspath(
    val entries: List<Path>,
    val directory: Path?,
)

internal object ClasspathIsolation {
    private val generation = AtomicLong()

    fun materialize(projectRoot: Path, entries: List<Path>): StagedClasspath {
        val gen = projectRoot.resolve("build/kona-classpath")
            .resolve("gen-${generation.incrementAndGet()}")
        var staged = false
        val resolved = ArrayList<Path>(entries.size)
        for (entry in entries) {
            for (path in resolve(entry, gen) { staged = true }) {
                resolved.add(path)
            }
        }
        return StagedClasspath(resolved, if (staged) gen else null)
    }

    /**
     * Expands a single classpath entry. When [ensureStaging] runs, the caller must treat
     * [stagingDir] as created and owned by this generation.
     */
    fun resolve(entry: Path, stagingDir: Path, ensureStaging: () -> Unit): List<Path> {
        if (Files.isDirectory(entry)) {
            return listOf(entry)
        }
        if (!isJar(entry)) {
            return listOf(entry)
        }
        val exploded = explodedForBuildJar(entry)
        if (exploded.isNotEmpty()) {
            return exploded
        }
        if (isMutableBuildJar(entry)) {
            ensureStaging()
            Files.createDirectories(stagingDir)
            return listOf(copyJar(entry, stagingDir))
        }
        return listOf(entry)
    }

    /** When [jar] is under build/libs, return sibling exploded class/resource dirs. */
    fun explodedForBuildJar(jar: Path): List<Path> {
        if (!isJar(jar)) {
            return emptyList()
        }
        val libs = jar.parent
        if (libs == null || libs.fileName?.toString() != "libs") {
            return emptyList()
        }
        val build = libs.parent
        if (build == null || build.fileName?.toString() != "build") {
            return emptyList()
        }
        return listOf(
            build.resolve("classes/kotlin/main"),
            build.resolve("classes/java/main"),
            build.resolve("resources/main"),
        ).filter { Files.isDirectory(it) }
    }

    fun isJar(path: Path): Boolean {
        val file = path.fileName
        if (file == null) {
            return false
        }
        return file.toString().endsWith(".jar", ignoreCase = true)
    }

    /** Jars Gradle may rewrite during `classes` / `jar` (project `build/libs`, not caches). */
    fun isMutableBuildJar(path: Path): Boolean {
        if (!isJar(path)) {
            return false
        }
        val libs = path.parent
        if (libs == null || libs.fileName?.toString() != "libs") {
            return false
        }
        val build = libs.parent
        if (build == null) {
            return false
        }
        return build.fileName?.toString() == "build"
    }

    fun copyJar(source: Path, stagingDir: Path): Path {
        val target = stagingDir.resolve(uniqueJarName(source))
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    fun deleteQuietly(directory: Path?) {
        if (directory == null || !Files.exists(directory)) {
            return
        }
        try {
            Files.walkFileTree(
                directory,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.deleteIfExists(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                        Files.deleteIfExists(dir)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (_: Exception) {
            // Best-effort cleanup between reloads.
        }
    }

    internal fun uniqueJarName(source: Path): String {
        val file = source.fileName
        val fileName = if (file == null) {
            "dep.jar"
        } else {
            file.toString()
        }
        val buildName = source.parent?.parent?.fileName
        val parentHint = if (buildName == null) {
            "lib"
        } else {
            buildName.toString()
        }
        return "$parentHint-$fileName"
    }
}
