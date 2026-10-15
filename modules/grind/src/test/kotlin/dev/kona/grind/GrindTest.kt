package dev.kona.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path


class GrindTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun helpAndUnknown() {
        val app = GrindApplication()
        assertEquals(0, app.run(emptyArray()))
        assertEquals(0, app.run(arrayOf("help")))
        assertEquals(0, app.run(arrayOf("--help")))
        assertEquals(0, app.run(arrayOf("-h")))
        assertEquals(1, app.run(arrayOf("nope")))
        assertEquals(1, app.run(arrayOf("new", "x")))
    }

    @Test
    fun makeController() {
        val base = temp.resolve("proj")
        Files.createDirectories(base.resolve("src/main/kotlin/app/http/controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("User", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/controllers/UserController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("AccountController", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/controllers/AccountController.kt")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "User", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "../../Evil", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "Bad-Name", "--base=$base")))
        assertTrue(Files.notExists(temp.resolve("EvilController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("Post", "--base=$base", "--lang=java")))
        val javaProj = temp.resolve("javap")
        Files.createDirectories(javaProj.resolve("src/main/java/app/http/controllers"))
        Files.writeString(javaProj.resolve("src/main/java/app/http/controllers/X.java"), "class X {}")
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$javaProj")))
        assertTrue(Files.exists(javaProj.resolve("src/main/java/app/http/controllers/AutoController.java")))
        val ktProj = temp.resolve("ktp")
        Files.createDirectories(ktProj.resolve("src/main/kotlin/app/http/controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$ktProj")))
        val bare = temp.resolve("bare")
        Files.createDirectories(bare)
        assertEquals(0, MakeControllerCommand().run(listOf("Bare", "--base=$bare")))
        val emptyJavaRoot = temp.resolve("empty-java")
        Files.createDirectories(emptyJavaRoot.resolve("src/main/java/app"))
        assertEquals(0, MakeControllerCommand().run(listOf("EmptyJava", "--base=$emptyJavaRoot")))
        assertTrue(Files.exists(emptyJavaRoot.resolve("src/main/kotlin/app/http/controllers/EmptyJavaController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("Order", "--base=$base", "--resource", "--lang=kotlin")))
        val resourceKt = Files.readString(base.resolve("src/main/kotlin/app/http/controllers/OrderController.kt"))
        assertTrue(resourceKt.contains("fun store("))
        assertTrue(resourceKt.contains("fun destroy("))
        assertEquals(0, MakeControllerCommand().run(listOf("Invoice", "--base=$base", "--resource", "--lang=java")))
        val resourceJava = Files.readString(base.resolve("src/main/java/app/http/controllers/InvoiceController.java"))
        assertTrue(resourceJava.contains("store(Request"))
        assertTrue(resourceJava.contains("destroy(Request"))
    }

    @Test
    fun makeMiddlewareAndRequest() {
        val base = temp.resolve("mw-req")
        Files.createDirectories(base.resolve("src/main/kotlin/app"))
        assertEquals(0, MakeMiddlewareCommand().run(listOf("Auth", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/middleware/AuthMiddleware.kt")))
        assertTrue(Files.readString(base.resolve("src/main/kotlin/app/http/middleware/AuthMiddleware.kt")).contains("Middleware"))
        assertEquals(0, MakeMiddlewareCommand().run(listOf("LogMiddleware", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/http/middleware/LogMiddleware.java")))
        assertEquals(1, GrindApplication().run(arrayOf("make:middleware", "Auth", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:middleware")))
        assertEquals(1, GrindApplication().run(arrayOf("make:middleware", "Bad-Name", "--base=$base")))

        assertEquals(0, MakeRequestCommand().run(listOf("StoreUser", "--base=$base", "--lang=kotlin")))
        val ktReq = Files.readString(base.resolve("src/main/kotlin/app/http/requests/StoreUserRequest.kt"))
        assertTrue(ktReq.contains("FormRequest"))
        assertTrue(ktReq.contains("fun rules("))
        assertEquals(0, MakeRequestCommand().run(listOf("UpdatePostRequest", "--base=$base", "--lang=java")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/http/requests/UpdatePostRequest.java")).contains("extends FormRequest"))
        assertEquals(1, GrindApplication().run(arrayOf("make:request", "StoreUser", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:request")))
        assertEquals(1, GrindApplication().run(arrayOf("make:request", "Bad-Name", "--base=$base")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:middleware" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:request" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "storage:link" })
    }

    @Test
    fun storageLink() {
        val base = temp.resolve("storage-proj")
        Files.createDirectories(base.resolve("src/main/resources/public"))
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$base")))
        val link = base.resolve("src/main/resources/public/storage")
        assertTrue(Files.isSymbolicLink(link))
        assertEquals(
            base.resolve("storage/app/public").normalize(),
            Files.readSymbolicLink(link).normalize(),
        )
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$base")))
        val conflict = temp.resolve("conflict-proj")
        Files.createDirectories(conflict.resolve("src/main/resources/public"))
        Files.writeString(conflict.resolve("src/main/resources/public/storage"), "file")
        assertThrows(GrindException::class.java) {
            StorageLinkCommand().run(listOf("--base=$conflict"))
        }
        val wrongLink = temp.resolve("wrong-link")
        Files.createDirectories(wrongLink.resolve("src/main/resources/public"))
        Files.createDirectories(wrongLink.resolve("storage/app/other"))
        Files.createSymbolicLink(
            wrongLink.resolve("src/main/resources/public/storage"),
            wrongLink.resolve("storage/app/other"),
        )
        assertThrows(GrindException::class.java) {
            StorageLinkCommand().run(listOf("--base=$wrongLink"))
        }
        val plain = temp.resolve("plain-public")
        Files.createDirectories(plain)
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$plain")))
        assertTrue(Files.isSymbolicLink(plain.resolve("public/storage")))
        assertEquals(0, GrindApplication().run(arrayOf("storage:link", "--base=$base")))
        val cwd = temp.resolve("cwd-link").toAbsolutePath()
        Files.createDirectories(cwd)
        assertEquals(0, StorageLinkCommand(cwd).run(emptyList()))
        assertTrue(Files.isSymbolicLink(cwd.resolve("public/storage")))
        // Default no-arg constructor (JvmOverloads) uses process CWD — just ensure it constructs.
        assertTrue(StorageLinkCommand().javaClass.name.contains("StorageLinkCommand"))
    }

    @Test
    fun makeView() {
        val base = temp.resolve("views-proj")
        Files.createDirectories(base)
        assertEquals(0, MakeViewCommand().run(listOf("home", "--base=$base")))
        assertTrue(Files.exists(base.resolve("src/main/resources/views/home.crema.html")))
        assertEquals(0, MakeViewCommand().run(listOf("posts.show", "--base=$base")))
        assertTrue(Files.exists(base.resolve("src/main/resources/views/posts/show.crema.html")))
        assertEquals(1, GrindApplication().run(arrayOf("make:view", "home", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:view")))
        assertEquals(1, GrindApplication().run(arrayOf("make:view", "Bad-Name", "--base=$base")))
        assertEquals("CreateUsersTable", Names.studly("create_users_table"))
        assertEquals("AB", Names.studly("a__b"))
        assertEquals(0, MakeMigrationCommand().run(listOf("create_widgets_table", "--base=$base", "--lang=kotlin")))
        assertEquals(0, MakeMigrationCommand().run(listOf("add_name_to_widgets_table", "--base=$base", "--lang=kotlin")))
    }

    @Test
    fun serveRequiresProject() {
        val empty = temp.resolve("empty")
        Files.createDirectories(empty)
        assertEquals(1, GrindApplication().run(arrayOf("serve", "--base=$empty")))
        assertNull(ServeCommand.findProjectRoot(empty))
        val nested = fixtureProject(temp.resolve("root")).resolve("src/main/kotlin/app/http")
        Files.createDirectories(nested)
        assertEquals(temp.resolve("root"), ServeCommand.findProjectRoot(nested))
        listOf("settings.gradle", "build.gradle.kts", "build.gradle").forEach { marker ->
            val dir = temp.resolve("m-$marker")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve(marker), "")
            assertEquals(dir, ServeCommand.findProjectRoot(dir))
        }
    }

    @Test
    fun serveSpawnsGradleRunInProjectRoot() {
        val calls = mutableListOf<Triple<List<String>, Path, Map<String, String>>>()
        val runner = ProcessRunner { command, dir, env ->
            calls += Triple(command, dir, env)
            7
        }
        val app = stubGradleApp(temp.resolve("spawned"))
        Files.createDirectories(app.resolve("routes/nested"))

        val serve = ServeCommand(runner = runner, windows = false)
        assertEquals(7, serve.run(listOf("--base=${app.resolve("routes/nested")}", "--host=0.0.0.0", "--port=9000")))
        assertEquals(listOf(app.resolve("gradlew").toString(), "run"), calls[0].first)
        assertEquals(app, calls[0].second)
        assertEquals(mapOf("HOST" to "0.0.0.0", "PORT" to "9000"), calls[0].third)

        assertEquals(7, serve.run(listOf("--base=$app")))
        assertEquals("127.0.0.1", calls[1].third["HOST"])
        assertEquals(DEFAULT_SERVE_PORT.toString(), calls[1].third["PORT"])

        assertEquals(listOf(app.resolve("gradlew.bat").toString(), "run"), ServeCommand(windows = true).gradleCommand(app))
        val noWrapper = fixtureProject(temp.resolve("nowrapper"))
        assertEquals(listOf("gradle", "run"), ServeCommand(windows = false).gradleCommand(noWrapper))
    }

    @Test
    fun systemProcessRunner() {
        assertEquals(3, ProcessRunner.SYSTEM.run(listOf("sh", "-c", "exit 3"), temp, emptyMap()))
        assertEquals(
            0,
            ProcessRunner.SYSTEM.run(listOf("sh", "-c", "test \"\$KONA_T\" = yes && test -n \"\$PWD\""), temp, mapOf("KONA_T" to "yes")),
        )
        assertThrows(GrindException::class.java) {
            ProcessRunner.SYSTEM.run(listOf("kona-definitely-missing-binary"), temp, emptyMap())
        }
    }

    @Test
    fun serveInProcessRunsBootstrapAndReadsDotEnv() {
        val port = freePort()
        val base = fixtureProject(temp.resolve("inproc"), "APP_NAME=Fixture\nHOST=127.0.0.1\nPORT=$port\n")
        var body = ""
        val serve = ServeCommand(awaitShutdown = { server ->
            assertTrue(server.isRunning())
            body = httpGet("http://127.0.0.1:$port/fixture").body()
        })
        assertEquals(0, serve.run(listOf("--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}")))
        assertEquals("fixture:Fixture", body)

        val port2 = freePort()
        assertEquals(
            0,
            GrindApplication().run(
                arrayOf("serve", "--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}", "--port=$port2", "--host=127.0.0.1", "--once"),
            ),
        )
    }

    @Test
    fun serveInProcessPortBusyOffersNextOrAborts() {
        java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            val port = occupied.localPort
            val next = port + 1
            val base = fixtureProject(temp.resolve("occupied"), "HOST=127.0.0.1\nPORT=$port\n")
            val out = java.io.ByteArrayOutputStream()
            assertThrows(GrindException::class.java) {
                ServeCommand(
                    prompts = Prompts(java.io.ByteArrayInputStream("n\n".toByteArray()), java.io.PrintStream(out), color = false),
                    io = GrindIO(java.io.PrintStream(out), color = false),
                ).run(listOf("--base=$base", "--in-process", "--once", "--bootstrap=${FixtureApp::class.java.name}"))
            }
            var body = ""
            assertEquals(
                0,
                ServeCommand(
                    awaitShutdown = { server ->
                        assertEquals(next, server.port())
                        body = httpGet("http://127.0.0.1:$next/fixture").body()
                    },
                    prompts = Prompts(java.io.ByteArrayInputStream("y\n".toByteArray()), java.io.PrintStream(out), color = false),
                    io = GrindIO(java.io.PrintStream(out), color = false),
                ).run(listOf("--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}")),
            )
            assertEquals("fixture:Kona", body)
        }
    }

    @Test
    fun serveInProcessDefaultWaitIsInterruptible() {
        val port = freePort()
        val base = fixtureProject(temp.resolve("blocking"), "PORT=$port\n")
        var failure: Throwable? = null
        val thread = Thread {
            try {
                ServeCommand().run(listOf("--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}"))
            } catch (t: Throwable) {
                failure = t
            }
        }
        thread.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val up = runCatching { httpGet("http://127.0.0.1:$port/fixture").statusCode() == 200 }.getOrDefault(false)
            if (up) break
            Thread.sleep(50)
        }
        thread.interrupt()
        thread.join(10_000)
        assertInstanceOf(InterruptedException::class.java, failure)
        assertThrows(java.net.ConnectException::class.java) { httpGet("http://127.0.0.1:$port/fixture") }
    }

    @Test
    fun appLoaderErrors() {
        val noClasses = temp.resolve("noclasses")
        Files.createDirectories(noClasses)
        Files.writeString(noClasses.resolve("build.gradle.kts"), "")
        assertTrue(AppLoader(noClasses).classpath().isEmpty())
        assertThrows(GrindException::class.java) { AppLoader(noClasses).create(emptyArray()) }
        assertEquals(1, GrindApplication().run(arrayOf("serve", "--base=$noClasses", "--in-process")))

        val base = fixtureProject(temp.resolve("loader"))
        Files.createDirectories(base.resolve("build/classes/java/main"))
        Files.createDirectories(base.resolve("build/resources/main"))
        assertEquals(3, AppLoader(base).classpath().size)
        val jar = temp.resolve("extra.jar")
        Files.writeString(jar, "x")
        val withRuntime = AppLoader(base, FixtureApp::class.java.name) { listOf(jar) }
        assertEquals(4, withRuntime.classpath().size)
        val args = arrayOf("--base=$base")
        assertThrows(GrindException::class.java) { AppLoader(base).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, NoCreateApp::class.java.name).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, InstanceCreateApp::class.java.name).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, WrongReturnApp::class.java.name).create(args) }
        val boom = assertThrows(IllegalStateException::class.java) {
            AppLoader(base, ThrowingApp::class.java.name).create(args)
        }
        assertEquals("boom 1", boom.message)
    }

    @Test
    fun appLoaderParsesRuntimeClasspath() {
        assertTrue(AppLoader.parseClasspathLine("").isEmpty())
        assertTrue(AppLoader.parseClasspathLine("noise\nmore").isEmpty())
        val a = temp.resolve("a.jar")
        val b = temp.resolve("b.jar")
        Files.writeString(a, "a")
        Files.writeString(b, "b")
        val sep = java.io.File.pathSeparator
        val parsed = AppLoader.parseClasspathLine("ignore\n${a.toAbsolutePath()}$sep${b.toAbsolutePath()}\n")
        assertEquals(listOf(a.toAbsolutePath(), b.toAbsolutePath()), parsed.map { it.toAbsolutePath() })
        val missing = temp.resolve("missing.jar")
        assertEquals(
            1,
            AppLoader.parseClasspathLine("${a.toAbsolutePath()}$sep${missing.toAbsolutePath()}").size,
        )
        val cache = temp.resolve("cp.txt")
        Files.writeString(cache, "${a.toAbsolutePath()}$sep${b.toAbsolutePath()}")
        assertEquals(2, AppLoader.parseClasspathFile(cache).size)
        // Missing wrapper → empty runtime list
        val bare = fixtureProject(temp.resolve("nort"))
        assertTrue(AppLoader.gradleRuntimeClasspath(bare).isEmpty())

        // Cached classpath file short-circuits Gradle
        val cachedProj = fixtureProject(temp.resolve("cached"))
        val cachedFile = cachedProj.resolve("build/kona-runtime.classpath")
        Files.createDirectories(cachedFile.parent)
        Files.writeString(cachedFile, a.toAbsolutePath().toString())
        assertEquals(listOf(a.toAbsolutePath()), AppLoader.gradleRuntimeClasspath(cachedProj).map { it.toAbsolutePath() })

        assertNull(AppLoader.resolveGradleWrapper(bare))
        val batOnly = fixtureProject(temp.resolve("bat"))
        val bat = batOnly.resolve("gradlew.bat")
        Files.writeString(bat, "@echo off\n")
        assertEquals(bat.toAbsolutePath(), AppLoader.resolveGradleWrapper(batOnly)?.toAbsolutePath())

        // gradlew present but konaClasspath fails → empty
        val failProj = fixtureProject(temp.resolve("failcp"))
        val failWrap = failProj.resolve("gradlew")
        Files.writeString(failWrap, "#!/bin/sh\nexit 1\n")
        failWrap.toFile().setExecutable(true)
        assertTrue(AppLoader.gradleRuntimeClasspath(failProj).isEmpty())

        // exit 0 but no classpath line → empty, no cache write
        val emptyProj = fixtureProject(temp.resolve("emptycp"))
        val emptyWrap = emptyProj.resolve("gradlew")
        Files.writeString(emptyWrap, "#!/bin/sh\necho nope\n")
        emptyWrap.toFile().setExecutable(true)
        assertTrue(AppLoader.gradleRuntimeClasspath(emptyProj).isEmpty())
        AppLoader.writeClasspathCache(emptyProj.resolve("build/kona-runtime.classpath"), emptyList())

        // ProcessBuilder start failure (empty command / non-directory working dir)
        assertNull(AppLoader.startClasspathProcess(Path.of(""), temp.resolve("nort")))
        val fileRoot = temp.resolve("not-a-dir")
        Files.writeString(fileRoot, "x")
        val anyWrap = temp.resolve("wrap.sh")
        Files.writeString(anyWrap, "#!/bin/sh\n")
        anyWrap.toFile().setExecutable(true)
        assertNull(AppLoader.startClasspathProcess(anyWrap, fileRoot))

        // starter returns null → empty classpath
        val nullStarterProj = fixtureProject(temp.resolve("nullstarter"))
        Files.writeString(nullStarterProj.resolve("gradlew"), "#!/bin/sh\n")
        nullStarterProj.resolve("gradlew").toFile().setExecutable(true)
        assertTrue(AppLoader.gradleRuntimeClasspath(nullStarterProj) { _, _ -> null }.isEmpty())

        // cache write failure: build exists as a file
        val badCache = temp.resolve("badcache-file")
        Files.writeString(badCache, "x")
        AppLoader.writeClasspathCache(badCache.resolve("child"), listOf(a))

        // gradlew prints a classpath line → parsed + cached
        val okProj = fixtureProject(temp.resolve("okcp"))
        val okWrap = okProj.resolve("gradlew")
        Files.writeString(
            okWrap,
            "#!/bin/sh\necho '${a.toAbsolutePath()}$sep${b.toAbsolutePath()}'\n",
        )
        okWrap.toFile().setExecutable(true)
        val resolved = AppLoader.gradleRuntimeClasspath(okProj)
        assertEquals(2, resolved.size)
        assertTrue(Files.isRegularFile(okProj.resolve("build/kona-runtime.classpath")))

        // empty path segments filtered
        assertEquals(
            1,
            AppLoader.parseClasspathLine("${a.toAbsolutePath()}$sep$sep${missing.toAbsolutePath()}").size,
        )
    }

    @Test
    fun parseOptionsShape() {
        val (pos, opt) = parseOptions(listOf("a", "--flag", "--k=v"))
        assertEquals(listOf("a"), pos)
        assertEquals("true", opt["flag"])
        assertEquals("v", opt["k"])
    }

    private fun stubGradleApp(dir: Path): Path {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("settings.gradle.kts"), "rootProject.name = \"stub\"\n")
        Files.writeString(dir.resolve("gradlew"), "#!/bin/sh\n")
        Files.writeString(dir.resolve("gradlew.bat"), "@echo off\n")
        dir.resolve("gradlew").toFile().setExecutable(true)
        return dir
    }
}
