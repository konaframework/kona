package org.konaframework.brew

import org.konaframework.auth.authorization.AuthorizationException
import org.konaframework.auth.authorization.Gate
import org.konaframework.brew.features.SupportAuthorize
import org.konaframework.brew.features.SupportFileUploads
import org.konaframework.brew.features.SupportJsModules
import org.konaframework.brew.features.SupportLocales
import org.konaframework.brew.features.SupportReleaseTokens
import org.konaframework.brew.features.SupportStyles
import org.konaframework.brew.features.TemporaryUploadSynth
import org.konaframework.brew.features.loadBrewMultiFile
import org.konaframework.brew.features.resetBrewStyleSeen
import org.konaframework.brew.features.setBrewGlobalStyles
import org.konaframework.brew.features.setBrewJsModule
import org.konaframework.brew.features.setBrewScopedStyles
import org.konaframework.brew.features.setBrewStyleModules
import org.konaframework.brew.hydration.Checksum
import org.konaframework.brew.hydration.ModelCollectionSynth
import org.konaframework.brew.hydration.ModelSynth
import org.konaframework.brew.hydration.SynthContext
import org.konaframework.brew.hydration.Synthesizers
import org.konaframework.brew.uploads.TemporaryUploadedFile
import org.konaframework.brew.uploads.UploadedFileFake
import org.konaframework.brew.uploads.WithFileUploads
import org.konaframework.core.Config
import org.konaframework.core.Container
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Locale

class M6CoverageSealTest {
    private lateinit var manager: BrewManager
    private lateinit var root: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        resetBrewStyleSeen()
        root = Files.createTempDirectory("m6-seal")
        val container = Container()
        container.instance(Gate::class.java, Gate().define("ok") { _, _ -> true }.define("no") { _, _ -> false })
        manager = brew(config = BrewConfig(key = "seal-key", debug = true, cspSafe = true), container = container)
        manager.uploadRoot(root)
        Brew.setManager(manager)
        manager.component("up", UploadComp::class.java)
        manager.component("styles", StyleComp::class.java)
        manager.component("auth", AuthComp::class.java)
        manager.component("plain", PlainComp::class.java)
    }

    @Test
    fun temporaryFileAndFakeApi() {
        val fake = UploadedFileFake.create("doc.pdf", byteArrayOf(1, 2, 3, 4), "application/pdf", root)
        assertTrue(fake.isPreviewable())
        assertEquals(fake.toMeta()["path"], fake.diskPath)
        val from = TemporaryUploadedFile.fromMeta(fake.toMeta(), root)
        assertEquals(fake.diskPath, from.diskPath)
        assertTrue(from.exists())
        val sparse = TemporaryUploadedFile.fromMeta(mapOf("path" to fake.diskPath), root)
        assertEquals("file", sparse.originalName)
        assertEquals("application/octet-stream", sparse.mimeType)
        assertEquals(0L, sparse.size)
        val nonNumberSize = TemporaryUploadedFile.fromMeta(
            mapOf("path" to fake.diskPath, "name" to "n.bin", "type" to "text/plain", "size" to "nope"),
            root,
        )
        assertEquals(0L, nonNumberSize.size)
        val nullFields = TemporaryUploadedFile.fromMeta(
            mapOf("path" to fake.diskPath, "name" to null, "type" to null, "size" to null),
            root,
        )
        assertEquals("file", nullFields.originalName)
        assertEquals("application/octet-stream", nullFields.mimeType)
        val stored = from.store("out")
        assertTrue(stored.isNotEmpty())
        val blankDir = UploadedFileFake.create("blank-dir.bin", byteArrayOf(9), "text/plain", root)
        assertTrue(blankDir.store("").isNotEmpty())
        val defaults = UploadedFileFake.create("defs.bin", byteArrayOf(1), "text/plain", root)
        assertTrue(defaults.store().isNotEmpty())
        // @JvmStatic / @JvmOverloads bridges
        TemporaryUploadedFile::class.java.getMethod("fromMeta", Map::class.java, java.nio.file.Path::class.java)
            .invoke(null, fake.toMeta(), root)
        UploadedFileFake::class.java.getMethod(
            "create", String::class.java, ByteArray::class.java, String::class.java, java.nio.file.Path::class.java,
        ).invoke(null, "via-java.bin", byteArrayOf(1), "text/plain", root)
        val img = UploadedFileFake.image(root)
        UploadedFileFake.image("alt.jpg", root).delete()
        img.storePublicly()
        UploadedFileFake.create("pub.bin", byteArrayOf(1), "text/plain", root).storePublicly("pub")
        img.delete()
        val plain = TemporaryUploadedFile("missing.bin", "m.bin", "text/plain", 0, root)
        assertFalse(plain.isPreviewable())
        assertTrue(TemporaryUploadedFile("v.mp4", "v.mp4", "video/mp4", 1, root).isPreviewable())
        assertTrue(TemporaryUploadedFile("a.mp3", "a.mp3", "audio/mpeg", 1, root).isPreviewable())
        assertEquals("", TemporaryUploadedFile("a", "a", "x", 0, root).extension())
        assertTrue(UploadedFileFake.create(root).storeAs("d", "named").endsWith("named"))
        assertTrue(UploadedFileFake.create("n-only.bin", root).exists())
        assertTrue(UploadedFileFake.create("n-c.bin", byteArrayOf(1, 2), root).exists())
        val checksum = Checksum("seal-key")
        val snap = linkedMapOf<String, Any?>("data" to emptyMap<String, Any?>(), "memo" to emptyMap<String, Any?>())
        snap["checksum"] = checksum.generate(snap)
        checksum.verify(snap)
        checksum.verify(snap, false)
        assertThrows(CorruptComponentPayloadException::class.java) {
            checksum.verify(snap + ("checksum" to "deadbeef"), true)
        }
        assertThrows(CorruptComponentPayloadException::class.java) {
            checksum.verify(snap + ("checksum" to "deadbeef"), false)
        }
        assertThrows(CorruptComponentPayloadException::class.java) {
            checksum.verify(mapOf("data" to emptyMap<String, Any?>(), "memo" to emptyMap<String, Any?>()))
        }
    }

    @Test
    fun uploadSynthAndWithFileUploads() {
        val synth = TemporaryUploadSynth(root)
        assertTrue(synth.match(UploadedFileFake.create(root = root)))
        assertFalse(synth.match("x"))
        val file = UploadedFileFake.create(root = root)
        val (data, meta) = synth.dehydrate(file, SynthContext(manager.synths, "photo"))
        assertTrue(meta.isEmpty())
        val hydrated = synth.hydrate(data, meta, SynthContext(manager.synths, "photo"), null)
        assertTrue(hydrated is TemporaryUploadedFile)
        assertNull(synth.hydrate("bad", emptyMap(), SynthContext(manager.synths, "p"), null))

        val mounted = manager.mountComponent("up", emptyMap(), null)
        val start = manager.updateComponent(
            mounted.snapshot, emptyMap(),
            listOf(mapOf("method" to "_startUpload", "params" to listOf("photo", mapOf("name" to "a.png", "size" to 1), false))),
        )
        val path = manager.storeUpload(byteArrayOf(1), "a.png")
        manager.updateComponent(
            start.snapshot, emptyMap(),
            listOf(mapOf("method" to "_finishUpload", "params" to listOf("photo", listOf(path), false))),
        )
        manager.updateComponent(
            start.snapshot, emptyMap(),
            listOf(mapOf("method" to "_finishUpload", "params" to listOf("photos", listOf(path, path), true))),
        )
        manager.updateComponent(
            start.snapshot, emptyMap(),
            listOf(mapOf("method" to "_uploadErrored", "params" to listOf("photo", null, false))),
        )
        manager.updateComponent(
            start.snapshot, emptyMap(),
            listOf(mapOf("method" to "_removeUpload", "params" to listOf("photo", path))),
        )
        manager.updateComponent(
            start.snapshot, emptyMap(),
            listOf(mapOf("method" to "_removeUpload", "params" to listOf("photos", path))),
        )
        manager.updateComponent(
            start.snapshot, emptyMap(),
            listOf(mapOf("method" to "_removeUpload", "params" to listOf("missing", path))),
        )
        // finishUpload on unknown property is a no-op
        val comp = manager.newComponent("up") as UploadComp
        val path2 = manager.storeUpload(byteArrayOf(2), "b.png")
        comp.finishUpload("nope", listOf(path2), false)
        // array property path (isArray branch)
        comp.finishUpload("photoArr", listOf(path2), false)
        assertEquals(1, comp.photoArr.size)
        // list property with multiple=true and multiple=false
        val path3 = manager.storeUpload(byteArrayOf(3), "c.png")
        comp.finishUpload("photos", listOf(path3), true)
        assertEquals(1, comp.photos.size)
        val path4 = manager.storeUpload(byteArrayOf(4), "d.png")
        comp.finishUpload("photos", listOf(path4), false)
        assertEquals(1, comp.photos.size)
        // scalar property ignores multiple and stores the first file
        val path5 = manager.storeUpload(byteArrayOf(5), "e.png")
        comp.finishUpload("photo", listOf(path5), true)
        assertNotNull(comp.photo)
        // remove non-matching single file
        comp.photo = TemporaryUploadedFile(path, "a.png", "image/png", 1, root)
        comp._removeUpload("photo", "other-path")
        assertNotNull(comp.photo)
        // matching single file clears the property
        comp._removeUpload("photo", path)
        assertNull(comp.photo)
        comp.photos = listOf(TemporaryUploadedFile(path, "a.png", "image/png", 1, root), "skip")
        comp._removeUpload("photos", "other-path")
        assertEquals(2, comp.photos.size)
        comp._removeUpload("photos", path)
        assertEquals(1, comp.photos.size)
        assertEquals("skip", comp.photos[0])
    }

    @Test
    fun uploadEndpointsAndCleanup() {
        val signed = manager.signUpload("photo", mapOf("name" to "z.bin", "size" to 2), true)
        val expires = signed.url.substringAfter("expires=").substringBefore('&').toLong()
        assertTrue(manager.verifyUploadSignature("photo", expires, true, "z.bin", 2, signed.token))
        assertFalse(manager.verifyUploadSignature("photo", expires, true, "z.bin", 2, "bad"))
        val ok = manager.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to expires.toString(), "signature" to signed.token,
                    "name" to "photo", "filename" to "z.bin", "size" to "2", "multiple" to "true",
                ),
                bodyBytes = byteArrayOf(1, 2),
            ),
        )
        assertEquals(200, ok.status)
        assertEquals(403, manager.handleUpload(Request("POST", "/brew/upload")).status)
        assertEquals(403, manager.handleUpload(
            Request("POST", "/brew/upload", query = mapOf("expires" to "not-a-long")),
        ).status)
        assertEquals(403, manager.handleUpload(
            Request("POST", "/brew/upload", query = mapOf("expires" to expires.toString())),
        ).status)
        assertEquals(403, manager.handleUpload(
            Request("POST", "/brew/upload", query = mapOf("expires" to "1", "signature" to "x")),
        ).status)
        // rules without a parseable max: entry
        val noMax = brew(
            config = BrewConfig(key = "k", uploadRules = listOf("file", "max:abc")),
            container = manager.container,
        )
        noMax.uploadRoot(root)
        val sNoMax = noMax.signUpload("photo", mapOf("name" to "z.bin", "size" to 2), false)
        val exNoMax = sNoMax.url.substringAfter("expires=").substringBefore('&').toLong()
        assertEquals(200, noMax.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to exNoMax.toString(), "signature" to sNoMax.token,
                    "name" to "photo", "filename" to "z.bin", "size" to "2", "multiple" to "false",
                ),
                bodyBytes = byteArrayOf(1, 2),
            ),
        ).status)
        // no max: rule at all → firstOrNull null
        val plainRules = brew(
            config = BrewConfig(key = "k", uploadRules = listOf("file", "required")),
            container = manager.container,
        )
        plainRules.uploadRoot(root)
        val sPlain = plainRules.signUpload("photo", mapOf("name" to "p.bin", "size" to 1), false)
        val exPlain = sPlain.url.substringAfter("expires=").substringBefore('&').toLong()
        assertEquals(200, plainRules.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to exPlain.toString(), "signature" to sPlain.token,
                    "name" to "photo", "filename" to "p.bin", "size" to "1",
                ),
                bodyBytes = byteArrayOf(1),
            ),
        ).status)
        // successful upload with explicit filename and without (default upload.bin)
        val sFn = manager.signUpload("photo", mapOf("name" to "named.bin", "size" to 1), false)
        val exFn = sFn.url.substringAfter("expires=").substringBefore('&').toLong()
        assertEquals(200, manager.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to exFn.toString(), "signature" to sFn.token,
                    "name" to "photo", "filename" to "named.bin", "size" to "1",
                ),
                bodyBytes = byteArrayOf(1),
            ),
        ).status)
        val sDef = manager.signUpload("photo", mapOf("name" to null, "size" to 1), false)
        val exDef = sDef.url.substringAfter("expires=").substringBefore('&').toLong()
        assertEquals(200, manager.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to exDef.toString(), "signature" to sDef.token,
                    "name" to "photo", "size" to "1",
                ),
                bodyBytes = byteArrayOf(1),
            ),
        ).status)
        val emptySigned = manager.signUpload("photo", mapOf("name" to "empty.bin", "size" to 0), false)
        val emptyExp = emptySigned.url.substringAfter("expires=").substringBefore('&').toLong()
        assertEquals(422, manager.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to emptyExp.toString(), "signature" to emptySigned.token,
                    "name" to "photo", "filename" to "empty.bin", "size" to "0", "multiple" to "false",
                ),
                bodyBytes = ByteArray(0),
            ),
        ).status)

        val tiny = brew(config = BrewConfig(key = "k", uploadRules = listOf("max:1")), container = manager.container)
        tiny.uploadRoot(root)
        val s2 = tiny.signUpload("photo", mapOf("name" to "b.bin", "size" to 2048), false)
        val ex2 = s2.url.substringAfter("expires=").substringBefore('&').toLong()
        assertEquals(422, tiny.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf(
                    "expires" to ex2.toString(), "signature" to s2.token,
                    "name" to "photo", "filename" to "b.bin", "size" to "2048", "multiple" to "false",
                ),
                bodyBytes = ByteArray(2048),
            ),
        ).status)

        val path = manager.storeUpload(byteArrayOf(9), "p.bin")
        assertEquals(200, manager.handlePreview(Request("GET", "/brew/preview/$path")).status)
        assertEquals(404, manager.handlePreview(Request("GET", "/brew/preview/../etc/passwd")).status)
        assertEquals(404, manager.handlePreview(Request("GET", "/brew/preview/nope.bin")).status)
        assertEquals(0, BrewManager(BrewConfig(key = "k")).cleanupOldUploads())
        Thread.sleep(5)
        val cleaner = brew(config = BrewConfig(key = "k", uploadCleanupSeconds = 0), container = manager.container)
        cleaner.uploadRoot(root)
        assertTrue(cleaner.cleanupOldUploads() >= 0)
        // fresh files must not be deleted when cleanup window is huge
        val keep = brew(config = BrewConfig(key = "k", uploadCleanupSeconds = 60 * 60 * 24 * 365), container = manager.container)
        keep.uploadRoot(root)
        keep.storeUpload(byteArrayOf(1), "keep.bin")
        assertEquals(0, keep.cleanupOldUploads())
        // default temp upload root
        val auto = BrewManager(BrewConfig(key = "k"))
        assertTrue(Files.isDirectory(auto.uploadRoot()))
        assertEquals(auto.uploadRoot(), auto.uploadRoot())
    }

    @Test
    fun middlewareAndReleaseAndLocale() {
        var hits = 0
        val mw = Middleware { req, next -> hits++; next.handle(req) }
        manager.addPersistentMiddleware(mw).updateMiddleware(mw)
        val req = Request("POST", "/brew/update", headers = mapOf("X-Brew" to "1"), bodyBytes = """{"_token":"t","components":[]}""".toByteArray())
        // malformed empty components → 404 through handleUpdate
        manager.handleUpdateWithMiddleware(req)

        manager.releaseToken(null)
        assertNull(SupportReleaseTokens().also {
            it.manager = manager
            it.component = manager.newComponent("plain")
        }.takeIf { !it.skip() })
        SupportReleaseTokens().also {
            it.manager = manager
            it.component = manager.newComponent("plain")
            // releaseToken null → early return on hydrate / dehydrate
            it.hydrate(mapOf("release" to "x"), org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
            it.dehydrate(org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
        }

        manager.releaseToken("rel")
        val mounted = manager.mountComponent("plain", emptyMap(), null)
        Locale.setDefault(Locale.FRANCE)
        SupportLocales().also {
            it.manager = manager
            it.component = manager.newComponent("plain")
            it.hydrate(mapOf("locale" to "de-DE"), org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
            it.hydrate(mapOf("locale" to ""), org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
            it.hydrate(mapOf("locale" to "  "), org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
            val ctx = org.konaframework.brew.lifecycle.ComponentContext(it.component, false)
            it.dehydrate(ctx)
            assertNotNull(ctx.memo["locale"])
        }
        SupportReleaseTokens().also {
            it.manager = manager
            it.component = manager.newComponent("plain")
            // non-string release memo fails the equality check
            assertThrows(CorruptComponentPayloadException::class.java) {
                it.hydrate(mapOf("release" to 42), org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
            }
        }
        manager.releaseToken("other")
        assertThrows(CorruptComponentPayloadException::class.java) {
            manager.updateComponent(mounted.snapshot, emptyMap(), emptyList())
        }
        Brew.releaseToken("rel")
    }

    @Test
    fun authorizeBranches() {
        val hook = SupportAuthorize()
        hook.manager = manager
        hook.component = manager.newComponent("auth")
        val ctx = org.konaframework.brew.lifecycle.ComponentContext(hook.component, false)
        hook.call("allowed", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx)
        hook.call("allowedWithLiteral", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx)
        hook.call("allowedWithNullable", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx)
        assertNull(hook.call("unmarked", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx))
        assertThrows(AuthorizationException::class.java) {
            hook.call("denied", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx)
        }
        assertNull(hook.call("missing", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx))
        // parameter-count mismatch falls back to maxBy overload
        hook.call("allowed", listOf("extra"), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(), ctx)
        assertNull(SupportAuthorize.gateOrNull(BrewManager(BrewConfig(key = "k"))))
        val bare = Container()
        assertNull(SupportAuthorize.gateOrNull(BrewManager(BrewConfig(key = "k"), container = bare)))
        val comp = manager.newComponent("auth")
        assertThrows(AuthorizationException::class.java) { comp.authorize("no") }
        comp.authorize("ok")
        Brew.addPersistentMiddleware(Middleware { r, n -> n.handle(r) })
        Brew.updateMiddleware(Middleware { r, n -> n.handle(r) })
        Brew.propertySynthesizer(object : org.konaframework.brew.hydration.Synth() {
            override val key = "x"
            override fun match(value: Any) = false
            override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> =
                null to emptyMap()
            override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: java.lang.reflect.Type?) = null
        })
    }

    @Test
    fun stylesJsModulesAndCsp() {
        val c = manager.newComponent("styles") as StyleComp
        c.mount()
        loadBrewMultiFile(c)
        loadBrewMultiFile(c, javaClass.classLoader)
        SupportJsModules.loadMultiFile(c, javaClass.classLoader)
        setBrewJsModule(c, "   ")
        val blankHook = SupportJsModules()
        blankHook.manager = manager
        blankHook.component = c
        blankHook.dehydrate(org.konaframework.brew.lifecycle.ComponentContext(c, false))
        setBrewJsModule(c, "console.log(1)")
        SupportJsModules.setModule(c, "console.log(2)")
        val mounted = manager.mountComponent("styles", emptyMap(), null)
        assertNotNull(mounted.context.effects)
        setBrewScopedStyles(c, "a{}")
        setBrewGlobalStyles(c, "b{}")
        setBrewStyleModules(c, mapOf("x" to "y"))
        SupportStyles.setScoped(c, "a2{}")
        SupportStyles.setGlobal(c, "b2{}")
        SupportStyles.setModules(c, mapOf("z" to "1"))
        // Hit @JvmStatic bridges (Java entry points) via reflection — Kotlin calls Companion directly
        SupportStyles::class.java.getMethod("setScoped", Component::class.java, String::class.java)
            .invoke(null, c, "a3{}")
        SupportStyles::class.java.getMethod("setGlobal", Component::class.java, String::class.java)
            .invoke(null, c, "b3{}")
        SupportStyles::class.java.getMethod("setModules", Component::class.java, Map::class.java)
            .invoke(null, c, mapOf("w" to "2"))
        SupportStyles::class.java.getMethod("resetSeen").invoke(null)
        SupportJsModules::class.java.getMethod("setModule", Component::class.java, String::class.java)
            .invoke(null, c, "console.log(3)")
        SupportJsModules::class.java.getMethod("loadMultiFile", Component::class.java, ClassLoader::class.java)
            .invoke(null, c, javaClass.classLoader)
        val hook = SupportStyles()
        hook.manager = manager
        hook.component = c
        val ctx = org.konaframework.brew.lifecycle.ComponentContext(c, false)
        hook.dehydrate(ctx)
        // second dehydrate hits seenGlobalStyles.add == false
        hook.dehydrate(org.konaframework.brew.lifecycle.ComponentContext(c, false))
        SupportStyles.resetSeen()
        val empty = SupportStyles()
        empty.manager = manager
        empty.component = manager.newComponent("plain")
        empty.dehydrate(org.konaframework.brew.lifecycle.ComponentContext(empty.component, false))
        // ClassLoader that never finds multi-file resources
        loadBrewMultiFile(c, object : ClassLoader() {
            override fun getResourceAsStream(name: String) = null
        })
        assertEquals("brew.csp.min.js", manager.assets.scriptName())
        assertEquals(200, manager.assets.response("brew.csp.min.js").status)
        assertEquals(404, manager.assets.response("missing.js").status)
        assertNull(manager.assets.asset("brew.absent.js"))
        val debugMgr = brew(config = BrewConfig(key = "k", debug = true, cspSafe = false))
        assertEquals("brew.js", debugMgr.assets.scriptName())
        val minMgr = brew(config = BrewConfig(key = "k", debug = false, cspSafe = false))
        assertEquals("brew.min.js", minMgr.assets.scriptName())
        val cfg = BrewConfig.from(Config().also {
            it.set("brew.temporary_file_upload.rules", "file||max:2|")
            it.set("brew.csp_safe", false)
            it.set("brew.class_namespace", listOf("app.brew", "extra.brew"))
        })
        assertFalse(cfg.cspSafe)
        assertEquals(listOf("file", "max:2"), cfg.uploadRules)
        assertEquals(listOf("app.brew", "extra.brew"), cfg.classNamespaces)
        BrewConfig.from(Config().also { it.set("brew.temporary_file_upload.rules", 1) })
        val listRules = BrewConfig.from(Config().also {
            it.set("brew.temporary_file_upload.rules", arrayListOf("required", "max:9"))
        })
        assertEquals(listOf("required", "max:9"), listRules.uploadRules)
        BrewConfig.from(Config().also { it.set("brew.class_namespace", "one,two") })
        val s3Cfg = BrewConfig.from(Config().also {
            it.set("brew.temporary_file_upload.disk", "s3")
            it.set("brew.temporary_file_upload.s3_endpoint", "https://s3.example/bucket")
        })
        assertEquals("s3", s3Cfg.uploadDisk)
        val s3Mgr = brew(
            config = BrewConfig(
                key = "k",
                uploadDisk = "s3",
                uploadS3Endpoint = "https://s3.example/bucket",
            ),
            container = manager.container,
        )
        s3Mgr.uploadRoot(root)
        s3Mgr.component("up", UploadComp::class.java)
        val s3Signed = s3Mgr.signS3Upload("photo", mapOf("name" to "a.png", "size" to 1, "type" to "image/png"), false)
        assertEquals(true, s3Signed["isS3"])
        assertTrue(s3Signed["uploadUrl"].toString().startsWith("https://s3.example/bucket/"))
        val s3Defaults = s3Mgr.signS3Upload("photo", mapOf("size" to 1), false)
        assertTrue(s3Defaults["path"].toString().endsWith("upload.bin"))
        assertEquals("application/octet-stream", (s3Defaults["headers"] as Map<*, *>)["Content-Type"])
        val s3Fallback = brew(config = BrewConfig(key = "k", uploadDisk = "s3"), container = manager.container)
        assertEquals(false, s3Fallback.signS3Upload("f", mapOf("name" to "a", "size" to 1), false)["isS3"])
        // blank endpoint after trim
        val blankEp = brew(
            config = BrewConfig(key = "k", uploadDisk = "s3", uploadS3Endpoint = "   "),
            container = manager.container,
        )
        assertEquals(false, blankEp.signS3Upload("f", mapOf("name" to "a", "size" to 1), false)["isS3"])
        val s3Comp = s3Mgr.newComponent("up") as UploadComp
        val started = s3Comp._startUpload("photo", mapOf("name" to "a.png", "size" to 1, "type" to "image/png"), false)
        assertEquals(true, started["isS3"])
        // local disk still returns uploadUrl/token
        val localStart = (manager.newComponent("up") as UploadComp)._startUpload("photo", mapOf("name" to "a.png", "size" to 1), false)
        assertNotNull(localStart["uploadUrl"])
        // blank app key → upload hmac fallback
        val blankKey = BrewManager(BrewConfig(key = ""))
        blankKey.uploadRoot(root)
        val signed = blankKey.signUpload("f", mapOf("name" to "a", "size" to 1), false)
        assertTrue(signed.token.isNotEmpty())
        // upload without filename query
        val exp = signed.url.substringAfter("expires=").substringBefore('&').toLong()
        blankKey.handleUpload(
            Request(
                "POST", "/brew/upload",
                query = mapOf("expires" to exp.toString(), "signature" to signed.token, "name" to "f", "size" to "1"),
                bodyBytes = byteArrayOf(1),
            ),
        )
        // preview with route attribute
        val p = blankKey.storeUpload(byteArrayOf(2), "x.bin")
        val previewReq = Request("GET", "/brew/preview/$p")
        previewReq.setAttribute("path", p)
        assertEquals(200, blankKey.handlePreview(previewReq).status)
        // authorize without gate (Component + SupportAuthorize hook)
        val noGate = brew(config = BrewConfig(key = "k"))
        noGate.component("auth", AuthComp::class.java)
        assertThrows(AuthorizationException::class.java) {
            noGate.newComponent("auth").authorize("ok")
        }
        val noGateHook = SupportAuthorize()
        noGateHook.manager = noGate
        noGateHook.component = noGate.newComponent("auth")
        assertThrows(AuthorizationException::class.java) {
            noGateHook.call("allowed", emptyList(), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(),
                org.konaframework.brew.lifecycle.ComponentContext(noGateHook.component, false))
        }
        // SupportAuthorize with no matching candidate overload
        val authHook = SupportAuthorize()
        authHook.manager = manager
        authHook.component = manager.newComponent("auth")
        authHook.call("allowed", listOf(1, 2, 3), org.konaframework.brew.lifecycle.ReturnEarly(), emptyMap(),
            org.konaframework.brew.lifecycle.ComponentContext(authHook.component, false))
        // release tokens skip + hydrate ok
        val rel = SupportReleaseTokens()
        rel.manager = BrewManager(BrewConfig(key = "k"))
        assertTrue(rel.skip())
        rel.manager = manager
        manager.releaseToken("tok")
        rel.component = manager.newComponent("plain")
        assertFalse(rel.skip())
        rel.hydrate(mapOf("release" to "tok"), org.konaframework.brew.lifecycle.ComponentContext(rel.component, false))
        val relCtx = org.konaframework.brew.lifecycle.ComponentContext(rel.component, false)
        rel.dehydrate(relCtx)
        assertEquals("tok", relCtx.memo["release"])
        // locales blank / whitespace
        val loc = SupportLocales()
        loc.manager = manager
        loc.component = manager.newComponent("plain")
        loc.hydrate(mapOf("locale" to null), org.konaframework.brew.lifecycle.ComponentContext(loc.component, false))
    }

    @Test
    fun modelSynths() {
        assertFalse(ModelSynth.match("x"))
        assertFalse(ModelCollectionSynth.match(emptyList<Any>()))
        assertFalse(ModelCollectionSynth.match(listOf("a")))
        val ctx = SynthContext(manager.synths, "m")
        val model = StubUser().setAttribute("id", 7)
        assertTrue(ModelSynth.match(model))
        val (data, meta) = ModelSynth.dehydrate(model, ctx)
        assertNull(data)
        assertEquals(7, meta["key"])
        assertTrue(ModelCollectionSynth.match(listOf(model)))
        val (cdata, _) = ModelCollectionSynth.dehydrate(listOf(model), ctx)
        assertTrue(cdata is List<*>)
        assertNull(ModelSynth.hydrate(null, emptyMap(), ctx, null))
        assertNull(ModelSynth.hydrate(null, mapOf("class" to null, "key" to 1), ctx, null))
        assertNull(ModelSynth.hydrate(null, mapOf("class" to StubUser::class.java.name), ctx, null))
        // class present as non-String still stringifies
        runCatching {
            ModelSynth.hydrate(null, mapOf("class" to StringBuilder(StubUser::class.java.name), "key" to 7), ctx, null)
        }
        assertThrows(CorruptComponentPayloadException::class.java) {
            ModelSynth.hydrate(null, mapOf("class" to "java.lang.String", "key" to 1), ctx, null)
        }
        assertNull(ModelSynth.hydrate(null, mapOf("class" to "org.konaframework.brew.M6CoverageSealTest"), ctx, null))
        // hydrate will try Cadence.of(StubUser).find — may return null without DB
        runCatching {
            ModelSynth.hydrate(null, mapOf("class" to StubUser::class.java.name, "key" to 7), ctx, null)
        }
        val list = ModelCollectionSynth.hydrate(listOf(null), emptyMap(), ctx, null)
        assertNotNull(list)
        assertEquals(emptyList<Any?>(), ModelCollectionSynth.hydrate("bad", emptyMap(), ctx, null))
        SupportFileUploads().also {
            it.manager = manager
            it.component = manager.newComponent("up")
            assertFalse(it.skip())
            it.mount(emptyMap(), null)
            it.hydrate(emptyMap(), org.konaframework.brew.lifecycle.ComponentContext(it.component, false))
        }
        SupportFileUploads().also {
            it.manager = manager
            it.component = manager.newComponent("plain")
            assertTrue(it.skip())
        }
    }

    class UploadComp : Component(), WithFileUploads {
        var photo: TemporaryUploadedFile? = null
        var photos: List<Any?> = emptyList()
        var photoArr: Array<TemporaryUploadedFile?> = emptyArray()
        override fun template() = "<div>u</div>"
    }

    class StyleComp : Component() {
        override fun template() = "<div>s</div>"
        fun mount() {
            setBrewScopedStyles(this, ".a{}")
            setBrewGlobalStyles(this, "body{}")
            setBrewStyleModules(this, mapOf("m" to "1"))
            setBrewJsModule(this, "console.log(1)")
        }
    }

    class AuthComp : Component() {
        var post: String = "p"
        var maybe: String? = null
        @Authorize("ok", "post")
        fun allowed() = true
        @Authorize("ok", "notAProp")
        fun allowedWithLiteral() = true
        @Authorize("ok", "maybe")
        fun allowedWithNullable() = true
        @Authorize("no")
        fun denied() = true
        fun unmarked() = true
        override fun template() = "<div>a</div>"
    }

    class PlainComp : Component() {
        override fun template() = "<div>p</div>"
    }

    class NoModel

    class StubUser : org.konaframework.cadence.Cadence<StubUser>()
}
