package dev.kona.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpPathsAndRequestTest {
    @Test
    fun normalizeCollapsesSlashesAndTrailingSlash() {
        assertEquals("/", HttpPaths.normalize(""))
        assertEquals("/", HttpPaths.normalize("/"))
        assertEquals("/", HttpPaths.normalize("///"))
        assertEquals("/api/health", HttpPaths.normalize("/api//health"))
        assertEquals("/api/health", HttpPaths.normalize("/api/health/"))
        assertEquals("/foo/bar", HttpPaths.normalize("foo///bar/"))
        assertEquals("/", HttpPaths.fromUriParts(null, null))
        assertEquals("/", HttpPaths.fromUriParts("", ""))
        assertEquals("/x", HttpPaths.fromUriParts(null, "/x"))
        assertEquals("/y", HttpPaths.fromUriParts("/y", "/ignored"))
    }

    @Test
    fun displayDecodesPercentEncoding() {
        assertEquals("/✓", HttpPaths.display("/%E2%9C%93"))
        assertEquals("/", HttpPaths.display("/"))
        assertEquals("//", HttpPaths.display("/%2F"))
        assertEquals("/%zz", HttpPaths.display("/%zz"))
    }

    @Test
    fun formIgnoresNonFormContentTypes() {
        val plain = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "text/plain"),
            body = "title=x",
        )
        assertEquals(emptyMap<String, String>(), plain.form())
        assertNull(plain.input("title"))

        val form = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = "title=x",
        )
        assertEquals("x", form.form()["title"])

        val multipart = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "multipart/form-data; boundary=x"),
            body = "title=z",
        )
        assertEquals("z", multipart.form()["title"])

        val classic = Request("POST", "/", body = "title=y")
        assertEquals("y", classic.form()["title"])
    }

    @Test
    fun routeReadsBoundAttributes() {
        val request = Request.get("/posts/1").setAttribute("id", "1")
        assertEquals("1", request.route("id"))
        assertNull(request.route("missing"))
        assertEquals("d", request.route("missing", "d"))
        assertEquals("1", request.route("id", "unused"))
    }

    @Test
    fun statusPhrasesIncludeUnprocessable() {
        assertEquals("Unprocessable Entity", HttpStatus.reasonPhrase(422))
        assertEquals("OK", HttpStatus.reasonPhrase(200))
        assertNull(HttpStatus.reasonPhrase(599))
    }

    @Test
    fun notFoundMessageDecodesDisplayPath() {
        val router = Router()
        val ex = assertThrows(NotFoundException::class.java) {
            router.resolve("GET", "/%E2%9C%93")
        }
        assertTrue(ex.message!!.contains("✓"), ex.message)
        assertTrue(ex.message!!.contains("could not be found"))
    }

    @Test
    fun routerMatchesCollapsedSlashes() {
        val router = Router()
        router.get("/foo/bar") { Response.text("ok") }
        val match = router.resolve("GET", "/foo//bar")
        assertEquals("/foo/bar", match.route.uri)
        assertEquals("ok", match.route.action(Request.get("/foo/bar")).body)
    }
}
