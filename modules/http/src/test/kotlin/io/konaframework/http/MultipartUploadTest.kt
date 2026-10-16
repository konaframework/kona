package io.konaframework.http

import io.konaframework.filesystem.LocalAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MultipartUploadTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun parsesFieldsAndFiles() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val body = multipart(
            "bound",
            textPart("title", "Hello"),
            filePart("avatar", "pic.png", "image/png", png),
            filePart("empty", "", "application/octet-stream", ByteArray(0)),
        )
        val request = Request.post(
            "/upload",
            body,
            mapOf("Content-Type" to "multipart/form-data; boundary=\"bound\""),
        )
        assertEquals("Hello", request.form()["title"])
        assertEquals("Hello", request.input("title"))
        assertTrue(request.hasFile("avatar"))
        assertFalse(request.hasFile("missing"))
        assertFalse(request.hasFile("empty"))
        assertNull(Request.get("/").file("x"))
        assertTrue(Request.get("/").allFiles().isEmpty())
        val file = request.file("avatar")!!
        assertEquals("pic.png", file.clientOriginalName)
        assertEquals("image/png", file.clientMimeType)
        assertEquals(png.toList(), file.contents.toList())
        assertEquals("png", file.extension())
        assertTrue(request.data()["avatar"] is UploadedFile)
        assertEquals("Hello", request.data()["title"])
    }

    @Test
    fun storeWritesViaFilesystemAdapter() {
        val disk = LocalAdapter(temp)
        val file = UploadedFile("notes.txt", "text/plain", "hi".toByteArray())
        val path = file.store("uploads", disk)
        assertTrue(path.startsWith("uploads/"))
        assertTrue(path.endsWith(".txt"))
        assertEquals("hi", disk.getString(path))
        val root = UploadedFile("x", null, "z".toByteArray()).store("", disk)
        assertFalse(root.contains("/"))
        val named = file.storeAs("uploads", "fixed.txt", disk)
        assertEquals("uploads/fixed.txt", named)
        assertEquals("hi", disk.getString(named))
        assertTrue(file.hashName("bin").endsWith(".bin"))
        assertFalse(file.hashName(null).contains(".."))
        assertEquals("", UploadedFile("noext", null, "a".toByteArray()).extension())
        assertEquals("", UploadedFile("ends.", null, "a".toByteArray()).extension())
        assertTrue(UploadedFile("noext", null, "a".toByteArray()).hashName().isNotBlank())
        assertFalse(UploadedFile("x", null, ByteArray(0)).isValid())
        assertTrue(UploadedFile("x", null, ByteArray(0)).isEmpty())
    }

    @Test
    fun fileValidationRules() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val body = multipart(
            "b",
            filePart("photo", "a.png", "image/png", png),
            filePart("doc", "a.pdf", "application/pdf", "pdf".toByteArray()),
            filePart("logo", "logo.webp", null, png),
        )
        val request = Request.post(
            "/v",
            body,
            mapOf("Content-Type" to "multipart/form-data; boundary=b"),
        )
        val ok = request.validate(
            mapOf(
                "photo" to listOf("required", "file", "image", "mimes:png,jpg", "max:1024"),
            ),
        )
        assertTrue(ok["photo"] is UploadedFile)
        assertThrows(ValidationException::class.java) {
            request.validate(mapOf("doc" to listOf("image")))
        }
        assertThrows(ValidationException::class.java) {
            request.validate(mapOf("doc" to listOf("mimes:png")))
        }
        assertThrows(ValidationException::class.java) {
            request.validate(mapOf("doc" to listOf("mimetypes:image/png")))
        }
        val typed = request.validate(mapOf("doc" to listOf("file", "mimetypes:application/pdf")))
        assertTrue(typed["doc"] is UploadedFile)
        // image by extension when mime missing
        assertTrue(request.validate(mapOf("logo" to listOf("image")))["logo"] is UploadedFile)
        assertThrows(ValidationException::class.java) {
            Request.post("/missing").validate(mapOf("photo" to listOf("required", "file")))
        }
        assertThrows(ValidationException::class.java) {
            request.validate(mapOf("photo" to listOf("mimes:")))
        }
        val huge = UploadedFile("big.bin", "application/octet-stream", ByteArray(3 * 1024))
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to huge), mapOf("f" to listOf("file", "max:1"))).validate()
        }
        assertTrue(
            Validator.make(mapOf("f" to huge), mapOf("f" to listOf("file", "min:1", "between:1,10"))).passes(),
        )
        assertEquals(
            true,
            Validator.make(mapOf("f" to huge), mapOf("f" to listOf("size:3"))).passes() ||
                Validator.make(mapOf("f" to huge), mapOf("f" to listOf("min:2"))).passes(),
        )
    }

    @Test
    fun parserEdgeCases() {
        assertTrue(
            MultipartParser.parse(ByteArray(0), "text/plain").fields.isEmpty(),
        )
        assertTrue(
            MultipartParser.parse("nope".toByteArray(), "multipart/form-data").fields.isEmpty(),
        )
        assertTrue(
            MultipartParser.parse("nope".toByteArray(), "multipart/form-data; boundary=missing").fields.isEmpty(),
        )
        // Part without header/body separator is skipped.
        val broken = "--b\r\nnoseparator\r\n--b--\r\n".toByteArray()
        assertTrue(MultipartParser.parse(broken, "multipart/form-data; boundary=b").fields.isEmpty())
        // Unquoted filename / name params
        val unquoted = (
            "--b\r\n" +
                "Content-Disposition: form-data; name=title\r\n\r\n" +
                "ok\r\n" +
                "--b\r\n" +
                "Content-Disposition: form-data; name=f; filename=raw.bin\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n" +
                "XY\r\n" +
                "--b--\r\n"
            ).toByteArray()
        val parsed = MultipartParser.parse(unquoted, "multipart/form-data; boundary=b")
        assertEquals("ok", parsed.fields["title"])
        assertEquals("raw.bin", parsed.files["f"]!!.clientOriginalName)
        // Empty JSON body form
        assertTrue(
            Request(
                "POST",
                "/",
                headers = mapOf("Content-Type" to "application/json"),
                body = "",
            ).form().isEmpty(),
        )
        // Empty body bytes form-urlencoded path
        assertTrue(
            Request("POST", "/", body = "", bodyBytes = ByteArray(0)).form().isEmpty(),
        )
        // bodyBytes without string body (urlencode)
        assertEquals(
            "1",
            Request(
                "POST",
                "/",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = "",
                bodyBytes = "n=1".toByteArray(),
            ).form()["n"],
        )
        // Invalid uploaded file fails `file` rule when present but empty
        assertThrows(ValidationException::class.java) {
            Validator.make(
                mapOf("f" to UploadedFile("x", null, ByteArray(0))),
                mapOf("f" to listOf("file")),
            ).validate()
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to "not-a-file"), mapOf("f" to listOf("image"))).validate()
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("mimes:png"))).validate()
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("mimetypes:image/png"))).validate()
        }
        val invalid = UploadedFile("a.png", "image/png", ByteArray(0))
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to invalid), mapOf("f" to listOf("mimes:png"))).validate()
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to invalid), mapOf("f" to listOf("mimetypes:image/png"))).validate()
        }
        val noMime = UploadedFile("a.png", null, byteArrayOf(1, 2, 3))
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to noMime), mapOf("f" to listOf("mimetypes:image/png"))).validate()
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("file"))).validate()
        }
        // Valid file rule success + mimetypes success already covered above.
        assertTrue(
            Validator.make(
                mapOf("f" to UploadedFile("a.png", "image/png", byteArrayOf(1))),
                mapOf("f" to listOf("file", "image", "mimes:png,,PNG", "mimetypes:image/png")),
            ).passes(),
        )
        assertThrows(ValidationException::class.java) {
            Validator.make(
                mapOf("f" to UploadedFile("a.txt", "text/plain", byteArrayOf(1))),
                mapOf("f" to listOf("image")),
            ).validate()
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(
                mapOf("f" to UploadedFile("empty.png", "image/png", ByteArray(0))),
                mapOf("f" to listOf("image")),
            ).validate()
        }
        // Cover Content-Type null path inside multipart().
        val bare = Request("POST", "/", bodyBytes = ByteArray(0))
        val method = Request::class.java.getDeclaredMethod("multipart")
        method.isAccessible = true
        val result = method.invoke(bare) as MultipartParser.Result
        assertTrue(result.fields.isEmpty())
    }

    private fun multipart(boundary: String, vararg parts: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        fun append(bytes: ByteArray) = bytes.forEach { out.add(it) }
        fun append(text: String) = append(text.toByteArray(Charsets.UTF_8))
        for (part in parts) {
            append("--$boundary\r\n")
            append(part)
        }
        append("--$boundary--\r\n")
        return out.toByteArray()
    }

    private fun textPart(name: String, value: String): ByteArray =
        (
            "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                value +
                "\r\n"
            ).toByteArray(Charsets.UTF_8)

    private fun filePart(name: String, filename: String, mime: String?, contents: ByteArray): ByteArray {
        val typeLine = if (mime == null) "" else "Content-Type: $mime\r\n"
        val header = (
            "Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n" +
                typeLine +
                "\r\n"
            ).toByteArray(Charsets.UTF_8)
        return header + contents + "\r\n".toByteArray(Charsets.UTF_8)
    }
}
