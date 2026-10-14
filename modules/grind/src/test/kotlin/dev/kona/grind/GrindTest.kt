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
        val javaProj = temp.resolve("javap")
        Files.createDirectories(javaProj.resolve("app/Http/Controllers"))
        Files.writeString(javaProj.resolve("app/Http/Controllers/X.java"), "class X {}")
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$javaProj")))
        assertTrue(Files.exists(javaProj.resolve("app/Http/Controllers/AutoController.java")))
        val ktProj = temp.resolve("ktp")
        Files.createDirectories(ktProj.resolve("app/Http/Controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$ktProj")))
        val bare = temp.resolve("bare")
        Files.createDirectories(bare)
        assertEquals(0, MakeControllerCommand().run(listOf("Bare", "--base=$bare")))
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
        val app = stubGradleApp(temp.resolve("spawned"))
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
