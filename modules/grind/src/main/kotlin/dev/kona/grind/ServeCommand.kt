package dev.kona.grind

import dev.kona.core.Env
import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.KonaHttpServer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * `grind serve` — runs the Kona application found at (or above) `--base` (Artisan `serve` shape).
 *
 * Default: `./gradlew run` in the project root, so the app is compiled and started with its
 * own runtime classpath. `--host` / `--port` are forwarded as `HOST` / `PORT` (process env wins
 * over `.env`).
 *
 * `--in-process`: boots the app inside grind from `build/classes` via [AppLoader]; HOST/PORT come
 * from the app's `.env` after load (default 127.0.0.1:8080). `--once` stops right after binding.
 */
class ServeCommand(
    private val awaitShutdown: (KonaHttpServer) -> Unit = { Thread.currentThread().join() },
    private val runner: ProcessRunner = ProcessRunner.SYSTEM,
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val start = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()
        val root = findProjectRoot(start)
            ?: throw GrindException(
                "No Kona application found at $start (expected settings.gradle.kts or build.gradle.kts). " +
                    "Create one with: grind new <name>",
            )
        if (options["in-process"] == "true") {
            return serveInProcess(root, options)
        }
        return serveWithGradle(root, options)
    }

    internal fun gradleCommand(root: Path): List<String> {
        val wrapper = root.resolve(if (windows) "gradlew.bat" else "gradlew")
        val gradle = if (Files.isRegularFile(wrapper)) wrapper.toString() else "gradle"
        return listOf(gradle, "run")
    }

    private fun serveWithGradle(root: Path, options: Map<String, String>): Int {
        val environment = linkedMapOf<String, String>()
        options["host"]?.let { environment["HOST"] = it }
        options["port"]?.let { environment["PORT"] = it }
        return runner.run(gradleCommand(root), root, environment)
    }

    private fun serveInProcess(root: Path, options: Map<String, String>): Int {
        Kona.reset()
        val app = AppLoader(root, options.getOrDefault("bootstrap", AppLoader.DEFAULT_BOOTSTRAP))
            .create(arrayOf("--base=$root"))
        val host = options.getOrDefault("host", Env.getOrDefault("HOST", "127.0.0.1"))
        val port = options.getOrDefault("port", Env.getOrDefault("PORT", "8080")).toInt()

        val server = KonaHttpServer(app.resolve(HttpKernel::class.java), host, port)
        server.start()
        try {
            if (options["once"] != "true") {
                awaitShutdown(server)
            }
        } finally {
            server.stop()
        }
        return 0
    }

    companion object {
        private val PROJECT_MARKERS = listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle")

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

/** Spawns a child process and waits for it; injectable so `serve` is testable without Gradle. */
fun interface ProcessRunner {
    fun run(command: List<String>, workingDir: Path, environment: Map<String, String>): Int

    companion object {
        @JvmField
        val SYSTEM = ProcessRunner { command, workingDir, environment ->
            val builder = ProcessBuilder(command).directory(workingDir.toFile()).inheritIO()
            builder.environment().putAll(environment)
            val process = try {
                builder.start()
            } catch (ex: IOException) {
                throw GrindException("Failed to start ${command.first()}: ${ex.message}")
            }
            process.waitFor()
        }
    }
}
