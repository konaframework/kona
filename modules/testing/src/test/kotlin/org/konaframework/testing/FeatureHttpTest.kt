package org.konaframework.testing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.AuthManager
import org.konaframework.auth.AuthServiceProvider
import org.konaframework.auth.SessionGuard
import org.konaframework.http.Cookie
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.session.SessionManager
import org.konaframework.session.SessionServiceProvider
import org.konaframework.session.StartSession
import java.nio.file.Path
import java.time.Duration
import java.util.function.Predicate

class FeatureHttpTest {
    @TempDir
    lateinit var base: Path

    private fun appWithSession(configure: (Router) -> Unit): org.konaframework.core.Application {
        val app = TestApplication.create(base, HttpServiceProvider(), SessionServiceProvider())
        app.resolve(HttpKernel::class.java).pushMiddleware(StartSession(app.resolve(SessionManager::class.java)))
        configure(app.resolve(Router::class.java))
        return app
    }

    @Test
    fun formPostsSessionsRedirectsCookiesHeadersAndInertia() {
        val app = appWithSession { router ->
            router.get("/login") { Response.html("<h1>Sign in</h1><p>Welcome guest</p>") }
            router.get("/home") { r ->
                Response.html("<h1>Home</h1><p>Hello ${r.session()?.get("user") ?: "anon"}</p>")
            }
            router.post("/login") { r ->
                val email = r.input("email")
                if (email.isNullOrBlank()) {
                    return@post Response.redirect("/login").withErrors(mapOf("email" to listOf("Required")))
                }
                r.session()?.put("user", email)
                Response.redirect("/home").with("status", "signed-in")
            }
            router.post("/nested") { r -> Response.json(mapOf("body" to r.body, "token" to r.input("_token"))) }
            router.get("/go") { Response.redirect("/home") }
            router.get("/absolute") { Response.redirect("http://localhost/home") }
            router.get("/users/{id}") { r -> Response.text("user-" + r.route("id")) }.name("users.show")
            router.get("/to-user") { Response.redirect("/users/1") }
            router.post("/set-cookie") { Response.text("ok").cookie(Cookie("theme", "dark")) }
            router.post("/clear-cookie") {
                Response.text("ok").cookie(Cookie("theme", "", maxAge = Duration.ZERO))
            }
            router.get("/headers") { r ->
                Response.text("ok").header("X-Trace", r.header("X-Trace") ?: "")
            }
            router.put("/echo") { r -> Response.json(mapOf("body" to r.body)) }
            router.patch("/echo") { r -> Response.json(mapOf("body" to r.body)) }
            router.get("/inertia-html") {
                val page =
                    """{"component":"Pos/Register","props":{"cart":{"lines":[{"sku":"A1"},{"sku":"B2"}]},"title":"POS"},"url":"/pos","version":"v1"}"""
                Response.html("""<div data-page='$page'></div>""")
            }
            router.get("/inertia-json") {
                Response.json(
                    mapOf(
                        "component" to "Pos/Register",
                        "props" to mapOf("cart" to mapOf("lines" to listOf(mapOf("sku" to "A1"))), "ok" to true),
                        "url" to "/pos",
                        "version" to "v1",
                    ),
                ).header("X-Inertia", "true")
            }
            router.post("/json-errors") {
                Response.json(mapOf("errors" to mapOf("email" to listOf("Taken"), "name" to "Required")), 422)
            }
            router.get("/forbidden") { Response.text("no", 403) }
            router.get("/missing") { Response.text("no", 404) }
            router.get("/unauth") { Response.text("no", 401) }
            router.get("/created") { Response.json(mapOf("id" to 1), 201) }
            router.get("/empty") { Response.text("", 204) }
            router.delete("/item") { Response.json(mapOf("ok" to true)) }
            router.delete("/item-form") { r -> Response.json(mapOf("reason" to r.input("reason"))) }
        }

        val http = HttpTestClient(app)
            .withHeaders(mapOf("X-App" to "test"))
            .withHeader("X-Trace", "abc")
            .withCookies(mapOf("pref" to "1"))
            .withCookie("locale", "en")

        http.get("/login").assertOk().assertSee("Sign in").assertDontSee("cashier")
            .assertSeeInOrder("Sign in", "Welcome guest")
            .assertSuccessful()
            .assertHeader("Content-Type")
        assertThrows(IllegalArgumentException::class.java) { http.get("/login").assertSee("missing") }
        assertThrows(IllegalArgumentException::class.java) { http.get("/login").assertDontSee("Sign in") }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/login").assertSeeInOrder("Welcome", "Sign in")
        }

        http.post("/login", mapOf("email" to "")).assertRedirect("/login")
            .assertSessionHasErrors()
            .assertSessionHasErrors("email")
            .assertSessionHasErrors(mapOf("email" to "Required"))
            .assertInvalid()
            .assertInvalid("email")
            .assertInvalid(mapOf("email" to "Required"))
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "")).assertSessionHasNoErrors()
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "")).assertSessionHasErrors("password")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "")).assertSessionHasErrors(mapOf("email" to "Nope"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "")).assertValid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "")).assertValid("email")
        }

        http.withSession(mapOf("hint" to "welcome"))
            .post("/login", mapOf("email" to "ada@test"))
            .assertRedirect("/home")
            .assertSessionHas("status")
            .assertSessionHas("status", "signed-in")
            .assertSessionHas("user", "ada@test")
            .assertSessionHasNoErrors()
            .assertValid()
            .assertValid("email")
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "ada@test")).assertSessionMissing("user")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "ada@test")).assertSessionHas("missing")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "ada@test")).assertSessionHas("status", "wrong")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "ada@test")).assertInvalid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "ada@test")).assertInvalid("email")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/login", mapOf("email" to "ada@test")).assertInvalid(mapOf("email" to "x"))
        }
        http.post("/login", mapOf("email" to "ada@test")).assertSessionMissing("missing-key")

        http.get("/home").assertOk().assertSee("Hello ada@test")
        http.followingRedirects().get("/go").assertOk().assertSee("Home")
        http.withoutFollowingRedirects().get("/go").assertRedirect()
        http.get("/absolute").assertRedirect("/home")
        http.get("/to-user").assertRedirectToRoute("users.show", mapOf("id" to "1"))
        assertThrows(IllegalArgumentException::class.java) { http.get("/go").assertRedirect("/nope") }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/go").assertRedirectToRoute("users.show", mapOf("id" to "1"))
        }
        assertThrows(IllegalStateException::class.java) {
            TestResponse(Response.redirect("/x")).assertRedirectToRoute("x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response(302, "", linkedMapOf("Location" to ""))).assertRedirect()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.json(mapOf("a" to 1))).assertRedirect()
        }

        http.post("/nested", mapOf("a" to mapOf("b" to "1"), "list" to listOf("x", null), "z" to null))
            .assertOk()
            .assertJsonContains("a%5Bb%5D")
        assertNotNull(http.post("/nested", emptyMap()).assertOk().body())

        http.post("/set-cookie").assertOk().assertCookie("theme").assertCookie("theme", "dark")
        assertTrue(http.cookies().containsKey("theme"))
        http.post("/clear-cookie").assertCookieExpired("theme")
        http.flushCookies()
        assertTrue(http.cookies().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { http.post("/set-cookie").assertCookie("missing") }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/set-cookie").assertCookie("theme", "light")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/set-cookie").assertCookieMissing("theme")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.post("/set-cookie").assertCookieExpired("theme")
        }
        http.post("/set-cookie").assertCookieMissing("other")

        http.get("/headers").assertHeader("X-Trace", "abc").assertHeaderMissing("X-Missing")
        assertThrows(IllegalArgumentException::class.java) { http.get("/headers").assertHeader("X-Missing") }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/headers").assertHeader("X-Trace", "nope")
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/headers").assertHeaderMissing("X-Trace")
        }

        http.get("/forbidden").assertForbidden()
        http.get("/missing").assertNotFound()
        http.get("/unauth").assertUnauthorized()
        http.get("/created").assertCreated()
        http.get("/empty").assertNoContent()
        assertThrows(IllegalArgumentException::class.java) { http.get("/login").assertStatus(500) }
        assertThrows(IllegalArgumentException::class.java) { http.get("/forbidden").assertSuccessful() }

        http.postJson("/json-errors", mapOf("x" to 1)).assertUnprocessable()
            .assertInvalid("email", "name")
            .assertInvalid(mapOf("email" to "Taken"))
        assertThrows(IllegalArgumentException::class.java) {
            http.postJson("/json-errors", emptyMap<String, Any>()).assertValid("email")
        }

        http.get("/inertia-html").assertInertia {
            component("Pos/Register")
            url("/pos")
            version("v1")
            has("cart.lines")
            has("cart.lines", 2)
            hasAll("title", "cart")
            missing("absent")
            where("title", "POS")
            where("title", Predicate { value -> value == "POS" })
            assertEquals("POS", prop("title"))
            assertTrue(props().containsKey("cart"))
            assertEquals("Pos/Register", component())
        }
        http.get("/inertia-json").assertInertia {
            component("Pos/Register")
            has("cart.lines", 1)
            where("ok", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/login").assertInertia { component("X") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { component("Wrong") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { url("/nope") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { version("nope") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { has("missing") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { has("title", 2) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { has("title", 1) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { missing("title") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { where("title", "no") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").assertInertia { where("title", Predicate { false }) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            http.get("/inertia-html").inertia().prop("missing.path")
        }

        http.delete("/item").assertOk()
        http.delete("/item-form", mapOf("reason" to "gone"), emptyMap()).assertJsonPath("reason", "gone")
        http.put("/echo", "raw").assertOk().assertJsonPath("body", "raw")
        http.put("/echo", mapOf("k" to "v")).assertOk()
        http.patch("/echo", "raw").assertOk()
        http.patch("/echo", mapOf("k" to "v")).assertOk()
        http.withoutCsrfToken().post("/nested", mapOf("a" to "1")).assertOk()
        http.withCsrfToken().post("/nested", mapOf("a" to "1")).assertOk()

        assertThrows(IllegalStateException::class.java) {
            HttpTestClient(TestApplication.create(base.resolve("no-session"), HttpServiceProvider()))
                .withSession(mapOf("a" to 1))
                .get("/")
        }

        val bare = TestApplication.create(base.resolve("bare"), HttpServiceProvider())
        bare.resolve(Router::class.java).get("/ping") { Response.text("ok") }
        HttpTestClient(bare).get("/ping").assertOk()
        assertNull(HttpTestClient(bare).get("/ping").session())
        assertThrows(IllegalStateException::class.java) {
            HttpTestClient(bare).get("/ping").assertSessionHas("x")
        }
        assertEquals(listOf<Cookie>(), HttpTestClient(bare).get("/ping").cookies())
        assertNull(HttpTestClient(bare).get("/ping").location())
    }

    @Test
    fun actingAsSetsRequestUserAndOptionalGuard() {
        val app = TestApplication.create(
            base.resolve("auth"),
            HttpServiceProvider(),
            SessionServiceProvider(),
            AuthServiceProvider(),
        )
        app.config.set("hash.rounds", 4)
        app.resolve(HttpKernel::class.java).pushMiddleware(StartSession(app.resolve(SessionManager::class.java)))
        app.resolve(Router::class.java).get("/me") { r ->
            val user = r.getAttribute(SessionGuard.USER_ATTRIBUTE) as? Authenticatable
            Response.json(mapOf("id" to user?.getAuthIdentifier()))
        }
        val user = object : Authenticatable {
            override fun getAuthIdentifier(): Any = 7
            override fun getAuthPassword(): String = "x"
        }
        HttpTestClient(app).actingAs(user).get("/me").assertJsonPath("id", 7)
        HttpTestClient(app).actingAs(user, "web").get("/me").assertJsonPath("id", 7)
        assertEquals("web", app.resolve(AuthManager::class.java).getDefaultDriver())
        HttpTestClient(app).actingAs(user).actingAsGuest().get("/me").assertJsonPath("id", null)
    }

    @Test
    fun jsonPathCookieAndValidationEdges() {
        val mapper = jacksonObjectMapper()
        val response = TestResponse(Response.json(mapOf("n" to 1, "s" to "x")), mapper)
        response.assertJson("n", 1).assertJson("n", "1")
        assertThrows(IllegalArgumentException::class.java) { response.assertJsonPath("missing", 1) }
        TestResponse(Response.redirect("/a")).assertRedirect("/a")
        TestResponse(Response.text("ok").cookie(Cookie("a", "", maxAge = Duration.ZERO))).assertCookieExpired("a")
        TestResponse(Response.text("ok").cookie(Cookie("a", ""))).assertCookieExpired("a")
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.json(mapOf("errors" to "bad")), mapper).assertInvalid()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestResponse(Response.text("not-json"), mapper).assertInvalid()
        }
        TestResponse(Response.text("not-json"), mapper).assertValid()
        assertEquals(null, JsonPaths.find(mapper.readTree("""{"a":[1]}"""), "a.x"))
        assertEquals(null, JsonPaths.find(mapper.readTree("""{"a":1}"""), "a.b"))
        assertTrue(JsonPaths.equal(1.0, 1))
        assertTrue(JsonPaths.equal(1, "1"))
        assertTrue(!JsonPaths.equal(null, 1))
        assertTrue(!JsonPaths.equal(1, null))
        assertTrue(!JsonPaths.equal("a", "b"))
        assertEquals(null, JsonPaths.plain(mapper.nullNode(), mapper))
        assertEquals(true, JsonPaths.plain(mapper.nodeFactory.booleanNode(true), mapper))
        assertEquals(1.5, JsonPaths.plain(mapper.nodeFactory.numberNode(1.5), mapper))
    }
}
