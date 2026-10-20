package org.konaframework.grind

import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.KonaHttpServer
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ServeWatchTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Kona.reset()
        if (Database.hasGlobal()) {
            runCatching { Database.global().close() }
            Database.clearGlobal()
        }
    }

    @Test
    fun wantsWatchMatrix() {
        assertTrue(ServeWatch.wantsWatch(emptyMap()))
        assertTrue(ServeWatch.wantsWatch(mapOf("watch" to "true")))
        assertFalse(ServeWatch.wantsWatch(mapOf("no-watch" to "true")))
        assertFalse(ServeWatch.wantsWatch(mapOf("once" to "true")))
        assertFalse(ServeWatch.wantsWatch(mapOf("in-process" to "true")))
        assertTrue(ServeWatch.wantsWatch(mapOf("in-process" to "true", "watch" to "true")))
        assertFalse(ServeWatch.wantsWatch(mapOf("watch" to "true", "once" to "true")))
    }

    @Test
    fun watchableFilesAndSkipDirs() {
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("App.kt")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("Foo.JAVA")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("welcome.crema")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("layout.html")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("beans.xml")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("app.yml")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("app.yaml")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("app.json")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("app.properties")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("build.kts")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of("note.txt")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of(".env")))
        assertTrue(SourceSnapshot.isWatchableFile(Path.of(".env.local")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of(".gitignore")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of("App.kt~")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of("App.swp")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of("App.tmp")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of("README")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of("trailing.")))
        assertFalse(SourceSnapshot.isWatchableFile(Path.of("/")))
        val nameless = object : Path by Path.of("App.kt") {
            override fun getFileName(): Path? = null
        }
        assertFalse(SourceSnapshot.isWatchableFile(nameless))
        assertTrue(SourceSnapshot.isSkippedDirectory("build"))
        assertFalse(SourceSnapshot.isSkippedDirectory("src"))
        val root = Path.of("/app/src/main")
        assertTrue(SourceSnapshot.isUnderSkipped(root, root.resolve("build/x.kt")))
        assertFalse(SourceSnapshot.isUnderSkipped(root, root.resolve("kotlin/App.kt")))
        assertFalse(SourceSnapshot.isUnderSkipped(root, Path.of("/other/App.kt")))
        assertFalse(SourceSnapshot.isUnderSkipped(Path.of("/abs"), Path.of("rel")))
    }

    @Test
    fun snapshotDiffAndRoots() {
        val src = temp.resolve("src/main/kotlin")
        Files.createDirectories(src)
        Files.createDirectories(temp.resolve("src/main/build/hidden"))
        Files.writeString(src.resolve("App.kt"), "class App")
        Files.writeString(src.resolve("README"), "no-extension")
        Files.writeString(temp.resolve("src/main/build/hidden/Skip.kt"), "class Skip")
        Files.writeString(temp.resolve(".env"), "APP_NAME=Watch\n")
        Files.writeString(temp.resolve("notes.md"), "no")
        val roots = SourceSnapshot.roots(temp)
        assertEquals(2, roots.size)
        val snap = SourceSnapshot.snapshot(roots)
        assertTrue(snap.keys.any { it.endsWith("App.kt") })
        assertTrue(snap.keys.any { it.endsWith(".env") })
        assertFalse(snap.keys.any { it.endsWith("Skip.kt") })
        assertFalse(snap.keys.any { it.endsWith("README") })
        assertEquals(emptyList<Path>(), SourceSnapshot.diff(snap, snap))
        val next = snap + ("added.kt" to FileStamp(1, 1))
        assertEquals(listOf(Path.of("added.kt")), SourceSnapshot.diff(snap, next))

        assertTrue(SourceSnapshot.roots(temp.resolve("missing")).isEmpty())
        val onlyEnv = temp.resolve("only-env")
        Files.createDirectories(onlyEnv)
        Files.writeString(onlyEnv.resolve(".env"), "x=1\n")
        assertEquals(listOf(onlyEnv.resolve(".env")), SourceSnapshot.roots(onlyEnv))

        val fileRoot = SourceSnapshot.snapshot(listOf(src.resolve("App.kt")))
        assertEquals(1, fileRoot.size)
        val skippedFile = SourceSnapshot.snapshot(listOf(temp.resolve("notes.md")))
        assertTrue(skippedFile.isEmpty())
        assertTrue(SourceSnapshot.snapshot(listOf(temp.resolve("no-such"))).isEmpty())
        val boomDir = Files.createDirectories(temp.resolve("boom-dir"))
        val boom = object : Path by boomDir {
            override fun getFileSystem(): java.nio.file.FileSystem = throw IllegalStateException("stat")
        }
        assertTrue(SourceSnapshot.snapshot(listOf(boom)).isEmpty())
        val stamp = FileStamp(1, 2)
        assertEquals(stamp, stamp.copy())
        assertEquals(stamp.hashCode(), stamp.copy().hashCode())
        assertTrue(stamp.toString().contains("1"))
        assertEquals(1L, stamp.component1())
        assertEquals(2L, stamp.component2())
        val compiled = CompileResult(0, "ok")
        assertEquals(compiled, compiled.copy())
        assertEquals("ok", compiled.component2())
    }

    @Test
    fun pollerFiresDebouncesAndStops() {
        val seen = CopyOnWriteArrayList<List<Path>>()
        val n = AtomicInteger(0)
        val first = mapOf("/a.kt" to FileStamp(1, 1))
        val second = mapOf("/a.kt" to FileStamp(2, 1))
        val poller = SourcePoller(
            intervalMs = 1,
            sleeper = {
                if (n.incrementAndGet() > 8) throw InterruptedException()
            },
            snapshotter = { if (n.get() < 2) first else second },
        )
        val handle = poller.start(listOf(Path.of("src"))) { seen += it }
        val deadline = System.currentTimeMillis() + 2_000
        while (seen.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        handle.close()
        assertFalse(seen.isEmpty())
        assertEquals(Path.of("/a.kt"), seen[0][0])

        val reverted = CopyOnWriteArrayList<List<Path>>()
        val m = AtomicInteger(0)
        val revert = SourcePoller(
            intervalMs = 1,
            sleeper = {
                if (m.incrementAndGet() > 8) throw InterruptedException()
            },
            snapshotter = { if (m.get() == 2) second else first },
        )
        val revertHandle = revert.start(listOf(Path.of("src"))) { reverted += it }
        val waitUntil = System.currentTimeMillis() + 500
        while (m.get() < 6 && System.currentTimeMillis() < waitUntil) {
            Thread.sleep(10)
        }
        revertHandle.close()
        assertTrue(reverted.isEmpty())

        val interrupted = SourcePoller(intervalMs = 1, sleeper = { throw InterruptedException() })
        interrupted.start(listOf(Path.of("src"))) { }.close()

        val flag = AtomicInteger(0)
        val stopAfterSleep = SourcePoller(
            intervalMs = 1,
            sleeper = {
                flag.incrementAndGet()
                Thread.currentThread().interrupt()
            },
        )
        stopAfterSleep.start(listOf(Path.of("src"))) { }.close()

        val debounceStop = AtomicInteger(0)
        val debounce = SourcePoller(
            intervalMs = 1,
            sleeper = {
                val tick = debounceStop.incrementAndGet()
                if (tick >= 2) {
                    Thread.currentThread().interrupt()
                }
            },
            snapshotter = { if (debounceStop.get() == 0) first else second },
        )
        debounce.start(listOf(Path.of("src"))) { }.close()

        Files.createDirectories(temp.resolve("src/main/kotlin"))
        Files.writeString(temp.resolve("src/main/kotlin/Live.kt"), "class Live")
        SourcePoller().start(SourceSnapshot.roots(temp)) { }.close()

        val afterFirstSleep = CountDownLatch(1)
        val interruptAfterSleep = SourcePoller(
            intervalMs = 1,
            sleeper = {
                afterFirstSleep.countDown()
                Thread.currentThread().interrupt()
            },
            snapshotter = { mapOf("/a.kt" to FileStamp(1, 1)) },
        )
        val sleepHandle = interruptAfterSleep.start(listOf(Path.of("src"))) { }
        assertTrue(afterFirstSleep.await(2, TimeUnit.SECONDS))
        sleepHandle.close()

        val debounceHit = CountDownLatch(1)
        val debounceTicks = AtomicInteger(0)
        val firstSnap = mapOf("/a.kt" to FileStamp(1, 1))
        val secondSnap = mapOf("/a.kt" to FileStamp(2, 1))
        val interruptDebounce = SourcePoller(
            intervalMs = 1,
            sleeper = {
                if (debounceTicks.incrementAndGet() >= 2) {
                    debounceHit.countDown()
                    Thread.currentThread().interrupt()
                }
            },
            snapshotter = { if (debounceTicks.get() < 1) firstSnap else secondSnap },
        )
        val debounceHandle = interruptDebounce.start(listOf(Path.of("src"))) { }
        assertTrue(debounceHit.await(2, TimeUnit.SECONDS))
        debounceHandle.close()
    }

    @Test
    fun gradleCompilerAndInvocation() {
        val unix = temp.resolve("unix")
        Files.createDirectories(unix)
        Files.writeString(unix.resolve("gradlew"), "#!/bin/sh\n")
        assertEquals("classes", GradleInvocation.command(unix, "classes", false).last())
        assertEquals("gradle", GradleInvocation.command(temp.resolve("none"), "classes", false).first())
        val win = temp.resolve("win")
        Files.createDirectories(win)
        Files.writeString(win.resolve("gradlew.bat"), "@echo off\n")
        assertTrue(GradleInvocation.command(win, "classes", true).first().endsWith("gradlew.bat"))

        val ok = GradleClassesCompiler { _, _ -> ProcessBuilder("sh", "-c", "echo compiled; exit 0").start() }
        val success = ok.compile(temp)
        assertEquals(0, success.exitCode)
        assertTrue(success.output.contains("compiled"), success.output)

        val fail = GradleClassesCompiler { _, _ -> ProcessBuilder("sh", "-c", "echo boom; exit 3").start() }
        assertEquals(3, fail.compile(temp).exitCode)

        assertThrows(GrindException::class.java) {
            GradleClassesCompiler { _, _ -> throw IOException("nope") }.compile(temp)
        }

        Thread.currentThread().interrupt()
        val interrupted = GradleClassesCompiler { _, _ -> ProcessBuilder("sleep", "30").start() }.compile(temp)
        assertEquals(130, interrupted.exitCode)
        assertTrue(Thread.interrupted())

        val defaulted = runCatching { GradleClassesCompiler(windows = false).compile(temp) }
        assertTrue(defaulted.isSuccess || defaulted.exceptionOrNull() is GrindException)
    }

    @Test
    fun ensureClassesAndDispose() {
        val withClasses = fixtureProject(temp.resolve("has-classes"))
        ServeWatch.ensureClasses(withClasses) { CompileResult(99, "should-not-run") }

        val missing = temp.resolve("missing-classes")
        Files.createDirectories(missing)
        Files.writeString(missing.resolve("settings.gradle.kts"), "")
        assertThrows(GrindException::class.java) {
            ServeWatch.ensureClasses(missing) { CompileResult(1, "") }
        }
        assertThrows(GrindException::class.java) {
            ServeWatch.ensureClasses(missing) { CompileResult(2, "kaboom") }
        }
        assertThrows(GrindException::class.java) {
            ServeWatch.ensureClasses(missing) { CompileResult(0) }
        }
        ServeWatch.ensureClasses(missing) {
            Files.createDirectories(missing.resolve("build/classes/kotlin/main"))
            CompileResult(0)
        }

        ServeWatch.closeQuietly { }
        ServeWatch.closeQuietly { throw IllegalStateException("already closed") }

        val plain = Application(temp.resolve("plain"))
        ServeWatch.dispose(plain, AppLoader(plain.basePath))

        val bound = Application(temp.resolve("bound"))
        val db = Database().addConnection("default", ConnectionConfig("jdbc:h2:mem:watch_bound;DB_CLOSE_DELAY=-1"))
        bound.container.instance(Database::class.java, db)
        db.connection()
        ServeWatch.dispose(bound, AppLoader(bound.basePath))

        val global = Database().addConnection("default", ConnectionConfig("jdbc:h2:mem:watch_global;DB_CLOSE_DELAY=-1"))
        global.setAsGlobal()
        ServeWatch.dispose(Application(temp.resolve("global")), AppLoader(temp.resolve("global")))
        assertFalse(Database.hasGlobal())

        val both = Application(temp.resolve("both"))
        val shared = Database().addConnection("default", ConnectionConfig("jdbc:h2:mem:watch_both;DB_CLOSE_DELAY=-1"))
        both.container.instance(Database::class.java, shared)
        shared.setAsGlobal()
        ServeWatch.dispose(both, AppLoader(both.basePath))
        assertFalse(Database.hasGlobal())
    }

    @Test
    fun serveWatchReloadsKeepsServingAndSurvivesFailures() {
        val port = freePort()
        val base = fixtureProject(temp.resolve("watch-ok"), "HOST=127.0.0.1\nPORT=$port\n")
        val compiles = AtomicInteger()
        val watcher = ManualSourceWatcher()
        val out = ByteArrayOutputStream()
        val serve = ServeCommand(
            awaitShutdown = {
                assertEquals("fixture:Kona", httpGet("http://127.0.0.1:$port/fixture").body())
                watcher.fire(base.resolve("src/main/kotlin/App.kt"))
                watcher.fire()
                watcher.fire(Path.of("/"))
                assertEquals("fixture:Kona", httpGet("http://127.0.0.1:$port/fixture").body())
            },
            io = GrindIO(PrintStream(out), PrintStream(out), color = false),
            watcher = watcher,
            compiler = ProjectCompiler {
                compiles.incrementAndGet()
                CompileResult(0)
            },
        )
        assertEquals(
            0,
            serve.run(listOf("--base=$base", "--watch", "--bootstrap=${FixtureApp::class.java.name}")),
        )
        assertEquals(3, compiles.get())
        val log = out.toString()
        assertTrue(log.contains("Reloaded"), log)
        assertTrue(log.contains("Watching src/main"), log)
        assertTrue(log.contains("Reload   save .kt"), log)

        val busy = freePort()
        val failBase = fixtureProject(temp.resolve("watch-fail"), "HOST=127.0.0.1\nPORT=$busy\n")
        val failWatcher = ManualSourceWatcher()
        val failOut = ByteArrayOutputStream()
        assertEquals(
            0,
            ServeCommand(
                awaitShutdown = {
                    failWatcher.fire(failBase.resolve("Broken.kt"))
                    assertEquals("fixture:Kona", httpGet("http://127.0.0.1:$busy/fixture").body())
                },
                io = GrindIO(PrintStream(failOut), PrintStream(failOut), color = false),
                watcher = failWatcher,
                compiler = ProjectCompiler { CompileResult(1, "error: boom") },
            ).run(listOf("--base=$failBase", "--bootstrap=${FixtureApp::class.java.name}")),
        )
        assertTrue(failOut.toString().contains("Compile failed"), failOut.toString())
        assertTrue(failOut.toString().contains("error: boom"), failOut.toString())

        val emptyFail = freePort()
        val emptyBase = fixtureProject(temp.resolve("watch-empty-fail"), "PORT=$emptyFail\n")
        val emptyWatcher = ManualSourceWatcher()
        val emptyOut = ByteArrayOutputStream()
        ServeCommand(
            awaitShutdown = { emptyWatcher.fire(Path.of("x.kt")) },
            io = GrindIO(PrintStream(emptyOut), PrintStream(emptyOut), color = false),
            watcher = emptyWatcher,
            compiler = ProjectCompiler { CompileResult(1) },
        ).run(listOf("--base=$emptyBase", "--watch", "--bootstrap=${FixtureApp::class.java.name}"))
        assertTrue(emptyOut.toString().contains("Compile failed"), emptyOut.toString())

        val reloadPort = freePort()
        val reloadBase = fixtureProject(temp.resolve("watch-reload-fail"), "PORT=$reloadPort\n")
        val reloadWatcher = ManualSourceWatcher()
        val reloadOut = ByteArrayOutputStream()
        ServeCommand(
            awaitShutdown = { reloadWatcher.fire(Path.of("Gone.kt")) },
            io = GrindIO(PrintStream(reloadOut), PrintStream(reloadOut), color = false),
            watcher = reloadWatcher,
            compiler = ProjectCompiler {
                Files.walk(reloadBase.resolve("build")).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
                CompileResult(0)
            },
        ).run(listOf("--base=$reloadBase", "--watch", "--bootstrap=${FixtureApp::class.java.name}"))
        assertTrue(reloadOut.toString().contains("Reload failed"), reloadOut.toString())

        FlipApp.calls = 0
        val flipPort = freePort()
        val flipBase = fixtureProject(temp.resolve("watch-flip"), "PORT=$flipPort\n")
        val flipWatcher = ManualSourceWatcher()
        val flipOut = ByteArrayOutputStream()
        ServeCommand(
            awaitShutdown = { flipWatcher.fire(Path.of("Flip.kt")) },
            io = GrindIO(PrintStream(flipOut), PrintStream(flipOut), color = false),
            watcher = flipWatcher,
            compiler = ProjectCompiler { CompileResult(0) },
        ).run(listOf("--base=$flipBase", "--watch", "--bootstrap=${FlipApp::class.java.name}"))
        assertTrue(flipOut.toString().contains("Reload failed"), flipOut.toString())
        assertTrue(flipOut.toString().contains("RuntimeException"), flipOut.toString())
    }

    @Test
    fun serveWatchOnceSkipsWatcherAndNoWatchUsesGradle() {
        val started = AtomicInteger()
        val watcher = SourceWatcher { _, _ ->
            started.incrementAndGet()
            AutoCloseable { }
        }
        val port = freePort()
        val base = fixtureProject(temp.resolve("once"), "PORT=$port\n")
        assertEquals(
            0,
            ServeCommand(
                watcher = watcher,
                compiler = ProjectCompiler { CompileResult(0) },
            ).run(
                listOf(
                    "--base=$base",
                    "--watch",
                    "--once",
                    "--in-process",
                    "--bootstrap=${FixtureApp::class.java.name}",
                ),
            ),
        )
        assertEquals(0, started.get())

        val calls = mutableListOf<List<String>>()
        val app = temp.resolve("gradle-no-watch")
        Files.createDirectories(app)
        Files.writeString(app.resolve("settings.gradle.kts"), "rootProject.name = \"g\"\n")
        Files.writeString(app.resolve("gradlew"), "#!/bin/sh\n")
        app.resolve("gradlew").toFile().setExecutable(true)
        val code = ServeCommand(
            runner = ProcessRunner { command, _, _ ->
                calls += command
                0
            },
            windows = false,
        ).run(listOf("--base=$app", "--no-watch", "--port=${freePort()}"))
        assertEquals(0, code)
        assertEquals("run", calls[0].last())
    }

    @Test
    fun serveWatchCompilesWhenClassesMissing() {
        val port = freePort()
        val root = temp.resolve("fresh")
        Files.createDirectories(root)
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"fresh\"\n")
        Files.writeString(root.resolve(".env"), "PORT=$port\nHOST=127.0.0.1\n")
        val out = ByteArrayOutputStream()
        assertEquals(
            0,
            ServeCommand(
                awaitShutdown = { },
                io = GrindIO(PrintStream(out), PrintStream(out), color = false),
                watcher = SourceWatcher { _, _ -> AutoCloseable { } },
                compiler = ProjectCompiler {
                    Files.createDirectories(root.resolve("build/classes/kotlin/main"))
                    CompileResult(0)
                },
            ).run(listOf("--base=$root", "--watch", "--bootstrap=${FixtureApp::class.java.name}")),
        )
    }

    @Test
    fun reloadSwapsLiveKernel() {
        val port = freePort()
        val first = Router().also { it.get("/ping") { Response.text("one") } }
        val second = Router().also { it.get("/ping") { Response.text("two") } }
        val server = KonaHttpServer(HttpKernel(first), "127.0.0.1", port, transportPreference = "nio")
        server.start()
        try {
            val base = fixtureProject(temp.resolve("kernel-swap"))
            val (loader, app) = ServeWatch.boot(base, mapOf("bootstrap" to FixtureApp::class.java.name))
            assertEquals("one", httpGet("http://127.0.0.1:$port/ping").body())
            server.replaceKernel(HttpKernel(second))
            assertEquals("two", httpGet("http://127.0.0.1:$port/ping").body())
            ServeWatch.dispose(app, loader)
        } finally {
            server.stop()
        }
    }

    @Test
    fun appLoaderCloseResetsContextClassLoader() {
        val base = fixtureProject(temp.resolve("close-loader"))
        val loader = AppLoader(base, FixtureApp::class.java.name)
        loader.close()
        assertNull(loader.classLoader)
        loader.create(arrayOf("--base=$base"))
        assertTrue(loader.classLoader != null)
        Thread.currentThread().contextClassLoader = ClassLoader.getSystemClassLoader()
        loader.close()
        assertNull(loader.classLoader)
        val again = AppLoader(base, FixtureApp::class.java.name)
        again.create(arrayOf("--base=$base"))
        again.close()
        assertNull(again.classLoader)
    }
}

internal object FlipApp {
    var calls = 0

    @JvmStatic
    fun create(args: Array<String>): Application {
        calls++
        if (calls > 1) {
            throw RuntimeException()
        }
        return FixtureApp.create(args)
    }
}

internal class ManualSourceWatcher : SourceWatcher {
    private var onChange: ((List<Path>) -> Unit)? = null

    override fun start(roots: List<Path>, onChange: (List<Path>) -> Unit): AutoCloseable {
        this.onChange = onChange
        return AutoCloseable { this.onChange = null }
    }

    fun fire(vararg paths: Path) {
        onChange?.invoke(paths.toList())
    }
}
