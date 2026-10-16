package io.konaframework.cache

import io.konaframework.redis.InMemoryRedisConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedisCacheStoreTest {
    @Test
    fun putGetForgetFlushBranches() {
        val redis = InMemoryRedisConnection()
        val store = RedisCacheStore(redis, "pfx:")
        assertNull(store.get("missing"))

        store.put("a", mapOf("x" to 1), 30)
        assertEquals(1, (store.get("a") as Map<*, *>)["x"])
        store.put("b", "short", 0)
        assertEquals("short", store.get("b"))

        store.forever("c", "keep")
        assertEquals("keep", store.get("c"))
        assertTrue(store.has("c"))
        assertTrue(store.forget("c"))
        assertFalse(store.has("c"))
        assertFalse(store.forget("c"))

        store.put("d", "1", 30)
        store.put("e", "2", 30)
        store.flush()
        assertNull(store.get("d"))
        assertNull(store.get("e"))

        // flush with no keys
        store.flush()
    }
}
