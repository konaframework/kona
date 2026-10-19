package org.konaframework.http

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.core.Application
import org.konaframework.core.Env
import org.konaframework.core.Kona
import org.konaframework.support.console.ConsoleIO
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

class LogRequestsTest {
    @AfterEach
    fun tearDown() {
        Env.clear()
        Kona.reset()
    }

    @Test
    fun defaultConstructorCoversJvmOverloads() {
        // Exercises @JvmOverloads defaults (ConsoleIO + alsoLog=true).
        val response = LogRequests().handle(Request.get("/defaults")) { Response.text("ok") }
        assertEquals(200, response.status)
    }

    @Test
    fun logsMethodPathStatusAndDuration() {
        val out = ByteArrayOutputStream()
        val console = ConsoleIO(PrintStream(out), PrintStream(out), color = false)
        val mw = LogRequests(console = console, alsoLog = false)
        val response =
            mw.handle(Request.get("/welcome")) {
                Response.text("ok", 201)
            }
        assertEquals(201, response.status)
        val line = out.toString()
        assertTrue(line.contains("GET"))
        assertTrue(line.contains("/welcome"))
        assertTrue(line.contains("201"))
        assertTrue(line.contains("ms"))
    }

    @Test
    fun alsoLogsThroughLogFacade() {
        val out = ByteArrayOutputStream()
        val console = ConsoleIO(PrintStream(out), PrintStream(out), color = false)
        LogRequests(console = console, alsoLog = true).handle(
            Request("GET", "/r", query = mapOf("x" to "1")),
        ) {
            Response.redirect("/elsewhere")
        }
        assertTrue(out.toString().contains("302") || out.toString().contains("301") || out.toString().contains("/r"))
    }

    @Test
    fun formatTargetIncludesQueryAndEmptyValues() {
        assertEquals("/a", LogRequests.formatTarget(Request.get("/a")))
        assertEquals(
            "/search?q=kona&lonely",
            LogRequests.formatTarget(
                Request(
                    "GET",
                    "/search",
                    query = linkedMapOf("q" to "kona", "lonely" to ""),
                ),
            ),
        )
    }

    @Test
    fun statusColorBuckets() {
        assertEquals(ConsoleIO.GREEN, LogRequests.statusColor(200))
        assertEquals(ConsoleIO.GREEN, LogRequests.statusColor(299))
        assertEquals(ConsoleIO.CYAN, LogRequests.statusColor(301))
        assertEquals(ConsoleIO.YELLOW, LogRequests.statusColor(404))
        assertEquals(ConsoleIO.RED, LogRequests.statusColor(500))
        assertEquals(ConsoleIO.RED, LogRequests.statusColor(199))
    }

    @Test
    fun coloredOutputUsesAnsiWhenEnabled() {
        val out = ByteArrayOutputStream()
        val console = ConsoleIO(PrintStream(out), PrintStream(out), color = true)
        LogRequests(console = console, alsoLog = false).handle(Request.get("/")) {
            Response.text("ok")
        }
        assertTrue(out.toString().contains(ConsoleIO.GREEN))
    }

    @Test
    fun requestLoggingEnabledResolution(@TempDir base: Path) {
        val app = Application(base)
        assertFalse(RequestLogging.enabled(app))

        app.config.set("app.debug", true)
        assertTrue(RequestLogging.enabled(app))

        app.config.set("logging.requests", false)
        assertFalse(RequestLogging.enabled(app))

        app.config.set("logging.requests", true)
        assertTrue(RequestLogging.enabled(app))

        val envApp = Application(base.resolve("env-app"))
        envApp.config.set("app.debug", false)
        Env.set("LOG_REQUESTS", "true")
        assertTrue(RequestLogging.enabled(envApp))
        Env.set("LOG_REQUESTS", "false")
        assertFalse(RequestLogging.enabled(envApp))
        Env.set("LOG_REQUESTS", "maybe")
        assertFalse(RequestLogging.enabled(envApp))
    }

    @Test
    fun providerPrependsLogRequestsWhenDebug(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("app.debug", true)
        app.config.set("logging.to_file", false)
        app.register(HttpServiceProvider()).boot()
        val kernel = app.resolve(HttpKernel::class.java)
        assertTrue(kernel.globalMiddleware.any { it is LogRequests })
        // Idempotent when boot logic runs again via a second provider instance.
        HttpServiceProvider().boot(app)
        assertEquals(1, kernel.globalMiddleware.count { it is LogRequests })
    }

    @Test
    fun providerSkipsWhenDisabled(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("app.debug", false)
        app.config.set("logging.requests", false)
        app.register(HttpServiceProvider()).boot()
        assertFalse(app.resolve(HttpKernel::class.java).globalMiddleware.any { it is LogRequests })
    }

    @Test
    fun konaSeedsLogRequestsFromEnv(@TempDir base: Path) {
        Env.set("LOG_REQUESTS", "true")
        val app =
            Kona.create(arrayOf("--base=$base"))
                .configure { it.set("logging.to_file", false) }
                .providers(HttpServiceProvider())
                .boot()
        assertEquals(true, app.config.getBoolean("logging.requests"))
        assertTrue(app.resolve(HttpKernel::class.java).globalMiddleware.any { it is LogRequests })
    }

    @Test
    fun accessLineSeesKernelNotFound(@TempDir base: Path) {
        val out = ByteArrayOutputStream()
        val console = ConsoleIO(PrintStream(out), PrintStream(out), color = false)
        val app = Application(base)
        app.config.set("app.debug", true)
        app.config.set("logging.to_file", false)
        app.register(HttpServiceProvider()).boot()
        val kernel = app.resolve(HttpKernel::class.java)
        kernel.setGlobalMiddleware(listOf(LogRequests(console = console, alsoLog = false)))
        val response = kernel.handle(Request.get("/missing"))
        assertEquals(404, response.status)
        assertTrue(out.toString().contains("404"))
    }
}
