package dev.kona.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepositoryTest {
    @Test
    fun repositoryApi() {
        val store = ArrayCacheStore()
        val repo = Repository(store)
        assertSame(store, repo.store())

        assertNull(repo.get("missing"))
        assertEquals("d", repo.get("missing", "d"))
        assertTrue(repo.put("a", "1", 60))
        assertEquals("1", repo.get("a"))
        assertEquals("1", repo.get("a", "d"))
        assertTrue(repo.has("a"))
        assertFalse(repo.missing("a"))
        assertTrue(repo.missing("z"))

        assertFalse(repo.add("a", "2", 60))
        assertEquals("1", repo.get("a"))
        assertTrue(repo.add("b", "2", 60))
        assertEquals("2", repo.get("b"))

        var calls = 0
        assertEquals("1", repo.remember("a", 60) { calls++; "new" })
        assertEquals(0, calls)
        assertEquals("made", repo.remember("c", 60) { calls++; "made" })
        assertEquals(1, calls)
        assertEquals("made", repo.remember("c", 60) { calls++; "other" })
        assertEquals(1, calls)

        assertEquals("made", repo.rememberForever("c") { calls++; "x" })
        assertEquals("forever", repo.rememberForever("d") { calls++; "forever" })
        assertEquals(2, calls)

        assertEquals("1", repo.pull("a"))
        assertNull(repo.get("a"))
        assertNull(repo.pull("a"))

        assertTrue(repo.forever("e", "v"))
        assertTrue(repo.forget("e"))
        assertTrue(repo.flush())

        assertEquals(1L, repo.increment("n"))
        assertEquals(3L, repo.increment("n", 2))
        assertEquals(2L, repo.decrement("n"))
        assertEquals(0L, repo.decrement("n", 2))
        assertEquals(6L, Repository(ArrayCacheStore()).also { it.forever("s", "5") }.increment("s"))
        assertEquals(1L, Repository(ArrayCacheStore()).also { it.forever("bad", "nope") }.increment("bad"))
    }
}
