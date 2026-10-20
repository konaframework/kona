package org.konaframework.grind

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory

fun interface ConsoleAppenderLookup {
    fun find(logger: Logger): List<ConsoleAppender<ILoggingEvent>>
}

/**
 * Keeps grind's stdout as the Termwind console. Library logs (Hikari, SQL, boot)
 * stay on file appenders under `storage/logs` unless the user passed `--verbose`.
 */
object GrindLogging {
    @JvmStatic
    fun wantsVerbose(options: Map<String, String>): Boolean =
        options.containsKey("verbose") || options.containsKey("v")

    @JvmStatic
    @JvmOverloads
    fun hushConsole(verbose: Boolean, root: Any? = null, lookup: ConsoleAppenderLookup? = null) {
        if (verbose) {
            return
        }
        val logger = root ?: LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME)
        if (logger !is Logger) {
            return
        }
        val targets = if (root == null) logger.loggerContext.loggerList else listOf(logger)
        targets.forEach { target -> hushLogger(target, lookup) }
    }

    private fun hushLogger(logger: Logger, lookup: ConsoleAppenderLookup?) {
        val appenders = if (lookup == null) defaultFind(logger) else lookup.find(logger)
        appenders.forEach { appender ->
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun defaultFind(logger: Logger): List<ConsoleAppender<ILoggingEvent>> {
        val found = mutableListOf<ConsoleAppender<ILoggingEvent>>()
        val iterator = logger.iteratorForAppenders()
        while (iterator.hasNext()) {
            val appender = iterator.next()
            if (appender is ConsoleAppender) {
                found.add(appender)
            }
        }
        return found
    }
}
