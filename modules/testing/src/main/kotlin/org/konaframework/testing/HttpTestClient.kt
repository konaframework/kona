package org.konaframework.testing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.AuthManager
import org.konaframework.auth.SessionGuard
import org.konaframework.core.Application
import org.konaframework.http.Cookie
import org.konaframework.http.HttpKernel
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.session.Csrf
import org.konaframework.session.SessionManager
import java.net.URLDecoder
import java.net.URLEncoder

private const val MAX_REDIRECTS = 10
private val HTTP_ORIGIN = Regex("^https?://[^/]+")
private val READ_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

/**
 * In-process HTTP test client (Laravel HTTP tests shape): verbs with JSON or form bodies, a cookie
 * jar and session that carry across requests, `actingAs`, redirects, and response assertions.
 *
 * Builder methods (`withHeaders`, `withCookies`, `withSession`, `actingAs`, `followingRedirects`)
 * stay in effect for every later request of this client and return it, so they chain:
 *
 * ```kotlin
 * http.actingAs(cashier).post("/pos/sales", mapOf("sku" to "A1")).assertRedirect("/pos/receipt")
 * ```
 *
 * When the app registers `kona-session`, state-changing requests carry the session's CSRF token
 * automatically (`_token` form field and `X-CSRF-TOKEN` header); [withoutCsrfToken] turns that off.
 */
class HttpTestClient(private val app: Application) {
    private val mapper = jacksonObjectMapper()
    private val jar = LinkedHashMap<String, String>()
    private val defaultHeaders = LinkedHashMap<String, String>()
    private val pendingSession = LinkedHashMap<String, Any?>()
    private var user: Authenticatable? = null
    private var following = false
    private var csrf = true

    // ——— Builders ———

    /** Send [headers] with every later request. */
    fun withHeaders(headers: Map<String, String>): HttpTestClient = apply { defaultHeaders.putAll(headers) }

    fun withHeader(name: String, value: String): HttpTestClient = apply { defaultHeaders[name] = value }

    /** Add [cookies] (name to plain value) to the jar; later `Set-Cookie` responses update it. */
    fun withCookies(cookies: Map<String, String>): HttpTestClient = apply { jar.putAll(cookies) }

    fun withCookie(name: String, value: String): HttpTestClient = apply { jar[name] = value }

    /** The cookies the client will send (plain values). */
    fun cookies(): Map<String, String> = LinkedHashMap(jar)

    /** Empty the cookie jar (the session cookie too, so the next request starts a new session). */
    fun flushCookies(): HttpTestClient = apply { jar.clear() }

    /** Put [data] in the session before the next request (needs `kona-session` registered). */
    fun withSession(data: Map<String, Any?>): HttpTestClient = apply { pendingSession.putAll(data) }

    /** Authenticate later requests as [user] on the default guard. */
    fun actingAs(user: Authenticatable): HttpTestClient = apply { this.user = user }

    /** Authenticate later requests as [user] and make [guard] the default guard (needs `kona-auth`). */
    fun actingAs(user: Authenticatable, guard: String): HttpTestClient = apply {
        val auth = app.resolve(AuthManager::class.java)
        auth.guard(guard)
        auth.setDefaultDriver(guard)
        this.user = user
    }

    /** Stop authenticating requests. */
    fun actingAsGuest(): HttpTestClient = apply { user = null }

    /** Follow redirects (up to 10 hops, each as a `GET`) and return the final response. */
    fun followingRedirects(): HttpTestClient = apply { following = true }

    /** Return redirects as they are (the default). */
    fun withoutFollowingRedirects(): HttpTestClient = apply { following = false }

    /** Stop adding the CSRF token to state-changing requests. */
    fun withoutCsrfToken(): HttpTestClient = apply { csrf = false }

    /** Add the CSRF token to state-changing requests again (the default). */
    fun withCsrfToken(): HttpTestClient = apply { csrf = true }

    // ——— GET / DELETE ———

    fun get(uri: String): TestResponse = get(uri, emptyMap())

    fun get(uri: String, headers: Map<String, String>): TestResponse =
        send("GET", uri, headers) { Payload("") }

    fun delete(uri: String): TestResponse = delete(uri, emptyMap())

    fun delete(uri: String, headers: Map<String, String>): TestResponse =
        send("DELETE", uri, headers) { Payload("") }

    /** `DELETE` with a form-encoded body. */
    fun delete(uri: String, formData: Map<String, Any?>, headers: Map<String, String>): TestResponse =
        form("DELETE", uri, formData, headers)

    // ——— Form posts ———

    fun post(uri: String): TestResponse = post(uri, emptyMap())

    /** `POST` [formData] as `application/x-www-form-urlencoded`; nested maps and lists become `a[b]` / `a[0]`. */
    fun post(uri: String, formData: Map<String, Any?>): TestResponse = post(uri, formData, emptyMap())

    fun post(uri: String, formData: Map<String, Any?>, headers: Map<String, String>): TestResponse =
        form("POST", uri, formData, headers)

    fun put(uri: String, formData: Map<String, Any?>): TestResponse = put(uri, formData, emptyMap())

    fun put(uri: String, formData: Map<String, Any?>, headers: Map<String, String>): TestResponse =
        form("PUT", uri, formData, headers)

    fun patch(uri: String, formData: Map<String, Any?>): TestResponse = patch(uri, formData, emptyMap())

    fun patch(uri: String, formData: Map<String, Any?>, headers: Map<String, String>): TestResponse =
        form("PATCH", uri, formData, headers)

    // ——— JSON and raw bodies ———

    fun postJson(uri: String, body: Any): TestResponse = postJson(uri, body, emptyMap())

    fun postJson(uri: String, body: Any, headers: Map<String, String>): TestResponse =
        json("POST", uri, body, headers)

    fun put(uri: String): TestResponse = put(uri, "", emptyMap())

    fun put(uri: String, body: String): TestResponse = put(uri, body, emptyMap())

    fun put(uri: String, body: String, headers: Map<String, String>): TestResponse =
        send("PUT", uri, headers) { Payload(body) }

    fun putJson(uri: String, body: Any): TestResponse = putJson(uri, body, emptyMap())

    fun putJson(uri: String, body: Any, headers: Map<String, String>): TestResponse =
        json("PUT", uri, body, headers)

    fun patch(uri: String): TestResponse = patch(uri, "", emptyMap())

    fun patch(uri: String, body: String): TestResponse = patch(uri, body, emptyMap())

    fun patch(uri: String, body: String, headers: Map<String, String>): TestResponse =
        send("PATCH", uri, headers) { Payload(body) }

    fun patchJson(uri: String, body: Any): TestResponse = patchJson(uri, body, emptyMap())

    fun patchJson(uri: String, body: Any, headers: Map<String, String>): TestResponse =
        json("PATCH", uri, body, headers)

    // ——— Plumbing ———

    private class Payload(val body: String, val contentType: String? = null)

    private fun json(method: String, uri: String, body: Any, headers: Map<String, String>): TestResponse =
        send(method, uri, headers) { Payload(mapper.writeValueAsString(body), "application/json") }

    private fun form(method: String, uri: String, data: Map<String, Any?>, headers: Map<String, String>): TestResponse =
        send(method, uri, headers) { token ->
            val fields = if (token != null && !data.containsKey(Csrf.TOKEN_KEY)) data + (Csrf.TOKEN_KEY to token) else data
            Payload(encodeForm(fields), "application/x-www-form-urlencoded")
        }

    private fun send(method: String, uri: String, headers: Map<String, String>, payload: (String?) -> Payload): TestResponse {
        val token = prepareSession(method)
        val built = payload(token)
        val extra = LinkedHashMap(headers)
        if (built.contentType != null) extra["Content-Type"] = built.contentType
        if (token != null) extra.putIfAbsent("X-CSRF-TOKEN", token)
        var result = dispatch(method, uri, extra, built.body)
        var hops = 0
        while (following && hops < MAX_REDIRECTS && isRedirect(result)) {
            result = dispatch("GET", result.location()!!.replace(HTTP_ORIGIN, ""), emptyMap(), "")
            hops++
        }
        return result
    }

    private fun dispatch(method: String, uri: String, headers: Map<String, String>, body: String): TestResponse {
        val merged = LinkedHashMap(defaultHeaders)
        merged.putAll(headers)
        if (jar.isNotEmpty()) merged["Cookie"] = cookieHeader()
        val request = Request(method, uri.substringBefore('?'), merged, query(uri), body)
        user?.let { request.setAttribute(SessionGuard.USER_ATTRIBUTE, it) }
        val response = app.resolve(HttpKernel::class.java).handle(request)
        absorb(response.cookies)
        return TestResponse(response, mapper, request.session()) { name, parameters ->
            app.resolve(Router::class.java).route(name, parameters)
        }
    }

    /**
     * Applies [pendingSession] and, for a state-changing request, makes sure the session has a CSRF
     * token. Returns the token to send, or null when none is needed. The probe starts the session
     * the way the next request will, so it ages flash data once more; both steps only save when
     * something changed.
     */
    private fun prepareSession(method: String): String? {
        val wantsToken = csrf && method.uppercase() !in READ_METHODS
        if (pendingSession.isEmpty() && !wantsToken) return null
        if (!app.container.bound(SessionManager::class.java)) {
            check(pendingSession.isEmpty()) { "withSession needs kona-session: register SessionServiceProvider" }
            return null
        }
        val manager = app.resolve(SessionManager::class.java)
        val session = manager.start(Request("GET", "/", cookieHeaders()))
        var changed = pendingSession.isNotEmpty()
        pendingSession.forEach { (key, value) -> session.put(key, value) }
        pendingSession.clear()
        var token: String? = null
        if (wantsToken) {
            val before = session.get(Csrf.TOKEN_KEY)
            token = Csrf.token(session)
            changed = changed || before != token
        }
        if (changed) {
            val saved = Response()
            manager.save(session, saved)
            absorb(saved.cookies)
        }
        return token
    }

    private fun cookieHeaders(): Map<String, String> =
        if (jar.isEmpty()) emptyMap() else mapOf("Cookie" to cookieHeader())

    private fun cookieHeader(): String =
        jar.entries.joinToString("; ") { (name, value) -> "${encode(name)}=${encode(value)}" }

    private fun absorb(cookies: List<Cookie>) {
        for (cookie in cookies) {
            val age = cookie.maxAge
            val expired = cookie.value.isEmpty() || (age != null && age.isZero)
            if (expired) {
                jar.remove(cookie.name)
            } else {
                jar[cookie.name] = cookie.value
            }
        }
    }

    private fun isRedirect(result: TestResponse): Boolean =
        result.status() in REDIRECT_STATUSES && !result.location().isNullOrBlank()

    private fun query(uri: String): Map<String, String> {
        if ('?' !in uri) return emptyMap()
        return uri.substringAfter('?').split('&').filter { it.isNotEmpty() }.associate { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }

    private fun encodeForm(data: Map<String, Any?>): String {
        val pairs = ArrayList<Pair<String, String>>()
        data.forEach { (key, value) -> flatten(key, value, pairs) }
        return pairs.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
    }

    private fun flatten(key: String, value: Any?, out: MutableList<Pair<String, String>>) {
        when (value) {
            is Map<*, *> -> value.forEach { (k, v) -> flatten("$key[$k]", v, out) }
            is Iterable<*> -> value.forEachIndexed { index, v -> flatten("$key[$index]", v, out) }
            null -> out += key to ""
            else -> out += key to value.toString()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
