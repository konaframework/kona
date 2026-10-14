package dev.kona.session

import dev.kona.core.Config
import dev.kona.http.Cookie
import dev.kona.http.Request
import dev.kona.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SessionManagerTest {
    @TempDir
    lateinit var base: Path

    @Test
    fun arrayDriverStartSaveAndReload() {
        val config = Config().apply {
            set("session.driver", "array")
            set("session.lifetime", 90)
            set("session.cookie", "sid")
            set("session.path", "/app")
            set("session.same_site", "Strict")
            set("session.secure", true)
            set("session.http_only", false)
        }
        val manager = SessionManager.fromConfig(config, base)
        assertEquals("array", manager.driverName())
        assertEquals(90, manager.lifetime())
        assertTrue(manager.driver() is ArraySessionStore)
        assertTrue(manager.driver("memory") is ArraySessionStore)

        val first = manager.start(Request.get("/"))
        assertFalse(first.exists)
        first.put("user", "ada")
        val response = Response.text("ok")
        manager.save(first, response)
        assertEquals(1, response.cookies.size)
        val cookie = response.cookies.first()
        assertEquals("sid", cookie.name)
        assertEquals(first.id, cookie.value)
        assertEquals("/app", cookie.path)
        assertEquals(Cookie.SameSite.Strict, cookie.sameSite)
        assertTrue(cookie.secure)
        assertFalse(cookie.httpOnly)

        val second = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "sid=${first.id}")),
        )
        assertTrue(second.exists)
        assertEquals("ada", second.get("user"))
    }

    @Test
    fun cookieDriverRoundTripAndSameSiteVariants(@TempDir tmp: Path) {
        val config = Config().apply {
            set("session.driver", "cookie")
            set("session.same_site", "None")
        }
        val manager = SessionManager(config, tmp)
        val session = manager.start(Request.get("/"))
        session.put("n", 1)
        val response = Response.text("ok")
        manager.save(session, response)
        val payload = response.cookies.first().value
        assertTrue(payload.isNotBlank())

        val again = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=$payload")),
        )
        assertEquals(1, again.get("n"))
        assertTrue(again.exists)

        config.set("session.same_site", "lax")
        manager.save(again, Response.text("x"))
        config.set("session.same_site", "weird")
        manager.save(again, Response.text("y"))
        config.set("session.same_site", null)
        val cleared = Response.text("z")
        manager.save(again, cleared)
        assertEquals(Cookie.SameSite.Lax, cleared.cookies.last().sameSite)

        val blankCookie = manager.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=")),
        )
        assertFalse(blankCookie.exists)
    }

    @Test
    fun fileDriverPathsAndInvalidCookieId(@TempDir tmp: Path) {
        val relative = Config().apply {
            set("session.driver", "file")
            set("session.files", "sess")
        }
        val relativeManager = SessionManager(relative, tmp)
        assertTrue(relativeManager.driver() is FileSessionStore)
        val session = relativeManager.start(Request.get("/"))
        session.put("f", true)
        relativeManager.save(session, Response.text("ok"))

        val absolute = Config().apply {
            set("session.driver", "file")
            set("session.files", tmp.resolve("abs-sessions").toAbsolutePath().toString())
        }
        val absManager = SessionManager(absolute, tmp)
        absManager.forgetDriver("file")
        assertTrue(absManager.driver("file") is FileSessionStore)

        val blankFiles = Config().apply {
            set("session.driver", "file")
            set("session.files", "   ")
        }
        val blankManager = SessionManager(blankFiles, tmp)
        blankManager.forgetDriver("file")
        blankManager.driver()

        val defaultFiles = Config().apply { set("session.driver", "file") }
        val defaultManager = SessionManager(defaultFiles, tmp)
        defaultManager.driver()

        val array = SessionManager(Config().apply { set("session.driver", "array") }, tmp)
        val bad = array.start(Request("GET", "/", headers = mapOf("Cookie" to "kona_session=not-a-valid-id")))
        assertFalse(bad.exists)
        assertTrue(SessionId.isValid(bad.id))

        val knownEmpty = array.start(
            Request("GET", "/", headers = mapOf("Cookie" to "kona_session=${SessionId.generate()}")),
        )
        assertTrue(knownEmpty.exists)
    }

    @Test
    fun unsupportedDriverThrows() {
        val manager = SessionManager(Config().apply { set("session.driver", "redis") }, base)
        assertThrows(IllegalArgumentException::class.java) { manager.driver() }
    }
}
