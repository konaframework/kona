package org.konaframework.grind

import org.konaframework.cadence.JdbcDrivers
import org.konaframework.support.packages.GradleDependencyEditor
import java.nio.file.Files
import java.nio.file.Path

/**
 * When a Kona app switches `DB_CONNECTION` (sqlite → pgsql, …), the JDBC JAR is
 * often missing because `kona new` only puts SQLite on the classpath.
 *
 * Grind adds the matching `runtimeOnly` coordinate to `build.gradle.kts` (or Groovy
 * `build.gradle`) before opening a connection, then drops the cached
 * `konaClasspath` so the next [AppLoader] / `gradlew run` sees the driver.
 *
 * Opt out with `--no-jdbc-install`.
 */
object JdbcDriverInstaller {
    const val OPT_OUT = "no-jdbc-install"

    enum class Status {
        SKIPPED,
        INSTALLED,
        DECLARED,
        NO_BUILD,
    }

    data class Outcome(
        val status: Status,
        val coordinate: String? = null,
        val buildFile: Path? = null,
        val engine: String? = null,
    )

    /** Java-friendly: process-like env map instead of a lambda. */
    @JvmStatic
    fun ensure(
        root: Path,
        options: Map<String, String>,
        io: GrindIO?,
        env: Map<String, String?>,
    ): Outcome = ensure(root, options, io, { env[it] })

    @JvmStatic
    @JvmOverloads
    fun ensure(
        root: Path,
        options: Map<String, String> = emptyMap(),
        io: GrindIO? = null,
        getenv: (String) -> String? = { System.getenv(it) },
        classLoader: ClassLoader = JdbcDriverInstaller::class.java.classLoader,
    ): Outcome {
        if (optedOut(options)) {
            return Outcome(Status.SKIPPED)
        }
        val dotenv = readDotenv(root)
        fun value(key: String): String? =
            getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]?.takeIf { it.isNotBlank() }
        val connection = value("DB_CONNECTION")
        val driver = value("DB_DRIVER")
        val url = value("DB_URL")
        val coordinate = JdbcDrivers.gradleCoordinate(connection, driver, url) ?: return Outcome(Status.SKIPPED)
        val engine =
            JdbcDrivers.engine(connection)
                ?: JdbcDrivers.engine(driver)
                ?: JdbcDrivers.ENGINES.first { it.gradle == coordinate }
        if (JdbcDrivers.isLoadable(engine.className, classLoader)) {
            return Outcome(Status.SKIPPED, engine.gradle, engine = engine.alias)
        }
        val parts = engine.gradle.split(':', limit = 3)
        val result =
            try {
                GradleDependencyEditor.ensureDependency(root, parts[0], parts[1], parts[2], "runtimeOnly")
            } catch (_: IllegalStateException) {
                return Outcome(Status.NO_BUILD, engine.gradle, engine = engine.alias)
            } ?: return Outcome(Status.NO_BUILD, engine.gradle, engine = engine.alias)
        forgetClasspathCache(root)
        if (result.added || result.updated) {
            io?.success("Added JDBC driver for [${engine.alias}]: runtimeOnly(\"${engine.gradle}\")")
            return Outcome(Status.INSTALLED, engine.gradle, result.path, engine.alias)
        }
        return Outcome(Status.DECLARED, engine.gradle, result.path, engine.alias)
    }

    internal fun optedOut(options: Map<String, String>): Boolean {
        if (!options.containsKey(OPT_OUT)) {
            return false
        }
        return MigrateCommand.flagEnabled(options[OPT_OUT])
    }

    internal fun forgetClasspathCache(root: Path) {
        Files.deleteIfExists(root.resolve("build/kona-runtime.classpath"))
    }

    internal fun readDotenv(root: Path): Map<String, String> {
        val path = root.resolve(".env")
        if (!Files.isRegularFile(path)) {
            return emptyMap()
        }
        val values = linkedMapOf<String, String>()
        Files.readAllLines(path).forEach { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEach
            }
            if (line.startsWith("export ")) {
                line = line.removePrefix("export ").trim()
            }
            val idx = line.indexOf('=')
            if (idx <= 0) {
                return@forEach
            }
            val key = line.substring(0, idx).trim()
            var value = line.substring(idx + 1).trim()
            val quoted = value.startsWith("\"") || value.startsWith("'")
            if (!quoted) {
                val hash = value.indexOf(" #")
                if (hash >= 0) {
                    value = value.substring(0, hash).trim()
                }
            }
            value = value.removeSurrounding("\"").removeSurrounding("'")
            values[key] = value
        }
        return values
    }
}
