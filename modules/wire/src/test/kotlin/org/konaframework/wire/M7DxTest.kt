package org.konaframework.wire

import org.konaframework.wire.console.WireConvertCommand
import org.konaframework.wire.console.WireLayoutCommand
import org.konaframework.wire.console.WirePublishCommand
import org.konaframework.wire.console.WireStubsCommand
import org.konaframework.wire.console.BrewConvertCommand
import org.konaframework.wire.console.BrewLayoutCommand
import org.konaframework.wire.console.BrewPublishCommand
import org.konaframework.wire.console.BrewStubsCommand
import org.konaframework.wire.console.MakeBrewCommand
import org.konaframework.wire.console.MakeWireCommand
import org.konaframework.core.Config
import org.konaframework.grind.CommandExecutor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class M7DxTest {
    private lateinit var manager: WireManager

    @BeforeEach
    fun setUp() {
        manager = brew(config = WireConfig(key = "m7", makeType = "sfc"))
        Wire.setManager(manager)
        manager.component("m7", M7Comp::class.java)
    }

    @AfterEach
    fun tearDown() {
        Wire.reset()
    }

    private fun run(dir: Path, command: org.konaframework.grind.ConsoleCommand, vararg args: String): Int =
        CommandExecutor(dir).run(command, args.toList() + "--base=$dir")

    @Test
    fun makeBrewFlagsAndStubs(@TempDir dir: Path) {
        assertEquals(0, run(dir, WireStubsCommand()))
        assertTrue(Files.exists(dir.resolve("stubs/wire/component.kt.stub")))
        assertEquals(1, run(dir, WireStubsCommand()))
        assertEquals(0, run(dir, WireStubsCommand(), "--force"))

        assertEquals(0, run(dir, MakeWireCommand(), "pages::Home", "--mfc", "--test", "--emoji"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/wire/pages/Home.kt")))
        assertTrue(
            Files.walk(dir.resolve("src/main/resources/views")).use { stream ->
                stream.anyMatch { p -> p.fileName.toString().contains("⚡") || p.fileName.toString().contains("Home") }
            },
        )
        assertTrue(
            Files.walk(dir.resolve("src/main/kotlin/app/wire/pages")).use { stream ->
                stream.anyMatch { p -> p.fileName.toString().endsWith(".js") }
            },
        )
        // --mfc again with --force refreshes assets
        assertEquals(0, run(dir, MakeWireCommand(), "pages::Home", "--mfc", "--force"))

        assertEquals(0, run(dir, MakeWireCommand(), "widget", "--class"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/widget.crema.html")))

        // configured makeType=class via Brew manager
        val typed = brew(config = WireConfig(key = "t", makeType = "class"))
        Wire.setManager(typed)
        assertEquals(0, run(dir, MakeWireCommand(), "Configured"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/configured.crema.html")))
        // invalid makeType falls back to class
        Wire.setManager(brew(config = WireConfig(key = "t", makeType = "nope")))
        assertEquals(0, run(dir, MakeWireCommand(), "FallbackClass", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/kotlin/app/wire/FallbackClass.kt")).contains("render()"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/fallback-class.crema.html")))
        // no manager → class
        Wire.reset()
        assertEquals(0, run(dir, MakeWireCommand(), "NoMgr", "--force"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/no-mgr.crema.html")))
        Wire.setManager(manager)

        // Java stubs (SFC-shaped stub + --class → fallback) and matching stub
        Files.writeString(
            dir.resolve("stubs/wire/component.java.stub"),
            "package {{package}};\npublic class {{class}} extends org.konaframework.wire.Component { public String template(){return \"\";} }\n",
        )
        assertEquals(0, run(dir, MakeWireCommand(), "JavaStub", "--lang=java", "--class", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/java/app/wire/JavaStub.java")).contains("render()"))
        Files.writeString(
            dir.resolve("stubs/wire/component.kt.stub"),
            "package {{package}}\nclass {{class}} : org.konaframework.wire.Component() { override fun render() = view(\"{{view}}\") }\n",
        )
        assertEquals(0, run(dir, MakeWireCommand(), "KtStub", "--lang=kotlin", "--class", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/kotlin/app/wire/KtStub.kt")).contains("render()"))
    }

    @Test
    fun publishLayoutConvert(@TempDir dir: Path) {
        assertEquals(0, run(dir, WirePublishCommand(), "--config"))
        assertTrue(Files.exists(dir.resolve("config/wire.kona.kts")))
        assertEquals(1, run(dir, WirePublishCommand(), "--config"))
        assertEquals(0, run(dir, WirePublishCommand(), "--config", "--force"))
        assertEquals(0, run(dir, BrewPublishCommand(), "--config"))
        assertTrue(Files.exists(dir.resolve("config/brew.kona.kts")))
        assertEquals(0, run(dir, MakeBrewCommand(), "Aliased", "--force"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/wire/Aliased.kt")))
        assertEquals(0, run(dir, BrewConvertCommand(), "src/main/kotlin/app/wire/Aliased.kt", "--to=class", "--force"))

        assertEquals(0, run(dir, WireLayoutCommand()))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/layouts/app.crema.html")))
        assertEquals(1, run(dir, WireLayoutCommand()))
        assertEquals(0, run(dir, WireLayoutCommand(), "--force"))
        assertEquals(0, run(dir, BrewLayoutCommand(), "--force"))
        assertEquals(0, run(dir, BrewStubsCommand(), "--force"))
        assertEquals(0, run(dir, WireLayoutCommand(), "--name=layouts.admin", "--force"))

        assertEquals(0, run(dir, MakeWireCommand(), "Toggle"))
        val rel = "src/main/kotlin/app/wire/Toggle.kt"
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=class", "--force"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/toggle.crema.html")))
        // already class-style (no template) → success no-op
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=class"))
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=sfc"))
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=sfc")) // already sfc
        // class conversion without force when view exists
        assertEquals(1, run(dir, WireConvertCommand(), rel, "--to=class"))
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=mfc", "--force"))
        assertEquals(1, run(dir, WireConvertCommand(), rel, "--to=mfc")) // js+css exist
        // only css present
        val mfcDir = dir.resolve("src/main/kotlin/app/wire/toggle")
        Files.deleteIfExists(mfcDir.resolve("Toggle.js"))
        assertEquals(1, run(dir, WireConvertCommand(), rel, "--to=mfc"))
        Files.deleteIfExists(mfcDir.resolve("Toggle.css"))
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=mfc"))
        assertEquals(1, run(dir, WireConvertCommand(), "missing.kt", "--to=class"))
        assertEquals(1, run(dir, WireConvertCommand(), rel, "--to=nope"))
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=class", "--force"))
        // view exists + no force while template present
        assertEquals(0, run(dir, WireConvertCommand(), rel, "--to=sfc", "--force"))
        assertEquals(1, run(dir, WireConvertCommand(), rel, "--to=class"))
        // force=false and view missing hits the else branch
        assertEquals(0, run(dir, MakeWireCommand(), "Fresh", "--force"))
        assertEquals(0, run(dir, WireConvertCommand(), "src/main/kotlin/app/wire/Fresh.kt", "--to=class"))

        // Java convert paths
        assertEquals(0, run(dir, MakeWireCommand(), "JavaToggle", "--lang=java", "--force"))
        val jrel = "src/main/java/app/wire/JavaToggle.java"
        assertEquals(0, run(dir, WireConvertCommand(), jrel, "--to=class", "--force"))
        assertEquals(0, run(dir, WireConvertCommand(), jrel, "--to=sfc"))
        Files.writeString(
            dir.resolve(jrel),
            "package app.wire;\npublic class JavaToggle extends org.konaframework.wire.Component { public Object render(){return view(\"brew.x\");} }\n",
        )
        assertEquals(0, run(dir, WireConvertCommand(), jrel, "--to=class"))
        assertEquals(0, run(dir, WireConvertCommand(), jrel, "--to=sfc"))
    }

    @Test
    fun configKeysAndTestable() {
        val cfg = WireConfig.from(Config().also {
            it.set("brew.make.type", "mfc")
            it.set("brew.render_on_redirect", true)
            it.set("brew.legacy_model_binding", true)
            it.set("brew.smart_wire_keys", false)
            it.set("brew.lazy_placeholder", "<div>…</div>")
            it.set("brew.release_token", "tok")
            it.set("brew.csp_safe", true)
            it.set("brew.layout", "layouts.admin")
        })
        assertEquals("mfc", cfg.makeType)
        assertTrue(cfg.renderOnRedirect)
        assertTrue(cfg.legacyModelBinding)
        assertFalse(cfg.smartWireKeys)
        assertEquals("<div>…</div>", cfg.lazyPlaceholder)
        assertEquals("layouts.admin", cfg.layout)
        assertTrue(cfg.cspSafe)

        val t = Wire.test("m7")
            .actingAs("user-1")
            .withCookie("a", "1")
            .withCookies(mapOf("b" to "2"))
            .withHeaders(mapOf("X-Test" to "yes"))
            .withoutLazyLoading()
            .assertNoRedirect()
            .assertNoJs()
        assertEquals("user-1", t.actor())
        assertEquals("1", t.cookies()["a"])
        assertEquals("yes", t.headers()["X-Test"])
        t.call("ping").assertSet("n", 1)
        t.call("go").assertRedirect().assertRedirectTo("/next")
        assertThrows(AssertionError::class.java) { t.assertNoRedirect() }
        t.call("script").assertJs("alert").assertNoJs("nope")
        assertThrows(AssertionError::class.java) { t.assertNoJs() }
        assertThrows(AssertionError::class.java) { t.assertNoJs("alert") }
        assertThrows(AssertionError::class.java) { Wire.test("m7").assertRedirect() }
        assertThrows(AssertionError::class.java) { Wire.test("m7").assertRedirectTo("/x") }
        assertThrows(AssertionError::class.java) { Wire.test("m7").assertJs("x") }
        Wire.test("m7").assertNoJs()
        Wire.test("m7").assertNoJs(null)
        // Java-callable no-arg overload
        Wire.test("m7").javaClass.getMethod("assertNoJs").invoke(Wire.test("m7"))
    }

    class M7Comp : Component() {
        var n = 0
        fun ping() {
            n++
        }
        fun go() {
            redirect("/next")
        }
        fun script() {
            js("alert(1)")
        }
        override fun template() = "<div>{{ n }}</div>"
    }
}
