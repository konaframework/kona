package org.konaframework.http

import org.konaframework.core.Application
import org.konaframework.filesystem.FilesystemManager
import org.konaframework.filesystem.FilesystemServiceProvider
import org.konaframework.filesystem.LocalAdapter
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

class BinaryDownloadTest {
    @Test
    fun responseBinaryDownloadAndMime(@TempDir base: Path) {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val binary = Response.binary(png, "image/png")
        assertArrayEquals(png, binary.bodyBytes())
        assertEquals("image/png", binary.headers["Content-Type"])

        val download = Response.download(png, "photo.png")
        assertTrue(download.headers["Content-Disposition"]!!.contains("attachment"))
        assertTrue(download.headers["Content-Disposition"]!!.contains("photo.png"))
        assertEquals("image/png", download.headers["Content-Type"])

        val quoted = Response.download(byteArrayOf(1), "weird\"name.bin", "application/x-test")
        assertTrue(quoted.headers["Content-Disposition"]!!.contains("weirdname.bin"))
        assertFalse(quoted.headers["Content-Disposition"]!!.contains("weird\""))

        val inline = Response.file(png, filename = "a.png")
        assertTrue(inline.headers["Content-Disposition"]!!.startsWith("inline"))
        assertEquals("image/png", inline.headers["Content-Type"])

        val noName = Response.file(png)
        assertFalse(noName.headers.containsKey("Content-Disposition"))

        val onDisk = base.resolve("report.pdf")
        Files.write(onDisk, byteArrayOf(1, 2, 3))
        val fromPath = Response.file(onDisk)
        assertArrayEquals(byteArrayOf(1, 2, 3), fromPath.bodyBytes())
        assertTrue(fromPath.headers["Content-Disposition"]!!.contains("report.pdf"))
        assertEquals("application/pdf", fromPath.headers["Content-Type"])
        val namedPath = Response.file(onDisk, "application/pdf", "named.pdf", 200)
        assertTrue(namedPath.headers["Content-Disposition"]!!.contains("named.pdf"))

        val dlPath = Response.download(onDisk, "custom.pdf", "application/pdf")
        assertTrue(dlPath.headers["Content-Disposition"]!!.contains("attachment"))
        assertTrue(dlPath.headers["Content-Disposition"]!!.contains("custom.pdf"))
        val dlDefault = Response.download(onDisk)
        assertTrue(dlDefault.headers["Content-Disposition"]!!.contains("report.pdf"))
        val dlStatus = Response.download(onDisk, null, null, 200)
        assertEquals(200, dlStatus.status)

        assertEquals("application/octet-stream", Response.binary(byteArrayOf(7)).headers["Content-Type"])

        val text = Response.text("hi").withBodyBytes(byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(9, 8), text.bodyBytes())
        assertEquals("", text.body)
        assertArrayEquals("ok".toByteArray(), Response.text("x").withBody("ok").bodyBytes())

        assertEquals("image/jpeg", MimeTypes.guess("x.JPG"))
        assertEquals("application/octet-stream", MimeTypes.guess("noext"))
        assertEquals("application/octet-stream", MimeTypes.guess("file.unknownext"))
        assertEquals("text/plain; charset=utf-8", MimeTypes.guess("notes.txt"))
        assertEquals("image/png", MimeTypes.guess("dir\\photo.PNG"))
    }

    @Test
    fun validateSignatureAndPublicStorage(@TempDir base: Path) {
        val router = Router()
        val urls = UrlGenerator(router).setKey("sig-key")
        val ok = urls.signed("/secure", expirationSeconds = 120)
        val query = ok.substringAfter('?').split('&').associate {
            val eq = it.indexOf('=')
            it.substring(0, eq) to java.net.URLDecoder.decode(it.substring(eq + 1), Charsets.UTF_8)
        }
        val good = Request("GET", "/secure", query = query)
        assertEquals("ok", ValidateSignature(urls).handle(good) { Response.text("ok") }.body)
        assertThrows(HttpException::class.java) {
            ValidateSignature(urls).handle(Request.get("/secure")) { Response.text("x") }
        }

        val disk = LocalAdapter(base.resolve("public"), "/storage")
        disk.put("avatars/a.png", byteArrayOf(1, 2, 3, 4))
        val mw = ServePublicStorage(disk)
        // Explicit one-arg constructor (JvmOverloads) for coverage.
        val defaultPrefix = ServePublicStorage(disk)
        assertEquals(mw.javaClass, defaultPrefix.javaClass)
        val served = mw.handle(Request.get("/storage/avatars/a.png")) { Response.text("next") }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), served.bodyBytes())
        assertEquals("image/png", served.headers["Content-Type"])
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            mw.handle(Request("HEAD", "/storage/avatars/a.png")) { Response.text("next") }.bodyBytes(),
        )
        assertEquals("next", mw.handle(Request.get("/other")) { Response.text("next") }.body)
        assertEquals("next", mw.handle(Request.post("/storage/avatars/a.png")) { Response.text("next") }.body)
        assertThrows(NotFoundException::class.java) {
            mw.handle(Request.get("/storage/missing.png")) { Response.text("n") }
        }
        assertThrows(NotFoundException::class.java) {
            mw.handle(Request.get("/storage")) { Response.text("n") }
        }
        assertThrows(NotFoundException::class.java) {
            mw.handle(Request.get("/storage/../escape")) { Response.text("n") }
        }
        assertEquals(
            "next",
            ServePublicStorage(disk, "/files").handle(Request.get("/storage/x")) { Response.text("next") }.body,
        )
    }

    @Test
    fun serverWritesBinaryBody(@TempDir base: Path) {
        val app = Application(base)
        app.register(HttpServiceProvider()).boot()
        val router = app.resolve(Router::class.java)
        val payload = byteArrayOf(0, 1, 2, 255.toByte())
        router.get("/bin") { Response.binary(payload, "application/octet-stream") }
        val kernel = app.resolve(HttpKernel::class.java)
        val free = java.net.ServerSocket(0).use { it.localPort }
        val server = KonaHttpServer(kernel, "127.0.0.1", free)
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            val resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$free/bin")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            assertEquals(200, resp.statusCode())
            assertArrayEquals(payload, resp.body())
        } finally {
            server.stop()
        }
    }

    @Test
    fun temporaryUrlViaManager(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("filesystems.default", "public")
        app.register(FilesystemServiceProvider()).boot()
        val manager = app.resolve(FilesystemManager::class.java)
        manager.disk().put("x.txt", "hi")
        val urls = UrlGenerator(Router()).setKey("k")
        val link = manager.temporaryUrl("x.txt", 60, { url, exp -> urls.signed(url, exp) })
        assertTrue(link.contains("/storage/x.txt"))
        assertTrue(link.contains("signature="))
        assertTrue(link.contains("expires="))
    }
}
