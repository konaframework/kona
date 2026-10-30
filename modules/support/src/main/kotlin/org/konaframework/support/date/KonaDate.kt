package org.konaframework.support.date

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Fluent date/time type (Carbon-class shape) over a UTC [Instant] plus a [ZoneId]
 * used for calendar arithmetic and formatting. JSON encodes as an ISO-8601 string.
 */
class KonaDate private constructor(
    private val instant: Instant,
    private val zone: ZoneId,
) {
    fun toInstant(): Instant = instant

    /** Zone used for local calendar arithmetic and [format]. */
    fun timezone(): ZoneId = zone

    fun toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(instant, zone)

    fun toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

    fun toZonedDateTime(): ZonedDateTime = instant.atZone(zone)

    fun toOffsetDateTime(): OffsetDateTime = instant.atZone(zone).toOffsetDateTime()

    /** Unix timestamp in seconds. */
    fun timestamp(): Long = instant.epochSecond

    /** Unix timestamp in milliseconds. */
    fun timestampMs(): Long = instant.toEpochMilli()

    fun year(): Int = toLocalDateTime().year

    fun month(): Int = toLocalDateTime().monthValue

    fun day(): Int = toLocalDateTime().dayOfMonth

    fun hour(): Int = toLocalDateTime().hour

    fun minute(): Int = toLocalDateTime().minute

    fun second(): Int = toLocalDateTime().second

    fun micro(): Int = toLocalDateTime().nano / 1_000

    fun milli(): Int = toLocalDateTime().nano / 1_000_000

    fun dayOfWeek(): Int = toLocalDate().dayOfWeek.value

    fun dayOfYear(): Int = toLocalDate().dayOfYear

    fun weekOfYear(): Int = toLocalDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun quarter(): Int = (month() - 1) / 3 + 1

    fun setDate(year: Int, month: Int, day: Int): KonaDate {
        val local = toLocalDateTime()
        return ofLocalDateTime(LocalDateTime.of(year, month, day, local.hour, local.minute, local.second, local.nano), zone)
    }

    fun setTime(hour: Int, minute: Int, second: Int = 0, nano: Int = 0): KonaDate {
        val local = toLocalDateTime()
        return ofLocalDateTime(LocalDateTime.of(local.toLocalDate(), LocalTime.of(hour, minute, second, nano)), zone)
    }

    fun setYear(year: Int): KonaDate = setDate(year, month(), day())

    fun setMonth(month: Int): KonaDate = setDate(year(), month, day())

    fun setDay(day: Int): KonaDate = setDate(year(), month(), day)

    fun setHour(hour: Int): KonaDate = setTime(hour, minute(), second(), toLocalDateTime().nano)

    fun setMinute(minute: Int): KonaDate = setTime(hour(), minute, second(), toLocalDateTime().nano)

    fun setSecond(second: Int): KonaDate = setTime(hour(), minute(), second, toLocalDateTime().nano)

    fun addMilliseconds(millis: Long): KonaDate = KonaDate(instant.plusMillis(millis), zone)

    fun addSeconds(seconds: Long): KonaDate = KonaDate(instant.plusSeconds(seconds), zone)

    fun addMinutes(minutes: Long): KonaDate = addSeconds(minutes * 60)

    fun addHours(hours: Long): KonaDate = addMinutes(hours * 60)

    fun addDays(days: Long): KonaDate = KonaDate(instant.plus(days, ChronoUnit.DAYS), zone)

    fun addWeeks(weeks: Long): KonaDate = addDays(weeks * 7)

    fun addMonths(months: Long): KonaDate =
        KonaDate(toLocalDateTime().plusMonths(months).atZone(zone).toInstant(), zone)

    fun addYears(years: Long): KonaDate =
        KonaDate(toLocalDateTime().plusYears(years).atZone(zone).toInstant(), zone)

    fun addMillisecond(): KonaDate = addMilliseconds(1)

    fun addSecond(): KonaDate = addSeconds(1)

    fun addMinute(): KonaDate = addMinutes(1)

    fun addHour(): KonaDate = addHours(1)

    fun addDay(): KonaDate = addDays(1)

    fun addWeek(): KonaDate = addWeeks(1)

    fun addMonth(): KonaDate = addMonths(1)

    fun addYear(): KonaDate = addYears(1)

    fun subMilliseconds(millis: Long): KonaDate = addMilliseconds(-millis)

    fun subSeconds(seconds: Long): KonaDate = addSeconds(-seconds)

    fun subMinutes(minutes: Long): KonaDate = addMinutes(-minutes)

    fun subHours(hours: Long): KonaDate = addHours(-hours)

    fun subDays(days: Long): KonaDate = addDays(-days)

    fun subWeeks(weeks: Long): KonaDate = addWeeks(-weeks)

    fun subMonths(months: Long): KonaDate = addMonths(-months)

    fun subYears(years: Long): KonaDate = addYears(-years)

    fun subMillisecond(): KonaDate = subMilliseconds(1)

    fun subSecond(): KonaDate = subSeconds(1)

    fun subMinute(): KonaDate = subMinutes(1)

    fun subHour(): KonaDate = subHours(1)

    fun subDay(): KonaDate = subDays(1)

    fun subWeek(): KonaDate = subWeeks(1)

    fun subMonth(): KonaDate = subMonths(1)

    fun subYear(): KonaDate = subYears(1)

    fun startOfSecond(): KonaDate {
        val local = toLocalDateTime().withNano(0)
        return ofLocalDateTime(local, zone)
    }

    fun endOfSecond(): KonaDate {
        val local = toLocalDateTime().withNano(999_999_999)
        return ofLocalDateTime(local, zone)
    }

    fun startOfMinute(): KonaDate {
        val local = toLocalDateTime().withSecond(0).withNano(0)
        return ofLocalDateTime(local, zone)
    }

    fun endOfMinute(): KonaDate {
        val local = toLocalDateTime().withSecond(59).withNano(999_999_999)
        return ofLocalDateTime(local, zone)
    }

    fun startOfHour(): KonaDate {
        val local = toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
        return ofLocalDateTime(local, zone)
    }

    fun endOfHour(): KonaDate {
        val local = toLocalDateTime().withMinute(59).withSecond(59).withNano(999_999_999)
        return ofLocalDateTime(local, zone)
    }

    fun startOfDay(): KonaDate {
        val start = toLocalDate().atStartOfDay(zone).toInstant()
        return KonaDate(start, zone)
    }

    fun endOfDay(): KonaDate {
        val end = toLocalDate().atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant()
        return KonaDate(end, zone)
    }

    /** ISO week: Monday 00:00:00.000. */
    fun startOfWeek(): KonaDate {
        val monday = toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ofLocalDate(monday, zone)
    }

    /** ISO week: Sunday 23:59:59.999. */
    fun endOfWeek(): KonaDate {
        val sunday = toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return ofLocalDate(sunday, zone).endOfDay()
    }

    fun startOfMonth(): KonaDate =
        ofLocalDate(toLocalDate().with(TemporalAdjusters.firstDayOfMonth()), zone)

    fun endOfMonth(): KonaDate =
        ofLocalDate(toLocalDate().with(TemporalAdjusters.lastDayOfMonth()), zone).endOfDay()

    fun startOfQuarter(): KonaDate {
        val firstMonth = (quarter() - 1) * 3 + 1
        return ofLocalDate(LocalDate.of(year(), firstMonth, 1), zone)
    }

    fun endOfQuarter(): KonaDate {
        val lastMonth = quarter() * 3
        return ofLocalDate(LocalDate.of(year(), lastMonth, 1), zone).endOfMonth()
    }

    fun startOfYear(): KonaDate =
        ofLocalDate(LocalDate.of(year(), 1, 1), zone)

    fun endOfYear(): KonaDate =
        ofLocalDate(LocalDate.of(year(), 12, 31), zone).endOfDay()

    /** Keep the same instant; change the zone used for calendar math / format. */
    fun setTimezone(zone: ZoneId): KonaDate = KonaDate(instant, zone)

    /** Alias of [setTimezone]. */
    fun timezone(zone: ZoneId): KonaDate = setTimezone(zone)

    /** Keep the same wall-clock local time; reinterpret in [zone]. */
    fun shiftTimezone(zone: ZoneId): KonaDate = ofLocalDateTime(toLocalDateTime(), zone)

    /** Same instant in UTC. */
    fun utc(): KonaDate = setTimezone(ZoneOffset.UTC)

    /** Alias of [utc]. */
    fun toUtc(): KonaDate = utc()

    fun eq(other: KonaDate): Boolean = instant == other.instant

    fun ne(other: KonaDate): Boolean = !eq(other)

    fun gt(other: KonaDate): Boolean = instant.isAfter(other.instant)

    fun gte(other: KonaDate): Boolean = !instant.isBefore(other.instant)

    fun lt(other: KonaDate): Boolean = instant.isBefore(other.instant)

    fun lte(other: KonaDate): Boolean = !instant.isAfter(other.instant)

    fun equalTo(other: KonaDate): Boolean = eq(other)

    fun notEqualTo(other: KonaDate): Boolean = ne(other)

    fun greaterThan(other: KonaDate): Boolean = gt(other)

    fun greaterThanOrEqualTo(other: KonaDate): Boolean = gte(other)

    fun lessThan(other: KonaDate): Boolean = lt(other)

    fun lessThanOrEqualTo(other: KonaDate): Boolean = lte(other)

    fun isBefore(other: KonaDate): Boolean = lt(other)

    fun isAfter(other: KonaDate): Boolean = gt(other)

    fun isSameAs(other: KonaDate): Boolean = eq(other)

    fun isSameDay(other: KonaDate): Boolean =
        toLocalDate() == LocalDate.ofInstant(other.instant, zone)

    fun isSameMonth(other: KonaDate): Boolean {
        val a = toLocalDate()
        val b = LocalDate.ofInstant(other.instant, zone)
        return a.year == b.year && a.month == b.month
    }

    fun isSameYear(other: KonaDate): Boolean =
        year() == LocalDate.ofInstant(other.instant, zone).year

    fun isSameHour(other: KonaDate): Boolean {
        val a = toLocalDateTime()
        val b = LocalDateTime.ofInstant(other.instant, zone)
        return a.toLocalDate() == b.toLocalDate() && a.hour == b.hour
    }

    fun isSameMinute(other: KonaDate): Boolean {
        val a = toLocalDateTime()
        val b = LocalDateTime.ofInstant(other.instant, zone)
        return isSameHour(other) && a.minute == b.minute
    }

    fun isSameSecond(other: KonaDate): Boolean {
        val a = toLocalDateTime()
        val b = LocalDateTime.ofInstant(other.instant, zone)
        return isSameMinute(other) && a.second == b.second
    }

    fun isSameWeek(other: KonaDate): Boolean {
        val a = toLocalDate()
        val b = LocalDate.ofInstant(other.instant, zone)
        return a.get(IsoFields.WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_BASED_YEAR) &&
            a.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }

    fun isSameQuarter(other: KonaDate): Boolean {
        val otherLocal = LocalDate.ofInstant(other.instant, zone)
        return year() == otherLocal.year && quarter() == ((otherLocal.monthValue - 1) / 3 + 1)
    }

    fun isBirthday(other: KonaDate): Boolean {
        val a = toLocalDate()
        val b = LocalDate.ofInstant(other.instant, zone)
        return a.month == b.month && a.dayOfMonth == b.dayOfMonth
    }

    fun isLeapYear(): Boolean = toLocalDate().isLeapYear

    fun isWeekday(): Boolean {
        val dow = toLocalDate().dayOfWeek
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
    }

    fun isWeekend(): Boolean = !isWeekday()

    fun isMonday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.MONDAY

    fun isTuesday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.TUESDAY

    fun isWednesday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.WEDNESDAY

    fun isThursday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.THURSDAY

    fun isFriday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.FRIDAY

    fun isSaturday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.SATURDAY

    fun isSunday(): Boolean = toLocalDate().dayOfWeek == DayOfWeek.SUNDAY

    fun isDayOfWeek(day: DayOfWeek): Boolean = toLocalDate().dayOfWeek == day

    fun isPast(): Boolean = isPast(Clock.systemUTC())

    fun isPast(clock: Clock): Boolean = instant.isBefore(Instant.now(clock))

    fun isFuture(): Boolean = isFuture(Clock.systemUTC())

    fun isFuture(clock: Clock): Boolean = instant.isAfter(Instant.now(clock))

    fun isToday(): Boolean = isToday(Clock.system(zone))

    fun isToday(clock: Clock): Boolean =
        toLocalDate() == LocalDate.now(clock.withZone(zone))

    fun isYesterday(): Boolean = isYesterday(Clock.system(zone))

    fun isYesterday(clock: Clock): Boolean =
        toLocalDate() == LocalDate.now(clock.withZone(zone)).minusDays(1)

    fun isTomorrow(): Boolean = isTomorrow(Clock.system(zone))

    fun isTomorrow(clock: Clock): Boolean =
        toLocalDate() == LocalDate.now(clock.withZone(zone)).plusDays(1)

    fun isCurrentYear(): Boolean = isCurrentYear(Clock.system(zone))

    fun isCurrentYear(clock: Clock): Boolean =
        year() == LocalDate.now(clock.withZone(zone)).year

    fun isCurrentMonth(): Boolean = isCurrentMonth(Clock.system(zone))

    fun isCurrentMonth(clock: Clock): Boolean {
        val now = LocalDate.now(clock.withZone(zone))
        return year() == now.year && month() == now.monthValue
    }

    fun isCurrentWeek(): Boolean = isCurrentWeek(Clock.system(zone))

    fun isCurrentWeek(clock: Clock): Boolean {
        val now = ofLocalDate(LocalDate.now(clock.withZone(zone)), zone)
        return isSameWeek(now)
    }

    @JvmOverloads
    fun between(first: KonaDate, second: KonaDate, equal: Boolean = true): Boolean {
        val low = if (first.instant.isBefore(second.instant)) first else second
        val high = if (first.instant.isBefore(second.instant)) second else first
        return if (equal) gte(low) && lte(high) else gt(low) && lt(high)
    }

    fun betweenIncluded(first: KonaDate, second: KonaDate): Boolean = between(first, second, true)

    fun betweenExcluded(first: KonaDate, second: KonaDate): Boolean = between(first, second, false)

    @JvmOverloads
    fun isBetween(first: KonaDate, second: KonaDate, equal: Boolean = true): Boolean =
        between(first, second, equal)

    fun min(other: KonaDate): KonaDate = if (!instant.isAfter(other.instant)) this else other

    fun max(other: KonaDate): KonaDate = if (!instant.isBefore(other.instant)) this else other

    fun closest(vararg others: KonaDate): KonaDate {
        require(others.isNotEmpty()) { "closest requires at least one date" }
        return others.minBy { abs(Duration.between(instant, it.instant).toMillis()) }
    }

    fun farthest(vararg others: KonaDate): KonaDate {
        require(others.isNotEmpty()) { "farthest requires at least one date" }
        return others.maxBy { abs(Duration.between(instant, it.instant).toMillis()) }
    }

    @JvmOverloads
    fun diffInMilliseconds(other: KonaDate, absolute: Boolean = false): Long {
        val value = Duration.between(other.instant, instant).toMillis()
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInSeconds(other: KonaDate, absolute: Boolean = false): Long {
        val value = Duration.between(other.instant, instant).seconds
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInMinutes(other: KonaDate, absolute: Boolean = false): Long {
        val value = Duration.between(other.instant, instant).toMinutes()
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInHours(other: KonaDate, absolute: Boolean = false): Long {
        val value = Duration.between(other.instant, instant).toHours()
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInDays(other: KonaDate, absolute: Boolean = false): Long {
        val value = ChronoUnit.DAYS.between(other.toLocalDate(), toLocalDate())
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInWeeks(other: KonaDate, absolute: Boolean = false): Long {
        val value = diffInDays(other) / 7
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInMonths(other: KonaDate, absolute: Boolean = false): Long {
        val value = ChronoUnit.MONTHS.between(other.toLocalDate().withDayOfMonth(1), toLocalDate().withDayOfMonth(1))
        return if (absolute) abs(value) else value
    }

    @JvmOverloads
    fun diffInYears(other: KonaDate, absolute: Boolean = false): Long {
        val value = ChronoUnit.YEARS.between(other.toLocalDate(), toLocalDate())
        return if (absolute) abs(value) else value
    }

    /** Whole years from this date until now. */
    fun age(): Long = age(Clock.system(zone))

    fun age(clock: Clock): Long = age(now(zone, clock))

    /** Whole years from this date until [other] (never negative). */
    fun age(other: KonaDate): Long {
        val years = ChronoUnit.YEARS.between(toLocalDate(), other.toLocalDate())
        return if (years < 0) 0 else years
    }

    /**
     * Human-readable relative difference (English).
     * Relative to now when [other] is null. When [absolute] is true, omits ago/from now/before/after.
     */
    fun diffForHumans(): String = diffForHumans(null, false, Clock.system(zone))

    fun diffForHumans(other: KonaDate?): String = diffForHumans(other, false, Clock.system(zone))

    fun diffForHumans(other: KonaDate?, absolute: Boolean): String =
        diffForHumans(other, absolute, Clock.system(zone))

    fun diffForHumans(other: KonaDate?, absolute: Boolean, clock: Clock): String {
        val reference = other ?: now(zone, clock)
        return humanDiff(this, reference, absolute, other == null)
    }

    /** Alias of [diffForHumans] with no other date. */
    fun ago(): String = ago(Clock.system(zone))

    fun ago(clock: Clock): String = diffForHumans(null, false, clock)

    /** Alias of [diffForHumans] with no other date. */
    fun fromNow(): String = fromNow(Clock.system(zone))

    fun fromNow(clock: Clock): String = diffForHumans(null, false, clock)

    fun format(pattern: String): String =
        DateTimeFormatter.ofPattern(pattern).withZone(zone).format(instant)

    fun toDateString(): String = format("yyyy-MM-dd")

    fun toFormattedDateString(): String = format("MMM d, yyyy")

    fun toDateTimeString(): String = format("yyyy-MM-dd HH:mm:ss")

    fun toTimeString(): String = format("HH:mm:ss")

    fun toRfc3339String(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(toOffsetDateTime())

    fun toAtomString(): String = toRfc3339String()

    @JsonValue
    fun toIso8601String(): String = instant.toString()

    /** Alias of [toIso8601String]. */
    fun toIso(): String = toIso8601String()

    fun copy(): KonaDate = KonaDate(instant, zone)

    /** Alias of [copy]. */
    fun clone(): KonaDate = copy()

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
        fun yesterday(): KonaDate = yesterday(defaultZone)

        @JvmStatic
        fun yesterday(zone: ZoneId): KonaDate = today(zone).subDay()

        @JvmStatic
        fun tomorrow(): KonaDate = tomorrow(defaultZone)

        @JvmStatic
        fun tomorrow(zone: ZoneId): KonaDate = today(zone).addDay()

        @JvmStatic
        @JvmOverloads
        fun create(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            zone: ZoneId = defaultZone,
        ): KonaDate =
            ofLocalDateTime(LocalDateTime.of(year, month, day, hour, minute, second), zone)

        @JvmStatic
        @JvmOverloads
        fun createFromDate(year: Int, month: Int, day: Int, zone: ZoneId = defaultZone): KonaDate =
            ofLocalDate(LocalDate.of(year, month, day), zone)

        @JvmStatic
        @JvmOverloads
        fun createFromTime(hour: Int, minute: Int, second: Int = 0, zone: ZoneId = defaultZone): KonaDate {
            val today = LocalDate.now(zone)
            return ofLocalDateTime(LocalDateTime.of(today, LocalTime.of(hour, minute, second)), zone)
        }

        @JvmStatic
        @JvmOverloads
        fun createFromTimestamp(seconds: Long, zone: ZoneId = defaultZone): KonaDate =
            ofInstant(Instant.ofEpochSecond(seconds), zone)

        @JvmStatic
        @JvmOverloads
        fun createFromTimestampMs(millis: Long, zone: ZoneId = defaultZone): KonaDate =
            ofInstant(Instant.ofEpochMilli(millis), zone)

        @JvmStatic
        fun ofInstant(instant: Instant): KonaDate = ofInstant(instant, defaultZone)

        @JvmStatic
        fun ofInstant(instant: Instant, zone: ZoneId): KonaDate =
            KonaDate(instant, zone)

        @JvmStatic
        @JsonCreator
        fun parse(text: String): KonaDate = parse(text, defaultZone)

        @JvmStatic
        fun parse(text: String, zone: ZoneId): KonaDate {
            val trimmed = text.trim()
            val instant = try {
                Instant.parse(trimmed)
            } catch (_: DateTimeParseException) {
                try {
                    OffsetDateTime.parse(trimmed).toInstant()
                } catch (_: DateTimeParseException) {
                    try {
                        ZonedDateTime.parse(trimmed).toInstant()
                    } catch (_: DateTimeParseException) {
                        try {
                            LocalDateTime.parse(trimmed.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
                        } catch (_: DateTimeParseException) {
                            try {
                                LocalDate.parse(trimmed).atStartOfDay(zone).toInstant()
                            } catch (_: DateTimeParseException) {
                                throw DateTimeParseException("Cannot parse KonaDate: $trimmed", trimmed, 0)
                            }
                        }
                    }
                }
            }
            return KonaDate(instant, zone)
        }

        @JvmStatic
        fun ofLocalDateTime(dateTime: LocalDateTime): KonaDate =
            ofLocalDateTime(dateTime, defaultZone)

        @JvmStatic
        fun ofLocalDateTime(dateTime: LocalDateTime, zone: ZoneId): KonaDate =
            KonaDate(dateTime.atZone(zone).toInstant(), zone)

        @JvmStatic
        fun ofLocalDate(date: LocalDate): KonaDate = ofLocalDate(date, defaultZone)

        @JvmStatic
        fun ofLocalDate(date: LocalDate, zone: ZoneId): KonaDate =
            KonaDate(date.atStartOfDay(zone).toInstant(), zone)

        @JvmStatic
        fun ofZonedDateTime(dateTime: ZonedDateTime): KonaDate =
            KonaDate(dateTime.toInstant(), dateTime.zone)

        @JvmStatic
        fun ofOffsetDateTime(dateTime: OffsetDateTime): KonaDate =
            KonaDate(dateTime.toInstant(), dateTime.offset)

        @JvmStatic
        fun today(): KonaDate = today(defaultZone)

        @JvmStatic
        fun today(zone: ZoneId): KonaDate {
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            return KonaDate(start, zone)
        }

        @JvmStatic
        fun min(first: KonaDate, second: KonaDate): KonaDate = first.min(second)

        @JvmStatic
        fun max(first: KonaDate, second: KonaDate): KonaDate = first.max(second)
    }
}

private fun humanDiff(self: KonaDate, other: KonaDate, absolute: Boolean, relativeToNow: Boolean): String {
    val seconds = Duration.between(other.toInstant(), self.toInstant()).seconds
    val ahead = seconds >= 0
    val absSeconds = abs(seconds)
    val years = abs(self.diffInYears(other))
    val months = abs(self.diffInMonths(other))
    val days = abs(self.diffInDays(other))

    val (count, unit) = when {
        years >= 1L -> years to if (years == 1L) "year" else "years"
        months >= 1L -> months to if (months == 1L) "month" else "months"
        days >= 7L -> {
            val w = days / 7
            w to if (w == 1L) "week" else "weeks"
        }
        days >= 1L -> days to if (days == 1L) "day" else "days"
        absSeconds >= 3_600L -> {
            val h = absSeconds / 3_600
            h to if (h == 1L) "hour" else "hours"
        }
        absSeconds >= 60L -> {
            val m = absSeconds / 60
            m to if (m == 1L) "minute" else "minutes"
        }
        else -> absSeconds to if (absSeconds == 1L) "second" else "seconds"
    }

    val amount = "$count $unit"
    if (absolute) return amount
    return if (relativeToNow) {
        if (ahead) "$amount from now" else "$amount ago"
    } else {
        if (ahead) "$amount after" else "$amount before"
    }
}
