package org.konaframework.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.function.Predicate

/**
 * Assertions over an Inertia page object (`component`, `props`, `url`, `version`).
 *
 * Paths are dotted and rooted at the props: `has("cart.lines", 2)` reads `props.cart.lines`.
 * A numeric segment indexes a list (`cart.lines.0.sku`). Every method returns `this`, so Java
 * callers chain them: `response.inertia().component("Pos/Register").has("cart.lines", 2)`.
 */
class InertiaAssert internal constructor(
    private val page: JsonNode,
    private val mapper: ObjectMapper,
) {
    /** The page `component` name. */
    fun component(): String = page.path("component").asText()

    /** Assert the page renders [expected] (`Pos/Register`). */
    fun component(expected: String): InertiaAssert = apply {
        val actual = component()
        require(actual == expected) { "Expected Inertia component [$expected] but was [$actual]" }
    }

    /** Assert the page `url`. */
    fun url(expected: String): InertiaAssert = apply {
        val actual = page.path("url").asText()
        require(actual == expected) { "Expected Inertia url [$expected] but was [$actual]" }
    }

    /** Assert the asset `version`. */
    fun version(expected: String): InertiaAssert = apply {
        val actual = page.path("version").asText()
        require(actual == expected) { "Expected Inertia version [$expected] but was [$actual]" }
    }

    /** Assert [path] exists in the props (a JSON `null` value still exists). */
    fun has(path: String): InertiaAssert = apply {
        requireNotNull(read(path)) { "Inertia props are missing [$path]: ${page.path("props")}" }
    }

    /** Assert [path] exists and is a list or object with [count] entries. */
    fun has(path: String, count: Int): InertiaAssert = apply {
        val node = requireNotNull(read(path)) { "Inertia props are missing [$path]: ${page.path("props")}" }
        require(node.isContainerNode) { "Inertia prop [$path] is not a list or object, so it has no size" }
        require(node.size() == count) { "Expected Inertia prop [$path] to have $count entries but it has ${node.size()}" }
    }

    /** Assert every path in [paths] exists in the props. */
    fun hasAll(vararg paths: String): InertiaAssert = apply { paths.forEach { has(it) } }

    /** Assert [path] is absent from the props. */
    fun missing(path: String): InertiaAssert = apply {
        require(read(path) == null) { "Inertia props unexpectedly contain [$path]" }
    }

    /** Assert the prop at [path] equals [expected] (numbers compare by value). */
    fun where(path: String, expected: Any?): InertiaAssert = apply {
        val actual = plain(path)
        require(JsonPaths.equal(actual, expected)) { "Expected Inertia prop [$path] = $expected but was $actual" }
    }

    /** Assert [check] accepts the prop at [path] (plain String / Number / Boolean / List / Map / null). */
    fun where(path: String, check: Predicate<Any?>): InertiaAssert = apply {
        val actual = plain(path)
        require(check.test(actual)) { "Inertia prop [$path] = $actual did not match the predicate" }
    }

    /** The prop at [path] as a plain value (String, Number, Boolean, List, Map, or null). */
    fun prop(path: String): Any? = plain(path)

    /** All props as a plain map. */
    fun props(): Map<String, Any?> {
        val plain = JsonPaths.plain(page.path("props"), mapper)
        if (plain is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return plain as Map<String, Any?>
        }
        return emptyMap()
    }

    private fun read(path: String): JsonNode? = JsonPaths.find(page.path("props"), path)

    private fun plain(path: String): Any? {
        val node = requireNotNull(read(path)) { "Inertia props are missing [$path]: ${page.path("props")}" }
        return JsonPaths.plain(node, mapper)
    }
}
