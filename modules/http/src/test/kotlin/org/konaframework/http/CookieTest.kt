package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class CookieTest {
    @Test
    fun `parses cookie header and reads from request`() {
        val request = Request(
            "GET",
            "/",
            headers = mapOf("Cookie" to "a=1; b=hello%20world"),
        )
        assertEquals("1", request.cookie("a"))
        assertEquals("hello world", request.cookies()["b"])
        assertNull(request.cookie("missing"))
        assertEquals("x", request.cookie("missing", "x"))
        assertTrue(Request.get("/").cookies().isEmpty())
    }

    @Test
    fun `builds set-cookie and attaches to response`() {
        val cookie = Cookie(
            name = "sid",
            value = "abc def",
            maxAge = Duration.ofSeconds(60),
            path = "/app",
            domain = "example.test",
            secure = true,
            httpOnly = true,
            sameSite = Cookie.SameSite.Strict,
        )
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("sid=abc%20def"))
        assertTrue(header.contains("Max-Age=60"))
        assertTrue(header.contains("Path=/app"))
        assertTrue(header.contains("Domain=example.test"))
        assertTrue(header.contains("Secure"))
        assertTrue(header.contains("HttpOnly"))
        assertTrue(header.contains("SameSite=Strict"))

        val minimal = Cookie(
            name = "x",
            value = "y",
            maxAge = null,
            path = "",
            domain = "",
            secure = false,
            httpOnly = false,
            sameSite = null,
        ).toSetCookieHeader()
        assertEquals("x=y", minimal)
        assertTrue(Cookie(name = "n", value = "v", maxAge = Duration.ofSeconds(-5)).toSetCookieHeader().contains("Max-Age=0"))
        assertTrue(Cookie.SameSite.None.headerValue == "None")
        assertTrue(Cookie.SameSite.Lax.headerValue == "Lax")

        val response = Response.text("ok")
            .cookie("flash", "1", maxAgeSeconds = 10)
            .cookie("plain", "2")
            .cookie(Cookie("obj", "3"))
        assertEquals(3, response.cookies.size)
        assertEquals("flash", response.cookies[0].name)
        assertNull(response.cookies[1].maxAge)
    }

    @Test
    fun `parseHeader ignores empty and malformed parts`() {
        assertTrue(Cookie.parseHeader(null).isEmpty())
        assertTrue(Cookie.parseHeader("").isEmpty())
        assertEquals(mapOf("a" to "1"), Cookie.parseHeader("=x; a=1; bare"))
        assertEquals(mapOf("a" to "1", "b" to "2"), Cookie.parseHeader("a=1;; b=2; "))
    }
}
