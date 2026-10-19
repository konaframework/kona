package org.konaframework.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EnvConfigApplicationTest {
    @AfterEach
    fun cleanup() {
        FileLogging.resetForTests()
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
    fun configNestedDotNotation() {
        val cfg = Config()
        cfg.loadMap("", mapOf("app" to mapOf("name" to "Kona", "debug" to true), "plain" to "v"))
        assertTrue(cfg.has("app.name"))
        assertEquals("Kona", cfg.getString("app.name"))
        assertEquals(true, cfg.get<Boolean>("app.debug"))
        assertEquals("d", cfg.get("missing", "d"))
        assertNull(cfg.get<Any>("missing"))
        assertEquals("v", cfg.all()["plain"])
        assertEquals(mapOf("name" to "Kona", "debug" to true), cfg.get<Map<String, Any?>>("app"))

        cfg.set("app.debug", false)
        cfg.set("db.connections.main.host", "localhost")
        assertEquals(false, cfg.get<Boolean>("app.debug"))
        assertEquals("localhost", cfg.getString("db.connections.main.host"))
        assertEquals(mapOf("host" to "localhost"), cfg.get<Map<String, Any?>>("db.connections.main"))
        assertFalse(cfg.has("app.name.deeper"))
        assertFalse(cfg.has("db.connections.other"))

        // Scalar replaced by a nested map and vice versa.
        cfg.set("plain.child", 1)
        assertEquals(1, cfg.get<Int>("plain.child"))
        cfg.set("db", "flat")
        assertEquals("flat", cfg.getString("db"))
        assertFalse(cfg.has("db.connections"))

        // Map values are normalized into nested maps (non-string keys stringified).
        cfg.set("cache", mapOf("stores" to mapOf(1 to "redis")))
        assertEquals("redis", cfg.getString("cache.stores.1"))
        cfg.set("cache.stores.2", "file")
        assertEquals(mapOf("1" to "redis", "2" to "file"), cfg.all()["cache"].let { (it as Map<*, *>)["stores"] })

        cfg.set("nullable", null)
        assertTrue(cfg.has("nullable"))
        assertNull(cfg.get("nullable", "fallback"))

        val snapshot = cfg.all()
        cfg.set("app.name", "Changed")
        assertEquals("Kona", (snapshot["app"] as Map<*, *>)["name"])
    }

    @Test
    fun configTypedHelpers() {
        val cfg = Config()
        cfg.set("s", "text")
        cfg.set("n", 42)
        cfg.set("l", 7L)
        cfg.set("ns", " 12 ")
        cfg.set("bad", "abc")
        cfg.set("list", listOf(1))
        cfg.set("t", true)

        assertEquals("text", cfg.getString("s"))
        assertEquals("42", cfg.getString("n"))
        assertNull(cfg.getString("missing"))
        assertEquals("dflt", cfg.getString("missing", "dflt"))
        assertEquals("text", cfg.getString("s", "dflt"))

        assertEquals(42, cfg.getInt("n"))
        assertEquals(7, cfg.getInt("l"))
        assertEquals(12, cfg.getInt("ns"))
        assertNull(cfg.getInt("bad"))
        assertNull(cfg.getInt("t"))
        assertEquals(5, cfg.getInt("missing", 5))
        assertEquals(42, cfg.getInt("n", 5))

        assertEquals(true, cfg.getBoolean("t"))
        assertNull(cfg.getBoolean("list"))
        assertNull(cfg.getBoolean("bad"))
        assertEquals(true, cfg.getBoolean("missing", true))
        assertEquals(true, cfg.getBoolean("t", false))
        for (truthy in listOf("true", "1", "YES", " on ")) {
            assertEquals(true, Config.toBoolean(truthy), truthy)
        }
        for (falsy in listOf("false", "0", "no", "off", "")) {
            assertEquals(false, Config.toBoolean(falsy), falsy)
        }
        assertEquals(true, Config.toBoolean(1))
        assertEquals(false, Config.toBoolean(0.0))
        assertNull(Config.toBoolean(null))
    }

    @Test
    fun configFileModules() {
        Env.set("DB_HOST", "db.example")
        val module = ConfigFile.of("database") {
            mapOf(
                "default" to "main",
                "connections" to mapOf(
                    "main" to mapOf(
                        "host" to Env.getOrDefault("DB_HOST", "localhost"),
                        "port" to 5432,
                    ),
                ),
                "replicas" to listOf("a", "b"),
            )
        }
        assertEquals("database", module.key())
        val cfg = Config()
        cfg.load(module)
        assertEquals("db.example", cfg.getString("database.connections.main.host"))
        assertEquals(5432, cfg.getInt("database.connections.main.port"))
        assertEquals(listOf("a", "b"), cfg.get<List<String>>("database.replicas"))

        cfg.loadMap("feature", mapOf("beta" to true))
        assertEquals(true, cfg.getBoolean("feature.beta"))
    }

    @Test
    fun applicationRegisterAndBoot() {
        val base = Files.createTempDirectory("kona-app")
        val app = Application(base)
        var registered = false
        var booted = false
        val first = object : ServiceProvider {
            override fun register(app: Application) {
                registered = true
            }

            override fun boot(app: Application) {
                booted = true
            }
        }
        app.register(first)
        assertTrue(registered)
        assertFalse(booted)
        assertFalse(app.isBooted())
        app.boot()
        assertTrue(booted)
        assertTrue(app.isBooted())
        app.boot() // idempotent

        var lateBooted = false
        app.register(object : ServiceProvider {
            override fun register(app: Application) = Unit

            override fun boot(app: Application) {
                lateBooted = true
            }
        })
        assertTrue(lateBooted, "providers registered after boot boot immediately")
        app.register(object : ServiceProvider {
            override fun register(app: Application) = Unit
            // default boot()
        })
        assertEquals(base.resolve("routes").resolve("web"), app.path("routes", "web"))
        assertSame(first, app.getProvider(first.javaClass))
        assertNull(app.getProvider(OrderProviderA::class.java))
        assertEquals(3, app.providers().size)

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
        assertSame(app.container, app.make(Container::class.java))
        assertSame(app.config, app.container.resolve<Config>("config"))
    }

    @Test
    fun applicationDeduplicatesProviderClasses() {
        val events = mutableListOf<String>()
        val app = Application(Files.createTempDirectory("kona-dedupe"))
        app.register(OrderProviderA(events))
        app.register(OrderProviderA(events))
        app.boot()
        assertEquals(listOf("register:A", "boot:A"), events)
    }

    class OrderProviderA(private val events: MutableList<String>) : ServiceProvider {
        override fun register(app: Application) {
            events += "register:A"
            assertFalse(app.isBooted())
        }

        override fun boot(app: Application) {
            events += "boot:A"
        }
    }

    class OrderProviderB(private val events: MutableList<String>) : ServiceProvider {
        override fun register(app: Application) {
            events += "register:B:${app.config.getString("app.name")}"
        }

        override fun boot(app: Application) {
            events += "boot:B"
        }
    }

    class OrderProviderC(private val events: MutableList<String>) : ServiceProvider {
        override fun register(app: Application) {
            events += "register:C"
        }

        override fun boot(app: Application) {
            events += "boot:C"
        }
    }

    @Test
    fun builderRegistersAllBeforeBootingOnce() {
        val events = mutableListOf<String>()
        val base = Files.createTempDirectory("kona-builder")
        val app = Application.builder(Path.of("/ignored"))
            .basePath(base)
            .withProvider(OrderProviderA(events))
            .withProviders(OrderProviderB(events))
            .withProviders(listOf(OrderProviderC(events)))
            .configure { it.set("app.name", "Built") }
            .build()
        assertEquals(
            listOf("register:A", "register:B:Built", "register:C", "boot:A", "boot:B", "boot:C"),
            events,
        )
        assertTrue(app.isBooted())
        assertEquals(base, app.basePath)

        val cwd = Application.builder()
            .configure { it.set("logging.to_file", false) }
            .boot()
        assertTrue(cwd.isBooted())
        assertEquals(Path.of("").toAbsolutePath().normalize(), cwd.basePath)
        assertTrue(
            Application.Builder()
                .configure { it.set("logging.to_file", false) }
                .build()
                .basePath.isAbsolute,
        )
    }

    @Test
    fun konaStartAndLog() {
        val base = Files.createTempDirectory("kona-start")
        Files.writeString(base.resolve(".env"), "APP_NAME=TestApp\nAPP_DEBUG=garbage\n")
        val app = Kona.start(arrayOf("--base=$base"))
        assertEquals("TestApp", app.config.getString("app.name"))
        assertEquals(false, app.config.get<Boolean>("app.debug"))
        assertEquals(app, Kona.app())
        Kona.reset()
        assertThrows(IllegalStateException::class.java) { Kona.app() }
        val app2 = Kona.start(base)
        assertEquals(app2.basePath, base.toAbsolutePath().normalize())
        Kona.reset()
        val cwdApp = Kona.create(emptyArray())
            .configure { it.set("logging.to_file", false) }
            .boot()
        assertTrue(cwdApp.basePath.isAbsolute)
        assertEquals(false, cwdApp.config.get<Boolean>("app.debug"))
        Log.info("i")
        Log.debug("d")
        Log.warn("w")
        Log.error("e")
        Log.error("e2", RuntimeException("x"))
    }

    @Test
    fun konaBootstrapSeedsLoggingRequestsFromEnv() {
        val enabledBase = Files.createTempDirectory("kona-log-requests-on")
        Files.writeString(enabledBase.resolve(".env"), "LOG_REQUESTS=true\n")
        val enabled = Kona.start(enabledBase)
        assertEquals(true, enabled.config.getBoolean("logging.requests"))
        Kona.reset()

        val disabledBase = Files.createTempDirectory("kona-log-requests-off")
        Files.writeString(disabledBase.resolve(".env"), "LOG_REQUESTS=no\n")
        val disabled = Kona.start(disabledBase)
        assertEquals(false, disabled.config.getBoolean("logging.requests"))
        Kona.reset()

        val garbageBase = Files.createTempDirectory("kona-log-requests-garbage")
        Files.writeString(garbageBase.resolve(".env"), "LOG_REQUESTS=maybe\n")
        val garbage = Kona.start(garbageBase)
        assertNull(garbage.config.getBoolean("logging.requests"))
        Kona.reset()

        val unsetBase = Files.createTempDirectory("kona-log-requests-unset")
        Files.writeString(unsetBase.resolve(".env"), "APP_NAME=NoLogRequests\n")
        val unset = Kona.start(unsetBase)
        assertNull(unset.config.getBoolean("logging.requests"))
        Kona.reset()
    }

    @Test
    fun konaCreateBuilderShape() {
        val base = Files.createTempDirectory("kona-create")
        Files.writeString(base.resolve(".env"), "APP_DEBUG=yes\n")
        val events = mutableListOf<String>()
        val services = object : ConfigFile {
            override fun key(): String = "services"

            override fun config(): Map<String, Any?> =
                mapOf("mail" to mapOf("driver" to "smtp"))
        }
        val app = Kona.create(base)
            .configs(services)
            .withConfigs(ConfigFile.of("a") { mapOf("n" to 1) }, ConfigFile.of("b") { mapOf("n" to 2) })
            .withConfig(ConfigFile.of("extra") { mapOf("ok" to true) })
            .providers(OrderProviderA(events), OrderProviderC(events))
            .configure { it.set("app.env", "testing") }
            .boot()
        assertSame(app, Kona.app())
        assertEquals(listOf("register:A", "register:C", "boot:A", "boot:C"), events)
        assertEquals(true, app.config.getBoolean("app.debug"))
        assertEquals("testing", app.config.getString("app.env"))
        assertEquals("Kona", app.config.getString("app.name"))
        assertEquals("smtp", app.config.getString("services.mail.driver"))
        assertEquals(true, app.config.getBoolean("extra.ok"))
        assertEquals(1, app.config.getInt("a.n"))
        assertEquals(2, app.config.getInt("b.n"))

        Kona.reset()
        assertTrue(Kona.create(arrayOf("--base=$base")).build().isBooted())
        assertTrue(
            Kona.create()
                .configure { it.set("logging.to_file", false) }
                .build()
                .basePath.isAbsolute,
        )
    }
}
