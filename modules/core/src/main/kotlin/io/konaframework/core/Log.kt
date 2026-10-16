package io.konaframework.core

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Logging facade with channels, drivers, levels, and shared context.
 *
 * When a [LogManager] is bound (normally during [Application.boot]), named channels
 * resolve from `logging.channels.*` config. Without a manager, channels fall back to
 * named SLF4J loggers.
 */
object Log {
    private val fallbackChannels = ConcurrentHashMap<String, LogChannel>()
    /** Synchronized map so null context values are allowed (CHM rejects nulls). */
    private val context = Collections.synchronizedMap(LinkedHashMap<String, Any?>())

    @Volatile
    private var manager: LogManager? = null

    @Volatile
    private var defaultOverride: String? = null

    @JvmStatic
    fun bind(manager: LogManager) {
        this.manager = manager
    }

    @JvmStatic
    fun unbind() {
        manager?.flush()
        manager = null
        fallbackChannels.clear()
        defaultOverride = null
        flushSharedContext()
    }

    @JvmStatic
    fun manager(): LogManager? = manager

    @JvmStatic
    fun channel(name: String): LogChannel {
        manager?.let { return it.channel(name) }
        return fallbackChannels.getOrPut(name) {
            NamedLogChannel(LoggerFactory.getLogger(name))
        }
    }

    /** Default channel (`logging.default`, or [setDefaultChannel] override). */
    @JvmStatic
    fun channel(): LogChannel = channel(defaultChannel())

    /**
     * Log to every named channel. With a [LogManager], builds a real fan-out stack.
     * Without a manager (or with no names), uses the default channel.
     */
    @JvmStatic
    fun stack(vararg names: String): LogChannel {
        manager?.let { return it.stack(*names) }
        if (names.isEmpty()) {
            return channel()
        }
        return StackLogChannel(names.map { channel(it) }, ignoreExceptions = false)
    }

    @JvmStatic
    fun withContext(values: Map<String, Any?>): LogChannel {
        shareContext(values)
        return channel()
    }

    @JvmStatic
    fun shareContext(values: Map<String, Any?>) {
        context.putAll(values)
        values.forEach { (key, value) ->
            val rendered = if (value == null) "null" else value.toString()
            MDC.put(key, rendered)
        }
    }

    @JvmStatic
    fun flushSharedContext() {
        context.keys.forEach { MDC.remove(it) }
        context.clear()
    }

    @JvmStatic
    fun sharedContext(): Map<String, Any?> = LinkedHashMap(context)

    @JvmStatic
    fun defaultChannel(): String =
        defaultOverride
            ?: manager?.defaultChannel()
            ?: "stack"

    @JvmStatic
    fun setDefaultChannel(name: String) {
        defaultOverride = name
    }

    @JvmStatic
    fun emergency(message: String) = write(LogLevel.EMERGENCY, message, null)

    @JvmStatic
    fun alert(message: String) = write(LogLevel.ALERT, message, null)

    @JvmStatic
    fun critical(message: String) = write(LogLevel.CRITICAL, message, null)

    @JvmStatic
    fun error(message: String) = write(LogLevel.ERROR, message, null)

    @JvmStatic
    fun error(message: String, throwable: Throwable) = write(LogLevel.ERROR, message, throwable)

    @JvmStatic
    fun warning(message: String) = write(LogLevel.WARNING, message, null)

    @JvmStatic
    fun warn(message: String) = warning(message)

    @JvmStatic
    fun notice(message: String) = write(LogLevel.NOTICE, message, null)

    @JvmStatic
    fun info(message: String) = write(LogLevel.INFO, message, null)

    @JvmStatic
    fun debug(message: String) = write(LogLevel.DEBUG, message, null)

    @JvmStatic
    fun log(level: String, message: String) = write(LogLevel.parse(level), message, null)

    @JvmStatic
    fun log(level: LogLevel, message: String) = write(level, message, null)

    private fun write(level: LogLevel, message: String, throwable: Throwable?) {
        val mgr = manager
        try {
            channel().write(level, message, throwable)
        } catch (ex: Exception) {
            mgr?.writeEmergency(message, ex) ?: throw ex
        }
    }
}

/** PSR-3-shaped severity levels mapped onto SLF4J. */
enum class LogLevel {
    EMERGENCY,
    ALERT,
    CRITICAL,
    ERROR,
    WARNING,
    NOTICE,
    INFO,
    DEBUG,
    ;

    companion object {
        @JvmStatic
        @KoverExclude
        fun parse(raw: String): LogLevel = LogLevels.parse(raw)
    }
}

/**
 * A writable log destination (configured channel, stack, or SLF4J fallback).
 */
abstract class LogChannel {
    fun emergency(message: String) = write(LogLevel.EMERGENCY, message, null)

    fun alert(message: String) = write(LogLevel.ALERT, message, null)

    fun critical(message: String) = write(LogLevel.CRITICAL, message, null)

    fun error(message: String) = write(LogLevel.ERROR, message, null)

    fun error(message: String, throwable: Throwable) = write(LogLevel.ERROR, message, throwable)

    fun warning(message: String) = write(LogLevel.WARNING, message, null)

    fun warn(message: String) = warning(message)

    fun notice(message: String) = write(LogLevel.NOTICE, message, null)

    fun info(message: String) = write(LogLevel.INFO, message, null)

    fun debug(message: String) = write(LogLevel.DEBUG, message, null)

    fun log(level: String, message: String) = write(LogLevel.parse(level), message, null)

    fun log(level: LogLevel, message: String) = write(level, message, null)

    fun with(values: Map<String, Any?>): LogChannel {
        Log.shareContext(values)
        return this
    }

    protected fun format(message: String): String {
        val shared = Log.sharedContext()
        if (shared.isEmpty()) {
            return message
        }
        return "$message $shared"
    }

    abstract fun write(level: LogLevel, message: String, throwable: Throwable?)
}
