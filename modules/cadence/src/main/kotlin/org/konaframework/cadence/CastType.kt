package org.konaframework.cadence

import org.konaframework.core.Config
import org.konaframework.support.date.KonaDate
import org.konaframework.support.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * Attribute casts declared in [Cadence.casts]. Reads convert the stored (database) value;
 * writes store the value as given, except [JSON] which serializes maps / lists to a JSON string.
 *
 * Date-time conversions without an explicit offset use the JVM default zone, matching
 * `java.sql.Timestamp` semantics.
 */
enum class CastType {
    INT,
    LONG,
    DOUBLE,
    BOOLEAN,
    STRING,

    /** JSON text ↔ `Map` / `List`. */
    JSON,

    /** `java.time.LocalDateTime`. */
    DATETIME,

    /** `java.time.Instant`. */
    INSTANT,
    ;

    /** Converts a stored value to this cast's Kotlin/Java type; null stays null. */
    fun get(value: Any?): Any? {
        if (value == null) {
            return null
        }
        return when (this) {
            INT -> if (value is Number) value.toInt() else number(value).toInt()
            LONG -> if (value is Number) value.toLong() else number(value).toLong()
            DOUBLE -> if (value is Number) value.toDouble() else number(value)
            BOOLEAN -> Config.toBoolean(value) ?: throw CadenceException("Cannot cast [$value] to boolean")
            STRING -> value.toString()
            JSON -> if (value is String) MAPPER.readValue(value, Any::class.java) else value
            DATETIME -> if (value is LocalDateTime) value else LocalDateTime.ofInstant(instant(value), ZoneId.systemDefault())
            INSTANT -> instant(value)
        }
    }

    /** Converts a value being assigned into its stored form. */
    fun set(value: Any?): Any? =
        if (this == JSON && value != null && value !is String) MAPPER.writeValueAsString(value) else value
}

private val MAPPER = Json.mapper

private fun number(value: Any): Double = when (value) {
    is Boolean -> if (value) 1.0 else 0.0
    else -> value.toString().trim().toDouble()
}

private fun instant(value: Any): Instant = when (value) {
    is Instant -> value
    is KonaDate -> value.toInstant()
    is java.sql.Date -> value.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
    is java.util.Date -> value.toInstant()
    is LocalDate -> value.atStartOfDay(ZoneId.systemDefault()).toInstant()
    is LocalDateTime -> value.atZone(ZoneId.systemDefault()).toInstant()
    is OffsetDateTime -> value.toInstant()
    is ZonedDateTime -> value.toInstant()
    is String -> parseInstant(value.trim())
    else -> throw CadenceException("Cannot cast [$value] (${value.javaClass.name}) to a date-time")
}

private fun parseInstant(text: String): Instant = try {
    Instant.parse(text)
} catch (_: DateTimeParseException) {
    LocalDateTime.parse(text.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant()
}
