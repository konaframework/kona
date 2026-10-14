package dev.kona.cli

import dev.kona.grind.GrindApplication
import dev.kona.grind.ProcessRunner
import dev.kona.grind.ServeCommand
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
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin")))

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
        assertEquals("Welcome to Kona", spawnedBody)

        assertServesInProcess(app)
    }

    @Test
    fun javaAppBuildsAndServes() {
        assumeTrue(System.getProperty("kona.repoRoot") != null, "run via Gradle :kona-cli:test")
        val app = temp.resolve("hello-java")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=java")))
        assertEquals(0, GrindApplication().run(arrayOf("make:controller", "Post", "--base=$app")))
        gradle(app, "classes")
        assertTrue(Files.isRegularFile(app.resolve("build/classes/java/main/bootstrap/App.class")))
        assertTrue(Files.isRegularFile(app.resolve("build/classes/java/main/app/Http/Controllers/PostController.class")))
        assertServesInProcess(app)
    }

    private fun assertServesInProcess(app: Path) {
        val port = freePort()
        val bodies = mutableMapOf<String, String>()
        val serve = ServeCommand(awaitShutdown = {
            bodies["/"] = httpGet("http://127.0.0.1:$port/").body()
            bodies["/api/health"] = httpGet("http://127.0.0.1:$port/api/health").body()
        })
        assertEquals(0, serve.run(listOf("--base=$app", "--in-process", "--port=$port")))
        assertEquals("Welcome to Kona", bodies["/"])
        assertEquals("{\"status\":\"ok\"}", bodies["/api/health"])
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

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }
}
