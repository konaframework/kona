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

    override fun kind(): String = when {
        withDate && withTime -> "date-time-picker"
        withTime -> "time-picker"
        else -> "date-picker"
    }

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
        val disabledCsv = disabledList.joinToString(",")
        val kind = when {
            withDate && withTime -> "datetime"
            withTime -> "time"
            else -> "date"
        }
        return buildString {
            append("""<div class="ff-picker" data-ff-picker data-ff-picker-kind="$kind" data-ff-path="${esc(path)}"${boolAttr("data-ff-date", withDate)}${boolAttr("data-ff-time", withTime)}${attr("data-ff-format", formatText)}${attr("data-ff-display", displayText)}${attr("data-ff-locale", localeTag)}${attr("data-ff-zone", zoneId.id)} data-ff-first-day="$firstDay"${boolAttr("data-ff-close", closeOnSelect)} data-ff-hours-step="$hoursStepCount" data-ff-minutes-step="$minutesStepCount" data-ff-seconds-step="$secondsStepCount"${boolAttr("data-ff-seconds", secondsFlag)}${attr("data-ff-min", min)}${attr("data-ff-max", max)}${attr("data-ff-disabled", disabledCsv.ifBlank { null })}${attr("data-ff-state", stored)}${attr("data-ff-focused", focused.toString())}>""")
            append("""<div class="ff-picker-field" data-ff-picker-open>""")
            if (withDate) append("""<span class="ff-picker-icon" aria-hidden="true">${calendarIcon()}</span>""")
            append("""<input class="ff-input ff-picker-display" type="text" readonly tabindex="0" placeholder="${esc(placeholderFor())}" value="${esc(display(stored))}" data-ff-picker-display${extraInputMarkup(get)} />""")
            if (withTime && !withDate) append("""<span class="ff-picker-caret" aria-hidden="true"></span>""")
            append("</div>")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(stored)}" data-ff-picker-value />""")
            append("""<div class="ff-picker-panel" data-ff-picker-panel hidden>""")
            if (withDate) {
                append("""<div class="ff-picker-nav">""")
                append("""<button type="button" class="ff-picker-nav-btn" data-ff-picker-prev aria-label="Previous month">‹</button>""")
                append("""<div class="ff-picker-caption" data-ff-picker-caption>${esc(monthCaption(focused))}</div>""")
                append("""<button type="button" class="ff-picker-nav-btn" data-ff-picker-next aria-label="Next month">›</button>""")
                append("</div>")
                append(weekdayHeader())
                append(monthGrid(schema, focused, stored))
            }
            if (withTime) append(timeSpinners(stored))
            append("""<div class="ff-picker-actions">""")
            append("""<button type="button" class="ff-picker-today" data-ff-picker-today>Today</button>""")
            append("""<button type="button" class="ff-picker-clear" data-ff-picker-clear>Clear</button>""")
            append("</div></div></div>")
        }
    }

    private fun placeholderFor(): String = when {
        withDate && withTime -> "Select date and time"
        withTime -> "Select time"
        else -> "Select date"
    }

    private fun monthCaption(focused: LocalDate): String =
        focused.month.getDisplayName(TextStyle.FULL, locale()) + " " + focused.year

    private fun weekdayHeader(): String {
        val locale = locale()
        val names = ArrayList<String>(7)
        val start = if (firstDay == 1) 1 else 7
        for (i in 0 until 7) {
            val day = java.time.DayOfWeek.of(((start - 1 + i) % 7) + 1)
            names.add(day.getDisplayName(TextStyle.SHORT, locale))
        }
        return names.joinToString("", prefix = """<div class="ff-picker-weekdays">""", postfix = "</div>") {
            """<span>${esc(it)}</span>"""
        }
    }

    private fun monthGrid(schema: Schema, focused: LocalDate, stored: String): String {
        val selected = parseDate(stored)
        val first = focused.withDayOfMonth(1)
        val lead = Math.floorMod(first.dayOfWeek.value - if (firstDay == 1) 1 else 7, 7)
        val start = first.minusDays(lead.toLong())
        return buildString {
            append("""<div class="ff-picker-grid" data-ff-picker-grid data-ff-month="${focused.year}-${focused.monthValue.toString().padStart(2, '0')}">""")
            var cursor = start
            var cell = 0
            while (cell < 42) {
                val outside = cursor.month != focused.month
                val disabled = isDisabled(cursor, schema)
                val isSelected = selected != null && cursor == selected
                val css = buildString {
                    append("ff-day")
                    if (outside) append(" ff-day-outside")
                    if (disabled) append(" ff-day-disabled")
                    if (isSelected) append(" ff-day-selected")
                }
                val disabledAttr = if (disabled) " disabled" else ""
                append("""<button type="button" class="$css" data-ff-day="$cursor"$disabledAttr>${cursor.dayOfMonth}</button>""")
                cursor = cursor.plusDays(1)
                cell += 1
            }
            append("</div>")
        }
    }

    private fun timeSpinners(stored: String): String {
        val time = timePart(stored) ?: LocalTime.MIDNIGHT
        return buildString {
            append("""<div class="ff-time" data-ff-picker-time>""")
            append(spinner("hours", hourChoices(), time.hour))
            append("""<span class="ff-time-sep">:</span>""")
            append(spinner("minutes", minuteChoices(), time.minute))
            if (secondsFlag) {
                append("""<span class="ff-time-sep">:</span>""")
                append(spinner("seconds", secondChoices(), time.second))
            }
            append("</div>")
        }
    }

    private fun spinner(name: String, values: List<Int>, selected: Int): String = buildString {
        append("""<select class="ff-time-$name" data-ff-$name="true" aria-label="$name">""")
        for (value in values) {
            val sel = if (value == selected) " selected" else ""
            append("""<option value="$value"$sel>${value.toString().padStart(2, '0')}</option>""")
        }
        append("</select>")
    }

    private fun calendarIcon(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" width="16" height="16"><path d="M6 2a1 1 0 0 0-1 1v1H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-1V3a1 1 0 1 0-2 0v1H7V3a1 1 0 0 0-1-1zm10 7H4v7h12V9z"/></svg>"""

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

/** Date-only picker (`DateTimePicker` with date, no time). */
object DatePicker {
    @JvmStatic
    fun make(name: String): DateTimePicker = DateTimePicker.date(name)

    @JvmStatic
    fun configureUsing(block: Consumer<DateTimePicker>?) = DateTimePicker.configureUsing(block)
}

/** Time-only picker (`DateTimePicker` with time, no date). */
object TimePicker {
    @JvmStatic
    fun make(name: String): DateTimePicker = DateTimePicker.time(name)

    @JvmStatic
    fun configureUsing(block: Consumer<DateTimePicker>?) = DateTimePicker.configureUsing(block)
}

/**
 * Flatpickr-inspired range: stores `"start to end"` (format dates) or empty.
 * Renders one field with a dual-select calendar popup.
 */
open class DateRangePicker protected constructor(name: String) : Field<DateRangePicker>(name) {
    private var formatText: String = "Y-m-d"
    private var displayText: String = "M j, Y"
    private var localeTag: String = Locale.getDefault().toLanguageTag()
    private var firstDay: Int = 1
    private val minText = Phrase()
    private val maxText = Phrase()
    private val focusedText = Phrase()
    private val disabledList = ArrayList<String>()

    fun format(pattern: String): DateRangePicker = apply { formatText = pattern }

    fun displayFormat(pattern: String): DateRangePicker = apply { displayText = pattern }

    fun locale(tag: String): DateRangePicker = apply { localeTag = tag }

    fun firstDayOfWeek(day: Int): DateRangePicker = apply {
        when (day) {
            0, 1 -> firstDay = day
            else -> throw FireflyException("First day of week must be 0 (Sunday) or 1 (Monday)")
        }
    }

    fun weekStartsOnMonday(): DateRangePicker = firstDayOfWeek(1)

    fun weekStartsOnSunday(): DateRangePicker = firstDayOfWeek(0)

    fun minDate(value: String): DateRangePicker = apply { minText.set(value) }

    fun maxDate(value: String): DateRangePicker = apply { maxText.set(value) }

    fun defaultFocusedDate(value: String): DateRangePicker = apply { focusedText.set(value) }

    fun disabledDates(dates: List<String>): DateRangePicker = apply {
        disabledList.clear()
        disabledList.addAll(dates)
    }

    fun normalize(raw: Any?): String? {
        val text = raw as? String ?: run {
            if (raw == null) return null
            throw FireflyException("Unsupported date range value [${raw.javaClass.name}]")
        }
        if (text.isBlank()) return null
        val parts = splitRange(text) ?: return null
        return "${parts.first} to ${parts.second}"
    }

    fun display(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        val parts = splitRange(stored) ?: return ""
        val start = Dates.parseDate(parts.first, formatText) ?: return ""
        val end = Dates.parseDate(parts.second, formatText) ?: return ""
        val locale = Locale.forLanguageTag(localeTag)
        return Dates.format(start, displayText, locale) + " – " + Dates.format(end, displayText, locale)
    }

    override fun kind(): String = "date-range-picker"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val stored = if (value == null) "" else value.toString()
        val min = minText.get(get)
        val max = maxText.get(get)
        val parts = splitRange(stored)
        val focusRaw = focusedText.get(get)
        val focusedCandidate = when {
            !focusRaw.isNullOrBlank() -> focusRaw.take(10)
            parts != null -> parts.first.take(10)
            else -> null
        }
        val focused = focusedCandidate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: FieldClock.today()
        val locale = Locale.forLanguageTag(localeTag)
        val caption = focused.month.getDisplayName(TextStyle.FULL, locale) + " " + focused.year
        return buildString {
            append("""<div class="ff-picker" data-ff-picker data-ff-picker-kind="range" data-ff-path="${esc(path)}" data-ff-date="true" data-ff-time="false"${attr("data-ff-format", formatText)}${attr("data-ff-display", displayText)}${attr("data-ff-locale", localeTag)} data-ff-first-day="$firstDay" data-ff-close="false"${attr("data-ff-min", min)}${attr("data-ff-max", max)}${attr("data-ff-disabled", disabledList.joinToString(",").ifBlank { null })}${attr("data-ff-state", stored)}${attr("data-ff-focused", focused.toString())}>""")
            append("""<div class="ff-picker-field" data-ff-picker-open>""")
            append("""<span class="ff-picker-icon" aria-hidden="true"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" width="16" height="16"><path d="M6 2a1 1 0 0 0-1 1v1H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-1V3a1 1 0 1 0-2 0v1H7V3a1 1 0 0 0-1-1zm10 7H4v7h12V9z"/></svg></span>""")
            append("""<input class="ff-input ff-picker-display" type="text" readonly tabindex="0" placeholder="Select date range" value="${esc(display(stored))}" data-ff-picker-display />""")
            append("</div>")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(stored)}" data-ff-picker-value />""")
            append("""<div class="ff-picker-panel" data-ff-picker-panel hidden>""")
            append("""<div class="ff-picker-nav"><button type="button" class="ff-picker-nav-btn" data-ff-picker-prev aria-label="Previous month">‹</button>""")
            append("""<div class="ff-picker-caption" data-ff-picker-caption>${esc(caption)}</div>""")
            append("""<button type="button" class="ff-picker-nav-btn" data-ff-picker-next aria-label="Next month">›</button></div>""")
            append(rangeWeekdays(locale))
            append(rangeGrid(focused, parts))
            append("""<div class="ff-picker-actions"><button type="button" class="ff-picker-today" data-ff-picker-today>Today</button>""")
            append("""<button type="button" class="ff-picker-clear" data-ff-picker-clear>Clear</button></div></div></div>""")
        }
    }

    private fun rangeWeekdays(locale: Locale): String {
        val names = ArrayList<String>(7)
        val start = when (firstDay) {
            1 -> 1
            else -> 7
        }
        for (i in 0 until 7) {
            val day = java.time.DayOfWeek.of(((start - 1 + i) % 7) + 1)
            names.add(day.getDisplayName(TextStyle.SHORT, locale))
        }
        return names.joinToString("", prefix = """<div class="ff-picker-weekdays">""", postfix = "</div>") {
            """<span>${esc(it)}</span>"""
        }
    }

    private fun rangeGrid(focused: LocalDate, parts: Pair<String, String>?): String {
        val startSel = parseRangeDay(parts?.first)
        val endSel = parseRangeDay(parts?.second)
        val first = focused.withDayOfMonth(1)
        val weekAnchor = when (firstDay) {
            1 -> 1
            else -> 7
        }
        val lead = Math.floorMod(first.dayOfWeek.value - weekAnchor, 7)
        var cursor = first.minusDays(lead.toLong())
        return buildString {
            append("""<div class="ff-picker-grid" data-ff-picker-grid data-ff-month="${focused.year}-${focused.monthValue.toString().padStart(2, '0')}">""")
            repeat(42) {
                val classes = ArrayList<String>(4)
                classes.add("ff-day")
                if (cursor.month != focused.month) classes.add("ff-day-outside")
                if (startSel != null && endSel != null && !cursor.isBefore(startSel) && !cursor.isAfter(endSel)) {
                    classes.add("ff-day-in-range")
                }
                if (cursor == startSel || cursor == endSel) classes.add("ff-day-selected")
                append("""<button type="button" class="${classes.joinToString(" ")}" data-ff-day="$cursor">${cursor.dayOfMonth}</button>""")
                cursor = cursor.plusDays(1)
            }
            append("</div>")
        }
    }

    private fun parseRangeDay(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    companion object {
        private val maker = Maker { name -> DateRangePicker(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<DateRangePicker>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): DateRangePicker = maker.make(name)

        internal fun splitRange(text: String): Pair<String, String>? {
            val normalized = text.trim()
            for (sep in listOf(" to ", " – ", " - ", "/")) {
                if (sep == "/" && normalized.count { it == '/' } != 1) continue
                val idx = normalized.indexOf(sep)
                if (idx < 0) continue
                val a = normalized.substring(0, idx).trim()
                val b = normalized.substring(idx + sep.length).trim()
                if (a.isNotEmpty() && b.isNotEmpty()) return a to b
            }
            return null
        }
    }
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
