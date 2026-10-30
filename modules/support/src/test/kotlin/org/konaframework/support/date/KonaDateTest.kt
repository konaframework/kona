package org.konaframework.support.date

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class KonaDateTest {
    private val fixed = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC)

    @AfterEach
    fun resetZone() {
        KonaDate.resetDefaultTimezone()
    }

    @Test
    fun arithmeticComparisonsAndFormat() {
        val now = KonaDate.now(ZoneOffset.UTC, fixed)
        assertEquals(Instant.parse("2024-06-15T12:00:00Z"), now.toInstant())
        assertEquals("2024-06-15", now.toLocalDate().toString())
        assertEquals(12, now.toLocalDateTime().hour)
        assertEquals(1_718_452_800L, now.timestamp())
        assertEquals(1_718_452_800_000L, now.timestampMs())
        assertEquals(2024, now.year())
        assertEquals(6, now.month())
        assertEquals(15, now.day())
        assertEquals(12, now.hour())
        assertEquals(0, now.minute())
        assertEquals(0, now.second())
        assertEquals(0, now.milli())
        assertEquals(0, now.micro())
        assertEquals(6, now.dayOfWeek())
        assertEquals(167, now.dayOfYear())
        assertEquals(2, now.quarter())
        assertNotNull(now.weekOfYear())
        assertNotNull(now.toZonedDateTime())
        assertNotNull(now.toOffsetDateTime())

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
        assertEquals("Jun 15, 2024", now.toFormattedDateString())
        assertEquals("2024-06-15 12:00:00", now.toDateTimeString())
        assertEquals("12:00:00", now.toTimeString())
        assertEquals("2024-06-15T12:00:00Z", now.toIso8601String())
        assertTrue(now.toRfc3339String().contains("2024-06-15"))
        assertEquals(now.toRfc3339String(), now.toAtomString())
        assertEquals("15/06", now.format("dd/MM"))
        assertEquals(now, now.copy())
        assertEquals(now, now.clone())
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

    @Test
    fun defaultTimezoneUsedByFactories() {
        assertEquals(ZoneOffset.UTC, KonaDate.defaultTimezone())
        KonaDate.setDefaultTimezone(ZoneId.of("UTC"))
        assertNotNull(KonaDate.now())
        assertEquals("2024-01-01", KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z")).toDateString())
        assertEquals("2024-01-02", KonaDate.parse("2024-01-02T03:04:05Z").toDateString())
        assertEquals(0, KonaDate.today().toLocalDateTime().hour)
        KonaDate.setDefaultTimezone(ZoneId.of("Africa/Nairobi"))
        assertEquals(ZoneId.of("Africa/Nairobi"), KonaDate.defaultTimezone())
        assertEquals(3, KonaDate.parse("2024-01-02T00:00:00Z").toLocalDateTime().hour)
    }

    @Test
    fun parseFormatsFactoriesTimezoneAndIso() {
        val utc = ZoneOffset.UTC
        val offset = KonaDate.parse("2024-01-02T03:04:05+02:00", utc)
        assertEquals(Instant.parse("2024-01-02T01:04:05Z"), offset.toInstant())

        val zoned = KonaDate.parse("2024-01-02T03:04:05+01:00[Europe/Paris]")
        assertEquals(Instant.parse("2024-01-02T02:04:05Z"), zoned.toInstant())

        val day = KonaDate.parse("2024-01-02", utc)
        assertEquals("2024-01-02T00:00:00Z", day.toIso())
        assertEquals(utc, day.timezone())

        assertThrows(java.time.format.DateTimeParseException::class.java) {
            KonaDate.parse("not-a-date")
        }

        val local = LocalDateTime.of(2024, 6, 15, 12, 0)
        assertEquals("2024-06-15T12:00:00Z", KonaDate.ofLocalDateTime(local).toIso8601String())
        assertEquals(
            "2024-06-15T10:00:00Z",
            KonaDate.ofLocalDateTime(local, ZoneOffset.ofHours(2)).toIso8601String(),
        )
        assertEquals("2024-06-15T00:00:00Z", KonaDate.ofLocalDate(LocalDate.of(2024, 6, 15)).toIso())
        assertEquals(
            "2024-06-14T22:00:00Z",
            KonaDate.ofLocalDate(LocalDate.of(2024, 6, 15), ZoneOffset.ofHours(2)).toIso(),
        )
        assertEquals(
            "2024-06-15T12:00:00Z",
            KonaDate.ofZonedDateTime(ZonedDateTime.of(local, utc)).toIso(),
        )
        assertEquals(
            "2024-06-15T10:00:00Z",
            KonaDate.ofOffsetDateTime(OffsetDateTime.of(local, ZoneOffset.ofHours(2))).toIso(),
        )
        assertEquals(
            ZoneOffset.ofHours(2),
            KonaDate.ofOffsetDateTime(OffsetDateTime.of(local, ZoneOffset.ofHours(2))).timezone(),
        )
    }

    @Test
    fun carbonFactoriesAndSingularArithmetic() {
        val created = KonaDate.create(2024, 3, 10, 8, 30, 15, ZoneOffset.UTC)
        assertEquals("2024-03-10 08:30:15", created.toDateTimeString())
        assertEquals("2024-03-10", KonaDate.createFromDate(2024, 3, 10, ZoneOffset.UTC).toDateString())
        assertEquals(9, KonaDate.createFromTime(9, 15, 0, ZoneOffset.UTC).hour())
        assertEquals(
            Instant.ofEpochSecond(1_700_000_000),
            KonaDate.createFromTimestamp(1_700_000_000L, ZoneOffset.UTC).toInstant(),
        )
        assertEquals(
            Instant.ofEpochMilli(1_700_000_000_123L),
            KonaDate.createFromTimestampMs(1_700_000_000_123L, ZoneOffset.UTC).toInstant(),
        )
        assertEquals(KonaDate.today(ZoneOffset.UTC).subDay(), KonaDate.yesterday(ZoneOffset.UTC))
        assertEquals(KonaDate.today(ZoneOffset.UTC).addDay(), KonaDate.tomorrow(ZoneOffset.UTC))
        assertNotNull(KonaDate.yesterday())
        assertNotNull(KonaDate.tomorrow())
        assertNotNull(KonaDate.create(2024, 1, 1))
        assertNotNull(KonaDate.createFromDate(2024, 1, 1))
        assertNotNull(KonaDate.createFromTime(1, 2))
        assertNotNull(KonaDate.createFromTimestamp(0))
        assertNotNull(KonaDate.createFromTimestampMs(0))

        val base = KonaDate.ofInstant(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC)
        assertEquals(base.addMilliseconds(1), base.addMillisecond())
        assertEquals(base.addSeconds(1), base.addSecond())
        assertEquals(base.addMinutes(1), base.addMinute())
        assertEquals(base.addHours(1), base.addHour())
        assertEquals(base.addDays(1), base.addDay())
        assertEquals(base.addWeeks(1), base.addWeek())
        assertEquals(base.addMonths(1), base.addMonth())
        assertEquals(base.addYears(1), base.addYear())
        assertEquals(base.subMilliseconds(1), base.subMillisecond())
        assertEquals(base.subSeconds(1), base.subSecond())
        assertEquals(base.subMinutes(1), base.subMinute())
        assertEquals(base.subHours(1), base.subHour())
        assertEquals(base.subDays(1), base.subDay())
        assertEquals(base.subWeeks(1), base.subWeek())
        assertEquals(base.subMonths(1), base.subMonth())
        assertEquals(base.subYears(1), base.subYear())
    }

    @Test
    fun settersAndStartEndUnits() {
        val d = KonaDate.ofInstant(Instant.parse("2024-06-15T12:30:45.500Z"), ZoneOffset.UTC)
        assertEquals(500, d.milli())
        assertEquals(500_000, d.micro())
        assertEquals("2025-07-20 12:30:45", d.setDate(2025, 7, 20).toDateTimeString().substring(0, 19))
        assertEquals("2024-06-15 01:02:03", d.setTime(1, 2, 3).toDateTimeString())
        assertEquals(2030, d.setYear(2030).year())
        assertEquals(11, d.setMonth(11).month())
        assertEquals(2, d.setDay(2).day())
        assertEquals(4, d.setHour(4).hour())
        assertEquals(5, d.setMinute(5).minute())
        assertEquals(6, d.setSecond(6).second())

        assertEquals(0, d.startOfSecond().toLocalDateTime().nano)
        assertEquals(999_999_999, d.endOfSecond().toLocalDateTime().nano)
        assertEquals(0, d.startOfMinute().second())
        assertEquals(59, d.endOfMinute().second())
        assertEquals(0, d.startOfHour().minute())
        assertEquals(59, d.endOfHour().minute())
        assertEquals(DayOfWeek.MONDAY, d.startOfWeek().toLocalDate().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, d.endOfWeek().toLocalDate().dayOfWeek)
        assertEquals(1, d.startOfMonth().day())
        assertEquals(30, d.endOfMonth().day())
        assertEquals(4, d.startOfQuarter().month())
        assertEquals(6, d.endOfQuarter().month())
        assertEquals(1, d.startOfYear().month())
        assertEquals(12, d.endOfYear().month())
        assertEquals(1, KonaDate.create(2024, 1, 15, zone = ZoneOffset.UTC).quarter())
        assertEquals(3, KonaDate.create(2024, 8, 1, zone = ZoneOffset.UTC).quarter())
        assertEquals(4, KonaDate.create(2024, 12, 1, zone = ZoneOffset.UTC).quarter())
    }

    @Test
    fun zonesComparisonsBetweenAndDiffs() {
        val a = KonaDate.ofInstant(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC)
        val b = a.addHours(3)
        assertTrue(a.eq(a.copy()))
        assertTrue(a.ne(b))
        assertTrue(b.gt(a))
        assertTrue(b.gte(a))
        assertTrue(a.lt(b))
        assertTrue(a.lte(b))
        assertTrue(a.equalTo(a))
        assertTrue(a.notEqualTo(b))
        assertTrue(b.greaterThan(a))
        assertTrue(b.greaterThanOrEqualTo(b))
        assertTrue(a.lessThan(b))
        assertTrue(a.lessThanOrEqualTo(a))
        assertTrue(a.isSameAs(a))
        assertTrue(a.isSameMonth(a.addDays(2)))
        assertFalse(a.isSameMonth(a.addMonths(1)))
        assertFalse(a.isSameMonth(a.addYears(1)))
        assertTrue(a.isSameYear(a.addMonths(2)))
        assertFalse(a.isSameYear(a.addYears(1)))
        assertTrue(a.isSameHour(a.addMinutes(10)))
        assertFalse(a.isSameHour(a.addHours(1)))
        assertFalse(a.isSameHour(a.addDays(1)))
        assertTrue(a.isSameMinute(a.addSeconds(10)))
        assertFalse(a.isSameMinute(a.addMinutes(1)))
        assertFalse(a.isSameMinute(a.addHours(1))) // short-circuit: not same hour
        assertTrue(a.isSameSecond(a.addMilliseconds(10)))
        assertFalse(a.isSameSecond(a.addSeconds(1)))
        assertFalse(a.isSameSecond(a.addMinutes(1))) // short-circuit: not same minute
        assertTrue(a.isSameWeek(a.addDays(1)))
        assertFalse(a.isSameWeek(a.addWeeks(2)))
        assertTrue(a.isSameQuarter(a.subMonths(1))) // May + June → Q2
        assertFalse(a.isSameQuarter(a.addMonths(1))) // June → July crosses into Q3
        assertFalse(a.isSameQuarter(a.addYears(1)))
        assertTrue(a.isBirthday(a.addYears(1)))
        assertFalse(a.isBirthday(a.addDays(1)))
        assertFalse(a.isBirthday(a.addMonths(1))) // short-circuit: different month
        assertTrue(a.isLeapYear())
        assertFalse(KonaDate.create(2023, 1, 1, zone = ZoneOffset.UTC).isLeapYear())

        assertTrue(a.between(a.subDay(), a.addDay()))
        assertTrue(a.between(a, b))
        assertTrue(a.betweenIncluded(a, b))
        assertFalse(a.betweenExcluded(a, b))
        assertFalse(b.betweenExcluded(a, b)) // equals high → exclusive false
        assertTrue(a.addHour().betweenExcluded(a, b))
        assertTrue(a.isBetween(a.subHour(), a.addHour()))
        assertTrue(a.isBetween(a.subHour(), a.addHour(), true))
        assertFalse(a.between(b, b.addHour(), false))
        assertFalse(a.between(b, b.addDay()))
        assertFalse(b.addHour().between(a, b)) // after high, inclusive
        // swapped bounds
        assertTrue(a.between(b, a.subDay()))

        assertEquals(a, a.min(b))
        assertEquals(a, b.min(a))
        assertEquals(b, a.max(b))
        assertEquals(b, b.max(a))
        assertEquals(a, KonaDate.min(a, b))
        assertEquals(b, KonaDate.max(a, b))
        assertEquals(a, b.closest(a, a.addDays(5)))
        assertEquals(a.addDays(5), b.farthest(a, a.addDays(5)))
        assertThrows(IllegalArgumentException::class.java) { a.closest() }
        assertThrows(IllegalArgumentException::class.java) { a.farthest() }

        assertEquals(3_000L * 60 * 60, b.diffInMilliseconds(a))
        assertEquals(b.diffInMilliseconds(a), b.diffInMilliseconds(a, false))
        assertEquals(3L, b.diffInHours(a))
        assertEquals(b.diffInHours(a), b.diffInHours(a, false))
        assertEquals(3L * 60, b.diffInMinutes(a))
        assertEquals(b.diffInMinutes(a), b.diffInMinutes(a, false))
        assertEquals(1L, a.addDays(1).diffInDays(a))
        assertEquals(a.addDays(1).diffInDays(a), a.addDays(1).diffInDays(a, false))
        assertEquals(1L, a.addWeeks(1).diffInWeeks(a))
        assertEquals(a.addWeeks(1).diffInWeeks(a), a.addWeeks(1).diffInWeeks(a, false))
        assertEquals(1L, a.addMonths(1).diffInMonths(a))
        assertEquals(a.addMonths(1).diffInMonths(a), a.addMonths(1).diffInMonths(a, false))
        assertEquals(1L, a.addYears(1).diffInYears(a))
        assertEquals(a.addYears(1).diffInYears(a), a.addYears(1).diffInYears(a, false))
        assertEquals(a.diffInSeconds(b), a.diffInSeconds(b, false))
        assertEquals(3L, a.diffInHours(b, true))
        assertEquals(180L, a.diffInMinutes(b, absolute = true))
        assertEquals(3_000L * 60 * 60, a.diffInMilliseconds(b, true))
        assertEquals(1L, a.diffInDays(a.addDays(1), true))
        assertEquals(1L, a.diffInWeeks(a.addWeeks(1), true))
        assertEquals(1L, a.diffInMonths(a.addMonths(1), true))
        assertEquals(1L, a.diffInYears(a.addYears(1), true))
        assertEquals(1L, a.diffInSeconds(a.addSeconds(1), true))

        val nairobi = a.timezone(ZoneId.of("Africa/Nairobi"))
        assertEquals(ZoneId.of("Africa/Nairobi"), nairobi.timezone())
        assertEquals(a.toInstant(), nairobi.toInstant())
        val wall = a.shiftTimezone(ZoneOffset.ofHours(3))
        assertEquals(a.toLocalDateTime(), wall.toLocalDateTime())
        assertEquals(ZoneOffset.UTC, a.utc().timezone())
        assertEquals(ZoneOffset.UTC, a.toUtc().timezone())
    }

    @Test
    fun weekdayTodayAgeAndHumanDiff() {
        // 2024-06-15 is Saturday
        val sat = KonaDate.ofInstant(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC)
        assertTrue(sat.isSaturday())
        assertFalse(sat.isSunday())
        assertTrue(sat.isWeekend())
        assertFalse(sat.isWeekday())
        assertFalse(sat.isMonday())
        assertFalse(sat.isTuesday())
        assertFalse(sat.isWednesday())
        assertFalse(sat.isThursday())
        assertFalse(sat.isFriday())
        val mon = sat.startOfWeek()
        assertTrue(mon.isMonday())
        assertFalse(mon.isSaturday())
        assertTrue(mon.isWeekday())
        assertTrue(mon.addDay().isTuesday())
        assertTrue(mon.addDays(2).isWednesday())
        assertTrue(mon.addDays(3).isThursday())
        assertTrue(mon.addDays(4).isFriday())
        val sun = mon.addDays(6)
        assertTrue(sun.isSunday())
        assertFalse(sun.isWeekday())
        assertTrue(sun.isWeekend())
        assertTrue(sat.isDayOfWeek(DayOfWeek.SATURDAY))
        assertFalse(sat.isDayOfWeek(DayOfWeek.MONDAY))

        assertTrue(sat.isToday(fixed))
        assertTrue(sat.subDay().isYesterday(fixed))
        assertTrue(sat.addDay().isTomorrow(fixed))
        assertFalse(sat.addDay().isToday(fixed))
        assertFalse(sat.addDay().isYesterday(fixed))
        assertFalse(sat.subDay().isTomorrow(fixed))
        assertTrue(sat.isCurrentYear(fixed))
        assertTrue(sat.isCurrentMonth(fixed))
        assertTrue(sat.isCurrentWeek(fixed))
        assertFalse(sat.addYears(1).isCurrentYear(fixed))
        assertFalse(sat.addMonths(1).isCurrentMonth(fixed))
        assertFalse(sat.addYears(1).isCurrentMonth(fixed))
        assertFalse(sat.addWeeks(2).isCurrentWeek(fixed))
        // no-arg overloads (system clock)
        assertNotNull(sat.isPast())
        assertNotNull(sat.isFuture())
        assertNotNull(sat.isToday())
        assertNotNull(sat.isYesterday())
        assertNotNull(sat.isTomorrow())
        assertNotNull(sat.isCurrentYear())
        assertNotNull(sat.isCurrentMonth())
        assertNotNull(sat.isCurrentWeek())
        assertNotNull(sat.ago())
        assertNotNull(sat.fromNow())
        assertNotNull(sat.diffForHumans())
        assertNotNull(sat.age())

        assertEquals(30, KonaDate.create(1994, 6, 15, zone = ZoneOffset.UTC).age(sat))
        assertEquals(0, sat.age(sat.subYears(1)))
        assertEquals(0, sat.age(fixed))

        assertEquals("3 hours from now", sat.addHours(3).diffForHumans(null, false, fixed))
        assertEquals("3 hours ago", sat.subHours(3).diffForHumans(null, false, fixed))
        assertEquals("3 hours after", sat.addHours(3).diffForHumans(sat))
        assertEquals("3 hours before", sat.subHours(3).diffForHumans(sat))
        assertEquals("3 hours", sat.subHours(3).diffForHumans(sat, true))
        assertEquals(sat.diffForHumans(sat), sat.diffForHumans(sat, false))
        assertEquals("1 second from now", sat.addSecond().diffForHumans(null, false, fixed))
        assertEquals("2 seconds from now", sat.addSeconds(2).diffForHumans(null, false, fixed))
        assertEquals("1 minute from now", sat.addMinute().diffForHumans(null, false, fixed))
        assertEquals("2 minutes from now", sat.addMinutes(2).diffForHumans(null, false, fixed))
        assertEquals("1 hour from now", sat.addHour().diffForHumans(null, false, fixed))
        assertEquals("1 day from now", sat.addDay().diffForHumans(null, false, fixed))
        assertEquals("2 days from now", sat.addDays(2).diffForHumans(null, false, fixed))
        assertEquals("1 week from now", sat.addWeek().diffForHumans(null, false, fixed))
        assertEquals("2 weeks from now", sat.addWeeks(2).diffForHumans(null, false, fixed))
        assertEquals("1 month from now", sat.addMonth().diffForHumans(null, false, fixed))
        assertEquals("2 months from now", sat.addMonths(2).diffForHumans(null, false, fixed))
        assertEquals("1 year from now", sat.addYear().diffForHumans(null, false, fixed))
        assertEquals("2 years from now", sat.addYears(2).diffForHumans(null, false, fixed))
        assertTrue(sat.subHours(2).ago(fixed).contains("ago"))
        assertTrue(sat.addHours(2).fromNow(fixed).contains("from now"))
        assertEquals("1 month", sat.addDays(40).diffForHumans(sat, true))
        assertEquals("0 seconds", sat.diffForHumans(sat, true))
    }
}
