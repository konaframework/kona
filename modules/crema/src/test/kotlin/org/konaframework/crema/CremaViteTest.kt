package org.konaframework.crema

import org.konaframework.core.Application
import org.konaframework.crema.assets.AssetManifest
import org.konaframework.crema.assets.Vite
import org.konaframework.crema.assets.ViteException
import org.konaframework.http.HttpKernel
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Request
import org.konaframework.http.ServePublicDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class CremaViteTest {
    @AfterEach
    fun reset() {
        Vite.reset()
    }

    @Test
    fun viteTagsFallbackWithoutManifest() {
        assertEquals(
            """<script type="module" src="/build/resources/js/app.js"></script>""",
            ViteTags.tag("resources/js/app.js"),
        )
        assertTrue(ViteTags.tags("a.js", "b.css").contains("stylesheet"))
    }

    @Test
    fun cremaProviderWiresHotAndManifest(@TempDir base: Path) {
        val public = base.resolve("public")
        Files.createDirectories(public.resolve("build"))
        Files.writeString(public.resolve("hot"), "http://127.0.0.1:5173\n")
        Files.writeString(
            public.resolve("build/manifest.json"),
            """{"resources/js/app.js":{"file":"assets/app.js","css":["assets/app.css"]}}""",
        )
        val app = Application.builder(base)
            .withProvider(CremaServiceProvider())
            .boot()
        assertTrue(app.resolve(AssetManifest::class.java).hotUrl()!!.contains("5173"))
        val tags = ViteTags.tag("resources/js/app.js")
        assertTrue(tags.contains("@vite/client"), tags)
        assertTrue(tags.contains("resources/js/app.js"), tags)

        Vite.reset()
        Files.delete(public.resolve("hot"))
        Vite.setManifest(AssetManifest(public))
        val prod = ViteTags.tags("resources/js/app.js")
        assertTrue(prod.contains("/build/assets/app.css"), prod)
        assertTrue(prod.contains("/build/assets/app.js"), prod)
    }

    @Test
    fun cremaProviderHonorsVitePublicPathConfig(@TempDir base: Path) {
        val custom = base.resolve("webroot")
        Files.createDirectories(custom.resolve("build"))
        Files.writeString(
            custom.resolve("build/manifest.json"),
            """{"app.js":{"file":"assets/x.js","css":[]}}""",
        )
        val relative = Application.builder(base)
            .configure { it.set("vite.public_path", "webroot") }
            .withProvider(CremaServiceProvider())
            .boot()
        assertEquals(custom.toAbsolutePath().normalize(), relative.resolve(AssetManifest::class.java).manifestPath().parent.parent)

        val absolute = Application.builder(base.resolve("abs"))
            .configure { it.set("vite.public_path", custom.toAbsolutePath().toString()) }
            .withProvider(CremaServiceProvider())
            .boot()
        assertTrue(absolute.resolve(AssetManifest::class.java).loadManifest().containsKey("app.js"))

        val inertiaKey = Application.builder(base.resolve("inertia-key"))
            .configure { it.set("inertia.public_path", custom.toAbsolutePath().toString()) }
            .withProvider(CremaServiceProvider())
            .boot()
        assertTrue(inertiaKey.resolve(AssetManifest::class.java).loadManifest().containsKey("app.js"))
    }

    @Test
    fun engineRendersViteDirectiveWithManifest(@TempDir base: Path) {
        val public = base.resolve("public")
        Files.createDirectories(public.resolve("build"))
        Files.writeString(
            public.resolve("build/manifest.json"),
            """{"resources/js/app.js":{"file":"assets/app-hash.js","css":[]}}""",
        )
        Vite.setManifest(AssetManifest(public))
        val engine = CremaEngine(
            MapViewFinder().put("page", "<html>@vite('resources/js/app.js')</html>"),
        )
        val html = engine.render("page", emptyMap())
        assertTrue(html.contains("app-hash.js"), html)
    }

    @Test
    fun devServerAndHashedManifestDoNotPrefixSourcePath(@TempDir base: Path) {
        val public = base.resolve("public")
        Files.createDirectories(public)
        val entry = "src/main/resources/js/app.js"
        val bogus = "/build/$entry"
        Files.writeString(public.resolve("hot"), "http://127.0.0.1:5173/\n")
        val devManifest = AssetManifest(public)
        val dev = devManifest.tags(entry, "src/main/resources/css/app.css")
        assertTrue(dev.contains("""src="http://127.0.0.1:5173/@vite/client""""), dev)
        assertTrue(dev.contains("""src="http://127.0.0.1:5173/$entry""""), dev)
        assertTrue(dev.contains("""href="http://127.0.0.1:5173/src/main/resources/css/app.css""""), dev)
        assertFalse(dev.contains(bogus), dev)
        assertEquals("http://127.0.0.1:5173/$entry", devManifest.assetUrl("./$entry"))

        Files.writeString(public.resolve("hot"), "   ")
        Files.createDirectories(public.resolve("build/.vite"))
        Files.writeString(
            public.resolve("build/.vite/manifest.json"),
            """{"$entry":{"file":"assets/from-vite-dir.js","isEntry":true}}""",
        )
        val nested = AssetManifest(public)
        assertTrue(nested.manifestPath().endsWith(".vite/manifest.json"))
        val fromNested = nested.tags(entry)
        assertTrue(fromNested.contains("/build/assets/from-vite-dir.js"), fromNested)
        assertFalse(fromNested.contains(bogus), fromNested)

        Files.writeString(
            public.resolve("build/manifest.json"),
            manifestJson(),
        )
        val prodManifest = AssetManifest(public)
        assertTrue(prodManifest.manifestPath().endsWith("build/manifest.json"))
        assertFalse(prodManifest.manifestPath().toString().contains(".vite"))
        Vite.setManifest(prodManifest)
        val prod = ViteTags.tags(entry)
        assertTrue(prod.contains("/build/assets/app-Ab12.js"), prod)
        assertTrue(prod.contains("/build/assets/app-Cd34.css"), prod)
        assertTrue(prod.contains("/build/assets/vendor-Gh78.css"), prod)
        assertFalse(prod.contains(bogus), prod)
        assertEquals("/build/assets/app-Ab12.js", Vite.asset(" /$entry"))
        assertEquals("/build/assets/aliased-Zz.js", Vite.asset("resources/js/aliased.js"))
        assertEquals("/build/assets/win.js", Vite.asset("src/main/resources/js/win.js"))
        assertEquals("/build/assets/abs.js", Vite.asset("src/main/resources/js/abs.js"))
        assertEquals("assets/app-Ab12.js", prodManifest.entry(entry)["file"])

        val css = Vite.tags("style.css")
        assertEquals("""<link rel="stylesheet" href="/build/assets/style-Ij90.css">""", css)
        val dup = Vite.tags("dup.css")
        assertEquals("""<link rel="stylesheet" href="/build/assets/dup.css">""", dup)
        val prefixed = Vite.tags("plain.css")
        assertEquals("""<link rel="stylesheet" href="/build/assets/plain.css">""", prefixed)
        assertTrue(Vite.tags("lonely.js").contains("/build/assets/lonely.js"))
        assertThrows(ViteException::class.java) { Vite.asset("no-such.js") }
        val broken = AssetManifest(base.resolve("empty-public"))
        assertThrows(ViteException::class.java) { broken.loadManifest() }
        Files.createDirectories(base.resolve("bad/build"))
        Files.writeString(base.resolve("bad/build/manifest.json"), "[1]")
        assertThrows(ViteException::class.java) { AssetManifest(base.resolve("bad")).loadManifest() }
        val noFile = base.resolve("nofile/build")
        Files.createDirectories(noFile)
        Files.writeString(noFile.resolve("manifest.json"), """{"entry.js":{"src":"entry.js"}}""")
        val noFileManifest = AssetManifest(base.resolve("nofile"))
        assertThrows(ViteException::class.java) { noFileManifest.tags("entry.js") }
        assertThrows(ViteException::class.java) { noFileManifest.assetUrl("entry.js") }

        val engine = CremaEngine(MapViewFinder().put("page", "@vite('$entry')"))
        val html = engine.render("page", emptyMap())
        assertTrue(html.contains("/build/assets/app-Ab12.js"), html)
        assertFalse(html.contains(bogus), html)
    }

    @Test
    fun httpServesHashedBuildAndSkipsDuplicateRoot(@TempDir base: Path) {
        val public = base.resolve("public")
        val asset = public.resolve("build/assets/app-Ab12.js")
        Files.createDirectories(asset.parent)
        Files.writeString(asset, "console.log('spa')")
        Files.writeString(
            public.resolve("build/manifest.json"),
            """{"src/main/resources/js/app.js":{"file":"assets/app-Ab12.js","isEntry":true}}""",
        )
        val app = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(CremaServiceProvider())
            .boot()
        val kernel = app.resolve(HttpKernel::class.java)
        val served = kernel.handle(Request.get("/build/assets/app-Ab12.js"))
        assertEquals(200, served.status)
        assertEquals("console.log('spa')", served.bodyBytes().toString(StandardCharsets.UTF_8))
        assertTrue(served.headers["Content-Type"]!!.contains("javascript"))
        assertEquals(404, kernel.handle(Request.get("/build/src/main/resources/js/app.js")).status)

        val before = kernel.globalMiddleware.count { it is ServePublicDirectory }
        CremaServiceProvider.ensureViteAssetsServed(app)
        assertEquals(before, kernel.globalMiddleware.count { it is ServePublicDirectory })

        val bare = Application.builder(base.resolve("bare"))
            .withProvider(HttpServiceProvider())
            .boot()
        val bareKernel = bare.resolve(HttpKernel::class.java)
        val other = base.resolve("other-public")
        bareKernel.prependMiddleware(ServePublicDirectory(other))
        CremaServiceProvider.ensureViteAssetsServed(bare)
        val dirs = bareKernel.globalMiddleware.filterIsInstance<ServePublicDirectory>()
        assertEquals(2, dirs.size)
        assertTrue(dirs.any { it.serves(bare.basePath.resolve("public")) })
        assertTrue(dirs.any { it.serves(other) })
        assertFalse(ServePublicDirectory(other).serves(bare.basePath.resolve("public")))
    }

    private fun manifestJson(): String =
        """
        {
          "src/main/resources/js/app.js": {
            "file": "assets/app-Ab12.js",
            "src": "src/main/resources/js/app.js",
            "isEntry": true,
            "css": ["assets/app-Cd34.css", null],
            "imports": ["vendor.js"]
          },
          "vendor.js": {
            "file": "assets/vendor-Ef56.js",
            "css": ["assets/vendor-Gh78.css"],
            "imports": ["src/main/resources/js/app.js"]
          },
          "style.css": {"file": "assets/style-Ij90.css"},
          "dup.css": {"file": "assets/dup.css", "css": ["assets/dup.css"]},
          "plain.css": {"file": "build/assets/plain.css", "css": "not-a-list", "imports": "not-a-list"},
          "lonely.js": {"file": "assets/lonely.js", "imports": ["gone.js"]},
          "_entry": {"file": "assets/aliased-Zz.js", "src": "resources/js/aliased.js"},
          "other.js": {"file": "assets/other.js", "src": 1},
          "src\\main\\resources\\js\\win.js": {"file": "assets/win.js"},
          "/opt/app/src/main/resources/js/abs.js": {"file": "assets/abs.js"}
        }
        """.trimIndent()
}
