package org.konaframework.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class LogChannelTest {
    @AfterEach
    fun cleanup() {
        Log.unbind()
        Log.flushSharedContext()
        Log.setDefaultChannel("stack")
        MDC.clear()
    }

    @Test
    fun channelsContextAndStackWithoutManager() {
        Log.setDefaultChannel("test-default")
        Log.shareContext(mapOf("requestId" to "r1"))
        assertEquals("r1", Log.sharedContext()["requestId"])
        assertEquals("r1", MDC.get("requestId"))

        Log.channel("orders").info("placed")
        Log.channel("orders").debug("dbg")
        Log.channel("orders").warn("warn")
        Log.channel("orders").warning("warn2")
        Log.channel("orders").error("err")
        Log.channel("orders").error("err2", RuntimeException("x"))
        Log.channel("orders").notice("n")
        Log.channel("orders").emergency("e")
        Log.channel("orders").alert("a")
        Log.channel("orders").critical("c")
        Log.channel("orders").log("debug", "lvl")
        Log.channel("orders").log(LogLevel.INFO, "lvl2")
        Log.channel("orders").with(mapOf("user" to 7)).info("with-user")

        Log.stack("a", "b").info("stacked")
        Log.stack().info("empty-stack")
        Log.withContext(mapOf("trace" to "t1")).info("ctx")

        assertTrue(Log.sharedContext().containsKey("user"))
        assertTrue(Log.sharedContext().containsKey("trace"))

        Log.info("i")
        Log.debug("d")
        Log.warn("w")
        Log.warning("w2")
        Log.notice("n")
        Log.emergency("em")
        Log.alert("al")
        Log.critical("cr")
        Log.error("e")
        Log.error("e2", RuntimeException("y"))
        Log.log("warn", "via-log")
        Log.log(LogLevel.ERROR, "via-enum")

        Log.flushSharedContext()
        assertTrue(Log.sharedContext().isEmpty())
        assertEquals(null, MDC.get("requestId"))
        Log.info("clean")
        assertEquals("test-default", Log.defaultChannel())
        assertEquals(null, Log.manager())
    }

    @Test
    fun logLevelParseAliasesAndUnknown() {
        assertEquals(LogLevel.EMERGENCY, LogLevel.parse("emergency"))
        assertEquals(LogLevel.EMERGENCY, LogLevel.parse("emerg"))
        assertEquals(LogLevel.ALERT, LogLevel.parse("alert"))
        assertEquals(LogLevel.CRITICAL, LogLevel.parse("critical"))
        assertEquals(LogLevel.CRITICAL, LogLevel.parse("CRIT"))
        assertEquals(LogLevel.ERROR, LogLevel.parse("error"))
        assertEquals(LogLevel.ERROR, LogLevel.parse("err"))
        assertEquals(LogLevel.WARNING, LogLevel.parse("warning"))
        assertEquals(LogLevel.WARNING, LogLevel.parse("warn"))
        assertEquals(LogLevel.NOTICE, LogLevel.parse("notice"))
        assertEquals(LogLevel.INFO, LogLevel.parse("info"))
        assertEquals(LogLevel.DEBUG, LogLevel.parse("debug"))
        assertThrows(IllegalArgumentException::class.java) { LogLevel.parse("nope") }
    }

    @Test
    fun namedChannelWriteWithThrowableCoversLevelBranches() {
        val channel = Log.channel("throwable-levels")
        val boom = RuntimeException("boom")
        channel.write(LogLevel.DEBUG, "d", boom)
        channel.write(LogLevel.INFO, "i", boom)
        channel.write(LogLevel.NOTICE, "n", boom)
        channel.write(LogLevel.WARNING, "w", boom)
        channel.write(LogLevel.ERROR, "e", boom)
    }

    @Test
    fun defaultChannelWithoutManagerOrOverrideIsStack() {
        Log.unbind()
        assertEquals("stack", Log.defaultChannel())
        assertEquals(null, Log.manager())
    }

    @Test
    fun facadeRethrowsWhenNoManagerAndDefaultChannelThrows() {
        Log.unbind()
        val boom =
            object : LogChannel() {
                override fun write(level: LogLevel, message: String, throwable: Throwable?) {
                    throw IllegalStateException("no-manager")
                }
            }
        val field = Log::class.java.getDeclaredField("fallbackChannels")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(null) as java.util.concurrent.ConcurrentHashMap<String, LogChannel>
        map["stack"] = boom
        assertThrows(IllegalStateException::class.java) { Log.info("explode") }
        map.clear()
    }

    @Test
    fun nullChannelAndShareNullValue() {
        Log.shareContext(mapOf<String, Any?>("empty" to null, "ok" to "v"))
        assertEquals("null", MDC.get("empty"))
        assertEquals("v", MDC.get("ok"))
        // non-null value uses toString path; null uses ?: "null"
        Log.info("with-null-context")
        assertEquals(null, Log.sharedContext()["empty"])
        NullLogChannel.info("ignored")
        NullLogChannel.error("ignored", RuntimeException("x"))
        ErrorLogChannel().debug("d")
        ErrorLogChannel().info("i")
        ErrorLogChannel().notice("n")
        ErrorLogChannel().warning("w")
        ErrorLogChannel().error("e")
        ErrorLogChannel().error("e2", RuntimeException("z"))
        ErrorLogChannel().critical("c")
        ErrorLogChannel().alert("a")
        ErrorLogChannel().emergency("em")
    }
}
