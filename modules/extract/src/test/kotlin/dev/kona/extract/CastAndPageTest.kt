package dev.kona.extract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

class CastAndPageTest {
    private val zone = ZoneId.systemDefault()

    @Test
    fun numericBooleanAndStringCasts() {
        CastType.entries.forEach { assertNull(it.get(null)) }
        assertEquals(42, CastType.INT.get(42L))
        assertEquals(42, CastType.INT.get(" 42 "))
        assertEquals(1, CastType.INT.get(true))
        assertEquals(0, CastType.INT.get(false))
        assertEquals(7L, CastType.LONG.get(7))
        assertEquals(7L, CastType.LONG.get("7"))
        assertEquals(1.5, CastType.DOUBLE.get(1.5f))
        assertEquals(2.25, CastType.DOUBLE.get("2.25"))
        assertEquals(true, CastType.BOOLEAN.get("yes"))
        assertEquals(false, CastType.BOOLEAN.get(0))
        assertThrows<ExtractException> { CastType.BOOLEAN.get("maybe") }
        assertEquals("5", CastType.STRING.get(5))
        assertThrows<NumberFormatException> { CastType.INT.get("x") }
    }

    @Test
    fun jsonCasts() {
        assertEquals(mapOf("a" to 1, "b" to listOf(true)), CastType.JSON.get("{\"a\":1,\"b\":[true]}"))
        val map = mapOf("k" to "v")
        assertSame(map, CastType.JSON.get(map))
        assertEquals("{\"k\":\"v\"}", CastType.JSON.set(map))
        assertEquals("[1,2]", CastType.JSON.set(listOf(1, 2)))
        assertEquals("{}", CastType.JSON.set("{}"))
        assertNull(CastType.JSON.set(null))
        assertEquals(5, CastType.INT.set(5))
    }

    @Test
    fun dateTimeCasts() {
        val local = LocalDateTime.of(2024, 1, 2, 3, 4, 5)
        val instant = local.atZone(zone).toInstant()
        assertSame(local, CastType.DATETIME.get(local))
        assertEquals(local, CastType.DATETIME.get(Timestamp.valueOf(local)))
        assertEquals(local, CastType.DATETIME.get(instant))
        assertEquals(local, CastType.DATETIME.get("2024-01-02 03:04:05"))

        assertSame(instant, CastType.INSTANT.get(instant))
        assertEquals(instant, CastType.INSTANT.get(Timestamp.valueOf(local)))
        assertEquals(instant, CastType.INSTANT.get(Date.from(instant)))
        assertEquals(instant, CastType.INSTANT.get(local))
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), CastType.INSTANT.get(OffsetDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC)))
        assertEquals(Instant.parse("2024-01-02T03:04:05Z"), CastType.INSTANT.get(" 2024-01-02T03:04:05Z "))
        assertEquals(instant, CastType.INSTANT.get("2024-01-02T03:04:05"))
        val midnight = LocalDate.of(2024, 1, 2).atStartOfDay(zone).toInstant()
        assertEquals(midnight, CastType.INSTANT.get(LocalDate.of(2024, 1, 2)))
        assertEquals(midnight, CastType.INSTANT.get(java.sql.Date.valueOf(LocalDate.of(2024, 1, 2))))
        val bad = assertThrows<ExtractException> { CastType.INSTANT.get(12) }
        assertTrue(bad.message!!.contains("java.lang.Integer"))
    }

    @Test
    fun pageMath() {
        val empty = Page(emptyList<String>(), 0, 10, 1)
        assertEquals(1, empty.lastPage)
        assertTrue(empty.isEmpty())
        assertFalse(empty.hasMorePages())

        val page = Page(listOf("a", "b"), 5, 2, 1)
        assertEquals(3, page.lastPage)
        assertTrue(page.hasMorePages())
        assertFalse(page.isEmpty())
        val mapped = page.map { it.uppercase() }
        assertEquals(listOf("A", "B"), mapped.data)
        assertEquals(3, mapped.lastPage)
        assertEquals(
            mapOf("data" to listOf("a", "b"), "total" to 5L, "per_page" to 2, "current_page" to 1, "last_page" to 3),
            page.toMap(),
        )
        assertEquals("Page(currentPage=1, lastPage=3, perPage=2, total=5)", page.toString())
        assertEquals(2, Page(listOf(1), 4, 2, 2).lastPage)

        val tag = Tag().forceFill(mapOf("code" to "x"))
        assertEquals(listOf(mapOf("code" to "x")), Page(listOf(tag), 1, 1, 1).toMap()["data"])
    }
}
