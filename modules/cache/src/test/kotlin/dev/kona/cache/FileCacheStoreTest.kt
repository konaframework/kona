package dev.kona.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FileCacheStoreTest {
    @Test
    fun roundTripExpiryForeverFlush(@TempDir dir: Path) {
        var now = Instant.parse("2024-01-01T00:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        }
        val store = FileCacheStore(dir, clock)
        assertNull(store.get("missing"))

        store.put("a", mapOf("n" to 1), 5)
        assertEquals(1, (store.get("a") as Map<*, *>)["n"])
        now = now.plusSeconds(6)
        assertNull(store.get("a"))

        store.put("b", "gone", 0)
        assertNull(store.get("b"))

        store.forever("c", "keep")
        assertEquals("keep", store.get("c"))
        assertTrue(store.has("c"))
        assertTrue(store.forget("c"))
        assertFalse(store.has("c"))
        assertFalse(store.forget("c"))

        store.put("weird/key!", "x", 30)
        assertEquals("x", store.get("weird/key!"))

        store.put("d", "1", 30)
        store.flush()
        assertNull(store.get("d"))

        // flush when directory removed
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        store.flush()
    }

    @Test
    fun missingValueNodeAndEmptyExpires(@TempDir dir: Path) {
        val store = FileCacheStore(dir)
        val path = dir.resolve("bare.json")
        Files.writeString(path, """{"expires":null}""")
        // key "bare" maps to bare.json — but forever writes expires null as putNull
        store.forever("bare2", "v")
        assertEquals("v", store.get("bare2"))

        Files.writeString(dir.resolve("empty_exp.json"), """{"expires":"","value":"x"}""")
        // empty expires text is treated as no expiry (isNotEmpty is false)
        // pathFor("empty_exp") -> empty_exp.json
        assertEquals("x", FileCacheStore(dir).get("empty_exp"))
    }
}
