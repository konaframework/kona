package dev.kona.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Logging facade with channels, levels, and contextual data (Laravel Log deepen).
 */
object Log {
    private val channels = ConcurrentHashMap<String, Logger>()
    private val context = ConcurrentHashMap<String, Any?>()
    private var defaultChannel: String = "kona"

    @JvmStatic
    fun channel(name: String): LogChannel {
        val logger = channels.getOrPut(name) { LoggerFactory.getLogger(name) }
        return LogChannel(logger)
    }

    @JvmStatic
    fun stack(vararg names: String): LogChannel = channel(names.firstOrNull() ?: defaultChannel)

    @JvmStatic
    fun withContext(values: Map<String, Any?>): LogChannel {
        context.putAll(values)
        return channel(defaultChannel)
    }

    @JvmStatic
    fun shareContext(values: Map<String, Any?>) {
        context.putAll(values)
    }

    @JvmStatic
    fun flushSharedContext() {
        context.clear()
    }

    @JvmStatic
    fun sharedContext(): Map<String, Any?> = LinkedHashMap(context)

    @JvmStatic
    fun info(message: String) = channel(defaultChannel).info(message)

    @JvmStatic
    fun debug(message: String) = channel(defaultChannel).debug(message)

    @JvmStatic
    fun warn(message: String) = channel(defaultChannel).warn(message)

    @JvmStatic
    fun error(message: String) = channel(defaultChannel).error(message)

    @JvmStatic
    fun error(message: String, throwable: Throwable) = channel(defaultChannel).error(message, throwable)

    @JvmStatic
    fun setDefaultChannel(name: String) {
        defaultChannel = name
    }
}

class LogChannel internal constructor(
    private val logger: Logger,
) {
    fun info(message: String) = logger.info(format(message))

    fun debug(message: String) = logger.debug(format(message))

    fun warn(message: String) = logger.warn(format(message))

    fun error(message: String) = logger.error(format(message))

    fun error(message: String, throwable: Throwable) = logger.error(format(message), throwable)

    fun with(values: Map<String, Any?>): LogChannel {
        Log.shareContext(values)
        return this
    }

    private fun format(message: String): String {
        val shared = Log.sharedContext()
        if (shared.isEmpty()) {
            return message
        }
        return "$message $shared"
    }
}
