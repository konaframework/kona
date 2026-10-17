package org.konaframework.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ArrayCacheStoreTest {
    @Test
    fun putGetExpireForeverFlush() {
        var now = Instant.parse("2024-01-01T00:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        }
        val store = ArrayCacheStore(clock)
        assertNull(store.get("missing"))
        assertFalse(store.has("missing"))

        store.put("a", "1", 5)
        assertEquals("1", store.get("a"))
        assertTrue(store.has("a"))
        now = now.plusSeconds(6)
        assertNull(store.get("a"))

        store.put("b", "2", 0)
        assertNull(store.get("b"))

        store.forever("c", "3")
        assertEquals("3", store.get("c"))
        assertTrue(store.forget("c"))
        assertFalse(store.forget("c"))
        store.forever("d", null)
        assertNull(store.get("d"))
        assertTrue(store.has("d").not() || store.get("d") == null)
        // null value: has() is get() != null → false
        assertFalse(store.has("d"))

        store.put("e", "x", 10)
        store.flush()
        assertNull(store.get("e"))
    }
}
