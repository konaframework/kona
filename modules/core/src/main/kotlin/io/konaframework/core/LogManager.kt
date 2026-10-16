package io.konaframework.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.net.SyslogAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiFunction
import java.util.function.Consumer

/**
 * Resolves named log channels from config (Laravel-shaped drivers on Logback).
 *
 * Config keys:
 * - `logging.default` — default channel name (default `stack`)
 * - `logging.channels.{name}.driver` — `stack`, `single`, `daily`, `stderr`, `stdout`,
 *   `console`, `syslog`, `errorlog`, `null`, or a name registered with [extend]
 * - stack: `channels` (comma string or list), optional `ignore_exceptions`
 * - single/daily: `path`, `level`, daily also `days` / `max_files` (default 14)
 * - stderr/stdout/console/errorlog/syslog: `level`; syslog also `host`, `facility`
 */
class LogManager(
    private val app: Application,
) {
    private val channels = ConcurrentHashMap<String, LogChannel>()
    private val creating = ThreadLocal.withInitial { mutableSetOf<String>() }
    private val taps = ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<LogChannel>>>()
    private val customDrivers = ConcurrentHashMap<String, BiFunction<String, Config, LogChannel>>()

    @Volatile
    internal var contextProvider: () -> LoggerContext? = {
        LoggerFactory.getILoggerFactory() as LoggerContext
    }

    fun defaultChannel(): String = app.config.getString("logging.default", "stack")

    fun channel(): LogChannel = channel(defaultChannel())

    fun channel(name: String): LogChannel =
        channels.getOrPut(name) { create(name) }

    /** Ephemeral stack of named channels (not cached under a config key). */
    fun stack(vararg names: String): LogChannel {
        if (names.isEmpty()) {
            return channel()
        }
        val children = names.map { channel(it) }
        return StackLogChannel(children, ignoreExceptions = false)
    }

    /** Register a tap invoked once when [channel] is first resolved. */
    fun tap(channel: String, callback: Consumer<LogChannel>): LogManager {
        taps.getOrPut(channel) { CopyOnWriteArrayList() }.add(callback)
        channels[channel]?.let { callback.accept(it) }
        return this
    }

    /** Register a custom driver name usable from `logging.channels.*.driver`. */
    fun extend(driver: String, factory: BiFunction<String, Config, LogChannel>): LogManager {
        customDrivers[driver.lowercase()] = factory
        return this
    }

    /** Replace a resolved channel (tests / advanced wiring). */
    fun set(name: String, channel: LogChannel) {
        channels[name] = channel
    }

    fun forget(name: String) {
        channels.remove(name)
    }

    fun flush() {
        channels.clear()
    }

    private fun create(name: String): LogChannel {
        val stack = creating.get()
        if (!stack.add(name)) {
            throw IllegalStateException("Circular log channel reference involving [$name]")
        }
        try {
            val channel = resolveDriver(name)
            applyTaps(name, channel)
            return channel
        } finally {
            stack.remove(name)
        }
    }

    private fun resolveDriver(name: String): LogChannel {
        val configured = app.config.getString("logging.channels.$name.driver")
        val driver =
            if (configured != null) {
                configured.lowercase()
            } else {
                builtinDriverHint(name)
            }
        if (driver == null) {
            return NamedLogChannel(LoggerFactory.getLogger(name))
        }

        customDrivers[driver]?.let { return it.apply(name, app.config) }

        return when (driver) {
            "stack" -> createStack(name)
            "single" -> createFileChannel(name, rolling = false)
            "daily" -> createFileChannel(name, rolling = true)
            "stderr", "console" -> createConsoleChannel(name, target = ConsoleTarget.SystemErr)
            "stdout" -> createConsoleChannel(name, target = ConsoleTarget.SystemOut)
            "syslog" -> createSyslogChannel(name)
            "errorlog" -> createErrorLogChannel(name)
            "null" -> NullLogChannel
            else -> throw IllegalArgumentException("Log driver [$driver] is not supported.")
        }
    }

    private fun builtinDriverHint(name: String): String? {
        val key = name.lowercase()
        return if (
            key == "stack" || key == "single" || key == "daily" ||
            key == "stderr" || key == "stdout" || key == "console" ||
            key == "syslog" || key == "errorlog" || key == "null"
        ) {
            key
        } else {
            null
        }
    }

    private fun createStack(name: String): LogChannel {
        val children = channelNames("logging.channels.$name.channels")
            .ifEmpty { listOf("single") }
            .map { channel(it) }
        val ignore = app.config.getBoolean("logging.channels.$name.ignore_exceptions", false)
        return StackLogChannel(children, ignore)
    }

    private fun createFileChannel(name: String, rolling: Boolean): LogChannel {
        val context = contextProvider() ?: return NullLogChannel
        val target = resolvePath(name)
        Files.createDirectories(target.parent)
        val logger = preparedLogger(context, name)
        // Share the root FileLogging appender when the path matches — two FileAppenders
        // on the same path silently drop events on many JVMs/OS combinations.
        val shared = sharedRootFileAppender(context, target)
        if (shared != null) {
            logger.addAppender(shared)
            return NamedLogChannel(logger)
        }
        val appender: Appender<ILoggingEvent> = if (rolling) {
            rollingFileAppender(context, name, target, days(name))
        } else {
            singleFileAppender(context, name, target)
        }
        logger.addAppender(appender)
        return NamedLogChannel(logger)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sharedRootFileAppender(
        context: LoggerContext,
        target: Path,
    ): Appender<ILoggingEvent>? {
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
        val existing = root.getAppender(FileLogging.APPENDER_NAME)
        if (existing == null) {
            return null
        }
        if (existing !is FileAppender<*>) {
            return null
        }
        if (existing.file != target.toString()) {
            return null
        }
        return existing as Appender<ILoggingEvent>
    }

    private fun createConsoleChannel(name: String, target: ConsoleTarget): LogChannel {
        val context = contextProvider() ?: return NullLogChannel
        val logger = preparedLogger(context, name)
        val encoder = patternEncoder(context)
        val appender = ConsoleAppender<ILoggingEvent>().apply {
            this.name = "KONA_CHANNEL_${name.uppercase()}"
            this.context = context
            this.target = target.logbackTarget
            this.encoder = encoder
            start()
        }
        logger.addAppender(appender)
        return NamedLogChannel(logger)
    }

    private fun createSyslogChannel(name: String): LogChannel {
        val context = contextProvider() ?: return NullLogChannel
        val logger = preparedLogger(context, name)
        val host = app.config.getString("logging.channels.$name.host", "localhost")
        val facility = app.config.getString("logging.channels.$name.facility", "USER")
        val appender = SyslogAppender().apply {
            this.name = "KONA_CHANNEL_${name.uppercase()}"
            this.context = context
            syslogHost = host
            this.facility = facility
            suffixPattern = DEFAULT_SYSLOG_SUFFIX
            start()
        }
        logger.addAppender(appender)
        return NamedLogChannel(logger)
    }

    private fun createErrorLogChannel(name: String): LogChannel {
        val context = contextProvider() ?: return ErrorLogChannel()
        val logger = preparedLogger(context, name)
        val encoder = patternEncoder(context)
        val appender = ConsoleAppender<ILoggingEvent>().apply {
            this.name = "KONA_CHANNEL_${name.uppercase()}"
            this.context = context
            this.target = ConsoleTarget.SystemErr.logbackTarget
            this.encoder = encoder
            start()
        }
        logger.addAppender(appender)
        return NamedLogChannel(logger)
    }

    private fun preparedLogger(context: LoggerContext, name: String): Logger {
        val logger = context.getLogger(loggerName(name))
        logger.detachAndStopAllAppenders()
        logger.isAdditive = false
        logger.level = resolveLevel(name)
        return logger
    }

    private fun singleFileAppender(
        context: LoggerContext,
        name: String,
        target: Path,
    ): FileAppender<ILoggingEvent> {
        val encoder = patternEncoder(context)
        return FileAppender<ILoggingEvent>().apply {
            this.name = "KONA_CHANNEL_${name.uppercase()}"
            this.context = context
            file = target.toString()
            isAppend = true
            this.encoder = encoder
            start()
        }
    }

    private fun rollingFileAppender(
        context: LoggerContext,
        name: String,
        target: Path,
        maxHistory: Int,
    ): RollingFileAppender<ILoggingEvent> {
        val encoder = patternEncoder(context)
        val appender = RollingFileAppender<ILoggingEvent>().apply {
            this.name = "KONA_CHANNEL_${name.uppercase()}"
            this.context = context
            file = target.toString()
            isAppend = true
            this.encoder = encoder
        }
        val policy = TimeBasedRollingPolicy<ILoggingEvent>().apply {
            this.context = context
            setParent(appender)
            fileNamePattern = target.parent.resolve(rollPattern(target.fileName.toString())).toString()
            this.maxHistory = maxHistory
            start()
        }
        appender.rollingPolicy = policy
        appender.start()
        return appender
    }

    private fun patternEncoder(context: LoggerContext): PatternLayoutEncoder =
        PatternLayoutEncoder().apply {
            this.context = context
            pattern = PATTERN
            start()
        }

    private fun resolvePath(name: String): Path {
        val raw = app.config.getString("logging.channels.$name.path")
            ?: app.config.getString("logging.file")
            ?: FileLogging.DEFAULT_FILE_NAME
        val path = Path.of(raw)
        if (path.isAbsolute) {
            return path.normalize()
        }
        return app.storagePath("logs").resolve(raw).normalize()
    }

    private fun resolveLevel(name: String): Level {
        val raw = app.config.getString("logging.channels.$name.level")
            ?: app.config.getString("logging.level")
            ?: Env.get("LOG_LEVEL")
            ?: "debug"
        return Level.toLevel(raw, Level.DEBUG)
    }

    private fun days(name: String): Int {
        val fromDays = app.config.getInt("logging.channels.$name.days")
        if (fromDays != null) {
            return fromDays
        }
        val fromMaxFiles = app.config.getInt("logging.channels.$name.max_files")
        if (fromMaxFiles != null) {
            return fromMaxFiles
        }
        val fromEnv = Env.get("LOG_DAILY_DAYS")
        if (fromEnv != null) {
            val parsed = fromEnv.toIntOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        return 14
    }

    private fun channelNames(key: String): List<String> {
        val raw = app.config.get<Any>(key) ?: return emptyList()
        return when (raw) {
            is Collection<*> -> normalizeChannelNames(raw)
            is Array<*> -> normalizeChannelNames(raw.asList())
            is String -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> listOf(raw.toString())
        }
    }

    private fun normalizeChannelNames(raw: Iterable<*>): List<String> {
        val names = mutableListOf<String>()
        for (item in raw) {
            if (item == null) {
                continue
            }
            val trimmed = item.toString().trim()
            if (trimmed.isNotEmpty()) {
                names += trimmed
            }
        }
        return names
    }

    private fun applyTaps(name: String, channel: LogChannel) {
        taps[name]?.forEach { it.accept(channel) }
    }

    private fun loggerName(channel: String): String = "kona.channel.$channel"

    private fun rollPattern(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) {
            fileName.substring(0, dot) + ".%d{yyyy-MM-dd}" + fileName.substring(dot)
        } else {
            "$fileName.%d{yyyy-MM-dd}"
        }
    }

    /**
     * Last-resort writer when channel resolution fails mid-write.
     * Path from `logging.channels.emergency.path`, else `logging.file`, else `kona.log`.
     */
    fun writeEmergency(message: String, cause: Throwable? = null) {
        try {
            val path = emergencyPath()
            Files.createDirectories(path.parent)
            val line = buildString {
                append(java.time.Instant.now())
                append(" EMERGENCY ")
                append(message)
                if (cause != null) {
                    append(" — ")
                    append(cause.message ?: cause.javaClass.name)
                }
                append('\n')
            }
            Files.writeString(
                path,
                line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND,
            )
        } catch (_: Exception) {
            System.err.println("EMERGENCY: $message")
            cause?.printStackTrace(System.err)
        }
    }

    private fun emergencyPath(): Path {
        val raw = app.config.getString("logging.channels.emergency.path")
            ?: app.config.getString("logging.file")
            ?: FileLogging.DEFAULT_FILE_NAME
        val path = Path.of(raw)
        if (path.isAbsolute) {
            return path.normalize()
        }
        return app.storagePath("logs").resolve(raw).normalize()
    }

    companion object {
        const val PATTERN: String =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
        private const val DEFAULT_SYSLOG_SUFFIX: String = "[%thread] %logger %msg"

        /** Seeds default channel map when not already present (idempotent per key). */
        @JvmStatic
        @KoverExclude
        fun seedDefaults(config: Config): Config = LogManagerDefaults.seed(config)
    }

    private enum class ConsoleTarget(val logbackTarget: String) {
        SystemOut("System.out"),
        SystemErr("System.err"),
    }
}

/** Drops every message (driver `null`). */
object NullLogChannel : LogChannel() {
    override fun write(level: LogLevel, message: String, throwable: Throwable?) = Unit
}

/**
 * Writes through JUL when Logback is unavailable (driver `errorlog` fallback).
 */
class ErrorLogChannel : LogChannel() {
    private val jul = java.util.logging.Logger.getLogger("kona.errorlog")

    override fun write(level: LogLevel, message: String, throwable: Throwable?) {
        val text = format(message)
        when (level) {
            LogLevel.DEBUG, LogLevel.INFO, LogLevel.NOTICE ->
                if (throwable != null) jul.log(java.util.logging.Level.INFO, text, throwable)
                else jul.info(text)
            LogLevel.WARNING ->
                if (throwable != null) jul.log(java.util.logging.Level.WARNING, text, throwable)
                else jul.warning(text)
            LogLevel.ERROR, LogLevel.CRITICAL, LogLevel.ALERT, LogLevel.EMERGENCY ->
                if (throwable != null) jul.log(java.util.logging.Level.SEVERE, text, throwable)
                else jul.severe(text)
        }
    }
}

internal class NamedLogChannel(
    private val logger: org.slf4j.Logger,
) : LogChannel() {
    override fun write(level: LogLevel, message: String, throwable: Throwable?) {
        val text = format(message)
        when (level) {
            LogLevel.DEBUG ->
                if (throwable != null) logger.debug(text, throwable) else logger.debug(text)
            LogLevel.INFO, LogLevel.NOTICE ->
                if (throwable != null) logger.info(text, throwable) else logger.info(text)
            LogLevel.WARNING ->
                if (throwable != null) logger.warn(text, throwable) else logger.warn(text)
            LogLevel.ERROR, LogLevel.CRITICAL, LogLevel.ALERT, LogLevel.EMERGENCY ->
                if (throwable != null) logger.error(text, throwable) else logger.error(text)
        }
    }
}

internal class StackLogChannel(
    private val channels: List<LogChannel>,
    private val ignoreExceptions: Boolean,
) : LogChannel() {
    override fun write(level: LogLevel, message: String, throwable: Throwable?) {
        for (channel in channels) {
            try {
                channel.write(level, message, throwable)
            } catch (ex: Exception) {
                if (!ignoreExceptions) {
                    throw ex
                }
            }
        }
    }
}
