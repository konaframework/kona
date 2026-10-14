package dev.kona.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogChannelTest {
    @AfterEach
    fun cleanup() {
        Log.flushSharedContext()
        Log.setDefaultChannel("kona")
    }

    @Test
    fun channelsContextAndStack() {
        Log.setDefaultChannel("test-default")
        Log.shareContext(mapOf("requestId" to "r1"))
        assertEquals("r1", Log.sharedContext()["requestId"])

        Log.channel("orders").info("placed")
        Log.channel("orders").debug("dbg")
        Log.channel("orders").warn("warn")
        Log.channel("orders").error("err")
        Log.channel("orders").error("err2", RuntimeException("x"))
        Log.channel("orders").with(mapOf("user" to 7)).info("with-user")

        Log.stack("a", "b").info("stacked")
        Log.stack().info("empty-stack")
        Log.withContext(mapOf("trace" to "t1")).info("ctx")

        assertTrue(Log.sharedContext().containsKey("user"))
        assertTrue(Log.sharedContext().containsKey("trace"))

        Log.info("i")
        Log.debug("d")
        Log.warn("w")
        Log.error("e")
        Log.error("e2", RuntimeException("y"))

        Log.flushSharedContext()
        assertTrue(Log.sharedContext().isEmpty())
        Log.info("clean")
    }
}
