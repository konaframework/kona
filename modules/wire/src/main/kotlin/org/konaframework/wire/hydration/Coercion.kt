package org.konaframework.wire.hydration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.lang.reflect.Type

/** Shared JSON mapper + scalar coercion (PHP-style lenient casting for values arriving from the browser). */
object Coercion {
    @JvmField
    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val TRUE = setOf("1", "true", "on", "yes")

    /** Default value for a missing argument / property of [type] (`0`, `false`, or `null`). */
    @JvmStatic
    fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    /**
     * Coerce [value] to [type]. Blank strings become `null` for numeric / temporal targets (Livewire unsets typed
     * properties on `''`). Throws [IllegalArgumentException] when no sensible conversion exists.
     */
    @JvmStatic
    @JvmOverloads
    fun toType(value: Any?, type: Class<*>, generic: Type = type): Any? {
        if (value == null) return defaultFor(type)
        val boxed = box(type)
        if (generic is Class<*> && boxed.isInstance(value)) return value
        if (value is String) {
            fromString(value, boxed)?.let { return it.value }
        }
        if (boxed == String::class.java && (value is Number || value is Boolean || value is Char)) return value.toString()
        if (boxed == java.lang.Boolean::class.java && value is Number) return value.toDouble() != 0.0
        if (boxed.isEnum && value is Number) return boxed.enumConstants[value.toInt()]
        return try {
            mapper.convertValue<Any?>(value, mapper.typeFactory.constructType(if (generic is Class<*>) boxed else generic))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Cannot convert value to ${type.typeName}", e)
        }
    }

    private class Holder(val value: Any?)

    private fun fromString(value: String, type: Class<*>): Holder? {
        val trimmed = value.trim()
        return when {
            type == java.lang.Boolean::class.java -> Holder(trimmed.lowercase() in TRUE)
            Number::class.java.isAssignableFrom(type) || type == java.lang.Character::class.java ->
                if (trimmed.isEmpty()) Holder(null) else null
            type.isEnum -> Holder(
                type.enumConstants.firstOrNull { (it as Enum<*>).name == trimmed }
                    ?: throw IllegalArgumentException("No ${type.simpleName} constant [$trimmed]"),
            )
            type.name.startsWith("java.time.") && trimmed.isEmpty() -> Holder(null)
            else -> null
        }
    }

    @JvmStatic
    fun box(type: Class<*>): Class<*> = if (!type.isPrimitive) type else when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        else -> java.lang.Character::class.java
    }
}
