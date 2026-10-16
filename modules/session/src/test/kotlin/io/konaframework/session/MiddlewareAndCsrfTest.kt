package io.konaframework.session

import io.konaframework.core.Application
import io.konaframework.core.Config
import io.konaframework.http.Request
import io.konaframework.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MiddlewareAndCsrfTest {
    @TempDir
    lateinit var base: Path

    @Test
    fun startSessionAttachesAndSavesCookie() {
        val manager = SessionManager(Config(), base)
        val mw = StartSession(manager)
        val response = mw.handle(Request.get("/")) { req ->
            val session = req.getAttribute(StartSession.ATTRIBUTE) as Session
            session.put("x", 1)
            Csrf.token(session)
            Response.text("ok")
        }
        assertEquals(1, response.cookies.size)
        assertEquals("kona_session", response.cookies.first().name)
        assertTrue(SessionId.isValid(response.cookies.first().value))
    }

    @Test
    fun startSessionSkipsRememberOnErrorOrRedirectAndKeepsQuery() {
        val manager = SessionManager(Config(), base)
        val mw = StartSession(manager)
        val notFound = mw.handle(Request.get("/gone")) { Response.text("no", 404) }
        assertEquals(404, notFound.status)
        val early = mw.handle(Request.get("/early")) { Response.text("early", 199) }
        assertEquals(199, early.status)

        val redirectGet = mw.handle(Request.get("/bounce")) {
            Response.redirect("/other")
        }
        assertEquals(302, redirectGet.status)

        val withLocationHeader = mw.handle(Request.get("/weird")) {
            Response.text("ok").header("Location", "/nope")
        }
        assertEquals(200, withLocationHeader.status)

        val cookie = mw.handle(Request("GET", "/search", query = mapOf("q" to "kona"))) {
            Response.html("results")
        }.cookies.first().value
        mw.handle(
            Request("GET", "/next", headers = mapOf("Cookie" to "kona_session=$cookie")),
        ) { req ->
            assertEquals("/search?q=kona", req.session()?.previousUrl())
            Response.text("ok")
        }
    }

    @Test
    fun startSessionAppliesRedirectFlashAndRemembersUrl() {
        val manager = SessionManager(Config(), base)
        val mw = StartSession(manager)
        val cookie = mw.handle(Request.get("/contact")) { Response.html("<form>") }.cookies.first().value

        val redirected = mw.handle(
            Request(
                "POST",
                "/contact",
                headers = mapOf("Cookie" to "kona_session=$cookie", "Accept" to "text/html"),
                body = "email=bad",
            ),
        ) { req ->
            Response.redirect("/elsewhere")
                .with("status", "saved")
                .withInput(req.all())
                .withErrors(mapOf("email" to listOf("Invalid")))
        }
        assertEquals("/elsewhere", redirected.headers["Location"])

        val next = mw.handle(
            Request("GET", "/elsewhere", headers = mapOf("Cookie" to "kona_session=$cookie")),
        ) { req ->
            val session = req.session()!!
            assertEquals("saved", session.get("status"))
            assertEquals("bad", session.old("email"))
            assertEquals("bad", req.old("email"))
            @Suppress("UNCHECKED_CAST")
            val errors = session.get(StoreSession.ERRORS) as Map<String, List<String>>
            assertEquals(listOf("Invalid"), errors["email"])
            assertEquals("/contact", session.previousUrl())
            Response.text("ok")
        }
        assertEquals("ok", next.body)
    }

    @Test
    fun validationRedirectBackUsesPreviousUrl() {
        val manager = SessionManager(Config(), base)
        val mw = StartSession(manager)
        val cookie = mw.handle(Request.get("/form")) { Response.html("form") }.cookies.first().value

        val kernel = io.konaframework.http.HttpKernel(
            io.konaframework.http.Router().also { router ->
                router.post("/form") { req ->
                    req.validate(mapOf("email" to listOf("required", "email")))
                    Response.redirect("/done")
                }
            },
        )
        kernel.pushMiddleware(mw)
        val response = kernel.handle(
            Request(
                "POST",
                "/form",
                headers = mapOf(
                    "Cookie" to "kona_session=$cookie",
                    "Accept" to "text/html",
                    "Content-Type" to "application/x-www-form-urlencoded",
                ),
                body = "email=not-an-email",
            ),
        )
        assertEquals(302, response.status)
        assertEquals("/form", response.headers["Location"])

        mw.handle(
            Request("GET", "/form", headers = mapOf("Cookie" to "kona_session=$cookie")),
        ) { req ->
            assertEquals("not-an-email", req.old("email"))
            assertNotNull(req.session()?.get(StoreSession.ERRORS))
            Response.html("form")
        }
    }

    @Test
    fun verifyCsrfSkipsReadingAndExceptPaths() {
        val ok = Response.text("ok")
        val mw = VerifyCsrfToken(listOf("/webhooks", "/hooks/*"))
        assertEquals("ok", mw.handle(Request.get("/post"), { ok }).body)
        assertEquals("ok", mw.handle(Request("HEAD", "/post"), { ok }).body)
        assertEquals("ok", mw.handle(Request("OPTIONS", "/post"), { ok }).body)
        assertEquals("ok", mw.handle(Request("TRACE", "/post"), { ok }).body)
        assertEquals("ok", mw.handle(Request.post("/webhooks"), { ok }).body)
        assertEquals("ok", mw.handle(Request.post("/hooks/stripe"), { ok }).body)
    }

    @Test
    fun verifyCsrfAcceptsInputAndHeaders() {
        val store = ArraySessionStore()
        val session = StoreSession(SessionId.generate(), store).start(false)
        val token = Csrf.token(session)
        assertEquals(token, Csrf.token(session))

        val mw = VerifyCsrfToken()
        val withSession: (Request) -> Request = { req ->
            req.setAttribute(StartSession.ATTRIBUTE, session)
            req
        }

        assertEquals(
            "ok",
            mw.handle(
                withSession(Request("POST", "/", query = mapOf("_token" to token))),
            ) { Response.text("ok") }.body,
        )
        assertEquals(
            "ok",
            mw.handle(
                withSession(Request("POST", "/", headers = mapOf("X-CSRF-TOKEN" to token))),
            ) { Response.text("ok") }.body,
        )
        assertEquals(
            "ok",
            mw.handle(
                withSession(Request("POST", "/", headers = mapOf("X-XSRF-TOKEN" to token))),
            ) { Response.text("ok") }.body,
        )
    }

    @Test
    fun verifyCsrfFailsWithoutSessionOrToken() {
        val mw = VerifyCsrfToken()
        val noSession = assertThrows(TokenMismatchException::class.java) {
            mw.handle(Request.post("/")) { Response.text("x") }
        }
        assertEquals(419, noSession.status)

        val session = StoreSession(SessionId.generate(), ArraySessionStore()).start(false)
        Csrf.token(session)
        val mismatch = assertThrows(TokenMismatchException::class.java) {
            mw.handle(Request.post("/").setAttribute(StartSession.ATTRIBUTE, session)) {
                Response.text("x")
            }
        }
        assertEquals(419, mismatch.status)

        val custom = TokenMismatchException("nope")
        assertEquals("nope", custom.message)
        assertEquals(419, custom.status)
    }

    @Test
    fun csrfTokensMatchHelpers() {
        assertTrue(!Csrf.tokensMatch(null, "a"))
        assertTrue(!Csrf.tokensMatch("a", null))
        assertTrue(!Csrf.tokensMatch("", "a"))
        assertTrue(!Csrf.tokensMatch("a", ""))
        assertTrue(!Csrf.tokensMatch("abc", "ab"))
        assertTrue(Csrf.tokensMatch("same", "same"))
        assertTrue(Csrf.generate().length == 64)
    }

    @Test
    fun sessionServiceProviderRegistersManager() {
        val app = Application.builder(base)
            .withProvider(SessionServiceProvider())
            .configure { it.set("session.driver", "array") }
            .boot()
        val manager = app.make(SessionManager::class.java)
        assertNotNull(manager)
        assertSame(manager, app.container.make("session.manager"))
        assertTrue(manager.driver() is ArraySessionStore)
    }
}
