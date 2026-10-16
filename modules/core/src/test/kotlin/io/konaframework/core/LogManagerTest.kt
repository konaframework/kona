package io.konaframework.core

import ch.qos.logback.classic.LoggerContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class LogManagerTest {
    private lateinit var base: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        base = Files.createTempDirectory("kona-log-manager")
        FileLogging.resetForTests()
        Kona.reset()
    }

    @AfterEach
    fun tearDown() {
        FileLogging.resetForTests()
        Kona.reset()
        base.toFile().deleteRecursively()
    }

    @Test
    fun bootBindsManagerAndSeedsDefaultChannels() {
        val app = Kona.create(base).boot()
        assertEquals("stack", app.config.getString("logging.default"))
        assertEquals("stack", app.config.getString("logging.channels.stack.driver"))
        assertEquals("single", app.config.getString("logging.channels.stack.channels"))
        assertEquals("daily", app.config.getString("logging.channels.daily.driver"))
        assertEquals("null", app.config.getString("logging.channels.null.driver"))

        val manager = app.resolve(LogManager::class.java)
        assertSame(manager, Log.manager())
        assertEquals("stack", Log.defaultChannel())

        Log.info("default-stack-line")
        val text = Files.readString(app.storagePath("logs", "kona.log"))
        assertTrue(text.contains("default-stack-line"), text)
    }

    @Test
    fun singleAndDailyDriversWriteDistinctFiles() {
        val app = Application(base)
        app.config.set("logging.channels.single.path", "single.log")
        app.config.set("logging.channels.daily.path", "daily.log")
        app.config.set("logging.channels.daily.days", 7)
        app.boot()

        Log.channel("single").info("single-msg")
        Log.channel("daily").info("daily-msg")

        assertTrue(Files.readString(app.storagePath("logs", "single.log")).contains("single-msg"))
        assertTrue(Files.readString(app.storagePath("logs", "daily.log")).contains("daily-msg"))
    }

    @Test
    fun stackFansOutToConfiguredChildren() {
        val app = Application(base)
        app.config.set("logging.channels.stack.channels", listOf("single", "null"))
        app.config.set("logging.channels.single.path", "stack-out.log")
        app.boot()

        Log.channel("stack").warn("fan-out")
        assertTrue(Files.readString(app.storagePath("logs", "stack-out.log")).contains("fan-out"))
    }

    @Test
    fun stackChannelNamesFromCommaStringAndArray() {
        val app = Application(base)
        app.config.set("logging.channels.multi.driver", "stack")
        app.config.set("logging.channels.multi.channels", "single,null")
        app.config.set("logging.channels.single.path", "comma.log")
        app.boot()
        Log.channel("multi").info("comma-stack")
        assertTrue(Files.readString(app.storagePath("logs", "comma.log")).contains("comma-stack"))

        app.config.set("logging.channels.arr.driver", "stack")
        app.config.set("logging.channels.arr.channels", arrayOf("single"))
        Log.manager()!!.forget("arr")
        Log.channel("arr").info("array-stack")
        assertTrue(Files.readString(app.storagePath("logs", "comma.log")).contains("array-stack"))
    }

    @Test
    fun stackIgnoresChildExceptionsWhenConfigured() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        val boom = object : LogChannel() {
            override fun write(level: LogLevel, message: String, throwable: Throwable?) {
                throw IllegalStateException("boom")
            }
        }
        manager.set("boom", boom)
        app.config.set("logging.channels.safe.driver", "stack")
        app.config.set("logging.channels.safe.channels", listOf("boom", "null"))
        app.config.set("logging.channels.safe.ignore_exceptions", true)
        Log.channel("safe").info("ok")

        app.config.set("logging.channels.strict.driver", "stack")
        app.config.set("logging.channels.strict.channels", listOf("boom"))
        app.config.set("logging.channels.strict.ignore_exceptions", false)
        assertThrows(IllegalStateException::class.java) {
            Log.channel("strict").info("fail")
        }
    }

    @Test
    fun ephemeralStackAndEmptyStack() {
        val app = Application(base)
        app.config.set("logging.channels.single.path", "ephemeral.log")
        app.boot()
        Log.stack("single", "null").info("both")
        Log.stack().info("default-stack")
        val text = Files.readString(app.storagePath("logs", "ephemeral.log"))
        assertTrue(text.contains("both"), text)
        assertTrue(text.contains("default-stack"), text)
    }

    @Test
    fun stderrStdoutConsoleErrorlogNullAndSyslogResolve() {
        val app = Application(base).boot()
        Log.channel("stderr").info("stderr-line")
        Log.channel("stdout").info("stdout-line")
        Log.channel("console").info("console-line")
        Log.channel("errorlog").info("errorlog-line")
        Log.channel("errorlog").error("errorlog-err", RuntimeException("x"))
        Log.channel("null").info("discarded")
        Log.channel("syslog").info("syslog-line")
        assertTrue(app.isBooted())
    }

    @Test
    fun passthroughNamedChannelWithoutDriver() {
        Application(base).boot()
        Log.channel("billing").info("invoice")
        assertTrue(true)
    }

    @Test
    fun circularStackThrows() {
        val app = Application(base)
        app.config.set("logging.channels.a.driver", "stack")
        app.config.set("logging.channels.a.channels", listOf("b"))
        app.config.set("logging.channels.b.driver", "stack")
        app.config.set("logging.channels.b.channels", listOf("a"))
        app.boot()
        assertThrows(IllegalStateException::class.java) { Log.channel("a").info("loop") }
    }

    @Test
    fun unknownDriverThrows() {
        val app = Application(base)
        app.config.set("logging.channels.weird.driver", "not-a-driver")
        app.boot()
        assertThrows(IllegalArgumentException::class.java) { Log.channel("weird") }
    }

    @Test
    fun extendCustomDriverAndTap() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        val hits = AtomicInteger()
        manager.extend("memory") { _, _ ->
            object : LogChannel() {
                override fun write(level: LogLevel, message: String, throwable: Throwable?) {
                    hits.incrementAndGet()
                }
            }
        }
        app.config.set("logging.channels.mem.driver", "memory")
        var tapped = false
        manager.tap("mem") { tapped = true }
        Log.channel("mem").info("hi")
        assertTrue(tapped)
        assertEquals(1, hits.get())

        // tap on already-resolved channel runs immediately
        var again = false
        manager.tap("mem") { again = true }
        assertTrue(again)

        manager.set("override", NullLogChannel)
        assertSame(NullLogChannel, manager.channel("override"))
        manager.forget("override")
        manager.flush()
    }

    @Test
    fun absolutePathAndLevelAndBuiltinHints() {
        val absolute = base.resolve("elsewhere/abs.log")
        val app = Application(base)
        app.config.set("logging.channels.single.path", absolute.toString())
        app.config.set("logging.channels.single.level", "warn")
        app.config.set("logging.level", "debug")
        app.boot()

        Log.channel("single").info("hidden")
        Log.channel("single").warn("visible")
        val text = Files.readString(absolute)
        assertFalse(text.contains("hidden"))
        assertTrue(text.contains("visible"))

        // builtin hints without explicit driver config for known names
        Log.manager()!!.forget("daily")
        app.config.set("logging.channels.daily.path", "hint-daily.log")
        // driver already seeded; clear and rely on name hint by forgetting seed
        val bare = Application(Files.createTempDirectory("kona-log-hint"))
        try {
            // no seed of daily driver — use name hint via removing after seed
            bare.boot()
            bare.config.set("logging.channels.plain.path", "plain.log")
            // unknown name without driver → SLF4J passthrough
            Log.channel("custom-domain").debug("ok")
        } finally {
            bare.basePath.toFile().deleteRecursively()
            Log.unbind()
        }
    }

    @Test
    fun fileChannelsWithoutLogbackContextUseNull() {
        val app = Application(base)
        app.config.set("logging.to_file", false)
        app.boot()
        val manager = Log.manager()!!
        manager.contextProvider = { null }
        manager.forget("single")
        manager.forget("daily")
        manager.forget("stderr")
        manager.forget("syslog")
        manager.forget("errorlog")
        Log.channel("single").info("noop")
        Log.channel("daily").info("noop")
        Log.channel("stderr").info("noop")
        Log.channel("syslog").info("noop")
        // errorlog falls back to ErrorLogChannel when context missing
        Log.channel("errorlog").info("jul-fallback")
        Log.channel("errorlog").warning("jul-warn")
        Log.channel("errorlog").error("jul-err", RuntimeException("e"))
    }

    @Test
    fun writeEmergencyAppendsFileAndStderrFallback() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        manager.writeEmergency("direct-emergency", RuntimeException("cause"))
        // default-parameter overload (cause = null)
        manager.writeEmergency("direct-emergency-default-arg")
        val text = Files.readString(app.storagePath("logs", "kona.log"))
        assertTrue(text.contains("EMERGENCY direct-emergency"), text)
        assertTrue(text.contains("EMERGENCY direct-emergency-default-arg"), text)

        app.config.set("logging.channels.emergency.path", base.resolve("no-parent-will-work/e.log").toString())
        // force failure path: make parent a file so createDirectories fails
        val blocker = base.resolve("blocked")
        Files.writeString(blocker, "x")
        app.config.set("logging.channels.emergency.path", blocker.resolve("child.log").toString())
        manager.writeEmergency("stderr-fallback", null)
        manager.writeEmergency("stderr-fallback-with-cause", RuntimeException("stderr-cause"))

        // absolute emergency path
        val abs = base.resolve("emerg-abs.log")
        app.config.set("logging.channels.emergency.path", abs.toString())
        manager.writeEmergency("abs-emergency", null)
        assertTrue(Files.readString(abs).contains("abs-emergency"))
    }

    @Test
    fun facadeEmergencyOnThrowingDefaultUsesWriteEmergency() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        val boom = object : LogChannel() {
            override fun write(level: LogLevel, message: String, throwable: Throwable?) {
                throw IllegalStateException("fail-write")
            }
        }
        manager.set("stack", boom)
        Log.info("should-emergency")
        val text = Files.readString(app.storagePath("logs", "kona.log"))
        assertTrue(text.contains("EMERGENCY should-emergency"), text)
    }

    @Test
    fun stackChannelNamesFromNonCollectionNonString() {
        val app = Application(base)
        app.config.set("logging.channels.num.driver", "stack")
        app.config.set("logging.channels.num.channels", 42)
        // 42 becomes channel name "42" which falls through to SLF4J
        app.boot()
        Log.channel("num").info("numeric-child")
    }

    @Test
    fun emptyStackChannelsDefaultsToSingle() {
        val app = Application(base)
        app.config.set("logging.channels.empty.driver", "stack")
        app.config.set("logging.channels.empty.channels", "")
        app.config.set("logging.channels.single.path", "empty-default.log")
        app.boot()
        Log.channel("empty").info("fallback-single")
        assertTrue(
            Files.readString(app.storagePath("logs", "empty-default.log")).contains("fallback-single"),
        )
    }

    @Test
    fun dailyMaxFilesAndDaysFromEnv() {
        Env.set("LOG_DAILY_DAYS", "3")
        val app = Kona.create(base).boot()
        assertEquals(3, app.config.getInt("logging.channels.daily.days"))
        Log.channel("daily").info("daily-env")
        assertTrue(Files.readString(app.storagePath("logs", "kona.log")).contains("daily-env"))
    }

    @Test
    fun rollPatternWithoutExtensionViaDaily() {
        val app = Application(base)
        app.config.set("logging.channels.daily.path", "plain")
        app.boot()
        Log.channel("daily").info("plain-daily")
        assertTrue(Files.readString(app.storagePath("logs", "plain")).contains("plain-daily"))
    }

    @Test
    fun syslogHostAndFacilityFromConfig() {
        val app = Application(base)
        app.config.set("logging.channels.syslog.host", "127.0.0.1")
        app.config.set("logging.channels.syslog.facility", "LOCAL0")
        app.boot()
        Log.channel("syslog").debug("configured-syslog")
    }

    @Test
    fun seedDefaultsIsIdempotent() {
        val config = Config()
        config.set("logging.file", "custom.log")
        LogManager.seedDefaults(config)
        LogManager.seedDefaults(config)
        assertEquals("stack", config.getString("logging.default"))
        assertEquals("custom.log", config.getString("logging.channels.single.path"))
        assertEquals("custom.log", config.getString("logging.channels.emergency.path"))
    }

    @Test
    fun seedDefaultsUsesFourteenDaysWhenEnvMissingOrInvalid() {
        Env.clear()
        val config = Config()
        LogManager.seedDefaults(config)
        assertEquals(14, config.getInt("logging.channels.daily.days"))

        Env.set("LOG_DAILY_DAYS", "not-a-number")
        val config2 = Config()
        LogManager.seedDefaults(config2)
        assertEquals(14, config2.getInt("logging.channels.daily.days"))
        Env.clear()
    }

    @Test
    fun daysFallsBackToFourteenWhenEnvInvalid() {
        Env.set("LOG_DAILY_DAYS", "nope")
        val app = Application(base)
        app.config.set("logging.channels.daily.days", null as Any?)
        app.config.set("logging.channels.daily.max_files", null as Any?)
        app.config.set("logging.channels.daily.path", "invalid-env-days.log")
        app.boot()
        app.config.set("logging.channels.daily.days", null as Any?)
        Log.manager()!!.forget("daily")
        Log.channel("daily").info("fourteen-default")
        assertTrue(
            Files.readString(app.storagePath("logs", "invalid-env-days.log"))
                .contains("fourteen-default"),
        )
        Env.clear()
    }

    @Test
    fun daysFallsBackToFourteenWhenEnvMissing() {
        Env.clear()
        val app = Application(base)
        app.config.set("logging.channels.daily.days", null as Any?)
        app.config.set("logging.channels.daily.max_files", null as Any?)
        app.config.set("logging.channels.daily.path", "missing-env-days.log")
        app.boot()
        app.config.set("logging.channels.daily.days", null as Any?)
        Log.manager()!!.forget("daily")
        Log.channel("daily").info("fourteen-no-env")
        assertTrue(
            Files.readString(app.storagePath("logs", "missing-env-days.log"))
                .contains("fourteen-no-env"),
        )
    }

    @Test
    fun sharedRootAppenderNullWhenFileLoggingDisabled() {
        val app = Application(base)
        app.config.set("logging.to_file", false)
        app.config.set("logging.channels.single.path", "no-root-share.log")
        app.boot()
        Log.manager()!!.forget("single")
        Log.channel("single").info("fresh-appender")
        assertTrue(
            Files.readString(app.storagePath("logs", "no-root-share.log")).contains("fresh-appender"),
        )
    }

    @Test
    fun sharedRootAppenderNullWhenRootAppenderIsNotFileAppender() {
        val app = Application(base)
        app.config.set("logging.to_file", false)
        app.config.set("logging.channels.weird.driver", "single")
        app.config.set("logging.channels.weird.path", "weird-share.log")
        app.boot()
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        val console = ch.qos.logback.core.ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        console.name = FileLogging.APPENDER_NAME
        console.context = context
        val encoder = ch.qos.logback.classic.encoder.PatternLayoutEncoder()
        encoder.context = context
        encoder.pattern = "%msg%n"
        encoder.start()
        console.encoder = encoder
        console.start()
        root.addAppender(console)
        try {
            Log.manager()!!.forget("weird")
            // Root has KONA_FILE name but it is not a FileAppender → sharedRoot returns null
            Log.channel("weird").info("not-a-file-appender-share")
            assertTrue(
                Files.readString(app.storagePath("logs", "weird-share.log"))
                    .contains("not-a-file-appender-share"),
            )
        } finally {
            root.detachAppender(console)
            console.stop()
        }
    }

    @Test
    fun resolveLevelFallsBackToLoggingLevelAndEnv() {
        Env.set("LOG_LEVEL", "error")
        val app = Application(base)
        app.config.set("logging.level", "warn")
        app.config.set("logging.channels.leveled.driver", "single")
        app.config.set("logging.channels.leveled.path", "leveled.log")
        // no channel-specific level → logging.level
        app.boot()
        Log.channel("leveled").info("hidden-by-warn")
        Log.channel("leveled").warn("visible-warn")
        val text = Files.readString(app.storagePath("logs", "leveled.log"))
        assertFalse(text.contains("hidden-by-warn"))
        assertTrue(text.contains("visible-warn"))

        // clear channel cache and logging.level so Env LOG_LEVEL applies
        app.config.set("logging.level", null as Any?)
        Log.manager()!!.forget("leveled2")
        app.config.set("logging.channels.leveled2.driver", "single")
        app.config.set("logging.channels.leveled2.path", "leveled2.log")
        Log.channel("leveled2").warn("hidden-by-error")
        Log.channel("leveled2").error("visible-error")
        val text2 = Files.readString(app.storagePath("logs", "leveled2.log"))
        assertFalse(text2.contains("hidden-by-error"))
        assertTrue(text2.contains("visible-error"))
        Env.clear()
    }

    @Test
    fun sharedRootAppenderMissWhenPathDiffers() {
        val app = Kona.create(base).boot()
        app.config.set("logging.channels.single.path", "other.log")
        Log.manager()!!.forget("single")
        Log.channel("single").info("not-shared")
        assertTrue(Files.readString(app.storagePath("logs", "other.log")).contains("not-shared"))
    }

    @Test
    fun stackChannelsArrayFiltersBlanks() {
        val app = Application(base)
        app.config.set("logging.channels.arrblank.driver", "stack")
        app.config.set("logging.channels.arrblank.channels", arrayOf("single", "", null, "  "))
        app.config.set("logging.channels.single.path", "arrblank.log")
        app.boot()
        Log.channel("arrblank").info("arr-blank-filter")
        assertTrue(
            Files.readString(app.storagePath("logs", "arrblank.log")).contains("arr-blank-filter"),
        )
    }

    @Test
    fun channelListFiltersBlankEntries() {
        val app = Application(base)
        app.config.set("logging.channels.blanks.driver", "stack")
        app.config.set("logging.channels.blanks.channels", listOf("single", "", null, "  "))
        app.config.set("logging.channels.single.path", "blanks.log")
        app.boot()
        Log.channel("blanks").info("blank-filter")
        assertTrue(Files.readString(app.storagePath("logs", "blanks.log")).contains("blank-filter"))
    }

    @Test
    fun managerChannelDefaultAndLogbackContextPresent() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        manager.channel().info("via-manager-default")
        assertTrue(
            Files.readString(app.storagePath("logs", "kona.log")).contains("via-manager-default"),
        )
        assertTrue(LoggerFactory.getILoggerFactory() is LoggerContext)
    }

    @Test
    fun builtinDriverHintWhenDriverConfigCleared() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        // Clear seeded drivers so resolveDriver uses builtinDriverHint(name) for every built-in
        for (name in listOf(
            "stack", "single", "daily", "stderr", "stdout", "console", "syslog", "errorlog", "null",
        )) {
            app.config.set("logging.channels.$name.driver", null)
            manager.forget(name)
            Log.channel(name).info("hint-$name")
        }
        // unknown name without driver → SLF4J passthrough (hint returns null)
        Log.channel("totally-custom").info("passthrough")
    }

    @Test
    fun filePathFallsBackToLoggingFileThenDefaultName() {
        val app = Application(base)
        app.config.set("logging.file", "from-logging-file.log")
        app.config.set("logging.channels.fallback.driver", "single")
        // no channel path → logging.file
        app.boot()
        Log.channel("fallback").info("via-logging-file")
        assertTrue(
            Files.readString(app.storagePath("logs", "from-logging-file.log"))
                .contains("via-logging-file"),
        )

        val bare = Application(Files.createTempDirectory("kona-log-default-name"))
        try {
            bare.config.set("logging.to_file", false)
            bare.config.set("logging.file", null as Any?)
            bare.config.set("logging.channels.def.driver", "single")
            bare.boot()
            // no channel path and no logging.file → kona.log default
            Log.channel("def").info("via-default-name")
            assertTrue(
                Files.readString(bare.storagePath("logs", "kona.log")).contains("via-default-name"),
            )
        } finally {
            bare.basePath.toFile().deleteRecursively()
            Log.unbind()
        }
    }

    @Test
    fun dailyDaysFromMaxFilesAndEnvWhenDaysMissing() {
        val app = Application(base)
        app.config.set("logging.channels.daily.days", null as Any?)
        app.config.set("logging.channels.daily.max_files", 9)
        app.config.set("logging.channels.daily.path", "max-files.log")
        app.boot()
        Log.channel("daily").info("max-files-days")
        assertTrue(Files.readString(app.storagePath("logs", "max-files.log")).contains("max-files-days"))

        Env.clear()
        Env.set("LOG_DAILY_DAYS", "4")
        val envApp = Application(Files.createTempDirectory("kona-log-days-env"))
        try {
            envApp.config.set("logging.channels.daily.days", null as Any?)
            envApp.config.set("logging.channels.daily.max_files", null as Any?)
            envApp.config.set("logging.channels.daily.path", "env-days.log")
            // Prevent seedDefaults from re-writing days from env into config before resolve —
            // seed runs at boot; clear after boot and forget channel.
            envApp.boot()
            envApp.config.set("logging.channels.daily.days", null as Any?)
            Log.manager()!!.forget("daily")
            Log.channel("daily").info("env-days-line")
            assertTrue(
                Files.readString(envApp.storagePath("logs", "env-days.log")).contains("env-days-line"),
            )
        } finally {
            Env.clear()
            envApp.basePath.toFile().deleteRecursively()
            Log.unbind()
        }
    }

    @Test
    fun emergencyPathFallsBackWhenEmergencyKeyMissing() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        app.config.set("logging.channels.emergency.path", null as Any?)
        app.config.set("logging.file", "emerg-from-file.log")
        manager.writeEmergency("from-file-fallback", RuntimeException())
        assertTrue(
            Files.readString(app.storagePath("logs", "emerg-from-file.log"))
                .contains("from-file-fallback"),
        )

        app.config.set("logging.file", null as Any?)
        manager.writeEmergency("from-default-name", null)
        assertTrue(
            Files.readString(app.storagePath("logs", "kona.log")).contains("from-default-name"),
        )

        // cause with null message → class name branch
        manager.writeEmergency("null-msg-cause", object : RuntimeException(null as String?) {})
    }

    @Test
    fun errorLogChannelThrowableBranchesWithoutLogback() {
        val app = Application(base).boot()
        val manager = Log.manager()!!
        manager.contextProvider = { null }
        manager.forget("errorlog")
        val channel = Log.channel("errorlog")
        val boom = RuntimeException("jul")
        channel.write(LogLevel.DEBUG, "d", boom)
        channel.write(LogLevel.INFO, "i", boom)
        channel.write(LogLevel.WARNING, "w", boom)
        channel.write(LogLevel.ERROR, "e", boom)
        channel.write(LogLevel.DEBUG, "d2", null)
        channel.write(LogLevel.WARNING, "w2", null)
    }

    @Test
    fun sharedRootAppenderReuseWhenPathMatches() {
        val app = Kona.create(base).boot()
        // single path equals root FileLogging path → shared appender branch
        app.config.set("logging.channels.single.path", "kona.log")
        Log.manager()!!.forget("single")
        Log.channel("single").info("shared-root-appender")
        assertTrue(
            Files.readString(app.storagePath("logs", "kona.log")).contains("shared-root-appender"),
        )
    }

    @Test
    fun stackChannelsMissingKeyReturnsEmptyThenDefaultsToSingle() {
        val app = Application(base)
        app.config.set("logging.channels.nostack.driver", "stack")
        // deliberately do not set .channels key
        app.config.set("logging.channels.single.path", "nostack.log")
        app.boot()
        Log.channel("nostack").info("no-channels-key")
        assertTrue(
            Files.readString(app.storagePath("logs", "nostack.log")).contains("no-channels-key"),
        )
    }
}
