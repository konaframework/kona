package org.konaframework.grind

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class GrindLoggingTest {
    @Test
    fun wantsVerboseReadsFlags() {
        assertFalse(GrindLogging.wantsVerbose(emptyMap()))
        assertTrue(GrindLogging.wantsVerbose(mapOf("verbose" to "true")))
        assertTrue(GrindLogging.wantsVerbose(mapOf("v" to "true")))
    }

    @Test
    fun hushSkipsWhenVerboseOrNonLogback() {
        val logger = isolatedLogger("grind-hush-skip")
        val console = consoleAppender(logger, "KEEP")
        logger.addAppender(console)
        GrindLogging.hushConsole(true, root = logger)
        assertNotNull(logger.getAppender("KEEP"))
        GrindLogging.hushConsole(false, root = "not-a-logger")
        assertNotNull(logger.getAppender("KEEP"))
    }

    @Test
    fun hushDetachesConsoleAndKeepsOtherAppenders() {
        val logger = isolatedLogger("grind-hush-detach")
        val console = consoleAppender(logger, "CONSOLE")
        val list = ListAppender<ILoggingEvent>().apply {
            name = "LIST"
            start()
        }
        logger.addAppender(console)
        logger.addAppender(list)
        GrindLogging.hushConsole(false, root = logger)
        assertNull(logger.getAppender("CONSOLE"))
        assertFalse(console.isStarted)
        assertNotNull(logger.getAppender("LIST"))
    }

    @Test
    fun hushUsesInjectedLookup() {
        val logger = isolatedLogger("grind-hush-lookup")
        val console = consoleAppender(logger, "LOOKED")
        logger.addAppender(console)
        GrindLogging.hushConsole(false, root = logger, lookup = { emptyList() })
        assertNotNull(logger.getAppender("LOOKED"))
        GrindLogging.hushConsole(false, root = logger, lookup = { listOf(console) })
        assertNull(logger.getAppender("LOOKED"))
    }

    @Test
    fun hushWithNoAppendersIsNoOp() {
        val logger = isolatedLogger("grind-hush-empty")
        GrindLogging.hushConsole(false, root = logger)
        assertNull(logger.getAppender("CONSOLE"))
    }

    @Test
    fun hushWalksContextWhenRootOmitted() {
        val root = LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val kept = mutableListOf<ch.qos.logback.core.Appender<ILoggingEvent>>()
        val iterator = root.iteratorForAppenders()
        while (iterator.hasNext()) {
            @Suppress("UNCHECKED_CAST")
            kept.add(iterator.next() as ch.qos.logback.core.Appender<ILoggingEvent>)
        }
        GrindLogging.hushConsole(false)
        kept.forEach { appender ->
            if (!appender.isStarted) {
                appender.start()
            }
            if (root.getAppender(appender.name) == null) {
                root.addAppender(appender)
            }
        }
        val eventsOut = java.io.ByteArrayOutputStream()
        val io = GrindIO(java.io.PrintStream(eventsOut), color = false)
        GrindMigrationEvents(io, "DONE").finished("create_users_table", 9)
        GrindMigrationEvents(io).finished("seeders", 0)
        val rendered = eventsOut.toString()
        assertTrue(rendered.contains("create_users_table"))
        assertTrue(rendered.contains("9ms"))
        assertTrue(rendered.contains("DONE"))
    }

    private fun isolatedLogger(name: String): Logger {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger(name)
        logger.detachAndStopAllAppenders()
        logger.isAdditive = false
        return logger
    }

    private fun consoleAppender(logger: Logger, name: String): ConsoleAppender<ILoggingEvent> =
        ConsoleAppender<ILoggingEvent>().apply {
            this.name = name
            context = logger.loggerContext
            start()
        }
}
