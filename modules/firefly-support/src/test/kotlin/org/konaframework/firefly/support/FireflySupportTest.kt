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
        assertTrue(css.contains(".ff-panel .ff-table-wrap"))
        assertTrue(css.contains(".ff-panel .ff-table-search-wrap"))
        assertTrue(css.contains(".ff-panel .ff-pagination"))
        assertTrue(css.contains(".ff-panel .ff-page-header-actions > .ff-schema"))
        assertTrue(css.contains("flex-direction: row"))
        assertTrue(css.contains(".ff-panel .ff-sidebar-brand"))
        assertTrue(css.contains(".ff-panel .ff-nav-item"))
        assertTrue(css.contains(".ff-panel .ff-page-heading"))
        assertTrue(css.contains(".ff-panel .ff-page-mode"))
        assertTrue(css.contains(".ff-panel .ff-action-outlined"))
        assertTrue(css.contains(".ff-panel .ff-row-actions"))
        assertTrue(css.contains("[data-ff-color=\"danger\"]"))
        // Sidebar + main foot share one shell band (not a white --ff-card strip),
        // locked to the same fixed min-height so empty vs "Signed in as …" match.
        assertTrue(css.contains(".ff-panel .ff-sidebar-foot"))
        assertTrue(css.contains(".ff-panel .ff-footer"))
        assertTrue(css.contains("--ff-footer-height:"))
        assertTrue(css.contains("min-height: var(--ff-footer-height)"))
        assertTrue(css.contains("background: var(--ff-shell)"))
        assertFalse(
            Regex("""\.ff-panel\s+\.ff-footer\s*\{[^}]*background:\s*var\(--ff-card\)""")
                .containsMatchIn(css),
        )
        // Dark tokens respond to both the self-class and an ancestor .ff-dark root.
        assertTrue(css.contains(".ff-dark .ff-panel"))
        assertTrue(css.contains(".ff-panel.ff-dark"))
        assertTrue(css.contains(".ff-auth-shell"))
        assertTrue(css.contains(".ff-column"))
        assertTrue(css.contains("--ff-primary: #DF8723"))
        assertTrue(css.contains(".ff-auth {") || css.contains(".ff-auth,"))
        assertTrue(css.contains("Inter"))
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
        val shell = Panel()
            .hook(Hook.SIDEBAR_START, "<b>nav</b>")
            .hook(Hook.FOOTER, "<i>foot</i>")
            .csrfToken("tok-123")
            .documentTitle("Desk")
            .brandName("Desk")
        val html = shell.render()
        assertTrue(html.contains("<!DOCTYPE html>", ignoreCase = true))
        assertTrue(html.contains("ff-panel"))
        assertTrue(html.contains("ff-column"))
        assertTrue(html.contains("ff-scroll"))
        assertTrue(html.contains("""name="csrf-token" content="tok-123""""))
        assertTrue(html.contains("<title>Desk</title>"))
        assertTrue(html.contains("<b>nav</b>"))
        assertTrue(html.contains("<i>foot</i>"))
        // Footer is pinned under the scroll region (sibling), not nested inside it.
        val scrollOpen = html.indexOf("""<div class="ff-scroll">""")
        val footerOpen = html.indexOf("""<footer class="ff-footer">""")
        assertTrue(scrollOpen >= 0 && footerOpen > scrollOpen)
        val afterScrollOpen = html.substring(scrollOpen)
        val scrollInnerEnd = afterScrollOpen.indexOf("</div>")
        assertTrue(scrollInnerEnd > 0)
        assertFalse(afterScrollOpen.substring(0, scrollInnerEnd).contains("""class="ff-footer""""))
        assertTrue(html.contains("ff-sidebar"))
        assertTrue(html.contains("background: var(--ff-surface)"))
        assertTrue(html.contains("fonts.googleapis.com"))
        assertTrue(html.contains("family=Inter"))
        // Dark no-flash init + chrome behavior scripts and the mobile drawer backdrop.
        assertTrue(html.contains("""<div class="ff-backdrop" data-ff-backdrop"""))
        assertTrue(html.contains("classList.toggle('ff-dark'"))
        assertTrue(html.contains("localStorage.getItem('ff-theme')"))
        assertTrue(html.contains("data-ff-sidebar-toggle"))
        assertTrue(html.contains("data-ff-menu-trigger"))
        assertTrue(html.contains(DefaultLayout.THEME_INIT_SCRIPT))
        assertEquals("", RenderHooks().render(Hook.BODY_START))

        val auth = Panel()
            .mode(ChromeMode.AUTH)
            .layout(AuthLayout())
            .brandName("Harbor")
            .documentTitle("Harbor · Sign in")
            .csrfToken("auth-tok")
            .overridePartial(
                "body",
                """<main class="ff-auth-main"><!-- ff-hook:body.start --><div class="ff-auth-panel">form</div><!-- ff-hook:body.end --></main>""",
            )
            .render()
        assertTrue(auth.contains("ff-auth-shell"))
        assertTrue(auth.contains("ff-auth-atmosphere"))
        assertTrue(auth.contains(DefaultLayout.THEME_INIT_SCRIPT))
        assertTrue(auth.contains("Harbor"))
        assertTrue(auth.contains("""name="csrf-token" content="auth-tok""""))
        assertFalse(auth.contains("""class="ff-sidebar""""))
        assertFalse(auth.contains("""class="ff-panel""""))
        assertTrue(DefaultLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->x<!-- ff-hook:body.end --></main>"""),
                "",
                mode = ChromeMode.AUTH,
                brandName = "X",
                documentTitle = "X · Sign in",
            ),
        ).contains("ff-auth-shell"))

        val escapedAuth = AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->ok<!-- ff-hook:body.end --></main>"""),
                "/* css */",
                csrfToken = null,
                documentTitle = "",
                brandName = "A & B <tag> \"q\"",
                mode = ChromeMode.AUTH,
            ),
        )
        assertTrue(escapedAuth.contains("ff-auth-brand__name"))
        assertTrue(escapedAuth.contains("&amp;"))
        assertTrue(escapedAuth.contains("&lt;"))
        assertTrue(escapedAuth.contains("&quot;"))
        assertFalse(escapedAuth.contains("""name="csrf-token""""))
        assertTrue(escapedAuth.contains("<title>"))
        assertThrows(FireflyException::class.java) {
            AuthLayout().render(
                ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "", mode = ChromeMode.AUTH),
            )
        }

        val panelEscaped = Panel()
            .csrfToken("a&b<\" ")
            .documentTitle("")
            .brandName("Desk")
            .render()
        assertTrue(panelEscaped.contains("content=\"a&amp;b&lt;&quot; \""))
        assertTrue(panelEscaped.contains("<title>Firefly</title>"))

        val fourArg = ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "css")
        assertEquals(ChromeMode.PANEL, fourArg.mode)
        assertEquals(null, fourArg.csrfToken)
        assertEquals("Firefly", fourArg.documentTitle)
        assertEquals("Firefly", ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "css", "tok").documentTitle)
        assertEquals("T", ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "css", "tok", "T").documentTitle)
        assertEquals("B", ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "css", "tok", "T", "B").brandName)
        assertEquals(
            ChromeMode.AUTH,
            ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "css", "tok", "T", "B", ChromeMode.AUTH).mode,
        )
        assertFalse(Panel().csrfToken("").render().contains("""name="csrf-token""""))
        assertFalse(Panel().csrfToken("   ").render().contains("""name="csrf-token""""))
        val authBlankCsrf = AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->y<!-- ff-hook:body.end --></main>"""),
                "",
                csrfToken = "",
                documentTitle = "Title",
                brandName = "Brand",
                mode = ChromeMode.AUTH,
            ),
        )
        assertFalse(authBlankCsrf.contains("""name="csrf-token""""))
        val authCsrfEscaped = AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->y<!-- ff-hook:body.end --></main>"""),
                "",
                csrfToken = "x&y<\" ",
                documentTitle = "Title",
                brandName = "Brand",
                mode = ChromeMode.AUTH,
            ),
        )
        assertTrue(authCsrfEscaped.contains("content=\"x&amp;y&lt;&quot; \""))

        val bad = object : FireflyTheme {
            override fun id() = "bad"
            override fun tokens() = KonaTheme().tokens()
            override fun partial(slot: String) = if (slot == "sidebar") "<aside></aside>" else null
        }
        assertThrows(FireflyException::class.java) { Panel().theme(bad).render() }

        val customPartial = object : FireflyTheme {
            override fun id() = "partial"
            override fun tokens() = KonaTheme().tokens()
            override fun partial(slot: String) =
                if (slot == "topbar") """<header class="ff-topbar"><!-- ff-hook:topbar.start -->T<!-- ff-hook:topbar.end --></header>""" else null
        }
        val withTheme = Panel().theme(customPartial).hook(Hook.TOPBAR_START, "L").render()
        assertTrue(withTheme.contains(">L"))
        assertTrue(withTheme.contains("T"))

        val overridden = Panel()
            .theme(customPartial)
            .overridePartial("topbar", """<header class="ff-topbar"><!-- ff-hook:topbar.start -->APP<!-- ff-hook:topbar.end --></header>""")
            .render()
        assertTrue(overridden.contains("APP"))
        assertFalse(overridden.contains(">T<"))

        val hooks = RenderHooks().add(Hook.FOOTER, "a").add(Hook.FOOTER, "b")
        assertEquals("ab", hooks.render(Hook.FOOTER))

        val replaced = Panel().layout(object : PanelLayout {
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
