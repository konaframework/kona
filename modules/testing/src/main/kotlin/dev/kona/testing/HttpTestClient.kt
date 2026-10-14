package dev.kona.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kona.core.Application
import dev.kona.http.HttpKernel
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * In-process HTTP test client (get/post/put/patch/delete + JSON assertions).
 */
class HttpTestClient(private val app: Application) {
    private val mapper = jacksonObjectMapper()

    fun get(uri: String): TestResponse = get(uri, emptyMap())

    fun get(uri: String, headers: Map<String, String>): TestResponse =
        handle(Request("GET", uri, headers))

    fun postJson(uri: String, body: Any): TestResponse = postJson(uri, body, emptyMap())

    fun postJson(uri: String, body: Any, headers: Map<String, String>): TestResponse =
        json("POST", uri, body, headers)

    fun put(uri: String): TestResponse = put(uri, "", emptyMap())

    fun put(uri: String, body: String): TestResponse = put(uri, body, emptyMap())

    fun put(uri: String, body: String, headers: Map<String, String>): TestResponse =
        handle(Request("PUT", uri, headers, emptyMap(), body))

    fun putJson(uri: String, body: Any): TestResponse = putJson(uri, body, emptyMap())

    fun putJson(uri: String, body: Any, headers: Map<String, String>): TestResponse =
        json("PUT", uri, body, headers)

    fun patch(uri: String): TestResponse = patch(uri, "", emptyMap())

    fun patch(uri: String, body: String): TestResponse = patch(uri, body, emptyMap())

    fun patch(uri: String, body: String, headers: Map<String, String>): TestResponse =
        handle(Request("PATCH", uri, headers, emptyMap(), body))

    fun patchJson(uri: String, body: Any): TestResponse = patchJson(uri, body, emptyMap())

    fun patchJson(uri: String, body: Any, headers: Map<String, String>): TestResponse =
        json("PATCH", uri, body, headers)

    fun delete(uri: String): TestResponse = delete(uri, emptyMap())

    fun delete(uri: String, headers: Map<String, String>): TestResponse =
        handle(Request("DELETE", uri, headers))

    private fun json(method: String, uri: String, body: Any, headers: Map<String, String>): TestResponse {
        val payload = mapper.writeValueAsString(body)
        val hdrs = headers + mapOf("Content-Type" to "application/json")
        return handle(Request(method, uri, hdrs, emptyMap(), payload))
    }

    private fun handle(request: Request): TestResponse {
        val response = app.resolve(HttpKernel::class.java).handle(request)
        return TestResponse(response, mapper)
    }
}

class TestResponse(
    private val response: Response,
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper = jacksonObjectMapper(),
) {
    fun status(): Int = response.status
    fun body(): String = response.body
    fun assertStatus(expected: Int): TestResponse {
        require(status() == expected) { "Expected status $expected but was ${status()}" }
        return this
    }
    fun assertOk(): TestResponse = assertStatus(200)
    fun assertJsonContains(fragment: String): TestResponse {
        require(body().contains(fragment)) { "Body does not contain $fragment: ${body()}" }
        return this
    }

    /** Assert a dotted JSON path (e.g. `user.name`) equals [expected]. */
    fun assertJsonPath(path: String, expected: Any?): TestResponse {
        val actual = readPath(path)
        require(jsonEquals(actual, expected)) {
            "Expected JSON path [$path] = $expected but was $actual in ${body()}"
        }
        return this
    }

    /** Alias for [assertJsonPath]. */
    fun assertJson(path: String, expected: Any?): TestResponse = assertJsonPath(path, expected)

    private fun readPath(path: String): Any? {
        val root = mapper.readTree(body())
        var node: JsonNode = root
        for (part in path.split('.').filter { it.isNotEmpty() }) {
            require(node.has(part)) { "Missing JSON path [$path] in ${body()}" }
            node = node.get(part)
        }
        return when {
            node.isNull -> null
            node.isTextual -> node.asText()
            node.isBoolean -> node.asBoolean()
            node.isIntegralNumber -> node.asLong()
            node.isFloatingPointNumber -> node.asDouble()
            else -> mapper.convertValue(node, Any::class.java)
        }
    }

    private fun jsonEquals(actual: Any?, expected: Any?): Boolean {
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
