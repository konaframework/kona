package dev.kona.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal object JsonSupport {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun stringify(value: Any?): String = mapper.writeValueAsString(value)

    /** JSON object body with natural types (lists, maps, numbers, booleans, nulls); empty for non-objects. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(json: String): Map<String, Any?> {
        if (json.isBlank()) {
            return emptyMap()
        }
        val tree = mapper.readTree(json)
        if (!tree.isObject) {
            return emptyMap()
        }
        return mapper.convertValue(tree, Map::class.java) as Map<String, Any?>
    }

    fun parseFlat(json: String): Map<String, String> {
        if (json.isBlank()) {
            return emptyMap()
        }
        val tree = mapper.readTree(json)
        if (!tree.isObject) {
            return emptyMap()
        }
        val result = linkedMapOf<String, String>()
        tree.fields().forEach { (k, v) ->
            result[k] = when {
                v.isNull -> ""
                v.isTextual -> v.asText()
                else -> v.toString()
            }
        }
        return result
    }
}
