package org.konaframework.cli

import org.konaframework.support.console.ConsoleIO
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * `kona grind <args…>` — finds the nearest Kona app and runs its local `./grind` / `grind.bat`.
 *
 * Project discovery matches [org.konaframework.grind.ServeCommand.findProjectRoot]: walk upward from the
 * current directory until a Gradle settings/build script is found.
 */
class GrindCommand
@JvmOverloads
constructor(
    private val runner: GrindProcessRunner = GrindProcessRunner.SYSTEM,
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
    private val startDir: Path = Path.of(".").toAbsolutePath().normalize(),
    private val io: ConsoleIO = ConsoleIO(),
) {
    fun run(args: List<String>): Int {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
            printHelp()
            return 0
        }
        val root = findProjectRoot(startDir)
            ?: throw KonaCliException(
                "No Kona application found at $startDir " +
                    "(expected settings.gradle.kts or build.gradle.kts). " +
                    "Run from a Kona app or create one with: kona new <name>",
            )
        val scriptName = if (windows) "grind.bat" else "grind"
        val script = root.resolve(scriptName)
        if (!Files.isRegularFile(script)) {
            throw KonaCliException(
                "Found a Gradle project at $root but no $scriptName. " +
                    "Run from a Kona app created with kona new, or use: kona new <name>",
            )
        }
        return runner.run(listOf(script.toString()) + args, root)
    }

    private fun printHelp() {
        io.box(
            "kona grind",
            listOf(
                "Proxy to the app-local ./grind (or grind.bat)",
                "Finds the nearest Gradle project and forwards your arguments",
            ),
        )
        io.twoColumn("kona grind serve", "Start the HTTP server")
        io.twoColumn("kona grind migrate", "Run pending migrations (--seed)")
        io.twoColumn("kona grind install <pkg>", "Add a Maven package + providers")
        io.twoColumn("kona grind <command>", "Any other ./grind command")
        io.writeln()
        io.info("Prefer ./grind when you are already inside the app directory.")
    }

    companion object {
        private val PROJECT_MARKERS =
            listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle")

        /** Nearest directory at or above [start] containing a Gradle build script. */
        @JvmStatic
        fun findProjectRoot(start: Path): Path? {
            var dir: Path? = start
            while (dir != null) {
                val current = dir
                if (PROJECT_MARKERS.any { Files.isRegularFile(current.resolve(it)) }) {
                    return current
                }
                dir = current.parent
            }
            return null
        }
    }
}

/** Spawns local grind; injectable so `kona grind` is testable without Gradle. */
fun interface GrindProcessRunner {
    fun run(command: List<String>, workingDir: Path): Int

    companion object {
        @JvmField
        val SYSTEM =
            GrindProcessRunner { command, workingDir ->
                val builder = ProcessBuilder(command).directory(workingDir.toFile()).inheritIO()
                val process =
                    try {
                        builder.start()
                    } catch (ex: IOException) {
                        throw KonaCliException("Failed to start ${command.first()}: ${ex.message}")
                    }
                process.waitFor()
            }
    }
}
