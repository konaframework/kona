package org.konaframework.grind

import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.KonaHttpServer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
/**
 * `grind serve` — runs the Kona application found at (or above) `--base`.
 *
 * Default: **watch** — boot in-process, keep Netty up, and on `src/main` changes run incremental
 * `gradlew classes` then swap the app [SoftReloadClassLoader] / HTTP kernel (soft reload).
 * `--no-watch` restores `./gradlew run` (child JVM, full restart). `--in-process` boots once
 * without watching. `--once` returns after bind.
 *
 * `--host` / `--port` are forwarded as `HOST` / `PORT` (process env wins over `.env`).
 * Default port is [DEFAULT_SERVE_PORT] (4000). When the preferred port is taken, grind asks
 * whether to try the next port.
 */
class ServeCommand
@JvmOverloads
constructor(
    private val awaitShutdown: (KonaHttpServer) -> Unit = { Thread.currentThread().join() },
    private val runner: ProcessRunner = ProcessRunner.SYSTEM,
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
    private val prompts: Prompts = Prompts(),
    private val io: GrindIO = GrindIO(),
    private val watcher: SourceWatcher = SourcePoller(),
    private val compiler: ProjectCompiler = GradleClassesCompiler(windows),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val start = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()
        val root = findProjectRoot(start)
            ?: throw GrindException(
                "No Kona application found at $start (expected settings.gradle.kts or build.gradle.kts). " +
                    "Create one with: kona new",
            )
        if (ServeWatch.wantsWatch(options)) {
            return serveWatching(root, options)
        }
        if (options["in-process"] == "true") {
            return serveInProcess(root, options)
        }
        return serveWithGradle(root, options)
    }

    internal fun gradleCommand(root: Path, task: String = "run"): List<String> =
        GradleInvocation.command(root, task, windows)

    private fun serveWithGradle(root: Path, options: Map<String, String>): Int {
        val (host, port) = resolveBind(root, options)
        announce(host, port, ready = false)
        val environment = linkedMapOf(
            "HOST" to host,
            "PORT" to port.toString(),
        )
        return runner.run(gradleCommand(root), root, environment)
    }

    private fun serveInProcess(root: Path, options: Map<String, String>): Int {
        Kona.reset()
        val verbose = GrindLogging.wantsVerbose(options)
        GrindLogging.hushConsole(verbose)
        val app = AppLoader(root, options.getOrDefault("bootstrap", AppLoader.DEFAULT_BOOTSTRAP))
            .create(arrayOf("--base=$root"))
        GrindLogging.hushConsole(verbose)
        val (host, port) = resolveBind(root, options)
        val server = KonaHttpServer(app.resolve(HttpKernel::class.java), host, port)
        server.start()
        announce(host, port, ready = true)
        try {
            if (options["once"] != "true") {
                awaitShutdown(server)
            }
        } finally {
            server.stop()
        }
        return 0
    }

    private fun serveWatching(root: Path, options: Map<String, String>): Int {
        ServeWatch.ensureClasses(root, compiler)
        val verbose = GrindLogging.wantsVerbose(options)
        GrindLogging.hushConsole(verbose)
        val booted = ServeWatch.boot(root, options)
        var loader = booted.first
        var app = booted.second
        GrindLogging.hushConsole(verbose)
        val (host, port) = resolveBind(root, options)
        val server = KonaHttpServer(app.resolve(HttpKernel::class.java), host, port)
        server.start()
        announce(host, port, ready = true, watching = true)
        io.info("Watching src/main — save .kt / .java / .crema / .css (and resources) to reload")
        val reloadLock = Any()
        val watch = watcher.start(SourceSnapshot.roots(root)) { paths ->
            // Serialize compile+swap so overlapping saves cannot close the live loader mid-boot.
            synchronized(reloadLock) {
                // Clear three-beat UX: Changed → Reloading → Reloaded (or error).
                io.info("Changed ${ServeWatch.changeLabel(paths)}")
                io.info("Reloading…")
                val result = compiler.compile(root)
                if (result.exitCode != 0) {
                    io.error("Compile failed — still serving previous classes")
                    val detail = ServeWatch.compileFailureDetail(result.output)
                    if (detail.isNotEmpty()) {
                        io.error(detail)
                    }
                    return@start
                }
                try {
                    GrindLogging.hushConsole(verbose)
                    val reloaded = ServeWatch.reload(root, options, server, app, loader)
                    loader = reloaded.first
                    app = reloaded.second
                    GrindLogging.hushConsole(verbose)
                    io.success("Reloaded")
                } catch (ex: Exception) {
                    io.error("Reload failed — still serving previous classes")
                    val message = ex.message?.takeIf { it.isNotBlank() } ?: ex.javaClass.simpleName
                    io.error(message)
                    if (verbose) {
                        // Prefer the head of the stack (app frames) over a truncated tail.
                        io.error(ex.stackTraceToString().take(4000))
                    } else {
                        io.info("Re-run with --verbose for the full stack trace")
                    }
                }
            }
        }
        try {
            awaitShutdown(server)
        } finally {
            ServeWatch.closeQuietly(watch)
            server.stop()
            ServeWatch.dispose(app, loader)
            Kona.reset()
        }
        return 0
    }

    /** Termwind serve card: always show the URL developers will open in a browser. */
    internal fun announce(
        host: String,
        port: Int,
        ready: Boolean,
        lanIp: String? = ServePorts.lanAddress(),
        wsl: Boolean = ServePorts.isWsl(),
        watching: Boolean = false,
    ) {
        val local = ServePorts.localUrl(host, port)
        val lan = lanIp?.let { "http://$it:$port" }
        val lines = buildList {
            add("Local    $local")
            if (ServePorts.isWildcardBind(host) && lan != null && lan != local) {
                add("Network  $lan")
            }
            if (ServePorts.isWildcardBind(host)) {
                add("Bind     $host:$port")
            }
            if (watching) {
                add("Reload   save .kt / .java / .crema / .css")
            }
            add("Stop     Ctrl+C")
        }
        io.intro("grind serve")
        io.box("grind serve", lines)
        if (wsl && !ServePorts.isWildcardBind(host)) {
            io.warn(
                "WSL tip: if Windows cannot open $local, set HOST=0.0.0.0 in .env and restart.",
            )
        }
        // Prefer localhost — WSL localhost forwarding / native loopback. Network is secondary.
        if (ready) {
            io.success("Server ready at $local")
        } else {
            io.info("Building and starting… open $local when ready")
        }
        io.outro(local)
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
