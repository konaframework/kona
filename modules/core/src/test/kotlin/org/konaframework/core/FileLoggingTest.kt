package org.konaframework.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.rolling.RollingFileAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Files

class FileLoggingTest {
    private lateinit var base: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        base = Files.createTempDirectory("kona-file-logging")
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
    fun bootWritesKonaLogUnderStorageLogs() {
        val app = Application(base).boot()
        Log.info("hello-storage-logs")

        val logFile = app.storagePath("logs", "kona.log")
        assertTrue(Files.isRegularFile(logFile), "expected $logFile")
        val text = Files.readString(logFile)
        assertTrue(text.contains("hello-storage-logs"), text)
        assertTrue(text.contains("Kona application booted"), text)
    }

    @Test
    fun konaCreateSeedsLoggingDefaultsAndWritesFile() {
        Files.writeString(base.resolve(".env"), "APP_NAME=LogDemo\n")
        val app = Kona.create(base).boot()
        assertEquals(true, app.config.getBoolean("logging.to_file"))
        assertEquals("kona.log", app.config.getString("logging.file"))
        assertEquals("info", app.config.getString("logging.level"))

        Log.info("from-kona-create")
        val text = Files.readString(app.storagePath("logs", "kona.log"))
        assertTrue(text.contains("from-kona-create"), text)
    }

    @Test
    fun disabledViaConfigSkipsFileAppender() {
        val app = Application(base)
        app.config.set("logging.to_file", false)
        app.boot()

        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        assertEquals(null, root.getAppender(FileLogging.APPENDER_NAME))
        assertFalse(Files.exists(app.storagePath("logs", "kona.log")))
    }

    @Test
    fun customRelativeFileNameAndLevel() {
        val app = Application(base)
        app.config.set("logging.file", "app.log")
        app.config.set("logging.level", "warn")
        app.boot()

        assertEquals(base.resolve("storage/logs/app.log").normalize(), FileLogging.resolvedLogFile(app))
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        assertEquals(Level.WARN, root.level)

        Log.warn("warn-line")
        Log.info("info-should-not-appear")
        val text = Files.readString(app.storagePath("logs", "app.log"))
        assertTrue(text.contains("warn-line"), text)
        assertFalse(text.contains("info-should-not-appear"), text)
    }

    @Test
    fun absoluteLogFilePath() {
        val absolute = base.resolve("elsewhere/custom.log")
        val app = Application(base)
        app.config.set("logging.file", absolute.toString())
        app.boot()
        Log.info("absolute-path-log")

        assertTrue(Files.isRegularFile(absolute))
        assertTrue(Files.readString(absolute).contains("absolute-path-log"))
    }

    @Test
    fun reconfigureWhenBasePathChanges() {
        val first = Application(base).boot()
        Log.info("first-base")
        assertTrue(Files.readString(first.storagePath("logs", "kona.log")).contains("first-base"))

        val otherBase = Files.createTempDirectory("kona-file-logging-2")
        try {
            val second = Application(otherBase).boot()
            Log.info("second-base")
            assertTrue(Files.readString(second.storagePath("logs", "kona.log")).contains("second-base"))

            val context = LoggerFactory.getILoggerFactory() as LoggerContext
            val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
            val appender = root.getAppender(FileLogging.APPENDER_NAME) as RollingFileAppender<*>
            assertEquals(second.storagePath("logs", "kona.log").toString(), appender.file)
        } finally {
            otherBase.toFile().deleteRecursively()
        }
    }

    @Test
    fun ensureConfiguredIsIdempotentForSamePath() {
        val app = Application(base).boot()
        FileLogging.ensureConfigured(app)
        FileLogging.ensureConfigured(app)
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        assertTrue(root.getAppender(FileLogging.APPENDER_NAME) is RollingFileAppender<*>)
    }

    @Test
    fun enabledFallsBackToEnvWhenConfigUnset() {
        val app = Application(base)
        Env.set("LOG_TO_FILE", "false")
        assertFalse(FileLogging.enabled(app))
        Env.set("LOG_TO_FILE", "true")
        assertTrue(FileLogging.enabled(app))
    }

    @Test
    fun rollPatternWithoutExtension() {
        val app = Application(base)
        app.config.set("logging.file", "plain")
        app.boot()
        Log.info("plain-name")
        assertTrue(Files.readString(app.storagePath("logs", "plain")).contains("plain-name"))
    }

    @Test
    fun resolvedLogFileUsesEnvWhenConfigUnset() {
        val app = Application(base)
        Env.set("LOG_FILE", "env.log")
        assertEquals(base.resolve("storage/logs/env.log").normalize(), FileLogging.resolvedLogFile(app))
    }

    @Test
    fun ensureConfiguredNoopsWithoutLogbackContext() {
        FileLogging.contextProvider = { null }
        Application(base).boot()
        assertFalse(Files.exists(base.resolve("storage/logs/kona.log")))
    }

    @Test
    fun resetForTestsNoopsWithoutLogbackContext() {
        FileLogging.contextProvider = { null }
        FileLogging.resetForTests()
    }

    @Test
    fun enabledTreatsUnknownEnvAsTrue() {
        val app = Application(base)
        Env.set("LOG_TO_FILE", "maybe")
        assertTrue(FileLogging.enabled(app))
    }

    @Test
    fun rootLevelFromEnvWhenConfigUnset() {
        Env.set("LOG_LEVEL", "error")
        val app = Application(base).boot()
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        assertEquals(Level.ERROR, root.level)
        assertTrue(app.isBooted())
    }

    @Test
    fun konaBootstrapUnknownLogToFileDefaultsTrue() {
        Files.writeString(base.resolve(".env"), "LOG_TO_FILE=maybe\n")
        val app = Kona.create(base).boot()
        assertEquals(true, app.config.getBoolean("logging.to_file"))
    }
}
