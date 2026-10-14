package dev.kona.testing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kona.core.Application
import dev.kona.http.HttpKernel
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * In-process HTTP test client (get/post/json assertions).
 */
class HttpTestClient(private val app: Application) {
    private val mapper = jacksonObjectMapper()

    fun get(uri: String): TestResponse = get(uri, emptyMap())

    fun get(uri: String, headers: Map<String, String>): TestResponse =
        handle(Request("GET", uri, headers))

    fun postJson(uri: String, body: Any): TestResponse = postJson(uri, body, emptyMap())

    fun postJson(uri: String, body: Any, headers: Map<String, String>): TestResponse {
        val json = mapper.writeValueAsString(body)
        val hdrs = headers + mapOf("Content-Type" to "application/json")
        return handle(Request("POST", uri, hdrs, emptyMap(), json))
    }

    private fun handle(request: Request): TestResponse {
        val response = app.resolve(HttpKernel::class.java).handle(request)
        return TestResponse(response)
    }
}

class TestResponse(private val response: Response) {
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
}
