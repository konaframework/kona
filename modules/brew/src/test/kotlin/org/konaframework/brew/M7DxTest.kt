package org.konaframework.brew

import org.konaframework.brew.console.BrewConvertCommand
import org.konaframework.brew.console.BrewLayoutCommand
import org.konaframework.brew.console.BrewPublishCommand
import org.konaframework.brew.console.BrewStubsCommand
import org.konaframework.brew.console.MakeBrewCommand
import org.konaframework.core.Config
import org.konaframework.grind.CommandExecutor
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
    private lateinit var manager: BrewManager

    @BeforeEach
    fun setUp() {
        manager = brew(config = BrewConfig(key = "m7", makeType = "sfc"))
        Brew.setManager(manager)
        manager.component("m7", M7Comp::class.java)
    }

    private fun run(dir: Path, command: org.konaframework.grind.ConsoleCommand, vararg args: String): Int =
        CommandExecutor(dir).run(command, args.toList() + "--base=$dir")

    @Test
    fun makeBrewFlagsAndStubs(@TempDir dir: Path) {
        assertEquals(0, run(dir, BrewStubsCommand()))
        assertTrue(Files.exists(dir.resolve("stubs/brew/component.kt.stub")))
        assertEquals(1, run(dir, BrewStubsCommand()))
        assertEquals(0, run(dir, BrewStubsCommand(), "--force"))

        assertEquals(0, run(dir, MakeBrewCommand(), "pages::Home", "--mfc", "--test", "--emoji"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/brew/pages/Home.kt")))
        assertTrue(
            Files.walk(dir.resolve("src/main/resources/views")).use { stream ->
                stream.anyMatch { p -> p.fileName.toString().contains("⚡") || p.fileName.toString().contains("Home") }
            },
        )
        assertTrue(
            Files.walk(dir.resolve("src/main/kotlin/app/brew/pages")).use { stream ->
                stream.anyMatch { p -> p.fileName.toString().endsWith(".js") }
            },
        )
        // --mfc again with --force refreshes assets
        assertEquals(0, run(dir, MakeBrewCommand(), "pages::Home", "--mfc", "--force"))

        assertEquals(0, run(dir, MakeBrewCommand(), "widget", "--class"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/widget.crema.html")))

        // configured makeType=class via Brew manager
        val typed = brew(config = BrewConfig(key = "t", makeType = "class"))
        Brew.setManager(typed)
        assertEquals(0, run(dir, MakeBrewCommand(), "Configured"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/configured.crema.html")))
        // invalid makeType falls back to sfc
        Brew.setManager(brew(config = BrewConfig(key = "t", makeType = "nope")))
        assertEquals(0, run(dir, MakeBrewCommand(), "FallbackSfc", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/kotlin/app/brew/FallbackSfc.kt")).contains("template()"))
        // no manager → sfc
        Brew.reset()
        assertEquals(0, run(dir, MakeBrewCommand(), "NoMgr", "--force"))
        Brew.setManager(manager)

        // Java stubs (SFC-shaped stub + --class → fallback) and matching stub
        Files.writeString(
            dir.resolve("stubs/brew/component.java.stub"),
            "package {{package}};\npublic class {{class}} extends org.konaframework.brew.Component { public String template(){return \"\";} }\n",
        )
        assertEquals(0, run(dir, MakeBrewCommand(), "JavaStub", "--lang=java", "--class", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/java/app/brew/JavaStub.java")).contains("render()"))
        Files.writeString(
            dir.resolve("stubs/brew/component.kt.stub"),
            "package {{package}}\nclass {{class}} : org.konaframework.brew.Component() { override fun render() = view(\"{{view}}\") }\n",
        )
        assertEquals(0, run(dir, MakeBrewCommand(), "KtStub", "--lang=kotlin", "--class", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/kotlin/app/brew/KtStub.kt")).contains("render()"))
    }

    @Test
    fun publishLayoutConvert(@TempDir dir: Path) {
        assertEquals(0, run(dir, BrewPublishCommand(), "--config"))
        assertTrue(Files.exists(dir.resolve("config/brew.kona.kts")))
        assertEquals(1, run(dir, BrewPublishCommand(), "--config"))
        assertEquals(0, run(dir, BrewPublishCommand(), "--config", "--force"))

        assertEquals(0, run(dir, BrewLayoutCommand()))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/layouts/app.crema.html")))
        assertEquals(1, run(dir, BrewLayoutCommand()))
        assertEquals(0, run(dir, BrewLayoutCommand(), "--force"))
        assertEquals(0, run(dir, BrewLayoutCommand(), "--name=layouts.admin", "--force"))

        assertEquals(0, run(dir, MakeBrewCommand(), "Toggle"))
        val rel = "src/main/kotlin/app/brew/Toggle.kt"
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=class", "--force"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/toggle.crema.html")))
        // already class-style (no template) → success no-op
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=class"))
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=sfc"))
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=sfc")) // already sfc
        // class conversion without force when view exists
        assertEquals(1, run(dir, BrewConvertCommand(), rel, "--to=class"))
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=mfc", "--force"))
        assertEquals(1, run(dir, BrewConvertCommand(), rel, "--to=mfc")) // js+css exist
        // only css present
        val mfcDir = dir.resolve("src/main/kotlin/app/brew/toggle")
        Files.deleteIfExists(mfcDir.resolve("Toggle.js"))
        assertEquals(1, run(dir, BrewConvertCommand(), rel, "--to=mfc"))
        Files.deleteIfExists(mfcDir.resolve("Toggle.css"))
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=mfc"))
        assertEquals(1, run(dir, BrewConvertCommand(), "missing.kt", "--to=class"))
        assertEquals(1, run(dir, BrewConvertCommand(), rel, "--to=nope"))
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=class", "--force"))
        // view exists + no force while template present
        assertEquals(0, run(dir, BrewConvertCommand(), rel, "--to=sfc", "--force"))
        assertEquals(1, run(dir, BrewConvertCommand(), rel, "--to=class"))
        // force=false and view missing hits the else branch
        assertEquals(0, run(dir, MakeBrewCommand(), "Fresh", "--force"))
        assertEquals(0, run(dir, BrewConvertCommand(), "src/main/kotlin/app/brew/Fresh.kt", "--to=class"))

        // Java convert paths
        assertEquals(0, run(dir, MakeBrewCommand(), "JavaToggle", "--lang=java", "--force"))
        val jrel = "src/main/java/app/brew/JavaToggle.java"
        assertEquals(0, run(dir, BrewConvertCommand(), jrel, "--to=class", "--force"))
        assertEquals(0, run(dir, BrewConvertCommand(), jrel, "--to=sfc"))
        Files.writeString(
            dir.resolve(jrel),
            "package app.brew;\npublic class JavaToggle extends org.konaframework.brew.Component { public Object render(){return view(\"brew.x\");} }\n",
        )
        assertEquals(0, run(dir, BrewConvertCommand(), jrel, "--to=class"))
        assertEquals(0, run(dir, BrewConvertCommand(), jrel, "--to=sfc"))
    }

    @Test
    fun configKeysAndTestable() {
        val cfg = BrewConfig.from(Config().also {
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

        val t = Brew.test("m7")
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
        assertThrows(AssertionError::class.java) { Brew.test("m7").assertRedirect() }
        assertThrows(AssertionError::class.java) { Brew.test("m7").assertRedirectTo("/x") }
        assertThrows(AssertionError::class.java) { Brew.test("m7").assertJs("x") }
        Brew.test("m7").assertNoJs()
        Brew.test("m7").assertNoJs(null)
        // Java-callable no-arg overload
        Brew.test("m7").javaClass.getMethod("assertNoJs").invoke(Brew.test("m7"))
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
