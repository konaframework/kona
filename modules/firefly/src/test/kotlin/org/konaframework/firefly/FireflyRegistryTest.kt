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
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Router
import java.nio.file.Files
import java.nio.file.Path

class FireflyRegistryTest {
    @AfterEach
    fun resetRegistry() {
        Firefly.reset()
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
