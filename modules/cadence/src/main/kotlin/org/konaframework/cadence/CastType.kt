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
import kotlin.reflect.KClass

/**
 * Attribute casts declared in [Cadence.casts]. Reads convert the stored (database) value;
 * writes store the value as given, except [JSON] which serializes maps / lists to a JSON string,
 * and [HASHED] which hashes plaintext via [CastHasher].
 *
 * Parameterized casts use companion factories ([decimal], [enumCast], [enumCollection]) or
 * declare an enum [Class] / [KClass] / `"decimal:<scale>"` string in the casts map.
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

    /**
     * Password hash on write via [CastHasher] / auth `Hash.make`.
     * Values that already look hashed ([AttributeHasher.isHashed]) are stored untouched.
     * Reads return the stored string as-is.
     */
    HASHED,
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
            HASHED -> value.toString()
        }
    }

    /** Converts a value being assigned into its stored form. */
    fun set(value: Any?): Any? {
        if (value == null) {
            return null
        }
        return when (this) {
            JSON -> if (value is String) value else MAPPER.writeValueAsString(value)
            HASHED -> {
                val text = value.toString()
                val hasher = CastHasher.get()
                if (hasher.isHashed(text)) text else hasher.make(text)
            }
            else -> value
        }
    }

    companion object {
        /** Exact [java.math.BigDecimal] cast with the given scale (`RoundingMode.HALF_UP`). */
        @JvmStatic
        @JvmOverloads
        fun decimal(scale: Int = 2): CastsAttributes = DecimalCast(scale)

        /** Single-enum cast; Java: `CastType.enumCast(OrderState.class)`. */
        @JvmStatic
        fun enumCast(type: Class<out Enum<*>>): CastsAttributes = EnumCast(type)

        /** Alias for [enumCast] preferred from Java (`CastType.enumType(Status.class)`). */
        @JvmStatic
        fun enumType(type: Class<out Enum<*>>): CastsAttributes = EnumCast(type)

        /** Single-enum cast from a Kotlin class reference (Companion-only; no duplicate JVM static). */
        fun enumCast(type: KClass<out Enum<*>>): CastsAttributes = EnumCast(type.java)

        /** JSON array of enums; Java: `CastType.enumCollection(OrderState.class)`. */
        @JvmStatic
        fun enumCollection(type: Class<out Enum<*>>): CastsAttributes = EnumCollectionCast(type)

        /** JSON array of enums from a Kotlin class reference (Companion-only). */
        fun enumCollection(type: KClass<out Enum<*>>): CastsAttributes = EnumCollectionCast(type.java)

        /**
         * Resolves a casts-map entry into [CastType] or [CastsAttributes].
         * Accepts enum [Class]/[KClass], `"decimal"` / `"decimal:<scale>"`, `"hashed"`, and built-in cast names.
         */
        @JvmStatic
        fun resolve(cast: Any): Any =
            when (cast) {
                is CastType, is CastsAttributes -> cast
                is KClass<*> -> resolve(cast.java)
                is Class<*> -> {
                    if (cast.isEnum) {
                        @Suppress("UNCHECKED_CAST")
                        EnumCast(cast as Class<out Enum<*>>)
                    } else {
                        throw CadenceException("Invalid cast class [${cast.name}]; expected an enum type")
                    }
                }
                is String -> parseString(cast)
                else -> throw CadenceException("Invalid cast: ${cast.javaClass.name}")
            }

        private fun parseString(spec: String): Any {
            val trimmed = spec.trim()
            if (trimmed.isEmpty()) {
                throw CadenceException("Invalid cast: empty string")
            }
            val lower = trimmed.lowercase()
            if (lower == "hashed") {
                return HASHED
            }
            if (lower == "decimal" || lower.startsWith("decimal:")) {
                val scale =
                    if (lower == "decimal") {
                        2
                    } else {
                        trimmed.substringAfter(':').trim().toIntOrNull()
                            ?: throw CadenceException("Invalid decimal cast [$spec]; scale must be an integer")
                    }
                return DecimalCast(scale)
            }
            return entries.find { it.name.equals(trimmed, ignoreCase = true) }
                ?: throw CadenceException("Invalid cast [$spec]")
        }
    }
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
