package dev.kona.crema

import dev.kona.core.Application
import dev.kona.crema.assets.AssetManifest
import dev.kona.crema.assets.Vite
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
}
