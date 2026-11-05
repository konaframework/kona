package org.konaframework.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Dotted-path reads over a JSON tree shared by [TestResponse] and [InertiaAssert]: `user.name`,
 * `cart.lines.0.sku`. A numeric segment indexes arrays; every other segment reads an object key.
 */
internal object JsonPaths {
    /** The node at [path], or null when any segment is missing. An empty [path] is [root]. */
    fun find(root: JsonNode, path: String): JsonNode? {
        var node: JsonNode = root
        for (part in path.split('.').filter { it.isNotEmpty() }) {
            val next = if (node.isArray) node.get(part.toIntOrNull() ?: return null) else node.get(part)
            node = next ?: return null
        }
        return node
    }

    /** Plain Kotlin value for [node] (null, String, Boolean, Long, Double, or a Map / List tree). */
    fun plain(node: JsonNode, mapper: ObjectMapper): Any? = when {
        node.isNull -> null
        node.isTextual -> node.asText()
        node.isBoolean -> node.asBoolean()
        node.isIntegralNumber -> node.asLong()
        node.isFloatingPointNumber -> node.asDouble()
        else -> mapper.convertValue(node, Any::class.java)
    }

    /** Loose equality: numbers compare by value, anything else falls back to its string form. */
    fun equal(actual: Any?, expected: Any?): Boolean {
        if (actual == expected) return true
        if (actual == null || expected == null) return false
        if (actual is Number) {
            if (expected is Number) {
                return actual.toDouble() == expected.toDouble()
            }
            return actual.toString() == expected.toString()
        }
        return actual.toString() == expected.toString()
    }
}
