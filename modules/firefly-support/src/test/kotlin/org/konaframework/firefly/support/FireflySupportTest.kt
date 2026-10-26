package org.konaframework.firefly.support

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FireflySupportTest {
    @AfterEach
    fun clearDialogDefaults() {
        Dialog.configureUsing(null)
    }

    @Test
    fun dialogModesWidthsAndDefaults() {
        val plain = Dialog.make("Hello").render()
        assertTrue(plain.contains("ff-dialog-modal"))
        assertTrue(plain.contains("""data-ff-align="top""""))
        assertTrue(plain.contains("data-ff-drag"))
        assertTrue(plain.contains("Hello"))

        val end = Dialog.make("Edit").slideOver().draggable(false).render()
        assertTrue(end.contains("ff-dialog-sidebar"))
        assertTrue(end.contains("""data-ff-position="end""""))
        assertFalse(end.contains("data-ff-drag"))

        val start = Dialog.make("Edit").slideOverStart().render()
        assertTrue(start.contains("""data-ff-position="start""""))

        val full = Dialog.make("All").fullscreen().draggable(true).width("screen").render()
        assertTrue(full.contains("ff-dialog-fullscreen"))
        assertTrue(full.contains("ff-dialog-screen"))
        assertFalse(full.contains("data-ff-drag"))
        assertFalse(full.contains("data-ff-align"))

        Dialog.configureUsing { dialog -> dialog.width("sm").body("<p>ok</p>") }
        val configured = Dialog.make("Saved").render()
        assertTrue(configured.contains("ff-dialog-sm"))
        assertTrue(configured.contains("<p>ok</p>"))

        val escaped = Dialog.make("""A & B <tag> "q"""").modal().render()
        assertTrue(escaped.contains("A &amp; B &lt;tag&gt; &quot;q&quot;"))

        val error = assertThrows(FireflyException::class.java) { Dialog.make("X").width("huge") }
        assertTrue(error.message!!.contains("huge"))
    }

    @Test
    fun iconsAndUnknownName() {
        listOf(Icon.HOME, Icon.BARS, Icon.USER, Icon.X).forEach { icon ->
            assertTrue(icon.svg().contains(icon.path))
        }
        assertEquals(Icon.HOME, Icon.fromName("HoMe"))
        assertEquals(Icon.BARS, Icon.fromName("bars"))
        assertEquals(Icon.USER, Icon.fromName("user"))
        assertEquals(Icon.X, Icon.fromName("x"))
        assertThrows(FireflyException::class.java) { Icon.fromName("nope") }
    }

    @Test
    fun stylesheetIsScopedAndThemesSupplyTokens() {
        val css = Stylesheet.render(KonaTheme())
        assertTrue(css.contains("--ff-primary: #DF8723"))
        assertTrue(css.contains("--ff-primary: #C9A57B"))
        assertTrue(css.contains(".ff-panel .ff-btn"))
        assertFalse(Regex("(?m)^\\s*body\\s*\\{").containsMatchIn(css))
        assertEquals("", KonaTheme().extraCss())
        assertEquals(null, KonaTheme().partial("sidebar"))

        val themed = object : FireflyTheme {
            override fun id() = "harbor"
            override fun tokens() = KonaTheme().tokens()
            override fun extraCss() = ".ff-btn { color: red; }"
        }
        assertTrue(Stylesheet.render(themed).contains("color: red"))

        val leak = object : FireflyTheme {
            override fun id() = "leak"
            override fun tokens() = KonaTheme().tokens()
            override fun extraCss() = "body { margin: 0 }"
        }
        assertThrows(FireflyException::class.java) { Stylesheet.render(leak) }
        assertThrows(FireflyException::class.java) { Stylesheet.readCss("/missing-firefly.css") }

        val source = listOf(
            Path.of("nope-firefly.css"),
            Path.of("modules/firefly-support/tailwind/input.css"),
            Path.of("tailwind/input.css"),
        ).first { Files.exists(it) }
        assertTrue(Files.readString(source).contains("tailwindcss"))
    }

    @Test
    fun chromeUsesThemeLayoutHooksAndOverrides() {
        val shell = PanelShell()
            .hook(Hook.SIDEBAR_START, "<b>nav</b>")
            .hook(Hook.FOOTER, "<i>foot</i>")
        val html = shell.render()
        assertTrue(html.contains("ff-panel"))
        assertTrue(html.contains("<b>nav</b>"))
        assertTrue(html.contains("<i>foot</i>"))
        assertTrue(html.contains("ff-sidebar"))
        assertEquals("", RenderHooks().render(Hook.BODY_START))

        val bad = object : FireflyTheme {
            override fun id() = "bad"
            override fun tokens() = KonaTheme().tokens()
            override fun partial(slot: String) = if (slot == "sidebar") "<aside></aside>" else null
        }
        assertThrows(FireflyException::class.java) { PanelShell().theme(bad).render() }

        val customPartial = object : FireflyTheme {
            override fun id() = "partial"
            override fun tokens() = KonaTheme().tokens()
            override fun partial(slot: String) =
                if (slot == "topbar") """<header class="ff-topbar"><!-- ff-hook:topbar.start -->T<!-- ff-hook:topbar.end --></header>""" else null
        }
        val withTheme = PanelShell().theme(customPartial).hook(Hook.TOPBAR_START, "L").render()
        assertTrue(withTheme.contains(">L"))
        assertTrue(withTheme.contains("T"))

        val overridden = PanelShell()
            .theme(customPartial)
            .overridePartial("topbar", """<header class="ff-topbar"><!-- ff-hook:topbar.start -->APP<!-- ff-hook:topbar.end --></header>""")
            .render()
        assertTrue(overridden.contains("APP"))
        assertFalse(overridden.contains(">T<"))

        val hooks = RenderHooks().add(Hook.FOOTER, "a").add(Hook.FOOTER, "b")
        assertEquals("ab", hooks.render(Hook.FOOTER))

        val replaced = PanelShell().layout(object : PanelLayout {
            override fun render(context: ChromeContext) = "layout:" + context.theme.id()
        }).render()
        assertEquals("layout:kona", replaced)

        assertEquals(emptyList<Hook>(), hooksFor("unknown"))
        assertTrue(requiredMarkers("sidebar").contains("<!-- ff-hook:sidebar.start -->"))
        assertThrows(FireflyException::class.java) { builtin("nope") }
        val empty = ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "")
        assertThrows(FireflyException::class.java) { DefaultLayout().render(empty) }
    }
}
