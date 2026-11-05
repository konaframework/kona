package org.konaframework.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.konaframework.http.Cookie
import org.konaframework.http.RequestSession
import org.konaframework.http.Response

private val RESPONSE_REDIRECTS = setOf(301, 302, 303, 307, 308)
private val RESPONSE_ORIGIN = Regex("^https?://[^/]+")
private val DATA_PAGE = Regex("""data-page=(["'])(.*?)\1""", RegexOption.DOT_MATCHES_ALL)

/**
 * The response of an in-process request, with fluent assertions. Failed assertions throw
 * [IllegalArgumentException] describing the mismatch.
 *
 * [session] is the session the request ran with (read after the response was produced, so flashed
 * values and validation errors are visible); null when the app has no `StartSession` middleware.
 * [routeUrl] resolves a route name for [assertRedirectToRoute].
 */
class TestResponse(
    private val response: Response,
    private val mapper: ObjectMapper,
    private val session: RequestSession?,
    private val routeUrl: ((String, Map<String, String>) -> String)?,
) {
    constructor(response: Response) : this(response, jacksonObjectMapper(), null, null)

    constructor(response: Response, mapper: ObjectMapper) : this(response, mapper, null, null)

    constructor(response: Response, mapper: ObjectMapper, session: RequestSession?) :
        this(response, mapper, session, null)
    fun status(): Int = response.status

    fun body(): String = response.body

    /** Header [name] (case-insensitive), or null. */
    fun header(name: String): String? =
        response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** The `Location` header of a redirect, or null. */
    fun location(): String? = header("Location")

    /** Cookies the response set. */
    fun cookies(): List<Cookie> = response.cookies.toList()

    /** The session the request ran with, or null. */
    fun session(): RequestSession? = session

    // ——— Status ———

    fun assertStatus(expected: Int): TestResponse {
        require(status() == expected) { "Expected status $expected but was ${status()}" }
        return this
    }

    fun assertOk(): TestResponse = assertStatus(200)

    fun assertCreated(): TestResponse = assertStatus(201)

    fun assertNoContent(): TestResponse = assertStatus(204)

    fun assertUnauthorized(): TestResponse = assertStatus(401)

    fun assertForbidden(): TestResponse = assertStatus(403)

    fun assertNotFound(): TestResponse = assertStatus(404)

    fun assertUnprocessable(): TestResponse = assertStatus(422)

    /** Status in the 2xx range. */
    fun assertSuccessful(): TestResponse = apply {
        require(status() in 200..299) { "Expected a successful status but was ${status()}" }
    }

    // ——— Redirects ———

    /** Assert a redirect (301, 302, 303, 307 or 308). */
    fun assertRedirect(): TestResponse = apply {
        require(status() in RESPONSE_REDIRECTS) { "Expected a redirect but status was ${status()}" }
        require(!location().isNullOrBlank()) { "Redirect response has no Location header" }
    }

    /**
     * Assert a redirect to [uri]. A path (`/login`) also matches an absolute Location on any host
     * (`http://localhost/login`).
     */
    fun assertRedirect(uri: String): TestResponse = apply {
        assertRedirect()
        val actual = location()!!
        require(actual == uri || withoutOrigin(actual) == uri) { "Expected redirect to [$uri] but was [$actual]" }
    }

    /** Assert a redirect to the URL of route [name] (needs a router with named routes). */
    fun assertRedirectToRoute(name: String): TestResponse = assertRedirectToRoute(name, emptyMap())

    fun assertRedirectToRoute(name: String, parameters: Map<String, String>): TestResponse {
        val resolver = checkNotNull(routeUrl) { "This response was created without a router, so routes cannot be resolved" }
        return assertRedirect(resolver(name, parameters))
    }

    // ——— Body ———

    fun assertJsonContains(fragment: String): TestResponse {
        require(body().contains(fragment)) { "Body does not contain $fragment: ${body()}" }
        return this
    }

    /** Assert the body contains [text]. */
    fun assertSee(text: String): TestResponse = apply {
        require(body().contains(text)) { "Expected the response to contain [$text]: ${body()}" }
    }

    /** Assert the body contains every text in [texts], in order. */
    fun assertSeeInOrder(vararg texts: String): TestResponse = apply {
        var from = 0
        for (text in texts) {
            val at = body().indexOf(text, from)
            require(at >= 0) { "Expected the response to contain [$text] after offset $from: ${body()}" }
            from = at + text.length
        }
    }

    /** Assert the body does not contain [text]. */
    fun assertDontSee(text: String): TestResponse = apply {
        require(!body().contains(text)) { "Expected the response not to contain [$text]: ${body()}" }
    }

    /** Assert a dotted JSON path (e.g. `user.name`, `lines.0.sku`) equals [expected]. */
    fun assertJsonPath(path: String, expected: Any?): TestResponse {
        val actual = readPath(path)
        require(JsonPaths.equal(actual, expected)) {
            "Expected JSON path [$path] = $expected but was $actual in ${body()}"
        }
        return this
    }

    /** Alias for [assertJsonPath]. */
    fun assertJson(path: String, expected: Any?): TestResponse = assertJsonPath(path, expected)

    // ——— Headers and cookies ———

    /** Assert header [name] is present. */
    fun assertHeader(name: String): TestResponse = apply {
        require(header(name) != null) { "Expected header [$name] to be present: ${response.headers}" }
    }

    /** Assert header [name] equals [expected]. */
    fun assertHeader(name: String, expected: String): TestResponse = apply {
        val actual = header(name)
        require(actual == expected) { "Expected header [$name] = [$expected] but was [$actual]" }
    }

    /** Assert header [name] is absent. */
    fun assertHeaderMissing(name: String): TestResponse = apply {
        require(header(name) == null) { "Expected header [$name] to be absent but was [${header(name)}]" }
    }

    /** Assert the response set cookie [name]. */
    fun assertCookie(name: String): TestResponse = apply {
        require(cookie(name) != null) { "Expected cookie [$name] to be set: ${cookies().map { it.name }}" }
    }

    /** Assert the response set cookie [name] to [expected]. */
    fun assertCookie(name: String, expected: String): TestResponse = apply {
        val actual = cookie(name)?.value
        require(actual == expected) { "Expected cookie [$name] = [$expected] but was [$actual]" }
    }

    /** Assert the response did not set cookie [name]. */
    fun assertCookieMissing(name: String): TestResponse = apply {
        require(cookie(name) == null) { "Expected cookie [$name] not to be set" }
    }

    /** Assert the response expired cookie [name] (empty value or zero max-age). */
    fun assertCookieExpired(name: String): TestResponse = apply {
        val found = cookie(name)
        require(found != null && isExpired(found)) { "Expected cookie [$name] to be expired" }
    }

    // ——— Session ———

    /** Assert the session holds a value under [key]. */
    fun assertSessionHas(key: String): TestResponse = apply {
        require(requireSession().get(key) != null) { "Expected the session to have [$key]" }
    }

    /** Assert the session holds [expected] under [key]. */
    fun assertSessionHas(key: String, expected: Any?): TestResponse = apply {
        val actual = requireSession().get(key)
        require(JsonPaths.equal(actual, expected)) { "Expected session [$key] = $expected but was $actual" }
    }

    /** Assert the session holds nothing under [key]. */
    fun assertSessionMissing(key: String): TestResponse = apply {
        require(requireSession().get(key) == null) { "Expected the session not to have [$key]" }
    }

    /** Assert validation errors were flashed (any field). */
    fun assertSessionHasErrors(): TestResponse = apply {
        require(errors().isNotEmpty()) { "Expected validation errors in the session but there were none" }
    }

    /** Assert validation errors were flashed for every field in [keys]. */
    fun assertSessionHasErrors(vararg keys: String): TestResponse = apply {
        val found = errors()
        val missing = keys.filter { it !in found }
        require(missing.isEmpty()) { "Expected session errors for $missing but had ${found.keys}" }
    }

    /** Assert a flashed error message for each field in [expected] (`field` to message). */
    fun assertSessionHasErrors(expected: Map<String, String>): TestResponse = apply {
        val found = errors()
        for ((key, message) in expected) {
            require(found[key].orEmpty().contains(message)) {
                "Expected session error [$key] = [$message] but had ${found[key]}"
            }
        }
    }

    /** Assert no validation errors were flashed. */
    fun assertSessionHasNoErrors(): TestResponse = apply {
        require(errors().isEmpty()) { "Expected no session errors but had ${errors()}" }
    }

    // ——— Validation (session errors, or the `errors` object of a JSON 422) ———

    /** Assert no validation errors at all. */
    fun assertValid(): TestResponse = apply {
        require(validationErrors().isEmpty()) { "Expected no validation errors but had ${validationErrors()}" }
    }

    /** Assert none of [keys] has a validation error. */
    fun assertValid(vararg keys: String): TestResponse = apply {
        val found = validationErrors()
        val failing = keys.filter { it in found }
        require(failing.isEmpty()) { "Expected $failing to be valid but had errors: $found" }
    }

    /** Assert at least one validation error. */
    fun assertInvalid(): TestResponse = apply {
        require(validationErrors().isNotEmpty()) { "Expected validation errors but there were none" }
    }

    /** Assert every field in [keys] has a validation error. */
    fun assertInvalid(vararg keys: String): TestResponse = apply {
        val found = validationErrors()
        val passing = keys.filter { it !in found }
        require(passing.isEmpty()) { "Expected validation errors for $passing but had ${found.keys}" }
    }

    /** Assert a validation message containing the given text for each field (`field` to message). */
    fun assertInvalid(expected: Map<String, String>): TestResponse = apply {
        val found = validationErrors()
        for ((key, message) in expected) {
            require(found[key].orEmpty().any { it.contains(message) }) {
                "Expected validation error [$key] containing [$message] but had ${found[key]}"
            }
        }
    }

    // ——— Inertia ———

    /** The Inertia page of this response (`X-Inertia` JSON, or the HTML `data-page` attribute). */
    fun inertia(): InertiaAssert = InertiaAssert(inertiaPage(), mapper)

    /** Run [block] against the Inertia page: `assertInertia { component("Pos/Register"); has("cart.lines", 2) }`. */
    fun assertInertia(block: InertiaAssert.() -> Unit): TestResponse = apply { inertia().block() }

    private fun inertiaPage(): JsonNode {
        val json = if (header("X-Inertia") != null) {
            body()
        } else {
            val match = DATA_PAGE.find(body())
            requireNotNull(match) { "The response is not an Inertia page: ${body()}" }
            unescapeAttribute(match.groupValues[2])
        }
        return mapper.readTree(json)
    }

    // ——— Internals ———

    private fun readPath(path: String): Any? {
        val root = mapper.readTree(body())
        val node = requireNotNull(JsonPaths.find(root, path)) { "Missing JSON path [$path] in ${body()}" }
        return JsonPaths.plain(node, mapper)
    }

    private fun requireSession(): RequestSession =
        checkNotNull(session) { "The request ran without a session; register StartSession to assert session state" }

    private fun cookie(name: String): Cookie? = response.cookies.lastOrNull { it.name == name }

    private fun isExpired(cookie: Cookie): Boolean {
        if (cookie.value.isEmpty()) return true
        val age = cookie.maxAge ?: return false
        return age.isZero
    }

    private fun errors(): Map<String, List<String>> {
        val raw = session?.get("errors") as? Map<*, *> ?: return emptyMap()
        return raw.entries.associate { (key, value) ->
            val messages = if (value is List<*>) value.map { it.toString() } else listOf(value.toString())
            key.toString() to messages
        }
    }

    private fun validationErrors(): Map<String, List<String>> {
        val flashed = errors()
        if (flashed.isNotEmpty()) return flashed
        val tree = try {
            mapper.readTree(body())
        } catch (_: com.fasterxml.jackson.core.JsonProcessingException) {
            return emptyMap()
        }
        val node = tree.get("errors")
        if (node == null || !node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (key, value) ->
            key to (if (value.isArray) value.map { it.asText() } else listOf(value.asText()))
        }
    }

    private fun withoutOrigin(location: String): String =
        location.replace(RESPONSE_ORIGIN, "")

    private fun unescapeAttribute(value: String): String =
        value.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
