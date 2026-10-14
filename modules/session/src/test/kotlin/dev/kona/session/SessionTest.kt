package dev.kona.session

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.http.DefaultExceptionHandler
import dev.kona.http.HttpKernel
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.http.Router
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionTest {
    @AfterEach
    fun cleanup() {
        Kona.reset()
    }

    @Test
    fun arrayDriverRoundTripAndFlash(@TempDir base: Path) {
        val manager = SessionManager.fromConfig(config(base, "array"), base)
        val start = manager.start(Request.get("/"))
        assertFalse(start.exists)
        start.put("user", "ada")
        start.flash("notice", "hi")
        assertTrue(start.has("user"))
        assertEquals("ada", start.get("user"))
        val response = Response.text("ok")
        manager.save(start, response)
        assertTrue(start.exists)
        assertEquals(1, response.cookies.size)
        assertTrue(response.cookies[0].toSetCookieHeader().contains(manager.cookieName()))

        val sid = start.id
        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "${manager.cookieName()}=$sid")),
        )
        assertEquals("ada", again.get("user"))
        assertEquals("hi", again.get("notice"))
        manager.save(again, Response.text("ok"))

        val third = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "${manager.cookieName()}=$sid")),
        )
        assertNull(third.get("notice"))
        assertEquals("ada", third.get("user"))

        val oldId = third.id
        third.regenerate()
        assertNotEquals(oldId, third.id)
        third.put("x", 1)
        third.invalidate()
        assertTrue(third.all().isEmpty())
        assertNotEquals(oldId, third.id)
        third.forget("missing")
        assertNull(third.get("missing"))
    }

    @Test
    fun memoryAliasAndUnsupportedDriver(@TempDir base: Path) {
        val config = config(base, "memory")
        val manager = SessionManager.fromConfig(config, base)
        assertTrue(manager.driver() is ArraySessionStore)
        assertTrue(manager.driver("array") is ArraySessionStore)
        (manager.driver("array") as ArraySessionStore).clear()
        assertEquals(0, (manager.driver("array") as ArraySessionStore).size())
        manager.forgetDriver("array")
        assertThrows(IllegalArgumentException::class.java) { manager.driver("bogus") }
        assertThrows(IllegalStateException::class.java) { manager.driver("redis") }
    }

    @Test
    fun fileDriverPersistsAndExpires(@TempDir base: Path) {
        val manager = SessionManager.fromConfig(config(base, "file"), base)
        val session = manager.start(Request.get("/"))
        session.put("k", "v")
        val response = Response.text("ok")
        manager.save(session, response)
        val sid = session.id
        val dir = base.resolve("storage/framework/sessions")
        assertTrue(Files.isRegularFile(dir.resolve(sid)))

        val loaded = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "${manager.cookieName()}=$sid")),
        )
        assertEquals("v", loaded.get("k"))

        val store = manager.driver("file") as FileSessionStore
        store.write(sid, mapOf("k" to "old"), lifetimeMinutes = 0)
        // lifetime 0 → expires_at ~= now; allow clock skew by rewriting past expiry
        val file = dir.resolve(sid)
        Files.writeString(
            file,
            """{"expires_at":1,"data":{"k":"gone"}}""",
        )
        assertTrue(store.read(sid).isEmpty())
        assertFalse(Files.exists(file))

        assertTrue(store.read("../etc/passwd").isEmpty())
        store.write("../bad", mapOf("a" to 1), 10)
        store.destroy("../bad")
        store.destroy(sid)

        Files.writeString(dir.resolve(SessionId.generate()), "not-json")
        val junkId = Files.list(dir).use { it.findFirst().get().fileName.toString() }
        assertTrue(FileSessionStore.isValidId(junkId))
        assertTrue(store.read(junkId).isEmpty())
    }

    @Test
    fun fileDriverCustomRelativeAndAbsolutePaths(@TempDir base: Path) {
        val relative = config(base, "file")
        relative.set("session.files", "custom-sessions")
        val mgr = SessionManager.fromConfig(relative, base)
        val s = mgr.start(Request.get("/"))
        s.put("a", 1)
        mgr.save(s, Response.text("ok"))
        assertTrue(Files.isDirectory(base.resolve("custom-sessions")))

        val absDir = base.resolve("abs-sessions")
        val absolute = config(base, "file")
        absolute.set("session.files", absDir.toAbsolutePath().toString())
        val mgr2 = SessionManager.fromConfig(absolute, base)
        val s2 = mgr2.start(Request.get("/"))
        s2.put("b", 2)
        mgr2.save(s2, Response.text("ok"))
        assertTrue(Files.isDirectory(absDir))
    }

    @Test
    fun cookieDriverUnsignedPayload(@TempDir base: Path) {
        val manager = SessionManager.fromConfig(config(base, "cookie"), base)
        val session = manager.start(Request.get("/"))
        session.put("cart", listOf(1, 2))
        val response = Response.text("ok")
        manager.save(session, response)
        val setCookie = response.cookies[0].toSetCookieHeader()
        val payload = setCookie.substringAfter("${manager.cookieName()}=").substringBefore(';')
        assertTrue(payload.isNotBlank())

        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "${manager.cookieName()}=$payload")),
        )
        assertEquals(listOf(1, 2), again.get("cart"))

        val bad = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "${manager.cookieName()}=!!!")),
        )
        assertTrue(bad.all().isEmpty())

        val store = manager.driver("cookie") as CookieSessionStore
        store.inboundPayload = ""
        assertTrue(store.read("x").isEmpty())
        store.destroy("x")
        assertEquals("", store.outboundPayload)
        assertNull(store.readId(null))
        assertNull(store.readId(""))
        assertNull(store.readId("%%%"))

        val doomed = manager.start(Request.get("/"))
        doomed.put("z", 1)
        doomed.invalidate()
        val expired = Response.text("ok")
        manager.save(doomed, expired)
        assertEquals(0L, expired.cookies[0].maxAge?.seconds)
        assertEquals("", expired.cookies[0].value)
    }

    @Test
    fun startSessionMiddlewareAndCsrf(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("session.driver", "array")
        app.register(SessionServiceProvider())
        app.boot()
        val manager = app.resolve(SessionManager::class.java)
        assertEquals(manager, app.container.make(SessionManager::class.java))

        val router = Router()
        router.get("/token") { req ->
            val session = req.getAttribute("session") as Session
            Response.text(Csrf.token(session))
        }
        router.post("/submit") { Response.text("saved") }
        router.post("/stripe/webhook") { Response.text("ok") }

        val kernel = HttpKernel(
            router,
            listOf(StartSession(manager), VerifyCsrfToken(except = listOf("/stripe/*"))),
            DefaultExceptionHandler(),
        )

        val get = kernel.handle(Request.get("/token"))
        assertEquals(200, get.status)
        val token = get.body
        assertTrue(token.length >= 32)
        val sid = get.cookies[0].value

        val denied = kernel.handle(
            Request(
                "POST",
                "/submit",
                headers = mapOf("Cookie" to "${manager.cookieName()}=$sid"),
                body = "",
            ),
        )
        assertEquals(419, denied.status)

        val okBody = kernel.handle(
            Request(
                "POST",
                "/submit",
                headers = mapOf(
                    "Cookie" to "${manager.cookieName()}=$sid",
                    "Content-Type" to "application/x-www-form-urlencoded",
                ),
                body = "_token=$token",
            ),
        )
        assertEquals("saved", okBody.body)

        val okHeader = kernel.handle(
            Request(
                "POST",
                "/submit",
                headers = mapOf(
                    "Cookie" to "${manager.cookieName()}=$sid",
                    "X-CSRF-TOKEN" to token,
                ),
            ),
        )
        assertEquals("saved", okHeader.body)

        val okXsrf = kernel.handle(
            Request(
                "POST",
                "/submit",
                headers = mapOf(
                    "Cookie" to "${manager.cookieName()}=$sid",
                    "X-XSRF-TOKEN" to token,
                ),
            ),
        )
        assertEquals("saved", okXsrf.body)

        assertEquals(
            "ok",
            kernel.handle(
                Request(
                    "POST",
                    "/stripe/webhook",
                    headers = mapOf("Cookie" to "${manager.cookieName()}=$sid"),
                ),
            ).body,
        )

        assertNotEquals(419, kernel.handle(Request("OPTIONS", "/submit")).status)
        assertNotEquals(419, kernel.handle(Request("HEAD", "/token")).status)
        assertNotEquals(419, kernel.handle(Request("TRACE", "/submit")).status)

        val bareKernel = HttpKernel(
            Router().also { r -> r.post("/x") { Response.text("n") } },
            listOf(VerifyCsrfToken()),
            DefaultExceptionHandler(),
        )
        assertEquals(419, bareKernel.handle(Request.post("/x")).status)
        assertThrows(TokenMismatchException::class.java) {
            VerifyCsrfToken().handle(Request.post("/x")) { Response.text("n") }
        }

        assertFalse(Csrf.tokensMatch(null, "a"))
        assertFalse(Csrf.tokensMatch("a", null))
        assertFalse(Csrf.tokensMatch("ab", "a"))
        assertTrue(Csrf.tokensMatch("ab", "ab"))
        assertNotNull(Csrf.generate())

        val exceptExact = VerifyCsrfToken(except = listOf("/exact"))
        assertEquals(
            200,
            HttpKernel(
                Router().also { it.post("/exact") { Response.text("e") } },
                listOf(exceptExact),
                DefaultExceptionHandler(),
            ).handle(Request.post("/exact")).status,
        )
    }

    @Test
    fun cookieFlagsFromConfig(@TempDir base: Path) {
        val config = config(base, "array")
        config.set("session.secure", true)
        config.set("session.http_only", false)
        config.set("session.same_site", "Strict")
        config.set("session.path", "/app")
        config.set("session.cookie", "my_sess")
        config.set("session.lifetime", 30)
        val manager = SessionManager.fromConfig(config, base)
        assertEquals("my_sess", manager.cookieName())
        assertEquals("/app", manager.cookiePath())
        assertEquals(30, manager.lifetime())
        val session = manager.start(Request.get("/"))
        val response = Response.text("ok")
        manager.save(session, response)
        val header = response.cookies[0].toSetCookieHeader()
        assertTrue(header.contains("Secure"))
        assertFalse(header.contains("HttpOnly"))
        assertTrue(header.contains("SameSite=Strict"))
        assertTrue(header.contains("Path=/app"))
        assertTrue(header.contains("Max-Age=1800"))

        config.set("session.same_site", "None")
        manager.forgetDriver("array")
        val m2 = SessionManager.fromConfig(config, base)
        val r2 = Response.text("ok")
        m2.save(m2.start(Request.get("/")), r2)
        assertTrue(r2.cookies[0].toSetCookieHeader().contains("SameSite=None"))

        config.set("session.same_site", "bogus")
        val m3 = SessionManager.fromConfig(config, base)
        val r3 = Response.text("ok")
        m3.save(m3.start(Request.get("/")), r3)
        assertTrue(r3.cookies[0].toSetCookieHeader().contains("SameSite=Lax"))

        config.set("session.same_site", "")
        val m4 = SessionManager.fromConfig(config, base)
        val r4 = Response.text("ok")
        m4.save(m4.start(Request.get("/")), r4)
        assertTrue(r4.cookies[0].toSetCookieHeader().contains("SameSite=Lax"))
    }

    @Test
    fun tokenMismatchExceptionStatus() {
        assertEquals(419, TokenMismatchException().status)
        assertEquals("CSRF token mismatch.", TokenMismatchException().message)
        assertEquals("x", TokenMismatchException("x").message)
    }

    @Test
    fun sessionIdHelpers() {
        val id = SessionId.generate()
        assertTrue(SessionId.isValid(id))
        assertFalse(SessionId.isValid("short"))
        assertFalse(FileSessionStore.isValidId(""))
    }

    @Test
    fun flashListCoercionBranches(@TempDir base: Path) {
        val store = ArraySessionStore()
        val session = StoreSession(SessionId.generate(), store)
        session.put(StoreSession.FLASH_NEW, listOf("a"))
        session.put(StoreSession.FLASH_OLD, listOf("b"))
        session.put("b", "old")
        session.put("a", "keep-next")
        session.start(exists = true)
        assertNull(session.get("b"))
        assertEquals("keep-next", session.get("a"))
        session.flash("a", "again")
        session.flash("a", "again")
        assertEquals(listOf("a"), session.get(StoreSession.FLASH_NEW))
    }

    private fun config(base: Path, driver: String) =
        Application(base).config.also {
            it.set("session.driver", driver)
        }
}
