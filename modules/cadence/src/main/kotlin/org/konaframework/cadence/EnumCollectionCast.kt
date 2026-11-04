package org.konaframework.cadence

import com.fasterxml.jackson.core.JsonProcessingException
import org.konaframework.support.json.Json

/**
 * JSON array of enums cast (`AsEnumCollection` shape).
 *
 * Reads return `List` of enum constants; writes accept a [Collection] of enums / raw values
 * and store a JSON array of each enum's storage value ([EnumCast.enumStoredValue]).
 */
class EnumCollectionCast(
    private val enumClass: Class<out Enum<*>>,
) : CastsAttributes {
    init {
        require(enumClass.isEnum) { "Enum collection cast requires an enum class (got ${enumClass.name})" }
    }

    override fun get(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) {
            return null
        }
        val items = when (value) {
            is String -> parseJsonArray(key, value)
            is Collection<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> throw CadenceException(
                "Cannot cast [$value] (${value.javaClass.name}) to enum collection for [$key]",
            )
        }
        return items.map { item ->
            if (item == null) {
                throw CadenceException("Enum collection for [$key] contains null")
            }
            EnumCast.resolveEnum(enumClass, item)
        }
    }

    override fun set(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) {
            return null
        }
        if (value is String) {
            val parsed = get(model, key, value, attributes) as List<*>
            val stored = parsed.map { EnumCast.enumStoredValue(it as Enum<*>) }
            return MAPPER.writeValueAsString(stored)
        }
        val items: List<*> =
            when (value) {
                is Collection<*> -> value.toList()
                is Array<*> -> value.toList()
                else -> throw CadenceException(
                    "Cannot store [$value] (${value.javaClass.name}) as enum collection for [$key]",
                )
            }
        val stored =
            items.map { item ->
                if (item == null) {
                    throw CadenceException("Enum collection for [$key] contains null")
                }
                val constant =
                    if (enumClass.isInstance(item)) {
                        item as Enum<*>
                    } else {
                        EnumCast.resolveEnum(enumClass, item)
                    }
                EnumCast.enumStoredValue(constant)
            }
        return MAPPER.writeValueAsString(stored)
    }

    private fun parseJsonArray(key: String, value: String): List<*> {
        if (value.isBlank()) {
            return emptyList<Any>()
        }
        val parsed =
            try {
                MAPPER.readValue(value, Any::class.java)
            } catch (ex: JsonProcessingException) {
                throw CadenceException("Enum collection cast expected a JSON array for [$key], got: $value", ex)
            }
        return parsed as? List<*>
            ?: throw CadenceException("Enum collection cast expected a JSON array for [$key], got: $value")
    }

    private companion object {
        private val MAPPER = Json.mapper
    }
}
