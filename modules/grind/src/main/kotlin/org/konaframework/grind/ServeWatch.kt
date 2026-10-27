package org.konaframework.grind

import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.KonaHttpServer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

/** Result of an incremental `gradlew classes` (or a test double). */
data class CompileResult @JvmOverloads constructor(
    val exitCode: Int,
    val output: String = "",
)

/** Compiles application sources so [AppLoader] can pick up a new class set. */
fun interface ProjectCompiler {
    fun compile(root: Path): CompileResult
}

/** Notifies when watched sources change. Implementations must be cheap (poll mtimes, not dual JVMs). */
fun interface SourceWatcher {
    fun start(roots: List<Path>, onChange: (List<Path>) -> Unit): AutoCloseable
}

internal data class FileStamp(val modified: Long, val size: Long)

internal object SourceSnapshot {
    val EXTENSIONS = setOf(
        "kt", "java", "crema", "kts", "json", "properties", "yml", "yaml", "xml", "html", "txt",
    )
    val SKIP_DIRS = setOf("build", ".git", "node_modules", ".gradle", "dist", ".idea", "out")

    fun isWatchableFile(path: Path): Boolean {
        val fileName = path.fileName ?: return false
        val name = fileName.toString()
        if (name.endsWith("~") || name.endsWith(".swp") || name.endsWith(".tmp")) {
            return false
        }
        if (name == ".env" || name.startsWith(".env.")) {
            return true
        }
        if (name.startsWith(".")) {
            return false
        }
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.lastIndex) {
            return false
        }
        return name.substring(dot + 1).lowercase(Locale.ROOT) in EXTENSIONS
    }

    fun isSkippedDirectory(name: String): Boolean = name in SKIP_DIRS

    fun isUnderSkipped(root: Path, path: Path): Boolean {
        val relative = try {
            root.relativize(path)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return relative.any { isSkippedDirectory(it.toString()) }
    }

    fun roots(project: Path): List<Path> {
        val list = ArrayList<Path>(2)
        val src = project.resolve("src/main")
        if (Files.isDirectory(src)) {
            list.add(src)
        }
        val env = project.resolve(".env")
        if (Files.isRegularFile(env)) {
            list.add(env)
        }
        return list
    }

    fun snapshot(roots: List<Path>): Map<String, FileStamp> {
        val stamps = linkedMapOf<String, FileStamp>()
        for (root in roots) {
            try {
                if (Files.isRegularFile(root)) {
                    remember(stamps, root)
                    continue
                }
                if (!Files.isDirectory(root)) {
                    continue
                }
                Files.walk(root).use { stream ->
                    stream.forEach { path ->
                        if (Files.isRegularFile(path) &&
                            !isUnderSkipped(root, path) &&
                            isWatchableFile(path)
                        ) {
                            remember(stamps, path)
                        }
                    }
                }
            } catch (_: Exception) {
                // Directory vanished between listing and stat — skip this tick.
            }
        }
        return stamps
    }

    fun diff(previous: Map<String, FileStamp>, next: Map<String, FileStamp>): List<Path> {
        val keys = LinkedHashSet<String>()
        keys.addAll(previous.keys)
        keys.addAll(next.keys)
        return keys.filter { previous[it] != next[it] }.map { Path.of(it) }
    }

    private fun remember(stamps: MutableMap<String, FileStamp>, path: Path) {
        if (!isWatchableFile(path)) {
            return
        }
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        stamps[path.toAbsolutePath().normalize().toString()] = FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size())
    }
}

/**
 * Low-memory source watch: poll mtimes under `src/main` (and `.env`). Preferable to NIO
 * [java.nio.file.WatchService] on WSL, where events are often missed.
 */
internal class SourcePoller(
    private val intervalMs: Long = 400,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val snapshotter: (List<Path>) -> Map<String, FileStamp> = SourceSnapshot::snapshot,
) : SourceWatcher {
    override fun start(roots: List<Path>, onChange: (List<Path>) -> Unit): AutoCloseable {
        val thread = Thread({
            var prev = snapshotter(roots)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    sleeper(intervalMs)
                    if (Thread.currentThread().isInterrupted) {
                        break
                    }
                    val next = snapshotter(roots)
                    if (next != prev) {
                        sleeper(intervalMs)
                        if (Thread.currentThread().isInterrupted) {
                            break
                        }
                        val stable = snapshotter(roots)
                        val changed = SourceSnapshot.diff(prev, stable)
                        prev = stable
                        if (changed.isNotEmpty()) {
                            onChange(changed)
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "kona-grind-watch")
        thread.isDaemon = true
        thread.start()
        return AutoCloseable {
            thread.interrupt()
            thread.join(2000)
        }
    }
}

internal object GradleInvocation {
    fun wrapper(root: Path, windows: Boolean): String {
        val file = root.resolve(if (windows) "gradlew.bat" else "gradlew")
        return if (Files.isRegularFile(file)) file.toString() else "gradle"
    }

    fun command(root: Path, task: String, windows: Boolean): List<String> {
        return listOf(wrapper(root, windows), "--quiet", "--console=plain", "-Dorg.gradle.console=plain", task)
    }
}

internal class GradleClassesCompiler(
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
    private val start: (List<String>, Path) -> Process = { command, dir ->
        ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start()
    },
) : ProjectCompiler {
    override fun compile(root: Path): CompileResult {
        val command = GradleInvocation.command(root, "classes", windows)
        val process = try {
            start(command, root)
        } catch (ex: IOException) {
            throw GrindException("Failed to compile with ${command.first()}: ${ex.message}")
        }
        val exit = try {
            process.waitFor()
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            return CompileResult(130, "interrupted")
        }
        val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        return CompileResult(exit, output)
    }
}

/**
 * Soft reload keeps framework facades in the parent class loader while [AppLoader] swaps the
 * child URLClassLoader. Call [reset] on each known facade when present so Firefly remounts panels
 * (and Wire/Inertia drop stale managers) on the new [Application].
 */
internal object SoftReload {
    val DEFAULT_FACADES = listOf(
        "org.konaframework.firefly.Firefly",
        "org.konaframework.wire.Wire",
        "org.konaframework.inertia.Inertia",
    )

    fun resetFacades(
        loader: ClassLoader = SoftReload::class.java.classLoader,
        names: List<String> = DEFAULT_FACADES,
    ) {
        for (name in names) {
            invokeReset(name, loader)
        }
    }

    fun invokeReset(className: String, loader: ClassLoader): Boolean {
        return try {
            val type = Class.forName(className, false, loader)
            type.getMethod("reset").invoke(null)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: NoClassDefFoundError) {
            false
        } catch (_: NoSuchMethodException) {
            false
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: ExceptionInInitializerError) {
            false
        }
    }
}

internal object ServeWatch {
    fun wantsWatch(options: Map<String, String>): Boolean {
        if (options["once"] == "true") {
            return false
        }
        if (options["no-watch"] == "true") {
            return false
        }
        if (options["in-process"] == "true" && options["watch"] != "true") {
            return false
        }
        return true
    }

    fun ensureClasses(root: Path, compiler: ProjectCompiler) {
        if (AppLoader(root).classpath().isNotEmpty()) {
            return
        }
        val result = compiler.compile(root)
        if (result.exitCode != 0) {
            val detail = result.output.trim()
            throw GrindException(
                if (detail.isEmpty()) {
                    "Compile failed (exit ${result.exitCode})"
                } else {
                    "Compile failed (exit ${result.exitCode}):\n$detail"
                },
            )
        }
        if (AppLoader(root).classpath().isEmpty()) {
            throw GrindException("No compiled classes under $root/build — run ./gradlew classes first")
        }
    }

    fun closeQuietly(closeable: AutoCloseable) {
        try {
            closeable.close()
        } catch (_: Exception) {
            // Old pools / watchers may already be gone during Ctrl+C.
        }
    }

    fun dispose(app: Application, loader: AppLoader) {
        val bound = if (app.container.bound(Database::class.java)) {
            app.resolve(Database::class.java)
        } else {
            null
        }
        val global = if (Database.hasGlobal()) Database.global() else null
        if (bound != null) {
            closeQuietly(bound)
        }
        // Reload boots the next app (and CadenceServiceProvider.setAsGlobal) before disposing
        // this one. Only clear the static global when it still *is* this app's Database —
        // otherwise a Wire/Counter save 500s with "No global Database".
        if (global != null && (bound == null || global === bound)) {
            if (bound == null) {
                closeQuietly(global)
            }
            Database.clearGlobal()
        }
        loader.close()
    }

    fun boot(root: Path, options: Map<String, String>): Pair<AppLoader, Application> {
        Kona.reset()
        // Firefly / Wire / Inertia keep process-wide state in the parent loader across URLClassLoader swaps.
        SoftReload.resetFacades()
        val loader = AppLoader(root, options.getOrDefault("bootstrap", AppLoader.DEFAULT_BOOTSTRAP))
        val app = loader.create(arrayOf("--base=$root"))
        return loader to app
    }

    fun reload(
        root: Path,
        options: Map<String, String>,
        server: KonaHttpServer,
        previousApp: Application,
        previousLoader: AppLoader,
    ): Pair<AppLoader, Application> {
        // Release ZipFile/JarURLConnection handles on the previous generation before the next
        // URLClassLoader opens Gradle outputs (ClasspathIsolation also avoids build/libs jars).
        previousLoader.close()
        val (loader, app) = boot(root, options)
        server.replaceKernel(app.resolve(HttpKernel::class.java))
        dispose(previousApp, previousLoader)
        return loader to app
    }
}
