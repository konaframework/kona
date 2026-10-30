package org.konaframework.firefly.support

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.MapViewFinder
import org.konaframework.crema.ViewNotFoundException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class FireflySupportTest {
    @AfterEach
    fun clearDialogDefaults() {
        Dialog.configureUsing(null)
    }

    @Test
    fun bundledStylesheetClosesEveryRule() {
        val css = Stylesheet.render(KonaTheme()).replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val open = ArrayList<String>()
        for (line in css.lines()) {
            val trimmed = line.trimStart()
            if (line == trimmed && trimmed.startsWith(".") && open.isNotEmpty() && !open.last().startsWith("@")) {
                throw AssertionError("rule [$trimmed] starts inside unclosed [${open.last()}]")
            }
            for (ch in line) {
                if (ch == '{') open.add(trimmed)
                if (ch == '}') open.removeAt(open.size - 1)
            }
        }
        assertEquals(emptyList<String>(), open)
        assertTrue(css.contains(".ff-filters-dropdown"))
    }

    @Test
    fun cremaClasspathFinderLoadsPanelLayout() {
        val finder = ClasspathViewFinder(FireflyCrema::class.java.classLoader)
        val source = finder.get("firefly.layouts.panel")
        assertTrue(source.contains("ff-panel"))
        assertTrue(source.contains("{!! sidebar !!}"))
        assertThrows(ViewNotFoundException::class.java) { finder.get("firefly.layouts.missing") }

        val temp = Files.createTempDirectory("ff-crema-kt")
        try {
            val views = temp.resolve("views/alt")
            Files.createDirectories(views)
            Files.writeString(views.resolve("shell.crema.kt"), "<div>kt</div>")
            Files.writeString(temp.resolve("bare.crema.html"), "<b>ok</b>")
            val loader = URLClassLoader(arrayOf(temp.toUri().toURL()), null)
            val ktFinder = ClasspathViewFinder(
                loader,
                "views",
                listOf(".crema.html", ".crema.kt"),
            )
            assertEquals("<div>kt</div>", ktFinder.get("alt.shell"))
            val emptyRoot = ClasspathViewFinder(loader, root = "")
            assertEquals("<b>ok</b>", emptyRoot.get("bare"))
            assertThrows(ViewNotFoundException::class.java) {
                ClasspathViewFinder(loader, root = "/").get("alt.shell")
            }
        } finally {
            Files.walk(temp).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }

        assertSame(FireflyCrema.engine(), FireflyCrema.engine())
        val panel = FireflyCrema.renderPanel(
            mapOf(
                "documentTitle" to "T",
                "brandName" to "B",
                "csrfToken" to null,
                "css" to "/*x*/",
                "sidebar" to "<aside></aside>",
                "topbar" to "<header></header>",
                "body" to "<main></main>",
                "footer" to "<footer></footer>",
                "themeInitScript" to "<script>init</script>",
                "chromeScript" to "<script>chrome</script>",
                "bodyBackground" to "#fff",
                "bodyColor" to "#111",
            ),
        )
        assertTrue(panel.contains("<title>T</title>"))
        assertTrue(panel.contains("ff-panel"))
        assertFalse(panel.contains("""name="csrf-token""""))

        val injected = CremaEngine(
            MapViewFinder().put(
                FireflyCrema.PANEL,
                "<html>{!! body !!}</html>",
            ),
        )
        val fromInjected = DefaultLayout(injected).render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf(
                    "sidebar" to "<aside>s</aside>",
                    "topbar" to "<header>t</header>",
                    "body" to "<main>b</main>",
                    "footer" to "<footer>f</footer>",
                ),
                "css",
            ),
        )
        assertEquals("<html><main>b</main></html>", fromInjected)
    }

    @Test
    fun dialogModesWidthsAndDefaults() {
        val plain = Dialog.make("Hello").render()
        assertTrue(plain.contains("ff-dialog-modal"))
        assertTrue(plain.contains("""data-ff-align="top""""))
        assertTrue(plain.contains("data-ff-drag"))
        assertTrue(plain.contains("Hello"))
        // Closed by default — fixed inset dialog must not cover the page.
        assertTrue(plain.contains(" hidden"))
        assertTrue(plain.contains("""aria-hidden="true""""))
        assertFalse(plain.contains("""aria-modal="true""""))

        val open = Dialog.make("Hello").render(open = true)
        assertTrue(open.contains("""aria-modal="true""""))
        assertFalse(open.contains("""role="dialog" hidden"""))
        assertTrue(open.contains("ff-dialog-close"))
        assertTrue(open.contains("data-ff-dialog-drag-handle"))
        assertTrue(open.contains("ff-dialog-title"))
        assertTrue(open.contains("ff-dialog-header-actions"))
        assertTrue(open.contains("data-ff-dialog-expand"))
        assertTrue(open.contains("data-ff-dialog-resize"))
        assertFalse(open.contains("Open in full page"))
        assertFalse(Dialog.make("Hello").closeButton(false).render(open = true).contains("ff-dialog-close"))
        assertFalse(Dialog.make("Hello").expandable(false).render(open = true).contains("data-ff-dialog-expand"))
        assertFalse(Dialog.make("Hello").resizable(false).render(open = true).contains("data-ff-dialog-resize"))

        val end = Dialog.make("Edit").slideOver().draggable(false).render()
        assertTrue(end.contains("ff-dialog-sidebar"))
        assertTrue(end.contains("""data-ff-position="end""""))
        assertFalse(end.contains("data-ff-drag"))
        assertTrue(end.contains(" hidden"))
        assertFalse(end.contains("data-ff-dialog-drag-handle"))
        assertTrue(end.contains("data-ff-dialog-expand"))
        assertFalse(end.contains("data-ff-dialog-resize"))

        val start = Dialog.make("Edit").slideOverStart().render()
        assertTrue(start.contains("""data-ff-position="start""""))

        val full = Dialog.make("All").fullscreen().draggable(true).width("screen").render()
        assertTrue(full.contains("ff-dialog-fullscreen"))
        assertTrue(full.contains("ff-dialog-screen"))
        assertFalse(full.contains("data-ff-drag"))
        assertFalse(full.contains("data-ff-align"))
        assertFalse(full.contains("data-ff-dialog-expand"))
        assertFalse(full.contains("data-ff-dialog-resize"))

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
        listOf(
            Icon.HOME,
            Icon.BARS,
            Icon.USER,
            Icon.CHECK,
            Icon.X,
            Icon.TRASH,
            Icon.ARROWS_POINTING_OUT,
            Icon.ARROWS_POINTING_IN,
        ).forEach { icon ->
            assertTrue(icon.svg().contains("ff-icon"))
            assertTrue(icon.svg().contains("<svg"))
            assertTrue(icon.name().startsWith("heroicon-o-"))
        }
        assertEquals(Icon.HOME, Icon.fromName("HoMe"))
        assertEquals(Icon.BARS, Icon.fromName("bars"))
        assertEquals(Icon.BARS, Icon.fromName("bars-3"))
        assertEquals(Icon.USER, Icon.fromName("user"))
        assertEquals(Icon.CHECK, Icon.fromName("check"))
        assertEquals(Icon.X, Icon.fromName("x"))
        assertEquals(Icon.X, Icon.fromName("x-mark"))
        assertEquals(Icon.TRASH, Icon.fromName("trash"))
        assertEquals(Icon.ARROWS_POINTING_OUT, Icon.fromName("arrows-pointing-out"))
        assertEquals(Icon.ARROWS_POINTING_OUT, Icon.fromName("arrows_pointing_out"))
        assertEquals(Icon.ARROWS_POINTING_IN, Icon.fromName("arrows-pointing-in"))
        assertEquals(Icon.ARROWS_POINTING_IN, Icon.fromName("arrows_pointing_in"))
        assertEquals("heroicon-o-bell", Icon.fromName("heroicon-o-bell").name())
        assertEquals("heroicon-o-academic-cap", Icon.fromName("academic-cap").name())
        assertThrows(FireflyException::class.java) { Icon.fromName("nope-not-an-icon-zzz") }
    }

    @Test
    fun stylesheetIsScopedAndThemesSupplyTokens() {
        val css = Stylesheet.render(KonaTheme())
        assertTrue(css.contains("--ff-primary: #DF8723"))
        assertTrue(css.contains("--ff-primary: #C9A57B"))
        // Shamar calm dark: gray-800 shell / gray-900 cards / near-white headings.
        assertTrue(css.contains("--ff-surface: #1F2937"))
        assertTrue(css.contains("--ff-heading: #F3F4F6"))
        assertTrue(css.contains("--ff-card: #111827"))
        assertTrue(css.contains("--ff-shell: var(--ff-surface, #1f2937)"))
        assertTrue(css.contains(".ff-panel .ff-btn"))
        assertTrue(css.contains(".ff-panel .ff-table-wrap"))
        // No primary top ribbon on topbar/sidebar-brand — leaves Wire navigate progress visible.
        assertFalse(css.contains("inset 0 3px 0 0 var(--ff-primary)"))
        assertTrue(css.contains("--ff-shell: var(--ff-surface"))
        assertTrue(css.contains("--ff-table-shadow:"))
        assertTrue(css.contains("box-shadow: var(--ff-table-shadow)"))
        assertTrue(css.contains("--ff-width-xl:"))
        assertTrue(css.contains("max-width: var(--ff-width-xl)"))
        assertTrue(css.contains("""[data-ff-max-width="xl"]"""))
        assertTrue(css.contains("margin-left: auto"))
        assertTrue(css.contains("margin-right: auto"))
        // Create/edit forms fill the content column like relation managers (#406).
        assertTrue(css.contains(".ff-resource-form"))
        assertTrue(
            Regex(
                """\.ff-edit\s+\.ff-page-content\s*>\s*\.ff-resource-form[\s\S]*?max-width:\s*none""",
            ).containsMatchIn(css),
        )
        assertTrue(
            Regex(
                """\.ff-create\s+\.ff-page-content\s*>\s*\.ff-resource-form[\s\S]*?max-width:\s*none""",
            ).containsMatchIn(css),
        )
        // View/edit infolist hosts fill the content column (no xl cap) (#272).
        assertTrue(
            Regex(
                """\.ff-view\s+\.ff-page-content\s*>\s*\.ff-infolist[\s\S]*?max-width:\s*none""",
            ).containsMatchIn(css),
        )
        assertTrue(
            Regex(
                """\.ff-edit\s+\.ff-page-content\s*>\s*\.ff-infolist[\s\S]*?max-width:\s*none""",
            ).containsMatchIn(css),
        )
        // List/manage table wraps keep elevation; chrome strip is create/edit/view only.
        assertTrue(css.contains("/* List/manage: keep the elevated white .ff-table-wrap card"))
        assertFalse(
            Regex(
                """\.ff-list\s+\.ff-page-content\s*>\s*\.ff-table-wrap\s*,[^{]*\{[^}]*box-shadow:\s*none""",
            ).containsMatchIn(css),
        )
        assertTrue(css.contains(".ff-panel .ff-table-wrap .ff-table"))
        // Flat white table surface: toolbar / thead / pagination use --ff-card (not inset).
        assertTrue(
            Regex("""\.ff-panel\s+\.ff-table-toolbar\s*\{[^}]*background:\s*var\(--ff-card\)""")
                .containsMatchIn(css),
        )
        assertTrue(
            Regex("""\.ff-panel\s+\.ff-table\s+th\s*\{[^}]*background:\s*var\(--ff-card\)""")
                .containsMatchIn(css),
        )
        assertTrue(
            Regex("""\.ff-panel\s+\.ff-pagination\s*\{[^}]*background:\s*var\(--ff-card\)""")
                .containsMatchIn(css),
        )
        // Relation managers: no outer floating card — .ff-table-wrap is the chrome.
        assertTrue(
            Regex("""\.ff-panel\s+\.ff-relation\s*\{[^}]*background:\s*transparent""")
                .containsMatchIn(css),
        )
        assertTrue(
            Regex("""\.ff-panel\s+\.ff-relation\s*\{[^}]*box-shadow:\s*none""")
                .containsMatchIn(css),
        )
        // List headers (including sort controls) are capital letters via CSS.
        assertTrue(css.contains(".ff-panel .ff-table th"))
        assertTrue(css.contains("text-transform: uppercase"))
        assertTrue(css.contains(".ff-panel .ff-table-sort"))
        assertTrue(css.contains(".ff-panel .ff-breadcrumbs"))
        assertTrue(css.contains(".ff-panel .ff-entry .ff-label"))
        assertTrue(css.contains("grid-template-columns: repeat(var(--ff-cols"))
        assertTrue(css.contains("--ff-cols-lg") || css.contains("var(--ff-cols-lg"))
        assertTrue(css.contains("@media (min-width: 640px)"))
        assertTrue(css.contains("@media (min-width: 1024px)"))
        assertTrue(css.contains("grid-column: span var(--ff-span"))
        assertTrue(css.contains(".ff-panel .ff-table-search-wrap"))
        assertTrue(css.contains(":not(.ff-table-search)"))
        assertTrue(css.contains(".ff-panel .ff-table-card"))
        assertTrue(css.contains(".ff-panel .ff-layout-split"))
        assertTrue(css.contains(".ff-panel .ff-layout-stack"))
        assertTrue(css.contains("data-ff-stacked-mobile"))
        assertTrue(css.contains(".ff-panel .ff-table-mobile-sort"))
        assertTrue(css.contains("ff-table-grid[data-ff-content-grid]") || css.contains("data-ff-content-grid"))
        assertTrue(css.contains(".ff-panel .ff-row-actions .ff-dialog") || css.contains(".ff-row-actions .ff-action-dialog"))
        assertTrue(css.contains(".ff-panel .ff-entry[data-ff-align=\"justify\"]") || css.contains(".ff-entry[data-ff-align=\"justify\"]"))
        assertTrue(css.contains("overflow-wrap: anywhere"))
        assertTrue(css.contains("word-break: break-word"))
        assertTrue(css.contains(""".ff-panel .ff-entry[data-ff-wrap="false"]""") || css.contains("data-ff-wrap=\"false\""))
        // View/manage dialogs live under .ff-row-actions-inner { white-space: nowrap } — reset (#370).
        assertTrue(css.contains(".ff-panel .ff-row-actions .ff-table-button-dialog") || css.contains(".ff-row-actions .ff-dialog"))
        val dialogReset = css.indexOf("Dialogs/infolists mount inside .ff-row-actions")
        assertTrue(dialogReset >= 0, "missing dialog inherit reset block")
        assertTrue(css.substring(dialogReset, dialogReset + 500).contains("white-space: normal"))
        assertTrue(css.contains(".ff-picker-ok") && css.contains("margin-left: auto"))
        // Month/year/decade: compound selectors after base 7-col so 4×3 wins (#420).
        val dayGrid7 = css.indexOf(".ff-panel .ff-picker-grid {\n  display: grid;\n  grid-template-columns: repeat(7, 1fr);")
        val months4x3 = css.indexOf(".ff-panel .ff-picker-grid.ff-picker-grid-months")
        assertTrue(dayGrid7 >= 0 && months4x3 > dayGrid7, "4×3 month rules must follow base 7-col day grid")
        assertTrue(css.contains("grid-template-columns: repeat(4, minmax(0, 1fr))"))
        assertTrue(css.contains("grid-template-rows: repeat(3, minmax(0, 1fr))"))
        assertTrue(css.contains(".ff-panel .ff-picker-grid.ff-picker-grid-years"))
        assertTrue(css.contains(".ff-panel .ff-picker-grid.ff-picker-grid-weeks"))
        assertTrue(css.contains("ff-picker-weeknum"))
        assertTrue(css.contains("ff-week-selected"))
        assertTrue(css.contains("ff-week-start") && css.contains("ff-week-end"))
        assertTrue(css.contains(".ff-panel .ff-control.has-prefix") && css.contains(".ff-panel .ff-prefix"))
        // Prefix/suffix join the input without a border/radius step (#426).
        assertTrue(css.contains("border-left: 0") && css.contains("border-right: 0"))
        assertTrue(css.contains("border-radius: var(--ff-radius) 0 0 var(--ff-radius)"))
        assertTrue(css.contains("border-radius: 0 var(--ff-radius) var(--ff-radius) 0"))
        assertTrue(css.contains(".ff-panel .ff-control.has-suffix > .ff-input-group > .ff-input-action:last-child"))
        assertTrue(css.contains("ff-column-money") && css.contains("tabular-nums"))
        assertTrue(css.contains(".ff-panel .ff-combobox-panel"))
        assertTrue(css.contains(".ff-panel .ff-combobox.ff-combobox-open"))
        assertTrue(css.contains(".ff-panel .ff-option"))
        assertTrue(css.contains("color: var(--ff-heading)"))
        assertTrue(css.contains(".ff-panel .ff-option-check"))
        assertTrue(css.contains(".ff-panel .ff-combobox-chip"))
        assertTrue(css.contains(".ff-panel .ff-combobox-chips"))
        assertTrue(css.contains(".ff-panel .ff-option.ff-selected"))
        assertTrue(css.contains(".ff-panel .ff-pagination"))
        assertTrue(css.contains(".ff-panel .ff-page-header-actions > .ff-schema"))
        assertTrue(css.contains(".ff-panel .ff-column-toggle"))
        assertTrue(css.contains(".ff-panel .ff-column-toggle-panel"))
        assertTrue(css.contains("flex-direction: row"))
        assertTrue(css.contains(".ff-panel .ff-sidebar-brand"))
        assertTrue(css.contains(".ff-panel .ff-nav-item"))
        assertTrue(css.contains(".ff-panel .ff-page-heading"))
        assertTrue(css.contains(".ff-panel .ff-page-toolbar"))
        assertTrue(css.contains(".ff-panel .ff-page-header > nav"))
        assertTrue(css.contains(".ff-panel .ff-page-mode"))
        assertTrue(css.contains("--ff-width-7xl: 80rem"))
        assertTrue(css.contains("--ff-content-max-width: var(--ff-width-7xl)"))
        assertTrue(css.contains("max-width: var(--ff-content-max-width)"))
        assertTrue(css.contains(""".ff-panel[data-ff-max-width="full"]"""))
        assertTrue(css.contains(""".ff-panel .ff-page[data-ff-max-width="xl"]"""))
        assertTrue(css.contains("max-width: none"))
        assertTrue(css.contains(".ff-panel .ff-action-outlined"))
        assertTrue(css.contains(".ff-panel .ff-row-actions"))
        assertTrue(css.contains(".ff-panel .ff-row-actions-inner"))
        assertTrue(css.contains("display: inline-flex"))
        assertTrue(css.contains(".ff-panel .ff-row-action-menu"))
        assertTrue(css.contains(".ff-panel .ff-row-action-menu .ff-table-more"))
        assertTrue(css.contains(".ff-panel .ff-row-action-menu .ff-menu-panel"))
        assertTrue(css.contains("position: fixed"))
        assertTrue(css.contains("z-index: 1100"))
        assertTrue(css.contains(".ff-panel .ff-boolean"))
        assertTrue(css.contains(".ff-panel .ff-boolean.is-true"))
        assertTrue(css.contains(".ff-panel .ff-boolean.is-false"))
        assertTrue(css.contains(".ff-panel .ff-boolean--icon-only"))
        assertTrue(css.contains(".ff-panel .ff-boolean__mark"))
        assertTrue(css.contains(".ff-panel .ff-boolean__label"))
        assertTrue(css.contains(".ff-panel .ff-sr"))
        assertTrue(css.contains(".ff-panel .ff-icons"))
        assertTrue(css.contains(".ff-panel .ff-component.ff-icon-entry"))
        assertTrue(css.contains("background: #ecfdf5"))
        assertTrue(css.contains("color: #047857"))
        assertTrue(css.contains("background: #f3f4f6"))
        assertTrue(css.contains("background: #064e3b"))
        assertTrue(css.contains("color: #6ee7b7"))
        assertTrue(Icon.CHECK.svg().contains("ff-icon"))
        assertTrue(Icon.CHECK.svg().contains("svg"))
        assertTrue(Icon.X.svg().contains("x-mark") || Icon.X.svg().contains("M6") || Icon.X.svg().contains("path"))
        assertEquals("heroicon-o-check", Icon.CHECK.name())
        assertTrue(css.contains(".ff-panel .ff-dialog[hidden]"))
        assertTrue(css.contains(".ff-panel .ff-table-button-dialog[hidden]"))
        assertTrue(css.contains(".ff-panel .ff-action-dialog.ff-open"))
        assertTrue(css.contains(".ff-panel .ff-dialog-overlay"))
        assertTrue(css.contains("pointer-events: none !important"))
        assertTrue(css.contains("pointer-events: auto !important"))
        // Open dialog mounts stack above the drawer backdrop; window paints above its overlay.
        assertTrue(css.contains("z-index: 60"))
        assertTrue(css.contains(".ff-panel .ff-dialog-modal .ff-dialog-window"))
        assertTrue(css.contains("position: relative"))
        assertTrue(css.contains(".ff-panel .ff-dialog-header"))
        assertTrue(css.contains(".ff-panel .ff-dialog-body"))
        assertTrue(css.contains(".ff-panel .ff-dialog-header-actions"))
        assertTrue(css.contains(".ff-panel .ff-dialog-expand"))
        assertTrue(css.contains(".ff-panel .ff-dialog-resize"))
        assertTrue(css.contains("ff-dialog-expanded"))
        assertTrue(css.contains(".ff-panel .ff-dialog-body > :not(.ff-action-footer)"))
        assertTrue(css.contains("overflow: hidden"))
        // Modal body: plain surface, no nested section/card chrome (#415).
        assertTrue(css.contains(".ff-panel .ff-dialog-body .ff-component.ff-section"))
        assertTrue(css.contains(".ff-panel .ff-dialog-body .ff-card"))
        assertTrue(css.contains("box-shadow: none"))
        assertTrue(css.contains(".ff-panel .ff-dialog-body .ff-component.ff-section .ff-section-inner"))
        // Create/action dialog chrome: compact close icon + primary Submit (type=button).
        assertTrue(css.contains(".ff-panel .ff-action-close") || css.contains(".ff-panel .ff-dialog-close"))
        assertTrue(css.contains(".ff-panel .ff-dialog-close"))
        assertTrue(css.contains("[data-ff-dialog-drag-handle]"))
        assertTrue(css.contains("justify-content: flex-end"))
        assertTrue(css.contains(".ff-picker-caption"))
        assertTrue(css.contains(".ff-checkbox-box"))
        assertTrue(css.contains("th[data-ff-align=\"end\"]"))
        assertTrue(css.contains(".ff-panel .ff-action-submit"))
        assertTrue(css.contains(".ff-panel .ff-action-footer"))
        assertTrue(css.contains(".ff-panel svg.ff-icon"))
        assertTrue(css.contains(".ff-panel > .ff-column"))
        assertTrue(css.contains(".ff-panel .ff-table .ff-icon-column"))
        assertTrue(css.contains("tr[data-ff-record-url]") || css.contains("data-ff-record-url"))
        assertTrue(css.contains("cursor: pointer"))
        assertTrue(css.contains("width: fit-content"))
        assertTrue(Icon.X.svg().contains("""class="ff-icon""""))
        assertTrue(css.contains(".ff-panel .ff-toggle-thumb"))
        assertTrue(css.contains(".ff-panel .ff-toggle-track"))
        assertTrue(css.contains(".ff-panel .ff-toggle-wrap"))
        assertTrue(css.contains(".ff-panel .ff-picker-panel"))
        assertTrue(css.contains(".ff-panel .ff-required-mark"))
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
        assertTrue(css.contains("@font-face"))
        assertTrue(css.contains("/_firefly/fonts/inter-latin-wght-normal.woff2"))
        assertFalse(css.contains("fonts.googleapis.com"))
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
        // Self-hosted Inter/DM Sans — no Google Fonts CDN; fonts come from /_firefly/fonts.
        assertFalse(html.contains("fonts.googleapis.com"))
        assertFalse(html.contains("fonts.gstatic.com"))
        assertTrue(html.contains("/_firefly/fonts/inter-latin-wght-normal.woff2"))
        assertTrue(html.contains("@font-face"))
        // Dark no-flash init + chrome behavior scripts and the mobile drawer backdrop.
        assertTrue(html.contains("""<div class="ff-backdrop" data-ff-backdrop"""))
        assertTrue(html.contains("classList.toggle('ff-dark'"))
        assertTrue(html.contains("localStorage.getItem('ff-theme')"))
        assertTrue(html.contains("data-ff-sidebar-toggle"))
        assertTrue(html.contains("data-ff-menu-trigger"))
        assertTrue(html.contains("positionRowMenu"))
        assertTrue(html.contains("hoistDialogPanel"))
        assertTrue(html.contains("dialogPanelFor"))
        assertTrue(html.contains("mountForPanel"))
        assertTrue(html.contains("switchScope"))
        assertTrue(html.contains("positionCombobox"))
        assertTrue(html.contains("positionPicker"))
        assertTrue(html.contains("clearFloatingPanel"))
        assertTrue(html.contains("repositionFloating"))
        assertTrue(html.contains("formatMask"))
        assertTrue(html.contains("applyMask"))
        assertTrue(html.contains("armMasks"))
        assertTrue(html.contains("data-ff-mask"))
        assertTrue(html.contains("ff-row-action-menu"))
        assertTrue(html.contains(DefaultLayout.THEME_INIT_SCRIPT))
        // Dialog open/close without Alpine (Action / TableButton mounts).
        assertTrue(html.contains("data-ff-dialog-open"))
        assertTrue(html.contains("data-ff-dialog-panel"))
        assertTrue(html.contains("data-ff-dialog-switch"))
        assertTrue(html.contains("switchMount"))
        assertTrue(html.contains("data-ff-record-url"))
        assertTrue(html.contains("data-ff-record-action"))
        assertTrue(html.contains("recordTrigger"))
        assertTrue(html.contains("ff-open"))
        // Processing buttons + toast arming (#380).
        assertTrue(html.contains("markLoading"))
        assertTrue(html.contains("ff-spinner"))
        assertTrue(html.contains("data-ff-loading"))
        assertTrue(html.contains("armNotifications"))
        assertTrue(html.contains("data-ff-notification-dismiss"))
        assertTrue(html.contains(".ff-notification"))
        assertTrue(html.contains("@keyframes ff-spin") || html.contains("ff-spin"))
        // Form toggles flip without Alpine/Wire.
        assertTrue(html.contains("data-ff-toggle"))
        assertTrue(html.contains("data-ff-toggle-value"))
        assertTrue(html.contains("data-ff-picker"))
        assertTrue(html.contains("closePickers"))
        assertTrue(html.contains("data-ff-combobox"))
        assertTrue(html.contains("closeComboboxes"))
        assertTrue(html.contains("openCombobox"))
        assertTrue(html.contains("filterCombobox"))
        assertTrue(html.contains("remoteFilterCombobox"))
        assertTrue(html.contains("formOptionsUrl"))
        // Relation pickers search their own endpoint; relation tabs switch panels in place.
        assertTrue(html.contains("data-ff-search-url"))
        assertTrue(html.contains("[data-ff-relation-tab]"))
        assertTrue(html.contains("searchParams.set('relation'"))
        assertTrue(html.contains("setComboboxValue"))
        assertTrue(html.contains("renderComboboxChips"))
        assertTrue(html.contains("data-ff-combobox-remove"))
        assertTrue(html.contains("optionCheckSvg"))
        assertTrue(html.contains("ff-icon"))
        assertTrue(html.contains("rebuildView"))
        assertTrue(html.contains("rebuildGrid"))
        assertTrue(html.contains("isoWeekOf"))
        assertTrue(html.contains("weekTokenOf"))
        assertTrue(html.contains("sundayWeekOf"))
        assertTrue(html.contains("focusFromWeek"))
        assertTrue(html.contains("ff-picker-grid-weeks"))
        assertTrue(html.contains("ff-picker-weeknum"))
        assertTrue(html.contains("ff-week-start"))
        assertTrue(html.contains("searchTimers"))
        assertTrue(html.contains("ff-table-search"))
        assertTrue(html.contains("button[data-ff-month]"))
        assertTrue(html.contains("button[data-ff-year]"))
        assertTrue(html.contains("setSelectionRange"))
        assertTrue(html.contains("data-ff-dialog-drag-handle") || html.contains("dragState"))
        // Drag leave position:fixed + margin:0; reopen must clear them so the window
        // returns to stylesheet centering (#316).
        assertTrue(html.contains("resetDialogDrag"))
        assertTrue(html.contains("dlg.style.position=''"))
        assertTrue(html.contains("dlg.style.margin=''"))
        assertTrue(html.contains("toggleDialogExpand"))
        assertTrue(html.contains("ff-dialog-expanded"))
        assertTrue(html.contains("resizeState"))
        assertTrue(html.contains("data-ff-dialog-resize"))
        assertTrue(html.contains("data-ff-dialog-expand"))
        // Closing a dialog resets forms so create/edit do not leak prior input.
        assertTrue(html.contains("resetDialogForms"))
        assertTrue(html.contains("f.reset()"))
        // Table soft-nav swaps .ff-table-wrap without a full document reload.
        assertTrue(html.contains("ffTableNav"))
        assertTrue(html.contains("data-ff-table-nav"))
        assertTrue(html.contains("data-ff-mobile-sort"))
        assertTrue(html.contains("data-ff-column-toggle"))
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
        assertFalse(auth.contains("fonts.googleapis.com"))
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
        assertEquals(
            null,
            ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "css", "tok", "T", "B", ChromeMode.PANEL).maxWidth,
        )
        assertEquals(
            "full",
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                emptyMap(),
                "css",
                "tok",
                "T",
                "B",
                ChromeMode.PANEL,
                "full",
            ).maxWidth,
        )
        assertEquals("7xl", MaxWidth.DEFAULT)
        assertTrue(MaxWidth.TOKENS.contains("xl"))
        assertEquals("full", MaxWidth.requireKnown("full"))
        assertThrows(FireflyException::class.java) { MaxWidth.requireKnown("huge") }
        assertThrows(FireflyException::class.java) { Panel().maxWidth("huge") }
        val wideChrome = Panel().maxWidth("full").render()
        assertTrue(wideChrome.contains("""class="ff-panel" data-ff-max-width="full""""))
        assertTrue(Panel().render().contains("""class="ff-panel""""))
        assertFalse(Panel().render().contains("""class="ff-panel" data-ff-max-width="""))
        val blankWidth = DefaultLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf(
                    "sidebar" to """<aside class="ff-sidebar"><!-- ff-hook:sidebar.start --><!-- ff-hook:sidebar.end --></aside>""",
                    "topbar" to """<header class="ff-topbar"><!-- ff-hook:topbar.start --><!-- ff-hook:topbar.end --></header>""",
                    "body" to """<main class="ff-body"><!-- ff-hook:body.start --><!-- ff-hook:body.end --></main>""",
                    "footer" to """<footer class="ff-footer"><!-- ff-hook:footer --></footer>""",
                ),
                "css",
                maxWidth = "   ",
            ),
        )
        assertTrue(blankWidth.contains("""class="ff-panel""""))
        assertFalse(blankWidth.contains("""class="ff-panel" data-ff-max-width="""))
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
