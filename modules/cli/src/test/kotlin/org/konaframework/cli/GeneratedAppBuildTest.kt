package org.konaframework.cli

import org.konaframework.grind.GrindApplication
import org.konaframework.grind.ProcessRunner
import org.konaframework.grind.ServeCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Golden path: `kona new` → Gradle build → `grind serve` / in-process HTTP.
 */
class GeneratedAppBuildTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun kotlinAppBuildsAndServes() {
        assumeTrue(System.getProperty("kona.repoRoot") != null, "run via Gradle :kona-cli:test")
        val app = temp.resolve("hello-kt")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin", "--no-interaction")))

        val port = freePort()
        var spawnedBody = ""
        val runner = ProcessRunner { command, dir, env ->
            val log = temp.resolve("run.log").toFile()
            val builder = ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log)
            builder.environment().putAll(env)
            val process = builder.start()
            try {
                spawnedBody = awaitBody("http://127.0.0.1:$port/", process) { log.readText() }
            } finally {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly().waitFor(30, TimeUnit.SECONDS)
            }
            0
        }
        assertEquals(0, ServeCommand(runner = runner).run(listOf("--base=$app", "--port=$port")))
        assertTrue(spawnedBody.contains("<!DOCTYPE html>", ignoreCase = true), spawnedBody)
        assertTrue(spawnedBody.contains("docs.konaframework.org"), spawnedBody)

        assertServesInProcess(app)
    }

    @Test
    fun javaAppBuildsAndServes() {
        assumeTrue(System.getProperty("kona.repoRoot") != null, "run via Gradle :kona-cli:test")
        val app = temp.resolve("hello-java")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=java", "--no-interaction")))
        assertEquals(0, GrindApplication().run(arrayOf("make:controller", "Post", "--base=$app")))
        gradle(app, "classes")
        assertTrue(Files.isRegularFile(app.resolve("build/classes/java/main/bootstrap/App.class")))
        assertTrue(Files.isRegularFile(app.resolve("build/classes/java/main/app/http/controllers/PostController.class")))
        assertServesInProcess(app)
    }

    @Test
    fun cremaKitServesHtmlWelcome() {
        assumeTrue(System.getProperty("kona.repoRoot") != null, "run via Gradle :kona-cli:test")
        val app = temp.resolve("crema-kt")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin", "--kit=crema", "--no-interaction")))
        gradle(app, "classes")
        assertServesInProcess(app)
    }

    @Test
    fun brewKitServesBrandedWelcome() {
        assumeTrue(System.getProperty("kona.repoRoot") != null, "run via Gradle :kona-cli:test")
        assumeTrue(
            konaBrewPublished() || System.getenv("KONA_HOME") != null,
            "need kona-brew on Maven Central / mavenLocal, or KONA_HOME for includeBuild",
        )
        val app = temp.resolve("brew-kt")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin", "--kit=brew", "--no-interaction")))
        gradle(app, "classes")
        assertServesInProcess(app)
    }

    @Test
    fun inertiaKitServesCremaWelcomeAndLogin() {
        assumeTrue(System.getProperty("kona.repoRoot") != null, "run via Gradle :kona-cli:test")
        assumeTrue(
            konaInertiaPublished() || System.getenv("KONA_HOME") != null,
            "need kona-inertia on Maven Central / mavenLocal, or KONA_HOME for includeBuild",
        )
        val app = temp.resolve("inertia-kt")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin", "--kit=inertia", "--no-interaction")))
        gradle(app, "classes")
        val port = freePort()
        var home = ""
        var login = ""
        val serve = ServeCommand(awaitShutdown = {
            home = httpGet("http://127.0.0.1:$port/").body()
            login = httpGet("http://127.0.0.1:$port/login").body()
        })
        assertEquals(0, serve.run(listOf("--base=$app", "--in-process", "--port=$port")))
        assertTrue(home.contains("<!DOCTYPE html>", ignoreCase = true), home)
        assertTrue(home.contains("docs.konaframework.org"), home)
        assertTrue(login.contains("Sign in") || login.contains("email"), login)
    }

    private fun assertServesInProcess(app: Path) {
        val port = freePort()
        val bodies = mutableMapOf<String, String>()
        val serve = ServeCommand(awaitShutdown = {
            bodies["/"] = httpGet("http://127.0.0.1:$port/").body()
            bodies["/api/health"] = httpGet("http://127.0.0.1:$port/api/health").body()
            bodies["/login"] = httpGet("http://127.0.0.1:$port/login").body()
            bodies["/branding/kona-logo.svg"] = httpGet("http://127.0.0.1:$port/branding/kona-logo.svg").body()
        })
        assertEquals(0, serve.run(listOf("--base=$app", "--in-process", "--port=$port")))
        val home = bodies["/"] ?: ""
        assertTrue(home.contains("<!DOCTYPE html>", ignoreCase = true), home)
        assertTrue(home.contains("docs.konaframework.org"), home)
        assertTrue(home.contains("Your app is ready") || home.contains("/login"), home)
        assertTrue(home.contains("#df8723") || home.contains("#DF8723"), home)
        assertEquals("{\"status\":\"ok\"}", bodies["/api/health"])
        assertTrue((bodies["/login"] ?: "").contains("email", ignoreCase = true), bodies["/login"])
        assertTrue((bodies["/branding/kona-logo.svg"] ?: "").contains("svg", ignoreCase = true))
    }

    private fun gradle(app: Path, vararg tasks: String) {
        val log = temp.resolve("gradle-${tasks.joinToString("-")}.log").toFile()
        val process = ProcessBuilder(listOf(app.resolve("gradlew").toString(), "--console=plain") + tasks)
            .directory(app.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        val finished = process.waitFor(10, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
        }
        assertTrue(finished && process.exitValue() == 0, "gradle ${tasks.toList()} failed:\n${log.readText()}")
    }

    private fun awaitBody(url: String, process: Process, output: () -> String): String {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
        while (System.nanoTime() < deadline) {
            assertTrue(process.isAlive, "gradlew run exited early:\n${output()}")
            val response = runCatching { httpGet(url) }.getOrNull()
            if (response != null) {
                return response.body()
            }
            Thread.sleep(250)
        }
        throw AssertionError("app did not answer $url:\n${output()}")
    }

    private fun httpGet(url: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun konaBrewPublished(): Boolean {
        val home = System.getProperty("user.home")
        val base = Path.of(home, ".m2", "repository", "org", "konaframework", "kona-brew")
        if (!Files.isDirectory(base)) return false
        return Files.walk(base).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".jar") } }
    }

    private fun konaInertiaPublished(): Boolean {
        val home = System.getProperty("user.home")
        val base = Path.of(home, ".m2", "repository", "org", "konaframework", "kona-inertia")
        if (!Files.isDirectory(base)) return false
        return Files.walk(base).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".jar") } }
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }
}
