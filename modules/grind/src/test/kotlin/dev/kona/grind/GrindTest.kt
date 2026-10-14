package dev.kona.grind

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    }

    @Test
    fun newKotlinEmitsGradleApp() {
        val kt = temp.resolve("appkt")
        assertEquals(0, NewCommand().run(listOf(kt.toString()))) // default lang kotlin
        listOf(
            "README.md",
            ".env",
            ".gitignore",
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "bootstrap/App.kt",
            "app/Providers/AppServiceProvider.kt",
            "app/Http/Controllers/WelcomeController.kt",
            "routes/web.kt",
        ).forEach { assertTrue(Files.isRegularFile(kt.resolve(it)), it) }
        listOf("config", "resources").forEach { assertTrue(Files.isDirectory(kt.resolve(it)), it) }
        assertTrue(Files.isExecutable(kt.resolve("gradlew")))
        assertFalse(Files.exists(kt.resolve("routes/web.kt.txt")))

        val versions = GrindVersions.load()
        assertTrue(Files.readString(kt.resolve(".env")).contains("APP_NAME=appkt\n"))
        assertTrue(Files.readString(kt.resolve("settings.gradle.kts")).contains("rootProject.name = \"appkt\""))
        val build = Files.readString(kt.resolve("build.gradle.kts"))
        assertTrue(build.contains("dev.kona:kona-http:${versions.kona}"))
        assertTrue(build.contains("kotlin(\"jvm\") version \"${versions.kotlin}\""))
        assertTrue(build.contains("mainClass.set(\"bootstrap.App\")"))
        assertTrue(Files.readString(kt.resolve("bootstrap/App.kt")).contains("fun create(args: Array<String>): Application"))
        assertNoPlaceholders(kt)

        val repoRoot = System.getProperty("kona.repoRoot")
        if (repoRoot != null) {
            assertArrayEquals(
                Files.readAllBytes(Path.of(repoRoot, "gradle/wrapper/gradle-wrapper.jar")),
                Files.readAllBytes(kt.resolve("gradle/wrapper/gradle-wrapper.jar")),
            )
        }

        assertEquals(1, GrindApplication().run(arrayOf("new", kt.toString(), "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("new", temp.resolve("x2").toString(), "--lang=ruby")))
        assertEquals(1, GrindApplication().run(arrayOf("new")))
    }

    @Test
    fun newJavaEmitsGradleApp() {
        val j = temp.resolve("appjava")
        assertEquals(0, NewCommand().run(listOf(j.toString(), "--lang=java", "--kona-version=9.9.9")))
        listOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradlew",
            "bootstrap/App.java",
            "app/Providers/AppServiceProvider.java",
            "app/Http/Controllers/WelcomeController.java",
            "routes/WebRoutes.java",
        ).forEach { assertTrue(Files.isRegularFile(j.resolve(it)), it) }
        val build = Files.readString(j.resolve("build.gradle.kts"))
        assertTrue(build.contains("dev.kona:kona-http:9.9.9"))
        assertFalse(build.contains("kotlin(\"jvm\")"))
        assertTrue(Files.readString(j.resolve("bootstrap/App.java")).contains("public static Application create(String[] args)"))
        assertNoPlaceholders(j)
    }

    @Test
    fun skeletonAndVersionErrors() {
        assertThrows(GrindException::class.java) {
            NewCommand().run(listOf(temp.resolve("x").toString(), "--lang=ruby"))
        }
        val target = temp.resolve("broken")
        Files.createDirectories(target)
        assertThrows(GrindException::class.java) {
            NewCommand().copySkeleton("broken", target, emptyMap())
        }
        assertThrows(GrindException::class.java) {
            NewCommand().copyResource("skeletons/nope/missing.txt", target.resolve("x"), emptyMap())
        }
        assertThrows(GrindException::class.java) { GrindVersions.load("dev/kona/grind/missing.properties") }
        val custom = NewCommand(GrindVersions("1.2.3", "2.0.0"))
        val app = temp.resolve("custom")
        assertEquals(0, custom.run(listOf(app.toString())))
        assertTrue(Files.readString(app.resolve("build.gradle.kts")).contains("kona-http:1.2.3"))
    }

    @Test
    fun makeController() {
        val base = temp.resolve("proj")
        Files.createDirectories(base.resolve("app/Http/Controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("User", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("app/Http/Controllers/UserController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("AccountController", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("app/Http/Controllers/AccountController.kt")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "User", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "../../Evil", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "Bad-Name", "--base=$base")))
        assertTrue(Files.notExists(temp.resolve("EvilController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("Post", "--base=$base", "--lang=java")))
        // detectLang java via existing .java under app/
        val javaProj = temp.resolve("javap")
        Files.createDirectories(javaProj.resolve("app/Http/Controllers"))
        Files.writeString(javaProj.resolve("app/Http/Controllers/X.java"), "class X {}")
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$javaProj")))
        assertTrue(Files.exists(javaProj.resolve("app/Http/Controllers/AutoController.java")))
        // detectLang kotlin default when no java
        val ktProj = temp.resolve("ktp")
        Files.createDirectories(ktProj.resolve("app/Http/Controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$ktProj")))
        // app dir missing -> kotlin
        val bare = temp.resolve("bare")
        Files.createDirectories(bare)
        assertEquals(0, MakeControllerCommand().run(listOf("Bare", "--base=$bare")))
    }

    @Test
    fun makeControllerInsideGeneratedApps() {
        val kt = temp.resolve("genkt")
        val java = temp.resolve("genjava")
        NewCommand().run(listOf(kt.toString()))
        NewCommand().run(listOf(java.toString(), "--lang=java"))
        assertEquals(0, GrindApplication().run(arrayOf("make:controller", "Post", "--base=$kt")))
        assertEquals(0, GrindApplication().run(arrayOf("make:controller", "Post", "--base=$java")))
        assertTrue(Files.exists(kt.resolve("app/Http/Controllers/PostController.kt")))
        assertTrue(Files.exists(java.resolve("app/Http/Controllers/PostController.java")))
    }

    @Test
    fun serveRequiresProject() {
        val empty = temp.resolve("empty")
        Files.createDirectories(empty)
        assertEquals(1, GrindApplication().run(arrayOf("serve", "--base=$empty")))
        assertNull(ServeCommand.findProjectRoot(empty))
        val nested = fixtureProject(temp.resolve("root")).resolve("app/Http")
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
        val app = temp.resolve("spawned")
        NewCommand().run(listOf(app.toString()))
        Files.createDirectories(app.resolve("routes/nested"))

        val serve = ServeCommand(runner = runner, windows = false)
        assertEquals(7, serve.run(listOf("--base=${app.resolve("routes/nested")}", "--host=0.0.0.0", "--port=9000")))
        assertEquals(listOf(app.resolve("gradlew").toString(), "run"), calls[0].first)
        assertEquals(app, calls[0].second)
        assertEquals(mapOf("HOST" to "0.0.0.0", "PORT" to "9000"), calls[0].third)

        assertEquals(7, serve.run(listOf("--base=$app")))
        assertEquals(emptyMap<String, String>(), calls[1].third)

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
    fun serveInProcessPortFromDotEnvIsUsed() {
        java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).use { occupied ->
            val base = fixtureProject(temp.resolve("occupied"), "PORT=${occupied.localPort}\n")
            assertThrows(java.net.BindException::class.java) {
                ServeCommand().run(listOf("--base=$base", "--in-process", "--once", "--bootstrap=${FixtureApp::class.java.name}"))
            }
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
        val args = arrayOf("--base=$base")
        assertThrows(GrindException::class.java) { AppLoader(base).create(args) } // bootstrap.App absent
        assertThrows(GrindException::class.java) { AppLoader(base, NoCreateApp::class.java.name).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, InstanceCreateApp::class.java.name).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, WrongReturnApp::class.java.name).create(args) }
        val boom = assertThrows(IllegalStateException::class.java) {
            AppLoader(base, ThrowingApp::class.java.name).create(args)
        }
        assertEquals("boom 1", boom.message)
    }

    @Test
    fun parseOptionsShape() {
        val (pos, opt) = parseOptions(listOf("a", "--flag", "--k=v"))
        assertEquals(listOf("a"), pos)
        assertEquals("true", opt["flag"])
        assertEquals("v", opt["k"])
    }

    private fun assertNoPlaceholders(root: Path) {
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && !it.toString().endsWith(".jar") }.forEach {
                assertFalse(Files.readString(it).contains("{{"), "placeholder left in $it")
            }
        }
    }
}
