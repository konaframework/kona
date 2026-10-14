package dev.kona.session

import dev.kona.core.Application
import dev.kona.core.Config
import dev.kona.http.Request
import dev.kona.http.Response
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
