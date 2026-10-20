package org.konaframework.support.date

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Fluent date/time wrapper (Carbon-class API shape) over [Instant].
 */
class KonaDate private constructor(
    private val instant: Instant,
    private val zone: ZoneId,
) {
    fun toInstant(): Instant = instant

    fun toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(instant, zone)

    fun toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

    fun timestamp(): Long = instant.epochSecond

    fun addSeconds(seconds: Long): KonaDate = KonaDate(instant.plusSeconds(seconds), zone)

    fun addMinutes(minutes: Long): KonaDate = addSeconds(minutes * 60)

    fun addHours(hours: Long): KonaDate = addMinutes(hours * 60)

    fun addDays(days: Long): KonaDate = KonaDate(instant.plus(days, ChronoUnit.DAYS), zone)

    fun addWeeks(weeks: Long): KonaDate = addDays(weeks * 7)

    fun addMonths(months: Long): KonaDate =
        KonaDate(toLocalDateTime().plusMonths(months).atZone(zone).toInstant(), zone)

    fun addYears(years: Long): KonaDate =
        KonaDate(toLocalDateTime().plusYears(years).atZone(zone).toInstant(), zone)

    fun subSeconds(seconds: Long): KonaDate = addSeconds(-seconds)

    fun subMinutes(minutes: Long): KonaDate = addMinutes(-minutes)

    fun subHours(hours: Long): KonaDate = addHours(-hours)

    fun subDays(days: Long): KonaDate = addDays(-days)

    fun subWeeks(weeks: Long): KonaDate = addWeeks(-weeks)

    fun subMonths(months: Long): KonaDate = addMonths(-months)

    fun subYears(years: Long): KonaDate = addYears(-years)

    fun startOfDay(): KonaDate {
        val start = toLocalDate().atStartOfDay(zone).toInstant()
        return KonaDate(start, zone)
    }

    fun endOfDay(): KonaDate {
        val end = toLocalDate().atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant()
        return KonaDate(end, zone)
    }

    fun setTimezone(zone: ZoneId): KonaDate = KonaDate(instant, zone)

    fun isBefore(other: KonaDate): Boolean = instant.isBefore(other.instant)

    fun isAfter(other: KonaDate): Boolean = instant.isAfter(other.instant)

    fun isSameDay(other: KonaDate): Boolean =
        toLocalDate() == LocalDate.ofInstant(other.instant, zone)

    fun isPast(clock: Clock = Clock.systemUTC()): Boolean = instant.isBefore(Instant.now(clock))

    fun isFuture(clock: Clock = Clock.systemUTC()): Boolean = instant.isAfter(Instant.now(clock))

    fun diffInSeconds(other: KonaDate): Long =
        Duration.between(other.instant, instant).seconds

    fun diffInDays(other: KonaDate): Long =
        ChronoUnit.DAYS.between(other.toLocalDate(), toLocalDate())

    fun format(pattern: String): String =
        DateTimeFormatter.ofPattern(pattern).withZone(zone).format(instant)

    fun toDateString(): String = format("yyyy-MM-dd")

    fun toDateTimeString(): String = format("yyyy-MM-dd HH:mm:ss")

    fun toIso8601String(): String = instant.toString()

    fun copy(): KonaDate = KonaDate(instant, zone)

    override fun equals(other: Any?): Boolean =
        other is KonaDate && other.instant == instant && other.zone == zone

    override fun hashCode(): Int = 31 * instant.hashCode() + zone.hashCode()

    override fun toString(): String = toIso8601String()

    companion object {
        @Volatile
        private var defaultZone: ZoneId = ZoneOffset.UTC

        /** Default zone used by [now], [today], [parse], and [ofInstant] overloads without a zone. */
        @JvmStatic
        fun defaultTimezone(): ZoneId = defaultZone

        /** Set the process default timezone (`app.timezone` / `APP_TIMEZONE`). */
        @JvmStatic
        fun setDefaultTimezone(zone: ZoneId) {
            defaultZone = zone
        }

        /** Restore UTC (tests). */
        @JvmStatic
        fun resetDefaultTimezone() {
            defaultZone = ZoneOffset.UTC
        }

        @JvmStatic
        fun now(): KonaDate = now(defaultZone)

        @JvmStatic
        fun now(zone: ZoneId): KonaDate = now(zone, Clock.system(zone))

        @JvmStatic
        fun now(zone: ZoneId, clock: Clock): KonaDate =
            KonaDate(Instant.now(clock), zone)

        @JvmStatic
        fun ofInstant(instant: Instant): KonaDate = ofInstant(instant, defaultZone)

        @JvmStatic
        fun ofInstant(instant: Instant, zone: ZoneId): KonaDate =
            KonaDate(instant, zone)

        @JvmStatic
        fun parse(text: String): KonaDate = parse(text, defaultZone)

        @JvmStatic
        fun parse(text: String, zone: ZoneId): KonaDate {
            val instant = runCatching { Instant.parse(text) }.getOrElse {
                LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
            }
            return KonaDate(instant, zone)
        }

        @JvmStatic
        fun today(): KonaDate = today(defaultZone)

        @JvmStatic
        fun today(zone: ZoneId): KonaDate {
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            return KonaDate(start, zone)
        }
    }
}
