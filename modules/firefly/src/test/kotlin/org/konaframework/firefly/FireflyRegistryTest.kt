package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.core.Application
import org.konaframework.core.Config
import org.konaframework.core.Kona
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Router
import java.nio.file.Files
import java.nio.file.Path

class FireflyRegistryTest {
    @AfterEach
    fun resetRegistry() {
        Firefly.reset()
        Kona.reset()
    }

    @Test
    fun unsetPathUsesThePanelId() {
        val billing = Router()
        Panel().id("billing").authenticate(false).page(ListPage("orders")).mount(billing)
        assertNotNull(billing.find("GET", "/billing/orders"))
        assertNull(billing.find("GET", "/admin/orders"))

        val admin = Router()
        Panel().authenticate(false).page(ListPage("orders")).mount(admin)
        assertNotNull(admin.find("GET", "/admin/orders"))

        val explicit = Router()
        Panel().id("billing").path("admin").authenticate(false).page(ListPage("orders")).mount(explicit)
        assertNotNull(explicit.find("GET", "/admin/orders"))

        val root = Router()
        Panel().id("ops").path("").authenticate(false).page(ListPage("home")).mount(root)
        assertNotNull(root.find("GET", "/home"))

        val closure = Router()
        Panel().id("billing").path { "staff" }.authenticate(false).page(ListPage("orders")).mount(closure)
        assertNotNull(closure.find("GET", "/staff/orders"))

        val named = Router()
        Panel().id { "ops" }.authenticate(false).page(ListPage("orders")).mount(named)
        assertNotNull(named.find("GET", "/ops/orders"))
    }

    @Test
    fun configReadsDiscoveryPaths() {
        val config = Config()
        assertEquals(emptyList<String>(), FireflyConfig.discovery(config))
        config.set("firefly.discovery", listOf("extra", " ", null, ""))
        assertEquals(listOf("extra"), FireflyConfig.discovery(config))
        config.set("firefly.discovery", "a, b,")
        assertEquals(listOf("a", "b"), FireflyConfig.discovery(config))
        config.set("firefly.discovery", 4)
        assertEquals(emptyList<String>(), FireflyConfig.discovery(config))
    }

    @Test
    fun providerDiscoversPanelsAndSkipsAClassAlreadyRegistered() {
        val base = Files.createTempDirectory("firefly-discover")
        val open = base.resolve("src/main/kotlin/app/panels/open")
        Files.createDirectories(open.resolve("pages"))
        Files.writeString(
            open.resolve("OpenPanel.kt"),
            "package org.konaframework.firefly\nclass OpenPanel : Panel()\n",
        )
        Files.writeString(
            open.resolve("pages/FoundPage.kt"),
            "package org.konaframework.firefly.discovered\nclass FoundPage : PanelPage(\"found\")\n",
        )
        val home = Files.createTempDirectory("firefly-home")
        Files.createDirectories(home.resolve("pages"))
        Files.writeString(
            home.resolve("HomePanel.kt"),
            "package org.konaframework.firefly\nclass HomePanel : Panel()\n",
        )
        Files.writeString(
            home.resolve("pages/FoundPage.kt"),
            "package org.konaframework.firefly.discovered\nclass FoundPage : PanelPage(\"found\")\n",
        )
        val empty = Files.createTempDirectory("firefly-empty-home")
        HomePanel.homeDiscover = empty
        val billing = Files.createTempDirectory("firefly-billing")
        Files.writeString(
            billing.resolve("BillingPanel.kt"),
            "package org.konaframework.firefly\nclass BillingPanel : Panel()\n",
        )
        Files.writeString(
            billing.resolve("NotAPanel.kt"),
            "package org.konaframework.firefly\nclass NotAPanel\n",
        )
        Files.writeString(
            billing.resolve("SkipPanel.kt"),
            "package org.konaframework.firefly\nabstract class SkipPanel : Panel()\n",
        )
        Files.writeString(billing.resolve("notes.txt"), "skip")
        Firefly.reset()
        Firefly.register(BillingPanel())
        val app = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .configure { config ->
                config.set(
                    "firefly.discovery",
                    listOf(home.toString(), billing.toString(), "src/main/kotlin/app/panels/open", " ", home.toString()),
                )
            }
            .build()
        val router = app.resolve(Router::class.java)
        assertNotNull(router.find("GET", "/billing/orders"))
        assertNotNull(router.find("GET", "/open/found"))
        assertNotNull(router.find("GET", "/home/here"))
        assertNull(router.find("GET", "/home/found"))
        Firefly.mount(app)
    }

    @Test
    fun discoveryRejectsAPathThatIsNotADirectory() {
        val base = Files.createTempDirectory("firefly-not-dir")
        val file = base.resolve("notes.txt")
        Files.writeString(file, "x")
        Firefly.reset()
        val failure = assertThrows<FireflyException> {
            Application.builder(base)
                .withProvider(HttpServiceProvider())
                .configure { it.set("firefly.discovery", listOf(file.toString())) }
                .build()
        }
        assertTrue(failure.message!!.contains("not a directory"))
    }

    @Test
    fun panelScanReportsMissingFilesAndBadSources() {
        val root = Files.createTempDirectory("firefly-scan")
        assertTrue(PanelDiscovery.panels(root.resolve("missing")).isEmpty())
        assertTrue(PanelDiscovery.panels(root.resolve("missing"), javaClass.classLoader).isEmpty())
        val file = Files.writeString(root.resolve("notes.txt"), "x")
        assertThrows<FireflyException> { PanelDiscovery.panels(file) }
        val bad = root.resolve("bad")
        Files.createDirectories(bad)
        Files.writeString(bad.resolve("Nope.kt"), "class Nope\n")
        val broken = assertThrows<FireflyException> { PanelDiscovery.panels(bad) }
        assertTrue(broken.message!!.contains("no package"))
    }

    /**
     * Desk layout: Panel.kt beside resources/, pages/, widgets/. Panel scan must not
     * Class.forName those children (uncompiled sources there used to crash serve).
     */
    @Test
    fun panelScanSkipsResourcesPagesAndWidgetsFolders() {
        val base = Files.createTempDirectory("firefly-skip-children")
        val admin = base.resolve("src/main/kotlin/app/panels/admin")
        Files.createDirectories(admin.resolve("pages"))
        Files.createDirectories(admin.resolve("resources"))
        Files.createDirectories(admin.resolve("widgets"))
        Files.writeString(
            admin.resolve("AdminDeskPanel.kt"),
            "package org.konaframework.firefly\nclass AdminDeskPanel : Panel()\n",
        )
        // Unloadable — CNFE if panel scan walked into these folders.
        Files.writeString(
            admin.resolve("resources/MissingResource.kt"),
            "package app.panels.admin.resources\nclass MissingResource\n",
        )
        Files.writeString(
            admin.resolve("pages/MissingPage.kt"),
            "package app.panels.admin.pages\nclass MissingPage\n",
        )
        Files.writeString(
            admin.resolve("widgets/MissingWidget.kt"),
            "package app.panels.admin.widgets\nclass MissingWidget\n",
        )
        val panels = PanelDiscovery.panels(base.resolve("src/main/kotlin/app/panels"), javaClass.classLoader)
        assertEquals(1, panels.size)
        assertTrue(panels.single() is AdminDeskPanel)
    }

    /** Resource under resources/ is adopted by the panel, never loaded as a Panel. */
    @Test
    fun bootsWithResourceUnderResourcesFolder() {
        val base = Files.createTempDirectory("firefly-with-resource")
        val admin = base.resolve("src/main/kotlin/app/panels/admin")
        Files.createDirectories(admin.resolve("resources"))
        Files.writeString(
            admin.resolve("AdminDeskPanel.kt"),
            "package org.konaframework.firefly\nclass AdminDeskPanel : Panel()\n",
        )
        Files.writeString(
            admin.resolve("resources/FoundResource.kt"),
            "package org.konaframework.firefly.discovered\nclass FoundResource : org.konaframework.firefly.Resource(\"notes\")\n",
        )
        Firefly.reset()
        val app = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(FireflyServiceProvider())
            .build()
        val router = app.resolve(Router::class.java)
        assertNotNull(router.find("GET", "/admin/login"))
        assertNotNull(router.find("GET", "/admin/notes/all"))
    }

    @Test
    fun mountWaitsUntilTheRouterIsBound() {
        val base = Files.createTempDirectory("firefly-router")
        Firefly.reset()
        val app = Application.builder(base).build()
        assertFalse(app.container.bound(Router::class.java))
        Firefly.register(Panel().id("billing").authenticate(false).page(ListPage("orders")))
        Firefly.mount(app)
        assertFalse(app.container.bound(Router::class.java))
        app.container.singleton(Router::class.java) { Router() }
        Firefly.mount(app)
        assertNotNull(app.resolve(Router::class.java).find("GET", "/billing/orders"))
        Firefly.mount(app)
    }

    @Test
    fun mountRegistersCachedFontAndWidgetAssets() {
        val base = Files.createTempDirectory("firefly-assets")
        Firefly.reset()
        val app = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(FireflyServiceProvider())
            .build()
        val router = app.resolve(Router::class.java)
        val interUri = "${org.konaframework.firefly.support.FireflyFonts.PREFIX}/${org.konaframework.firefly.support.FireflyFonts.INTER}"
        assertNotNull(router.find("GET", interUri))
        val inter = router.resolve("GET", interUri)
        val fontResponse = inter.route.action.handle(
            org.konaframework.http.Request("GET", interUri).also { req ->
                req.attributes.putAll(inter.parameters)
            },
        )
        assertEquals(200, fontResponse.status)
        assertEquals("font/woff2", fontResponse.headers["Content-Type"])
        assertTrue(fontResponse.headers["Cache-Control"]!!.contains("immutable"))
        assertTrue(fontResponse.bodyBytes().isNotEmpty())

        assertEquals(404, FireflyAssets.fontResponse("nope.woff2").status)
        assertEquals(404, FireflyAssets.widgetResponse("missing.js").status)
        val widget = FireflyAssets.widgetResponse("ff-widget.js")
        assertEquals(200, widget.status)
        assertTrue(widget.headers["Content-Type"]!!.contains("javascript"))
        assertTrue(widget.headers["Cache-Control"]!!.contains("immutable"))

        // Handler branches when the {file} param is absent.
        val missingParam = router.resolve("GET", interUri).route.action.handle(
            org.konaframework.http.Request("GET", interUri),
        )
        assertEquals(404, missingParam.status)

        val widgetUri = "/_firefly/widgets/ff-widget.js"
        val widgetMatch = router.resolve("GET", widgetUri)
        val widgetOk = widgetMatch.route.action.handle(
            org.konaframework.http.Request("GET", widgetUri).also { it.attributes.putAll(widgetMatch.parameters) },
        )
        assertEquals(200, widgetOk.status)
        val widgetMissingParam = widgetMatch.route.action.handle(org.konaframework.http.Request("GET", widgetUri))
        assertEquals(404, widgetMissingParam.status)

        org.konaframework.firefly.support.FireflyFonts.resetCache()
        org.konaframework.firefly.support.FireflyFonts.resourceDirectory =
            "/org/konaframework/firefly/support/fonts-missing/"
        assertEquals(
            404,
            FireflyAssets.fontResponse(org.konaframework.firefly.support.FireflyFonts.INTER).status,
        )
        org.konaframework.firefly.support.FireflyFonts.resourceDirectory =
            "/org/konaframework/firefly/support/fonts/"
        org.konaframework.firefly.support.FireflyFonts.resetCache()
    }

    /**
     * grind serve soft-reloads a new Application while Firefly stays in the parent loader.
     * Without prepare/reset, mounted stays true and the new router has no panel routes (404).
     */
    @Test
    fun remountsDiscoveredPanelsWhenANewApplicationBoots() {
        val base = Files.createTempDirectory("firefly-soft-reload")
        val admin = base.resolve("src/main/kotlin/app/panels/admin")
        Files.createDirectories(admin.resolve("pages"))
        Files.writeString(
            admin.resolve("AdminDeskPanel.kt"),
            "package org.konaframework.firefly\nclass AdminDeskPanel : Panel()\n",
        )
        Files.writeString(
            admin.resolve("pages/FoundPage.kt"),
            "package org.konaframework.firefly.discovered\nclass FoundPage : PanelPage(\"found\")\n",
        )
        Firefly.reset()
        val first = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(FireflyServiceProvider())
            .build()
        assertNotNull(first.resolve(Router::class.java).find("GET", "/admin/found"))

        val second = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(FireflyServiceProvider())
            .build()
        assertNotNull(second.resolve(Router::class.java).find("GET", "/admin/found"))
        assertNotNull(second.resolve(Router::class.java).find("GET", "/admin/login"))
    }

    /** Hand [Firefly.register] after prepare must survive; prior app manuals must not. */
    @Test
    fun prepareDropsPriorManualsBeforeNewRegistration() {
        Firefly.reset()
        val firstBase = Files.createTempDirectory("firefly-manual-first")
        val first = Application.builder(firstBase)
            .withProvider(HttpServiceProvider())
            .withProvider(
                object : FireflyServiceProvider() {
                    override fun register(app: Application) {
                        super.register(app)
                        Firefly.register(
                            Panel().id("billing").authenticate(false).page(ListPage("orders")),
                        )
                    }
                },
            )
            .build()
        assertNotNull(first.resolve(Router::class.java).find("GET", "/billing/orders"))

        val secondBase = Files.createTempDirectory("firefly-manual-second")
        val second = Application.builder(secondBase)
            .withProvider(HttpServiceProvider())
            .withProvider(
                object : FireflyServiceProvider() {
                    override fun register(app: Application) {
                        super.register(app)
                        Firefly.register(
                            Panel().id("ops").authenticate(false).page(ListPage("home")),
                        )
                    }
                },
            )
            .build()
        val router = second.resolve(Router::class.java)
        assertNotNull(router.find("GET", "/ops/home"))
        assertNull(router.find("GET", "/billing/orders"))
    }

    /**
     * FireflyServiceProvider boots panels during Application.boot, before Kona.onBooted
     * sets the facade. Panel constructors must not call Kona.app(); adoptHome supplies the
     * panel directory so pages/widgets/resources still load.
     */
    @Test
    fun providerDiscoversPanelsBeforeKonaAppFacadeIsReady() {
        val base = Files.createTempDirectory("firefly-no-kona")
        val admin = base.resolve("src/main/kotlin/app/panels/admin")
        Files.createDirectories(admin.resolve("pages"))
        Files.writeString(
            admin.resolve("AdminDeskPanel.kt"),
            "package org.konaframework.firefly\nclass AdminDeskPanel : Panel()\n",
        )
        Files.writeString(
            admin.resolve("pages/FoundPage.kt"),
            "package org.konaframework.firefly.discovered\nclass FoundPage : PanelPage(\"found\")\n",
        )
        Kona.reset()
        Firefly.reset()
        assertThrows<IllegalStateException> { Kona.app() }
        val app = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(FireflyServiceProvider())
            .build()
        assertThrows<IllegalStateException> { Kona.app() }
        val router = app.resolve(Router::class.java)
        assertNotNull(router.find("GET", "/admin/found"))
        assertNotNull(router.find("GET", "/admin/login"))
    }
}

class BillingPanel : Panel() {
    init {
        id("billing")
        authenticate(false)
        page(ListPage("orders"))
    }
}

class OpenPanel : Panel() {
    init {
        id("open")
        authenticate(false)
    }
}

/** Desk-shaped panel: id/brand only. Directory comes from [Panel.adoptHome] during discovery. */
class AdminDeskPanel : Panel() {
    init {
        id("admin")
        brand("Desk")
        authenticate(false)
    }
}

class HomePanel : Panel() {
    init {
        id("home")
        authenticate(false)
        discover(homeDiscover)
        page(ListPage("here"))
    }

    companion object {
        var homeDiscover: Path = Path.of("missing-home-panel-dir")
    }
}

class NotAPanel

abstract class SkipPanel : Panel()
