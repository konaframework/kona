package org.konaframework.firefly.forms

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

object FieldClock {
    private var zone: ZoneId = ZoneId.systemDefault()
    private var pinnedToday: LocalDate? = null

    @JvmStatic
    fun appZone(): ZoneId = zone

    @JvmStatic
    fun appZone(next: ZoneId) {
        zone = next
    }

    @JvmStatic
    fun appZone(id: String) {
        zone = ZoneId.of(id)
    }

    @JvmStatic
    fun today(): LocalDate {
        val pinned = pinnedToday
        if (pinned != null) return pinned
        return LocalDate.now(zone)
    }

    @JvmStatic
    fun pinToday(date: LocalDate?) {
        pinnedToday = date
    }
}

open class DateTimePicker protected constructor(
    name: String,
    private val withDate: Boolean,
    private val withTime: Boolean,
) : Field<DateTimePicker>(name) {
    private var nativeFlag: Boolean = false
    private var formatText: String = defaultFormat(withDate, withTime)
    private var displayText: String = defaultDisplay(withDate, withTime)
    private var zoneId: ZoneId = FieldClock.appZone()
    private var localeTag: String = Locale.getDefault().toLanguageTag()
    private var firstDay: Int = 0
    private var closeOnSelect: Boolean = !withTime
    private var hoursStepCount: Int = 1
    private var minutesStepCount: Int = 1
    private var secondsStepCount: Int = 1
    private var secondsFlag: Boolean = false
    private val minText = Phrase()
    private val maxText = Phrase()
    private val focusedText = Phrase()
    private val disabledList = ArrayList<String>()
    private var disabledWhen: Predicate<LocalDate>? = null

    @JvmName("useNative")
    fun native(): DateTimePicker = native(true)

    @JvmName("useNative")
    fun native(on: Boolean): DateTimePicker = apply { nativeFlag = on }

    fun isNative(): Boolean = nativeFlag

    fun format(pattern: String): DateTimePicker = apply { formatText = pattern }

    fun displayFormat(pattern: String): DateTimePicker = apply { displayText = pattern }

    fun timezone(id: String): DateTimePicker = apply {
        zoneId = try {
            ZoneId.of(id)
        } catch (_: Exception) {
            throw FireflyException("Unknown timezone [$id]")
        }
    }

    fun zone(): ZoneId = zoneId

    fun locale(tag: String): DateTimePicker = apply { localeTag = tag }

    fun firstDayOfWeek(day: Int): DateTimePicker = apply {
        if (day != 0 && day != 1) throw FireflyException("First day of week must be 0 (Sunday) or 1 (Monday)")
        firstDay = day
    }

    fun weekStartsOnMonday(): DateTimePicker = firstDayOfWeek(1)

    fun weekStartsOnSunday(): DateTimePicker = firstDayOfWeek(0)

    fun closeOnDateSelection(on: Boolean): DateTimePicker = apply { closeOnSelect = on }

    fun hoursStep(step: Int): DateTimePicker = apply {
        if (step < 1 || step > 24) throw FireflyException("Hours step must be from 1 to 24")
        hoursStepCount = step
    }

    fun minutesStep(step: Int): DateTimePicker = apply {
        if (step < 1 || step > 60) throw FireflyException("Minutes step must be from 1 to 60")
        minutesStepCount = step
    }

    fun secondsStep(step: Int): DateTimePicker = apply {
        if (step < 1 || step > 60) throw FireflyException("Seconds step must be from 1 to 60")
        secondsStepCount = step
    }

    fun seconds(on: Boolean): DateTimePicker = apply {
        secondsFlag = on
        if (on && !formatText.contains('s')) formatText = "$formatText:s"
        if (on && !displayText.contains('s')) displayText = "$displayText:s"
    }

    fun minDate(value: String): DateTimePicker = apply { minText.set(value) }

    fun minDate(value: Function<Get, String>): DateTimePicker = apply { minText.set(value) }

    fun maxDate(value: String): DateTimePicker = apply { maxText.set(value) }

    fun maxDate(value: Function<Get, String>): DateTimePicker = apply { maxText.set(value) }

    fun defaultFocusedDate(value: String): DateTimePicker = apply { focusedText.set(value) }

    fun defaultFocusedDate(value: Function<Get, String>): DateTimePicker = apply { focusedText.set(value) }

    fun disabledDates(dates: List<String>): DateTimePicker = apply {
        disabledList.clear()
        disabledList.addAll(dates)
        disabledWhen = null
    }

    fun disabledDates(predicate: Predicate<LocalDate>): DateTimePicker = apply {
        disabledWhen = predicate
        disabledList.clear()
    }

    fun hasDate(): Boolean = withDate

    fun hasTime(): Boolean = withTime

    fun hourChoices(): List<Int> = stepped(24, hoursStepCount)

    fun minuteChoices(): List<Int> = stepped(60, minutesStepCount)

    fun secondChoices(): List<Int> = stepped(60, secondsStepCount)

    fun isDisabled(date: LocalDate, schema: Schema): Boolean {
        val get = Reads.of(schema, this)
        val min = parseDate(minText.get(get))
        val max = parseDate(maxText.get(get))
        if (min != null && date.isBefore(min)) return true
        if (max != null && date.isAfter(max)) return true
        if (date.toString() in disabledList) return true
        val predicate = disabledWhen
        if (predicate != null && predicate.test(date)) return true
        return false
    }

    fun normalize(raw: Any?): String? {
        if (raw == null) return null
        if (raw is String && raw.isBlank()) return null
        val date = when (raw) {
            is LocalDate -> raw
            is LocalDateTime -> raw.toLocalDate()
            is LocalTime -> null
            is OffsetDateTime -> raw.atZoneSameInstant(appZoneForDate()).toLocalDate()
            is ZonedDateTime -> raw.withZoneSameInstant(appZoneForDate()).toLocalDate()
            is Instant -> raw.atZone(appZoneForDate()).toLocalDate()
            is String -> datePart(raw)
            else -> throw FireflyException("Unsupported date value [${raw.javaClass.name}]")
        }
        val time = when (raw) {
            is LocalTime -> raw
            is LocalDateTime -> raw.toLocalTime()
            is OffsetDateTime -> raw.atZoneSameInstant(zoneId).toLocalTime()
            is ZonedDateTime -> raw.withZoneSameInstant(zoneId).toLocalTime()
            is Instant -> raw.atZone(zoneId).toLocalTime()
            is String -> timePart(raw)
            else -> null
        }
        if (withDate && !withTime) {
            if (date == null) {
                if (raw is String) return null
                throw FireflyException("Date value is missing a day")
            }
            return Dates.format(date, formatText, locale())
        }
        if (!withDate) {
            if (time == null) {
                if (raw is String) return null
                throw FireflyException("Time value is missing a time")
            }
            return Dates.format(time, formatText, locale())
        }
        if (date == null) {
            if (raw is String) return null
            throw FireflyException("Date value is missing a day")
        }
        val day = date
        val clock = time ?: LocalTime.MIDNIGHT
        val local = LocalDateTime.of(day, clock)
        return Dates.format(local, formatText, locale())
    }

    fun display(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        val normalized = normalize(stored) ?: return ""
        val pattern = DateTimeFormatter.ofPattern(Dates.toJava(formatText))
        if (!withDate) {
            return Dates.format(LocalTime.parse(normalized, pattern), displayText, locale())
        }
        if (!withTime) {
            return Dates.format(LocalDate.parse(normalized, pattern), displayText, locale())
        }
        return Dates.format(LocalDateTime.parse(normalized, pattern), displayText, locale())
    }

    override fun kind(): String = "date-time-picker"

    override fun validationRules(schema: Schema): List<String> {
        val out = ArrayList(super.validationRules(schema))
        out.add("date_format:$formatText")
        return out
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val stored = if (value == null) "" else value.toString()
        val min = minText.get(get)
        val max = maxText.get(get)
        if (nativeFlag) {
            val htmlType = when {
                withDate && withTime -> "datetime-local"
                withTime -> "time"
                else -> "date"
            }
            val nativeValue = nativeValue(stored)
            return """<input class="ff-input"${inputAttrs(get, htmlType, path, nativeValue)}${attr("min", min)}${attr("max", max)}${attr("step", if (withTime) secondsStepCount.toString() else null)} />"""
        }
        val focused = focusedDate(schema, stored)
        return buildString {
            append("""<div class="ff-picker" x-data="ffDatePicker" data-ff-path="${esc(path)}"${boolAttr("data-ff-native", false)}${boolAttr("data-ff-date", withDate)}${boolAttr("data-ff-time", withTime)}${attr("data-ff-format", formatText)}${attr("data-ff-display", displayText)}${attr("data-ff-locale", localeTag)}${attr("data-ff-zone", zoneId.id)} data-ff-first-day="$firstDay"${boolAttr("data-ff-close", closeOnSelect)} data-ff-hours-step="$hoursStepCount" data-ff-minutes-step="$minutesStepCount" data-ff-seconds-step="$secondsStepCount"${boolAttr("data-ff-seconds", secondsFlag)}${attr("data-ff-min", min)}${attr("data-ff-max", max)}${attr("data-ff-state", stored)}>""")
            append("""<input class="ff-input" type="text" readonly value="${esc(display(stored))}"${extraInputMarkup(get)} />""")
            if (withDate) append(monthGrid(schema, focused))
            if (withTime) append(timeSpinners())
            append("</div>")
        }
    }

    private fun monthGrid(schema: Schema, focused: LocalDate): String {
        val locale = locale()
        val caption = focused.month.getDisplayName(TextStyle.FULL, locale) + " " + focused.year
        val first = focused.withDayOfMonth(1)
        val lead = Math.floorMod(first.dayOfWeek.value - if (firstDay == 1) 1 else 7, 7)
        val start = first.minusDays(lead.toLong())
        return buildString {
            append("""<table class="ff-picker-grid"><caption>${esc(caption)}</caption><tbody>""")
            var cursor = start
            var cell = 0
            while (cell < 42) {
                if (cell % 7 == 0) append("<tr>")
                val outside = cursor.month != focused.month
                val disabled = isDisabled(cursor, schema)
                val css = buildString {
                    append("ff-day")
                    if (outside) append(" ff-day-outside")
                    if (disabled) append(" ff-day-disabled")
                }
                append("""<td class="$css" data-ff-day="${cursor}">${cursor.dayOfMonth}</td>""")
                if (cell % 7 == 6) append("</tr>")
                cursor = cursor.plusDays(1)
                cell += 1
            }
            append("</tbody></table>")
        }
    }

    private fun timeSpinners(): String = buildString {
        append("""<div class="ff-time">""")
        append(spinner("hours", hourChoices()))
        append(spinner("minutes", minuteChoices()))
        if (secondsFlag) append(spinner("seconds", secondChoices()))
        append("</div>")
    }

    private fun spinner(name: String, values: List<Int>): String = buildString {
        append("""<select class="ff-time-$name" data-ff-$name="true">""")
        for (value in values) append("""<option value="$value">${value.toString().padStart(2, '0')}</option>""")
        append("</select>")
    }

    private fun focusedDate(schema: Schema, stored: String): LocalDate {
        val explicit = focusedText.get(Reads.of(schema, this))
        val fromExplicit = parseDate(explicit)
        if (fromExplicit != null) return fromExplicit
        val fromState = parseDate(stored)
        if (fromState != null) return fromState
        return FieldClock.today()
    }

    private fun nativeValue(stored: String): String {
        if (stored.isBlank()) return ""
        if (withDate && withTime) {
            val local = Dates.parseDateTime(stored, formatText) ?: return stored
            return local.toString()
        }
        return stored
    }

    private fun appZoneForDate(): ZoneId = if (withDate && !withTime) FieldClock.appZone() else zoneId

    private fun locale(): Locale = Locale.forLanguageTag(localeTag)

    private fun datePart(text: String): LocalDate? {
        val parsed = Dates.parseDate(text, formatText)
        if (parsed != null) return parsed
        return try {
            if (text.endsWith("Z") || text.contains("T")) Instant.parse(text).atZone(appZoneForDate()).toLocalDate()
            else LocalDate.parse(text.take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun timePart(text: String): LocalTime? {
        val parsed = Dates.parseTime(text, formatText)
        if (parsed != null) return parsed
        return try {
            if (text.endsWith("Z") || text.contains("T")) Instant.parse(text).atZone(zoneId).toLocalTime()
            else LocalTime.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalDate.parse(text.take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    companion object {
        private val maker = Maker { name -> DateTimePicker(name, true, true) }

        @JvmStatic
        fun configureUsing(block: Consumer<DateTimePicker>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): DateTimePicker = maker.make(name)

        @JvmStatic
        fun date(name: String): DateTimePicker = maker.finish(DateTimePicker(name, true, false))

        @JvmStatic
        fun time(name: String): DateTimePicker = maker.finish(DateTimePicker(name, false, true))

        private fun defaultFormat(date: Boolean, time: Boolean): String {
            if (date && time) return "Y-m-d H:i"
            if (time) return "H:i"
            return "Y-m-d"
        }

        private fun defaultDisplay(date: Boolean, time: Boolean): String {
            if (date && time) return "M j, Y H:i"
            if (time) return "H:i"
            return "M j, Y"
        }

        private fun stepped(limit: Int, step: Int): List<Int> {
            val out = ArrayList<Int>()
            var value = 0
            while (value < limit) {
                out.add(value)
                value += step
            }
            return out
        }
    }
}

object DatePicker {
    @JvmStatic
    fun make(name: String): DateTimePicker = DateTimePicker.date(name)

    @JvmStatic
    fun configureUsing(block: Consumer<DateTimePicker>?) = DateTimePicker.configureUsing(block)
}

object TimePicker {
    @JvmStatic
    fun make(name: String): DateTimePicker = DateTimePicker.time(name)

    @JvmStatic
    fun configureUsing(block: Consumer<DateTimePicker>?) = DateTimePicker.configureUsing(block)
}

internal object Dates {
    fun format(value: LocalDate, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(toJava(pattern), locale).format(value)

    fun format(value: LocalTime, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(toJava(pattern), locale).format(value)

    fun format(value: LocalDateTime, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(toJava(pattern), locale).format(value)

    fun parseDate(text: String, pattern: String): LocalDate? = try {
        LocalDate.parse(text, DateTimeFormatter.ofPattern(toJava(pattern)))
    } catch (_: DateTimeParseException) {
        null
    }

    fun parseTime(text: String, pattern: String): LocalTime? = try {
        LocalTime.parse(text, DateTimeFormatter.ofPattern(toJava(pattern)))
    } catch (_: DateTimeParseException) {
        null
    }

    fun parseDateTime(text: String, pattern: String): LocalDateTime? = try {
        LocalDateTime.parse(text, DateTimeFormatter.ofPattern(toJava(pattern)))
    } catch (_: DateTimeParseException) {
        null
    }

    fun toJava(pattern: String): String {
        if (!pattern.any { it == 'Y' || it == 'i' }) {
            return pattern
        }
        val out = StringBuilder()
        for (ch in pattern) {
            when (ch) {
                'Y' -> out.append("yyyy")
                'y' -> out.append("yy")
                'm' -> out.append("MM")
                'n' -> out.append("M")
                'd' -> out.append("dd")
                'j' -> out.append("d")
                'H' -> out.append("HH")
                'G' -> out.append("H")
                'h' -> out.append("hh")
                'g' -> out.append("h")
                'i' -> out.append("mm")
                's' -> out.append("ss")
                'M' -> out.append("MMM")
                'F' -> out.append("MMMM")
                'D' -> out.append("EEE")
                'l' -> out.append("EEEE")
                'A' -> out.append("a")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
