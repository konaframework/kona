package dev.kona.support.date

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class KonaDateTest {
    private val fixed = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun arithmeticComparisonsAndFormat() {
        val now = KonaDate.now(ZoneOffset.UTC, fixed)
        assertEquals(Instant.parse("2024-06-15T12:00:00Z"), now.toInstant())
        assertEquals("2024-06-15", now.toLocalDate().toString())
        assertEquals(12, now.toLocalDateTime().hour)
        assertEquals(1_718_452_800L, now.timestamp())

        val later = now.addSeconds(30).addMinutes(1).addHours(1).addDays(1)
        assertTrue(later.isAfter(now))
        assertTrue(now.isBefore(later))
        assertEquals(now, later.subSeconds(30).subMinutes(1).subHours(1).subDays(1))
        assertEquals(3600L + 60 + 30 + 86400, later.diffInSeconds(now))

        assertTrue(now.subDays(1).isPast(fixed))
        assertTrue(now.addDays(1).isFuture(fixed))
        assertFalse(now.addDays(1).isPast(fixed))
        assertFalse(now.subDays(1).isFuture(fixed))

        assertEquals("2024-06-15", now.toDateString())
        assertEquals("2024-06-15 12:00:00", now.toDateTimeString())
        assertEquals("2024-06-15T12:00:00Z", now.toIso8601String())
        assertEquals("15/06", now.format("dd/MM"))
        assertEquals(now, now.copy())
        assertEquals(now.toString(), now.toIso8601String())
    }

    @Test
    fun factoriesParseEquals() {
        val fromInstant = KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z"))
        assertEquals("2024-01-01", fromInstant.toDateString())

        val iso = KonaDate.parse("2024-01-02T03:04:05Z")
        assertEquals(3, iso.toLocalDateTime().hour)
        val local = KonaDate.parse("2024-01-02 03:04:05")
        assertEquals(3, local.toLocalDateTime().hour)

        val today = KonaDate.today(ZoneOffset.UTC)
        assertEquals(0, today.toLocalDateTime().hour)

        val a = KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
        val b = KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, KonaDate.ofInstant(Instant.parse("2024-01-02T00:00:00Z")))
        assertNotEquals(a, "nope")
        assertNotEquals(
            a,
            KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.ofHours(1)),
        )
    }

    @Test
    fun deepenCalendarBoundaries() {
        val d = KonaDate.ofInstant(Instant.parse("2024-01-15T12:30:00Z"), ZoneOffset.UTC)
        assertEquals("2024-01-15T00:00:00Z", d.startOfDay().toIso8601String())
        assertTrue(d.endOfDay().toLocalDateTime().hour == 23)
        assertEquals("2024-01-22", d.addWeeks(1).toDateString())
        assertEquals("2024-02-15", d.addMonths(1).toDateString())
        assertEquals("2025-01-15", d.addYears(1).toDateString())
        assertEquals("2024-01-08", d.subWeeks(1).toDateString())
        assertEquals("2023-12-15", d.subMonths(1).toDateString())
        assertEquals("2023-01-15", d.subYears(1).toDateString())
        assertEquals(1, d.diffInDays(d.subDays(1)))
        assertTrue(d.isSameDay(d.addHours(1)))
        assertFalse(d.isSameDay(d.addDays(1)))
        val shifted = d.setTimezone(ZoneOffset.ofHours(2))
        assertEquals("2024-01-15 14:30:00", shifted.toDateTimeString())
    }
}
