package io.konaframework.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Attaches a daily rolling file appender under `storage/logs` (default `kona.log`).
 *
 * Console appenders from Logback defaults or an app `logback.xml` are left alone.
 * Idempotent: reuses the existing appender when the target path is unchanged;
 * otherwise detaches and recreates it (useful when tests boot apps with different bases).
 */
object FileLogging {
    const val APPENDER_NAME: String = "KONA_FILE"
    const val DEFAULT_FILE_NAME: String = "kona.log"
    private const val PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

    /** Overridable for tests that exercise a missing logger context. */
    @Volatile
    internal var contextProvider: () -> LoggerContext? = {
        // kona-core depends on logback-classic; cast is intentional.
        LoggerFactory.getILoggerFactory() as LoggerContext
    }

    /**
     * Ensures the root logger writes to [resolvedLogFile] when file logging is enabled.
     *
     * Disable with config `logging.to_file=false` or env `LOG_TO_FILE=false`.
     * File name via `logging.file` / `LOG_FILE` (relative names resolve under `storage/logs`).
     * Root level via `logging.level` / `LOG_LEVEL` (default `info`).
     */
    @JvmStatic
    fun ensureConfigured(app: Application) {
        if (!enabled(app)) {
            return
        }
        val context = contextProvider() ?: return
        val target = resolvedLogFile(app)
        Files.createDirectories(target.parent)
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        applyRootLevel(app, root)

        val existing = root.getAppender(APPENDER_NAME)
        if (existing is RollingFileAppender<*> && existing.file == target.toString()) {
            return
        }
        if (existing != null) {
            root.detachAppender(APPENDER_NAME)
            existing.stop()
        }

        val encoder = PatternLayoutEncoder().apply {
            this.context = context
            pattern = PATTERN
            start()
        }

        val appender = RollingFileAppender<ILoggingEvent>().apply {
            name = APPENDER_NAME
            this.context = context
            file = target.toString()
            isAppend = true
            this.encoder = encoder
        }

        val policy = TimeBasedRollingPolicy<ILoggingEvent>().apply {
            this.context = context
            setParent(appender)
            fileNamePattern = target.parent.resolve(rollPattern(target.fileName.toString())).toString()
            maxHistory = 14
            start()
        }
        appender.rollingPolicy = policy
        appender.start()
        root.addAppender(appender)
    }

    @JvmStatic
    fun enabled(app: Application): Boolean {
        app.config.getBoolean("logging.to_file")?.let { return it }
        return Config.toBoolean(Env.getOrDefault("LOG_TO_FILE", "true")) ?: true
    }

    @JvmStatic
    fun resolvedLogFile(app: Application): Path {
        val name = app.config.getString("logging.file")
            ?: Env.get("LOG_FILE")
            ?: DEFAULT_FILE_NAME
        val path = Path.of(name)
        if (path.isAbsolute) {
            return path.normalize()
        }
        return app.storagePath("logs").resolve(name).normalize()
    }

    @JvmStatic
    fun resetForTests() {
        try {
            val context = contextProvider() ?: return
            val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
            val existing = root.getAppender(APPENDER_NAME) ?: return
            root.detachAppender(APPENDER_NAME)
            existing.stop()
        } finally {
            contextProvider = { LoggerFactory.getILoggerFactory() as LoggerContext }
        }
    }

    private fun applyRootLevel(app: Application, root: Logger) {
        val raw = app.config.getString("logging.level")
            ?: Env.get("LOG_LEVEL")
            ?: "info"
        root.level = Level.toLevel(raw, Level.INFO)
    }

    private fun rollPattern(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) {
            fileName.substring(0, dot) + ".%d{yyyy-MM-dd}" + fileName.substring(dot)
        } else {
            "$fileName.%d{yyyy-MM-dd}"
        }
    }
}
