package dev.kona.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class EnvConfigApplicationTest {
    @AfterEach
    fun cleanup() {
        Kona.reset()
        Env.clear()
    }

    @Test
    fun envLoadAndAccess() {
        val dir = Files.createTempDirectory("kona-env")
        val env = dir.resolve(".env")
        Files.writeString(
            env,
            """
            # comment

            export APP_NAME="Demo App"
            EMPTY=
            =novalue
            NOEQ
            BAD
            KEY=value
            QUOTED='x'
            INLINE=hello # trailing
            KEEP="hash # inside"
            PATH=/should-not-win
            NOHASH=plain
            """.trimIndent(),
        )
        Env.load(env)
        Env.load(dir.resolve("missing.env"))
        assertEquals("Demo App", Env.get("APP_NAME"))
        assertEquals("value", Env.get("KEY"))
        assertEquals("x", Env.get("QUOTED"))
        assertEquals("hello", Env.get("INLINE"))
        assertEquals("hash # inside", Env.get("KEEP"))
        assertEquals("plain", Env.get("NOHASH"))
        assertEquals("fallback", Env.getOrDefault("NOPE", "fallback"))
        // Process environment wins over .env
        val path = Env.get("PATH")
        assertTrue(path == null || path != "/should-not-win")
        Env.set("CUSTOM", "1")
        assertEquals("1", Env.require("CUSTOM"))
        assertThrows(IllegalStateException::class.java) { Env.require("MISSING_REQUIRED") }
    }

    @Test
    fun configNestedAndApplicationBoot() {
        val cfg = Config()
        cfg.loadMap("", mapOf("app" to mapOf("name" to "Kona", "debug" to true), "plain" to "v"))
        assertTrue(cfg.has("app.name"))
        assertEquals("Kona", cfg.getString("app.name"))
        assertEquals(true, cfg.get<Boolean>("app.debug"))
        assertEquals("d", cfg.get("missing", "d"))
        assertEquals("v", cfg.all()["plain"])

        val base = Files.createTempDirectory("kona-app")
        val app = Application(base)
        var registered = false
        var booted = false
        app.register(object : ServiceProvider {
            override fun register(app: Application) {
                registered = true
            }

            override fun boot(app: Application) {
                booted = true
            }
        })
        assertTrue(registered)
        assertFalse(app.isBooted())
        app.boot()
        assertTrue(booted)
        assertTrue(app.isBooted())
        app.boot() // idempotent
        app.register(object : ServiceProvider {
            override fun register(app: Application) = Unit
            // default boot()
        })
        assertEquals(base.resolve("routes").resolve("web"), app.path("routes", "web"))

        val nestedApp = Application(base)
        var nestedBooted = false
        nestedApp.register(object : ServiceProvider {
            override fun register(app: Application) = Unit

            override fun boot(app: Application) {
                app.register(object : ServiceProvider {
                    override fun register(app: Application) = Unit

                    override fun boot(app: Application) {
                        nestedBooted = true
                    }
                })
            }
        })
        nestedApp.boot()
        assertTrue(nestedBooted)
        assertEquals(app, app.resolve(Application::class.java))
        assertEquals(app, app.make(Application::class.java))
    }

    @Test
    fun konaStartAndLog() {
        val base = Files.createTempDirectory("kona-start")
        Files.writeString(base.resolve(".env"), "APP_NAME=TestApp\n")
        val app = Kona.start(arrayOf("--base=$base"))
        assertEquals("TestApp", app.config.getString("app.name"))
        assertEquals(false, app.config.get<Boolean>("app.debug"))
        assertEquals(app, Kona.app())
        Kona.reset()
        assertThrows(IllegalStateException::class.java) { Kona.app() }
        val app2 = Kona.start(base)
        assertEquals(app2.basePath, base.toAbsolutePath().normalize())
        Kona.reset()
        val cwdApp = Kona.start(emptyArray())
        assertTrue(cwdApp.basePath.isAbsolute)
        assertEquals(false, cwdApp.config.get<Boolean>("app.debug"))
        Log.info("i")
        Log.debug("d")
        Log.warn("w")
        Log.error("e")
        Log.error("e2", RuntimeException("x"))
    }
}
