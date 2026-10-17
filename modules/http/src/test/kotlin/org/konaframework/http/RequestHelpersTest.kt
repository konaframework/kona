package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RequestHelpersTest {
    @Test
    fun onlyExceptHasFilledMissing() {
        val request = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"name":"Ada","age":36,"note":null}""",
            query = mapOf("page" to "1"),
        )
        assertEquals(mapOf("name" to "Ada", "page" to "1"), request.only("name", "page"))
        assertFalse(request.except("age", "note").containsKey("age"))
        assertTrue(request.has("note"))
        assertFalse(request.filled("note"))
        assertTrue(request.filled("name"))
        assertTrue(request.missing("missing"))
        assertFalse(request.missing("name"))
        assertEquals(mapOf("name" to "Ada"), request.only(listOf("name")))
        assertEquals(setOf("name", "age", "note", "page"), request.except(listOf("x")).keys)
    }

    @Test
    fun mergeReplaceAndSafe() {
        val request = Request.post("/", "a=1&b=2")
        request.merge(mapOf("b" to "9", "c" to "3"))
        assertEquals("1", request.input("a"))
        assertEquals("9", request.input("b"))
        assertEquals("3", request.input("c"))
        request.replace(mapOf("z" to "1"))
        assertEquals(mapOf("z" to "1"), request.all())
        assertNull(request.input("a"))

        val validated = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"email":"a@b.co"}""",
        )
        assertThrows(IllegalStateException::class.java) { validated.safe() }
        assertEquals(mapOf("email" to "a@b.co"), validated.validate(mapOf("email" to listOf("required", "email"))))
        assertEquals(mapOf("email" to "a@b.co"), validated.safe())
    }

    @Test
    fun booleanDateBearerIpUserAgent() {
        val request = Request(
            "GET",
            "/",
            headers = mapOf(
                "Authorization" to "Bearer secret-token",
                "User-Agent" to "KonaTest/1.0",
            ),
            query = mapOf(
                "on" to "yes",
                "off" to "0",
                "flag" to "true",
                "when" to "2024-05-01",
                "blank" to "",
            ),
        )
        assertTrue(request.boolean("on"))
        assertFalse(request.boolean("off"))
        assertTrue(request.boolean("flag"))
        assertFalse(request.boolean("missing"))
        assertTrue(request.boolean("missing", true))
        assertFalse(request.boolean("blank"))
        assertEquals(LocalDate.of(2024, 5, 1), request.date("when"))
        assertNull(request.date("missing"))
        assertNull(request.date("blank"))
        assertEquals("secret-token", request.bearerToken())
        assertEquals("KonaTest/1.0", request.userAgent())

        assertNull(request.ip())
        request.setAttribute(TrustProxies.REMOTE_ADDR, "10.0.0.1")
        assertEquals("10.0.0.1", request.ip())
        request.setAttribute(TrustProxies.CLIENT_IP, "203.0.113.9")
        assertEquals("203.0.113.9", request.ip())

        assertNull(Request.get("/").bearerToken())
        assertNull(Request("GET", "/", headers = mapOf("Authorization" to "Basic x")).bearerToken())
        assertNull(Request("GET", "/", headers = mapOf("Authorization" to "Bearer   ")).bearerToken())
    }

    @Test
    fun whenHasFilledMissingCallbacks() {
        val request = Request.post("/", "name=Ada&empty=")
        val has = AtomicReference<Any?>(null)
        val filled = AtomicReference<Any?>(null)
        val missing = AtomicBoolean(false)
        request.whenHas("name") { has.set(it) }
        request.whenFilled("name") { filled.set(it) }
        request.whenMissing("nope") { missing.set(true) }
        request.whenHas("nope") { error("should not run") }
        request.whenFilled("empty") { error("should not run") }
        assertEquals("Ada", has.get())
        assertEquals("Ada", filled.get())
        assertTrue(missing.get())
    }

    @Test
    fun dateParsesDateTimeAndLocalDateInstance() {
        val json = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"a":"2024-05-01T12:00:00","b":"2024-05-01T12:00:00+02:00"}""",
        )
        assertEquals(LocalDate.of(2024, 5, 1), json.date("a"))
        assertEquals(LocalDate.of(2024, 5, 1), json.date("b"))
        json.merge(mapOf("c" to LocalDate.of(2020, 1, 2), "bad" to "not-a-date"))
        assertEquals(LocalDate.of(2020, 1, 2), json.date("c"))
        assertNull(json.date("bad"))
    }

    @Test
    fun fileReadsMergedUpload() {
        val file = UploadedFile("a.png", "image/png", byteArrayOf(1, 2, 3))
        val request = Request.post("/", "x=1").merge(mapOf("photo" to file))
        assertEquals(file, request.file("photo"))
        assertTrue(request.hasFile("photo"))
    }

    @Test
    fun withHeadersOnResponse() {
        val response = Response.text("ok").withHeaders(mapOf("X-A" to "1", "X-B" to "2"))
        assertEquals("1", response.headers["X-A"])
        assertEquals("2", response.headers["X-B"])
    }
}
