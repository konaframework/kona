package org.konaframework.grind

import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.KonaHttpServer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * `grind serve` — runs the Kona application found at (or above) `--base` (Artisan `serve` shape).
 *
 * Default: `./gradlew run` in the project root, so the app is compiled and started with its
 * own runtime classpath. `--host` / `--port` are forwarded as `HOST` / `PORT` (process env wins
 * over `.env`). Default port is [DEFAULT_SERVE_PORT] (4000). When the preferred port is taken,
 * grind asks whether to try the next port.
 *
 * `--in-process`: boots the app inside grind from `build/classes` via [AppLoader]; HOST/PORT come
 * from the app's `.env` after load. `--once` stops right after binding.
 */
class ServeCommand
@JvmOverloads
constructor(
    private val awaitShutdown: (KonaHttpServer) -> Unit = { Thread.currentThread().join() },
    private val runner: ProcessRunner = ProcessRunner.SYSTEM,
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
    private val prompts: Prompts = Prompts(),
    private val io: GrindIO = GrindIO(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val start = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()
        val root = findProjectRoot(start)
            ?: throw GrindException(
                "No Kona application found at $start (expected settings.gradle.kts or build.gradle.kts). " +
                    "Create one with: kona new <name>",
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
        val (host, port) = resolveBind(root, options)
        val environment = linkedMapOf(
            "HOST" to host,
            "PORT" to port.toString(),
        )
        return runner.run(gradleCommand(root), root, environment)
    }

    private fun serveInProcess(root: Path, options: Map<String, String>): Int {
        Kona.reset()
        val app = AppLoader(root, options.getOrDefault("bootstrap", AppLoader.DEFAULT_BOOTSTRAP))
            .create(arrayOf("--base=$root"))
        val (host, port) = resolveBind(root, options)
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

    private fun resolveBind(root: Path, options: Map<String, String>): Pair<String, Int> {
        val (host, preferred) = ServePorts.preferredHostPort(root, options)
        val port = ServePorts.resolve(host, preferred, prompts, io)
        return host to port
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
        val SYSTEM: ProcessRunner = ManagedProcessRunner()
    }
}

/**
 * Runs a child process and tears down the whole process tree on Ctrl+C / JVM exit.
 *
 * Without this, `grind serve` → `./gradlew run` → app JVM can orphan the HTTP listener when
 * SIGINT kills only the grind JVM (Address already in use on the next serve).
 */
internal class ManagedProcessRunner(
    private val addShutdownHook: (Thread) -> Unit = { Runtime.getRuntime().addShutdownHook(it) },
    private val removeShutdownHook: (Thread) -> Unit = { Runtime.getRuntime().removeShutdownHook(it) },
    private val destroyTree: (Process) -> Unit = ProcessTrees::destroy,
) : ProcessRunner {
    override fun run(command: List<String>, workingDir: Path, environment: Map<String, String>): Int {
        val builder = ProcessBuilder(command).directory(workingDir.toFile()).inheritIO()
        builder.environment().putAll(environment)
        val process = try {
            builder.start()
        } catch (ex: IOException) {
            throw GrindException("Failed to start ${command.first()}: ${ex.message}")
        }
        val shutdown = Thread({ destroyTree(process) }, "kona-grind-process-shutdown")
        try {
            addShutdownHook(shutdown)
        } catch (_: IllegalStateException) {
            destroyTree(process)
            return if (process.isAlive) 143 else process.exitValue()
        }
        try {
            return try {
                process.waitFor()
            } catch (_: InterruptedException) {
                destroyTree(process)
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                Thread.currentThread().interrupt()
                if (process.isAlive) 143 else process.exitValue()
            }
        } finally {
            try {
                removeShutdownHook(shutdown)
            } catch (_: IllegalStateException) {
                // JVM is already shutting down — hook may still be running.
            }
            // Always tear down: safe if the child already exited, required if Ctrl+C left it alive.
            destroyTree(process)
        }
    }
}

/** Kills [process] and every living descendant (Gradle wrapper → application JVM). */
internal object ProcessTrees {
    fun destroy(process: Process) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }
}
