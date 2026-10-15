package dev.kona.extract

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SimpleCursorPaginateTest {
    @BeforeEach
    fun setUp() {
        TestDb.create()
        for (name in listOf("Ada", "Bob", "Cid", "Dee", "Eve")) {
            User.create(mapOf("name" to name))
        }
    }

    @AfterEach
    fun tearDown() {
        Database.clearGlobal()
    }

    @Test
    fun queryBuilderSimplePaginate() {
        val page = Database.global().table("users").orderBy("id").simplePaginate(2, 1)
        assertEquals(2, page.data.size)
        assertTrue(page.hasMorePages())
        assertEquals(1, page.currentPage)
        val last = Database.global().table("users").orderBy("id").simplePaginate(2, 3)
        assertEquals(1, last.data.size)
        assertFalse(last.hasMorePages())
        assertThrows(IllegalArgumentException::class.java) {
            Database.global().table("users").simplePaginate(0)
        }
    }

    @Test
    fun queryBuilderCursorPaginate() {
        val first = Database.global().table("users").cursorPaginate(2)
        assertEquals(2, first.data.size)
        assertNotNull(first.nextCursor)
        assertNull(first.prevCursor)
        assertTrue(first.hasMorePages())

        val second = Database.global().table("users").cursorPaginate(2, first.nextCursor)
        assertEquals(2, second.data.size)
        assertNotNull(second.nextCursor)
        assertNotNull(second.prevCursor)

        val third = Database.global().table("users").cursorPaginate(2, second.nextCursor)
        assertEquals(1, third.data.size)
        assertNull(third.nextCursor)
        assertFalse(third.hasMorePages())

        assertEquals("Ada", CursorPage.decode(CursorPage.encode("Ada")))
        assertTrue(first.toMap().containsKey("next_cursor"))
        assertTrue(first.map { it["name"] }.data.contains("Ada"))
    }

    @Test
    fun modelSimpleAndCursorPaginate() {
        val simple = User.query().orderBy("id").simplePaginate(2, 2)
        assertEquals(2, simple.data.size)
        assertTrue(simple.hasMorePages())
        assertEquals("Cid", simple.data[0]["name"])

        val cursor = User.query().orderBy("id").cursorPaginate(2)
        assertEquals(2, cursor.data.size)
        val next = User.query().orderBy("id").cursorPaginate(2, cursor.nextCursor)
        assertEquals("Cid", next.data[0]["name"])
        assertEquals(true, simple.map { it["name"] }.toMap()["has_more"])
        assertFalse(simple.isEmpty())
        assertFalse(cursor.isEmpty())

        val defaults = Database.global().table("users").orderBy("id").simplePaginate()
        assertEquals(5, defaults.data.size)
        assertFalse(defaults.hasMorePages())
        val cursorDefaults = Database.global().table("users").cursorPaginate()
        assertEquals(5, cursorDefaults.data.size)

        assertEquals(5, User.simplePaginate().data.size)
        assertEquals(2, User.simplePaginate(2).data.size)
        assertEquals(2, User.query().simplePaginate(2).data.size)
        assertEquals(5, User.query().simplePaginate().data.size)
        val companionSimple = User.simplePaginate(2, 1)
        assertEquals(2, companionSimple.data.size)
        assertTrue((companionSimple.toMap()["data"] as List<*>).isNotEmpty())
        val companionCursor = User.cursorPaginate(2)
        assertNotNull(companionCursor.nextCursor)
        assertNotNull(User.cursorPaginate().nextCursor ?: companionCursor.nextCursor)
        assertEquals(2, User.cursorPaginate(2, null, "id").data.size)
        assertTrue((companionCursor.toMap()["data"] as List<*>).isNotEmpty())
        assertTrue((companionCursor.map { it }.toMap()["data"] as List<*>).isNotEmpty())
        assertEquals(15, Database.global().table("users").simplePaginate().perPage)
        assertEquals(15, Database.global().table("users").cursorPaginate().perPage)
        assertThrows(IllegalArgumentException::class.java) {
            Database.global().table("users").cursorPaginate(0)
        }
        // Empty page with cursor: hasMore false, page empty → next/prev null branches.
        val pastEnd = Database.global().table("users").where("id", ">", 9999).cursorPaginate(2, CursorPage.encode(9999))
        assertTrue(pastEnd.isEmpty())
        assertNull(pastEnd.nextCursor)
        assertNull(pastEnd.prevCursor)
        assertEquals("null", CursorPage.decode(CursorPage.encode(null)))
    }
}
