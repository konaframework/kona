package org.konaframework.firefly.forms

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.filesystem.FilesystemAdapter
import org.konaframework.filesystem.LocalAdapter
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.Request
import org.konaframework.http.Router
import org.konaframework.http.UploadedFile
import java.io.InputStream
import java.nio.file.Path

class FireflyFormsUploadTest {
    @AfterEach
    fun reset() {
        FileUpload.configureUsing(null)
    }

    @Test
    fun routeAndNames(@TempDir dir: Path) {
        val disk = LocalAdapter(dir)
        val disks = UploadDisks { name ->
            if (name == "broken") throw IllegalStateException("nope")
            disk
        }
        val openRouter = Router()
        FireflyUploads.register(openRouter, disks)
        val missing = openRouter.resolve("POST", FireflyUploads.PATH).route.action.handle(Request.post(FireflyUploads.PATH))
        assertEquals(422, missing.status)
        val emptyFile = openRouter.resolve("POST", FireflyUploads.PATH).route.action.handle(upload("a.txt", ByteArray(0), emptyMap()))
        assertEquals(422, emptyFile.status)
        val guarded = Router()
        FireflyUploads.register(guarded, disks) { it.input("ok") == "yes" }
        val denied = guarded.resolve("POST", FireflyUploads.PATH).route.action.handle(upload("a.txt", byteArrayOf(1), mapOf("ok" to "no")))
        assertEquals(419, denied.status)
        val badDisk = guarded.resolve("POST", FireflyUploads.PATH).route.action.handle(upload("a.txt", byteArrayOf(1), mapOf("ok" to "yes", "disk" to "../x")))
        assertEquals(422, badDisk.status)
        val thrown = guarded.resolve("POST", FireflyUploads.PATH).route.action.handle(upload("a.txt", byteArrayOf(1), mapOf("ok" to "yes", "disk" to "broken")))
        assertEquals(422, thrown.status)
        val saved = guarded.resolve("POST", FireflyUploads.PATH).route.action.handle(upload("My File.txt", byteArrayOf(1, 2), mapOf("ok" to "yes")))
        assertEquals(200, saved.status)
        assertTrue(saved.body.contains("firefly-temporary"))
        assertEquals("file", FireflyUploads.safeName(" "))
        assertEquals("file", FireflyUploads.safeName("."))
        assertEquals("file", FireflyUploads.safeName(".."))
        assertEquals("b.png", FireflyUploads.safeName("a/b.png"))
        assertEquals("b.png", FireflyUploads.safeName("a\\b.png"))
        assertEquals("weird_name.txt", FireflyUploads.safeName("weird name.txt"))
        val named = FireflyUploads.store(UploadedFile("Pic.PNG", "image/png", byteArrayOf(1)), disk, { "shot.png" }, false)
        assertTrue(named.path.endsWith("shot.png"))
        val fake = FakeDisk()
        fake.failMove = true
        assertThrows(FireflyException::class.java) {
            FireflyUploads.store(UploadedFile("a.txt", "text/plain", byteArrayOf(1)), fake, { "b.txt" }, false)
        }
        val preserved = FireflyUploads.store(UploadedFile("keep.txt", "text/plain", byteArrayOf(9)), disk, null, true)
        assertTrue(preserved.path.endsWith("keep.txt"))
        val generated = FireflyUploads.store(UploadedFile("keep.txt", "text/plain", byteArrayOf(9)), disk, null, false)
        assertTrue(generated.path.startsWith("firefly-temporary/"))
        assertFalse(generated.path.endsWith("keep.txt"))
    }

    @Test
    fun fieldCommit(@TempDir dir: Path) {
        val disk = LocalAdapter(dir)
        val disks = UploadDisks { disk }
        val upload = FileUpload.make("avatar")
            .disk("local")
            .directory("photos")
            .visibility("public")
            .multiple(false)
            .maxFiles(2)
            .minFiles(1)
            .acceptedFileTypes("image/*", "image/png", ".png", "png")
            .maxSize(10)
            .minSize(0)
            .image()
            .imageEditor(true)
            .imageEditorAspectRatios("1:1", "16:9")
            .imageEditorMode("crop")
            .imageEditorEmptyFillColor("#DF8723")
            .imageEditorViewportWidth(200)
            .imageEditorViewportHeight(200)
            .imagePreviewHeight(80)
            .imageResizeMode("cover")
            .imageResizeTargetWidth(40)
            .imageResizeTargetHeight(40)
            .imageCropAspectRatio("1:1")
            .imageResizeUpscale(true)
            .orientImagesFromExif(false)
            .downloadable(true)
            .openable(true)
            .previewable(true)
            .reorderable(true)
            .appendFiles(true)
            .storeFiles(true)
            .moveFiles(false)
            .preserveFilenames(true)
            .preventFilePathTampering(true)
            .pasteable(true)
            .fetchFileInformation(true)
            .maxParallelUploads(2)
            .panelLayout("compact")
            .panelAspectRatio("1:1")
            .uploadButtonPosition("right")
            .removeUploadedFileButtonPosition("right")
            .uploadProgressIndicatorPosition("left")
            .loadingIndicatorPosition("left")
            .uploadingMessage("Sending")
            .storeFileNamesIn("avatar_name")
            .getUploadedFileNameForStorageUsing { "portrait.png" }
            .avatar()
        assertEquals("local", upload.disk())
        assertEquals("photos", upload.directory())
        assertTrue(upload.accepts("firefly-temporary/a.png"))
        assertTrue(upload.accepts("photos/a.png"))
        assertTrue(upload.accepts("photos"))
        assertFalse(upload.accepts("../secret"))
        assertFalse(upload.accepts("photos/a.png.ffmeta"))
        assertFalse(upload.accepts("/etc/passwd"))
        assertFalse(upload.accepts("other/a.png"))
        upload.preventFilePathTampering(false)
        assertTrue(upload.accepts("../secret"))
        upload.preventFilePathTampering(true).directory("")
        assertTrue(upload.accepts("notes.txt"))
        upload.directory("photos")
        val stored = upload.receive(UploadedFile("face.png", "image/png", byteArrayOf(1, 2, 3)), disks)
        val schema = Schema.make().components(upload, org.konaframework.firefly.forms.Hidden.make("avatar_name"))
        schema.fill(mapOf("avatar" to stored.path))
        val html = schema.render()
        assertTrue(html.contains("ff-filepond"))
        assertTrue(html.contains("filepond"))
        assertTrue(html.contains("/_firefly/uploads"))
        assertTrue(html.contains("data-ff-image-editor=\"true\""))
        assertTrue(html.contains("data-ff-avatar=\"true\""))
        val saved = upload.commit(schema, disks)
        assertEquals("photos/portrait.png", saved)
        assertTrue(disk.exists("photos/portrait.png"))
        assertEquals("public", disk.getVisibility("photos/portrait.png"))
        val copy = FileUpload.make("doc").directory("docs").acceptedFileTypes("txt")
        val temp = FireflyUploads.store(UploadedFile("note.txt", "text/plain", byteArrayOf(4)), disk, null, false)
        val copySchema = Schema.make().components(copy).fill(mapOf("doc" to temp.path))
        assertEquals("docs/${temp.path.substringAfterLast('/')}", copy.commit(copySchema, disks))
        assertTrue(disk.exists("${temp.path}.ffmeta"))
        val mover = FileUpload.make("doc").directory("moved").moveFiles(true)
        val again = FireflyUploads.store(UploadedFile("note.txt", "text/plain", byteArrayOf(5)), disk, null, false)
        val moveSchema = Schema.make().components(mover).fill(mapOf("doc" to again.path))
        mover.commit(moveSchema, disks)
        assertFalse(disk.exists("${again.path}.ffmeta"))
        val stay = FileUpload.make("doc").storeFiles(false)
        val staySchema = Schema.make().components(stay).fill(mapOf("doc" to "firefly-temporary/a.txt"))
        assertEquals("firefly-temporary/a.txt", stay.commit(staySchema, disks))
        val many = FileUpload.make("docs").multiple(true).directory("batch").minFiles(1).maxFiles(2)
        val one = FireflyUploads.store(UploadedFile("a.txt", "text/plain", byteArrayOf(1)), disk, null, false)
        val two = FireflyUploads.store(UploadedFile("b.txt", "text/plain", byteArrayOf(1, 2)), disk, null, false)
        val manySchema = Schema.make().components(many).fill(mapOf("docs" to listOf(one.path, two.path, null, "")))
        val listed = many.commit(manySchema, disks) as List<*>
        assertEquals(2, listed.size)
        assertThrows(FireflyException::class.java) {
            FileUpload.make("doc").directory("bad").preventFilePathTampering(true).commit(
                Schema.make().components(FileUpload.make("doc")).fill(mapOf("doc" to "nope.txt")),
                disks,
            )
        }
        val present = FileUpload.make("doc").directory("photos")
        assertThrows(FireflyException::class.java) {
            present.commit(Schema.make().components(present).fill(mapOf("doc" to "photos/missing.txt")), disks)
        }
        disk.put("photos/big.txt", ByteArray(2048))
        val big = FileUpload.make("doc").directory("photos").maxSize(1)
        assertThrows(FireflyException::class.java) {
            big.commit(Schema.make().components(big).fill(mapOf("doc" to "photos/big.txt")), disks)
        }
        disk.put("photos/small.txt", byteArrayOf(1))
        val small = FileUpload.make("doc").directory("photos").minSize(1)
        assertThrows(FireflyException::class.java) {
            small.commit(Schema.make().components(small).fill(mapOf("doc" to "photos/small.txt")), disks)
        }
        disk.put("photos/note.txt", byteArrayOf(1))
        val typed = FileUpload.make("doc").directory("photos").acceptedFileTypes("image/*")
        assertThrows(FireflyException::class.java) {
            typed.commit(Schema.make().components(typed).fill(mapOf("doc" to "photos/note.txt")), disks)
        }
        val png = FileUpload.make("doc").directory("photos").image()
        disk.put("photos/pic.png", byteArrayOf(1))
        assertEquals("photos/pic.png", png.commit(Schema.make().components(png).fill(mapOf("doc" to "photos/pic.png")), disks))
        val open = FileUpload.make("doc").acceptedFileTypes()
        disk.put("anywhere.txt", byteArrayOf(1))
        assertEquals("anywhere.txt", open.commit(Schema.make().components(open).fill(mapOf("doc" to "anywhere.txt")), disks))
        val exact = FileUpload.make("doc").directory("photos").acceptedFileTypes("image/png")
        disk.put("photos/exact.png", byteArrayOf(1))
        assertEquals("photos/exact.png", exact.commit(Schema.make().components(exact).fill(mapOf("doc" to "photos/exact.png")), disks))
        val dotted = FileUpload.make("doc").directory("photos").acceptedFileTypes(".png")
        disk.put("photos/dotted.png", byteArrayOf(1))
        assertEquals("photos/dotted.png", dotted.commit(Schema.make().components(dotted).fill(mapOf("doc" to "photos/dotted.png")), disks))
        val empty = FileUpload.make("doc").multiple(true)
        assertEquals(emptyList<String>(), empty.commit(Schema.make().components(empty), disks))
        val none = FileUpload.make("doc")
        assertEquals(null, none.commit(Schema.make().components(none), disks))
        val required = FileUpload.make("doc").required()
        val requiredSchema = Schema.make().components(required)
        assertTrue(required.extraErrors(requiredSchema).containsKey("doc"))
        val bad = FileUpload.make("doc").directory("photos")
        val badSchema = Schema.make().components(bad).fill(mapOf("doc" to "nope.txt"))
        assertTrue(bad.extraErrors(badSchema)["doc"]!!.any { it.contains("invalid") })
        val fine = FileUpload.make("doc").directory("photos")
        assertTrue(fine.extraErrors(Schema.make().components(fine).fill(mapOf("doc" to "photos/pic.png"))).isEmpty())
        disk.put("batch/a", byteArrayOf(1))
        disk.put("batch/b", byteArrayOf(1))
        disk.put("batch/c", byteArrayOf(1))
        val tooFew = FileUpload.make("docs").multiple(true).directory("batch").minFiles(3)
        assertThrows(FireflyException::class.java) {
            tooFew.commit(Schema.make().components(tooFew).fill(mapOf("docs" to listOf("batch/a"))), disks)
        }
        val tooMany = FileUpload.make("docs").multiple(true).directory("batch").maxFiles(1)
        assertThrows(FireflyException::class.java) {
            tooMany.commit(
                Schema.make().components(tooMany).fill(mapOf("docs" to listOf("batch/a", "batch/b"))),
                disks,
            )
        }
        val failing = FakeDisk()
        failing.files["firefly-temporary/a.txt"] = byteArrayOf(1)
        val field = FileUpload.make("doc").directory("out")
        assertThrows(FireflyException::class.java) {
            field.commit(Schema.make().components(field).fill(mapOf("doc" to "firefly-temporary/a.txt")), UploadDisks { failing })
        }
        FileUpload.configureUsing { it.image() }
        val preset = FileUpload.make("pic").acceptedFileTypes("text/plain").image()
        assertTrue(Schema.make().components(preset).render().contains("data-ff-accept=\"text/plain\""))
        val avatar = FileUpload.make("pic").imageEditorAspectRatios("4:3").avatar()
        assertTrue(Schema.make().components(avatar).render().contains("4:3"))
        FileUpload.configureUsing(null)
        assertTrue(Schema.make().components(FileUpload.make("pic").multiple(true)).render().contains("multiple"))
    }

    @Test
    fun uploadBranches(@TempDir dir: Path) {
        val disk = LocalAdapter(dir)
        val disks = UploadDisks { disk }
        val bare = FireflyUploads.store(UploadedFile("README", "text/plain", byteArrayOf(1)), disk, null, false)
        assertFalse(bare.path.contains("."))
        assertTrue(Schema.make().components(FileUpload.make("pic").avatar()).render().contains("1:1"))
        val nameless = FileUpload.make("").storeFiles(false)
        assertNull(nameless.commit(Schema.make().components(nameless), disks))
        assertTrue(nameless.extraErrors(Schema.make().components(nameless)).isEmpty())
        val required = FileUpload.make("photo").required().directory("photos")
        assertTrue(required.extraErrors(Schema.make().components(required)).containsKey("photo"))
        disk.put("photos/pic.png", byteArrayOf(1))
        disk.put("photos/pic.png.ffmeta", "pic.png")
        val filled = Schema.make().components(required).fill(mapOf("photo" to "photos/pic.png"))
        assertTrue(required.extraErrors(filled).isEmpty())
        val loose = FileUpload.make("photo").preventFilePathTampering(false).directory("photos")
        assertTrue(loose.extraErrors(Schema.make().components(loose).fill(mapOf("photo" to "../x"))).isEmpty())
        val bad = FileUpload.make("photo").directory("photos")
        assertTrue(bad.extraErrors(Schema.make().components(bad).fill(mapOf("photo" to "../x")))["photo"]!!.first().contains("invalid"))
        val blank = FileUpload.make("photo")
        assertNull(blank.commit(Schema.make().components(blank).fill(mapOf("photo" to "")), disks))

        val typed = FakeDisk()
        typed.mime = "image/png"
        typed.put("photos/note.txt", byteArrayOf(1))
        typed.put("photos/note.txt.ffmeta", "note.txt")
        val imageMime = FileUpload.make("doc").directory("photos").acceptedFileTypes("image/*")
        assertEquals(
            "photos/note.txt",
            imageMime.commit(Schema.make().components(imageMime).fill(mapOf("doc" to "photos/note.txt")), UploadDisks { typed }),
        )
        val extDisk = FakeDisk()
        extDisk.mime = "text/plain"
        extDisk.put("photos/pic.png", byteArrayOf(1))
        extDisk.put("photos/pic.png.ffmeta", "pic.png")
        val byExt = FileUpload.make("doc").directory("photos").acceptedFileTypes("image/*")
        assertEquals(
            "photos/pic.png",
            byExt.commit(Schema.make().components(byExt).fill(mapOf("doc" to "photos/pic.png")), UploadDisks { extDisk }),
        )
        val dotMiss = FakeDisk()
        dotMiss.put("photos/pic.png", byteArrayOf(1))
        dotMiss.put("photos/pic.png.ffmeta", "pic.png")
        val dots = FileUpload.make("doc").directory("photos").acceptedFileTypes(".gif")
        assertThrows(FireflyException::class.java) {
            dots.commit(Schema.make().components(dots).fill(mapOf("doc" to "photos/pic.png")), UploadDisks { dotMiss })
        }
        val tokenMiss = FileUpload.make("doc").directory("photos").acceptedFileTypes("gif")
        assertThrows(FireflyException::class.java) {
            tokenMiss.commit(Schema.make().components(tokenMiss).fill(mapOf("doc" to "photos/pic.png")), UploadDisks { dotMiss })
        }
        val token = FileUpload.make("doc").directory("photos").acceptedFileTypes("PNG")
        assertEquals(
            "photos/pic.png",
            token.commit(Schema.make().components(token).fill(mapOf("doc" to "photos/pic.png")), UploadDisks { dotMiss }),
        )
        val keptDisk = FakeDisk()
        keptDisk.failCopy = false
        keptDisk.put("firefly-temporary/keep.txt", byteArrayOf(1))
        keptDisk.put("firefly-temporary/keep.txt.ffmeta", "keep.txt")
        val kept = FileUpload.make("doc").directory("photos").preserveFilenames(true)
        assertEquals(
            "photos/keep.txt",
            kept.commit(
                Schema.make().components(kept).fill(mapOf("doc" to "firefly-temporary/keep.txt")),
                UploadDisks { keptDisk },
            ),
        )
        val namedDisk = FakeDisk()
        namedDisk.failCopy = false
        namedDisk.put("firefly-temporary/a.txt", byteArrayOf(1))
        namedDisk.put("firefly-temporary/a.txt.ffmeta", "a.txt")
        val named = FileUpload.make("doc").directory("photos").getUploadedFileNameForStorageUsing { "renamed.txt" }
        assertEquals(
            "photos/renamed.txt",
            named.commit(
                Schema.make().components(named).fill(mapOf("doc" to "firefly-temporary/a.txt")),
                UploadDisks { namedDisk },
            ),
        )
    }

    private fun upload(name: String, bytes: ByteArray, fields: Map<String, String>): Request {
        val chunks = ArrayList<ByteArray>()
        for ((key, value) in fields) {
            chunks.add("Content-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n".toByteArray())
        }
        val header = "Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\nContent-Type: text/plain\r\n\r\n".toByteArray()
        chunks.add(header + bytes + "\r\n".toByteArray())
        val body = multipart("bound", chunks)
        return Request.post(
            FireflyUploads.PATH,
            body,
            mapOf("Content-Type" to "multipart/form-data; boundary=bound"),
        )
    }

    private fun multipart(boundary: String, parts: List<ByteArray>): ByteArray {
        val out = ArrayList<Byte>()
        fun append(bytes: ByteArray) = bytes.forEach { out.add(it) }
        for (part in parts) {
            append("--$boundary\r\n".toByteArray())
            append(part)
        }
        append("--$boundary--\r\n".toByteArray())
        return out.toByteArray()
    }
}

internal class FakeDisk : FilesystemAdapter {
    val files = LinkedHashMap<String, ByteArray>()
    var failMove: Boolean = false
    var failCopy: Boolean = true

    override fun put(path: String, contents: ByteArray) {
        files[path] = contents
    }

    override fun put(path: String, contents: String) {
        put(path, contents.toByteArray())
    }

    override fun get(path: String): ByteArray? = files[path]

    override fun getString(path: String): String? = files[path]?.toString(Charsets.UTF_8)

    override fun exists(path: String): Boolean = files.containsKey(path)

    override fun missing(path: String): Boolean = !exists(path)

    override fun delete(path: String): Boolean = files.remove(path) != null

    override fun copy(from: String, to: String): Boolean {
        if (failCopy) return false
        val bytes = files[from] ?: return false
        files[to] = bytes
        return true
    }

    override fun move(from: String, to: String): Boolean {
        if (failMove) return false
        val bytes = files.remove(from) ?: return false
        files[to] = bytes
        return true
    }

    override fun size(path: String): Long = files[path]?.size?.toLong() ?: 0

    override fun lastModified(path: String): Long = 0

    var mime: String? = null

    override fun mimeType(path: String): String? = mime

    override fun checksum(path: String, algorithm: String): String? = null

    override fun files(directory: String): List<String> {
        if (directory.isEmpty() && files.isEmpty()) return emptyList()
        return ArrayList(files.keys)
    }

    override fun allFiles(directory: String): List<String> = emptyList()

    override fun directories(directory: String): List<String> = emptyList()

    override fun allDirectories(directory: String): List<String> = emptyList()

    override fun makeDirectory(path: String): Boolean = true

    override fun deleteDirectory(path: String): Boolean = true

    override fun url(path: String): String = path

    override fun path(path: String): String = path

    override fun append(path: String, data: String) = Unit

    override fun prepend(path: String, data: String) = Unit

    override fun readStream(path: String): InputStream? = null

    override fun writeStream(path: String, input: InputStream) = Unit

    override fun putFile(directory: String, source: Path): String = source.toString()

    override fun putFileAs(directory: String, source: Path, name: String): String = name

    override fun getVisibility(path: String): String = "private"

    override fun setVisibility(path: String, visibility: String) = Unit

    override fun providesTemporaryUrls(): Boolean = false

    override fun temporaryUrl(path: String, expirationSeconds: Long): String = path
}
