package org.konaframework.firefly.infolists

import org.konaframework.firefly.support.FireflyException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

internal object EntryClock {
    var frozen: Instant? = null

    fun now(): Instant {
        val fixed = frozen
        if (fixed != null) return fixed
        return Instant.now()
    }
}

internal fun zoneOf(name: String?): ZoneId {
    if (name == null) return ZoneId.systemDefault()
    try {
        return ZoneId.of(name)
    } catch (ex: DateTimeException) {
        throw FireflyException("Unknown timezone [$name]")
    }
}

internal fun temporalText(state: Any?, pattern: String, zone: ZoneId): String {
    val formatter = try {
        DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
    } catch (ex: IllegalArgumentException) {
        throw FireflyException("Unknown date pattern [$pattern]")
    }
    return when (state) {
        is LocalDate -> formatLocalDate(state, formatter, zone)
        is LocalDateTime -> state.format(formatter)
        is Instant -> state.atZone(zone).format(formatter)
        is OffsetDateTime -> state.format(formatter)
        is ZonedDateTime -> state.format(formatter)
        is java.sql.Timestamp -> state.toInstant().atZone(zone).format(formatter)
        is java.sql.Date -> formatLocalDate(state.toLocalDate(), formatter, zone)
        is Date -> state.toInstant().atZone(zone).format(formatter)
        is String -> formatParsed(state, formatter, zone)
        else -> stringify(state)
    }
}

private fun formatLocalDate(date: LocalDate, formatter: DateTimeFormatter, zone: ZoneId): String {
    return try {
        date.format(formatter)
    } catch (_: DateTimeException) {
        date.atStartOfDay(zone).format(formatter)
    }
}

private fun formatParsed(text: String, formatter: DateTimeFormatter, zone: ZoneId): String {
    val instant = parseInstant(text, zone)
    if (instant == null) return text
    return instant.atZone(zone).format(formatter)
}

internal fun parseInstant(text: String, zone: ZoneId): Instant? {
    try {
        return Instant.parse(text)
    } catch (ex: DateTimeParseException) {
        // try the next shape
    }
    try {
        return OffsetDateTime.parse(text).toInstant()
    } catch (ex: DateTimeParseException) {
        // try the next shape
    }
    try {
        return ZonedDateTime.parse(text).toInstant()
    } catch (ex: DateTimeParseException) {
        // try the next shape
    }
    try {
        return LocalDateTime.parse(text).atZone(zone).toInstant()
    } catch (ex: DateTimeParseException) {
        // try the next shape
    }
    try {
        return LocalDate.parse(text).atStartOfDay(zone).toInstant()
    } catch (ex: DateTimeParseException) {
        return null
    }
}

internal fun toInstant(state: Any?, zone: ZoneId): Instant? {
    return when (state) {
        null -> null
        is Instant -> state
        is OffsetDateTime -> state.toInstant()
        is ZonedDateTime -> state.toInstant()
        is LocalDateTime -> state.atZone(zone).toInstant()
        is LocalDate -> state.atStartOfDay(zone).toInstant()
        is Date -> state.toInstant()
        is String -> parseInstant(state, zone)
        else -> null
    }
}

internal fun sinceText(state: Any?, zone: ZoneId): String {
    val instant = toInstant(state, zone)
    if (instant == null) return stringify(state)
    val seconds = Duration.between(instant, EntryClock.now()).seconds
    val future = seconds < 0
    val abs = if (future) -seconds else seconds
    if (abs < 45) return "just now"
    val phrase = if (abs < 90 * 60) {
        amount(abs / 60, "minute")
    } else if (abs < 36 * 3600) {
        amount(abs / 3600, "hour")
    } else {
        amount(abs / 86400, "day")
    }
    if (future) return "in $phrase"
    return "$phrase ago"
}

private fun amount(count: Long, unit: String): String {
    val n = if (count < 1) 1 else count
    if (n == 1L) return "1 $unit"
    return "$n ${unit}s"
}

internal fun toNumber(state: Any?): Double? {
    return when (state) {
        is Double -> finite(state)
        is Float -> finite(state.toDouble())
        is Number -> state.toDouble()
        is String -> state.toDoubleOrNull()
        else -> null
    }
}

private fun finite(value: Double): Double? {
    if (value.isNaN() || value.isInfinite()) return null
    return value
}

internal fun moneyText(state: Any?, currency: String): String {
    val number = toNumber(state)
    if (number == null) return stringify(state)
    return currency + " " + String.format(Locale.US, "%.2f", number)
}

internal fun numericText(state: Any?, decimals: Int): String {
    if (decimals < 0) throw FireflyException("Numeric scale is negative [$decimals]")
    val number = toNumber(state)
    if (number == null) return stringify(state)
    return String.format(Locale.US, "%.${decimals}f", number)
}

internal fun limitText(value: String, chars: Int?, words: Int?): String {
    var text = value
    if (chars != null && text.length > chars) {
        text = text.substring(0, chars) + "…"
    }
    if (words != null) {
        val parts = text.split(Regex("\\s+"))
        if (parts.size > words) {
            text = parts.take(words).joinToString(" ") + "…"
        }
    }
    return text
}

internal fun stringify(state: Any?): String {
    if (state == null) return ""
    return state.toString()
}

internal fun markdown(source: String): String {
    if (source.isEmpty()) return ""
    val out = StringBuilder()
    val blocks = source.split(Regex("\r?\n\\s*\r?\n"))
    for (block in blocks) {
        val trimmed = block.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.startsWith("```")) {
            out.append("<pre><code>")
            out.append(escapeHtml(fenceBody(trimmed)))
            out.append("</code></pre>")
        } else {
            out.append("<p>")
            out.append(inlineMarkdown(trimmed.replace("\n", " ")))
            out.append("</p>")
        }
    }
    return out.toString()
}

internal fun fenceBody(trimmed: String): String {
    var body = trimmed
    if (body.startsWith("```")) body = body.substring(3)
    val nl = body.indexOf('\n')
    if (nl >= 0) body = body.substring(nl + 1)
    if (body.endsWith("```")) body = body.substring(0, body.length - 3)
    return body.trim()
}

internal fun inlineMarkdown(text: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end < 0) {
                out.append(escapeHtml("**"))
                i += 2
            } else {
                out.append("<strong>")
                out.append(escapeHtml(text.substring(i + 2, end)))
                out.append("</strong>")
                i = end + 2
            }
        } else if (text.startsWith("`", i)) {
            val end = text.indexOf('`', i + 1)
            if (end < 0) {
                out.append(escapeHtml("`"))
                i += 1
            } else {
                out.append("<code>")
                out.append(escapeHtml(text.substring(i + 1, end)))
                out.append("</code>")
                i = end + 1
            }
        } else if (text.startsWith("[", i)) {
            val mid = text.indexOf("](", i + 1)
            if (mid < 0) {
                out.append(escapeHtml(text[i].toString()))
                i += 1
            } else {
                val end = text.indexOf(')', mid + 2)
                if (end < 0) {
                    out.append(escapeHtml(text[i].toString()))
                    i += 1
                } else {
                    val label = text.substring(i + 1, mid)
                    val href = text.substring(mid + 2, end)
                    out.append("""<a href="${escapeHtml(href)}">""")
                    out.append(escapeHtml(label))
                    out.append("</a>")
                    i = end + 1
                }
            }
        } else if (text.startsWith("*", i)) {
            val end = text.indexOf('*', i + 1)
            if (end < 0) {
                out.append(escapeHtml("*"))
                i += 1
            } else {
                out.append("<em>")
                out.append(escapeHtml(text.substring(i + 1, end)))
                out.append("</em>")
                i = end + 1
            }
        } else {
            out.append(escapeHtml(text[i].toString()))
            i += 1
        }
    }
    return out.toString()
}

internal fun toJson(value: Any?, flags: Int): String = JsonWriter(flags).write(value)

internal class JsonWriter(flags: Int) {
    private val pretty = flags and 128 != 0
    private val hexTag = flags and 1 != 0
    private val escapeSlash = flags and 64 == 0
    private val out = StringBuilder()

    fun write(value: Any?): String {
        emit(value, 0)
        return out.toString()
    }

    private fun emit(value: Any?, depth: Int) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Number -> out.append(number(value))
            is Map<*, *> -> emitMap(value, depth)
            is Iterable<*> -> emitList(value, depth)
            else -> emitString(value.toString())
        }
    }

    private fun number(value: Number): String {
        if (value is Double || value is Float) {
            val d = value.toDouble()
            if (d.isNaN() || d.isInfinite()) return "null"
            return d.toString()
        }
        return value.toLong().toString()
    }

    private fun emitMap(value: Map<*, *>, depth: Int) {
        if (value.isEmpty()) {
            out.append("{}")
            return
        }
        out.append('{')
        if (pretty) out.append('\n')
        var first = true
        for ((key, item) in value) {
            if (!first) out.append(if (pretty) ",\n" else ",")
            first = false
            if (pretty) pad(depth + 1)
            emitString(stringify(key))
            out.append(if (pretty) ": " else ":")
            emit(item, depth + 1)
        }
        if (pretty) {
            out.append('\n')
            pad(depth)
        }
        out.append('}')
    }

    private fun emitList(value: Iterable<*>, depth: Int) {
        val items = value.toList()
        if (items.isEmpty()) {
            out.append("[]")
            return
        }
        out.append('[')
        if (pretty) out.append('\n')
        var first = true
        for (item in items) {
            if (!first) out.append(if (pretty) ",\n" else ",")
            first = false
            if (pretty) pad(depth + 1)
            emit(item, depth + 1)
        }
        if (pretty) {
            out.append('\n')
            pad(depth)
        }
        out.append(']')
    }

    private fun pad(depth: Int) {
        var n = 0
        while (n < depth) {
            out.append("  ")
            n += 1
        }
    }

    private fun emitString(text: String) {
        out.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '<' -> if (hexTag) out.append("\\u003C") else out.append('<')
                '/' -> if (escapeSlash) out.append("\\/") else out.append('/')
                else -> {
                    if (ch < ' ') out.append(String.format("\\u%04x", ch.code))
                    else out.append(ch)
                }
            }
        }
        out.append('"')
    }
}
