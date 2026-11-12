package org.konaframework.firefly.support

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.crema.CremaEngine
import org.konaframework.icons.IconSetOptions
import org.konaframework.icons.Icons
import org.konaframework.crema.MapViewFinder
import org.konaframework.crema.ViewNotFoundException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class FireflySupportTest {
    @AfterEach
    fun clearDialogDefaults() {
        Dialog.configureUsing(null)
        ThemeAssets.root = Path.of("").toAbsolutePath()
        ThemeCssCompiler.reset()
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
    fun authCremaRendersShellBodyAndForm() {
        val body = FireflyCrema.render(FireflyCrema.AUTH_BODY, mapOf("form" to "<p>form</p>"))
        assertTrue(body.contains("ff-auth-main"))
        assertTrue(body.contains("<!-- ff-hook:body.start --><p>form</p><!-- ff-hook:body.end -->"))

        val login = FireflyCrema.render(
            FireflyCrema.AUTH_FORM,
            mapOf(
                "css" to "ff-login",
                "title" to "Sign in",
                "error" to null,
                "action" to "/login",
                "csrf" to """<input type="hidden" name="_token" value="t">""",
                "showName" to false,
                "showPassword" to true,
                "passwordAutocomplete" to "current-password",
                "submit" to "Sign in",
            ),
        )
        assertTrue(login.contains("ff-auth-panel ff-login"))
        assertTrue(login.contains("current-password"))
        assertTrue(login.contains("""name="_token""""))
        assertFalse(login.contains("ff-auth-name"))
        assertFalse(login.contains("ff-auth-alert"))

        val register = FireflyCrema.render(
            FireflyCrema.AUTH_FORM,
            mapOf(
                "css" to "ff-register",
                "title" to "A & B",
                "error" to "Bad <token>",
                "action" to "/register",
                "csrf" to "",
                "showName" to true,
                "showPassword" to true,
                "passwordAutocomplete" to "new-password",
                "submit" to "Register",
            ),
        )
        assertTrue(register.contains("ff-auth-name"))
        assertTrue(register.contains("new-password"))
        assertTrue(register.contains("ff-auth-alert"))
        assertTrue(register.contains("Bad &lt;token&gt;"))
        assertTrue(register.contains("A &amp; B"))

        val reset = FireflyCrema.render(
            FireflyCrema.AUTH_FORM,
            mapOf(
                "css" to "ff-password-reset",
                "title" to "Reset",
                "error" to "",
                "action" to "/password-reset",
                "csrf" to "",
                "showName" to false,
                "showPassword" to false,
                "passwordAutocomplete" to "new-password",
                "submit" to "Send",
            ),
        )
        assertFalse(reset.contains("ff-auth-password"))
        assertFalse(reset.contains("ff-auth-name"))
        assertFalse(reset.contains("ff-auth-alert"))
        assertTrue(reset.contains("ff-auth-email"))
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
            assertTrue(icon.svg().contains("""width="16""""))
            assertTrue(icon.svg().contains("""height="16""""))
            assertTrue(icon.svg().contains("""stroke-width="1.5""""))
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
        assertEquals("heroicon-s-home", Icon.fromName("heroicon-s-home").name())
        assertTrue(Icon.fromName("heroicon-s-home").svg().contains("fill=\"currentColor\""))
        assertTrue(Icon.fromName("heroicon-o-funnel").svg().contains("stroke=\"currentColor\""))
        assertEquals("heroicon-o-academic-cap", Icon.fromName("academic-cap").name())
        assertEquals("heroicon-o-home", Icon.fromName("home").name())
        assertThrows(FireflyException::class.java) { Icon.fromName("nope-not-an-icon-zzz") }
        assertThrows(FireflyException::class.java) { Icon.fromName("fas-home") }
        Icons.add("fontawesome", IconSetOptions(prefix = "fas", classpath = "icons/custom"))
        try {
            assertEquals("fas-star", Icon.fromName("fas-star").name())
        } finally {
            Icons.reset()
        }
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
        // Weekday header with weeknums must beat the base 7-col weekdays rule (#427).
        val weekdays7 = css.indexOf(".ff-panel .ff-picker-weekdays {\n  display: grid;\n  grid-template-columns: repeat(7, 1fr);")
        val weekdaysWeeks = css.indexOf(".ff-panel .ff-picker-weekdays.ff-picker-weekdays-weeks")
        assertTrue(weekdays7 >= 0 && weekdaysWeeks > weekdays7, "week header 8-col must follow base 7-col weekdays")
        assertTrue(css.contains("1.75rem repeat(7, 1fr)"))
        assertTrue(css.contains("ff-picker-weeknum"))
        assertTrue(css.contains("ff-week-selected"))
        assertTrue(css.contains("ff-week-start") && css.contains("ff-week-end"))
        assertTrue(css.contains(".ff-panel .ff-control.has-prefix") && css.contains(".ff-panel .ff-prefix"))
        // Prefix/suffix join the input without a border/radius step (#426).
        assertTrue(css.contains("border-left: 0") && css.contains("border-right: 0"))
        assertTrue(css.contains("border-radius: var(--ff-radius) 0 0 var(--ff-radius)"))
        assertTrue(css.contains("border-radius: 0 var(--ff-radius) var(--ff-radius) 0"))
        assertTrue(css.contains(".ff-panel .ff-control.has-suffix > .ff-input-group > .ff-input-action:last-child"))
        val widgets = Stylesheet.readCss("/org/konaframework/firefly/support/form-widgets.css")
        assertTrue(widgets.contains("border-top-left-radius: 0 !important"), "prefix join must beat input:not(...)")
        assertTrue(widgets.contains("border-top-right-radius: var(--ff-radius) !important"), "reveal suffix must round outer edge")
        assertTrue(widgets.contains(".ff-panel .ff-color-column > span.ff-color"), "ColorColumn swatch sizing")
        assertTrue(widgets.contains(".ff-panel .ff-swatch"), "ColorEntry swatch sizing")
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
        val sidebarBrand = css.substringAfter(".ff-panel .ff-sidebar-brand {").substringBefore("}")
        assertTrue(sidebarBrand.contains("padding-block: 0"), sidebarBrand)
        assertTrue(sidebarBrand.contains("padding-inline: 1.1rem"), sidebarBrand)
        val topbarBrand = css.substringAfter(".ff-panel .ff-topbar-brand {").substringBefore("}")
        assertTrue(topbarBrand.contains("padding-block: 0"), topbarBrand)
        val authBrand = css.substringAfter(".ff-auth-brand {").substringBefore("}")
        assertTrue(authBrand.contains("padding-block: 0"), authBrand)
        assertTrue(css.contains("height: var(--ff-logo-height, 2rem)"))
        assertTrue(css.contains("width: var(--ff-logo-width, auto)"))
        // html.ff-dark must hide the light logo. The old panel-only :not(.ff-dark) still matched.
        assertTrue(css.contains("html:not(.ff-dark) .ff-panel:not(.ff-dark) .ff-brand-logo--light"))
        assertTrue(css.contains("html:not(.ff-dark) .ff-auth:not(.ff-dark) .ff-brand-logo--light"))
        assertFalse(
            css.lineSequence().any { line ->
                val rule = line.trim()
                rule == ".ff-panel:not(.ff-dark) .ff-brand-logo--light," ||
                    rule == ".ff-auth:not(.ff-dark) .ff-brand-logo--light," ||
                    rule == "html:not(.ff-dark) .ff-panel .ff-brand-logo--light," ||
                    rule == "html:not(.ff-dark) .ff-auth .ff-brand-logo--light {"
            },
        )
        assertTrue(css.contains("html.ff-dark .ff-panel .ff-brand-logo--dark"))
        // Empty sidebar must not keep the column (#499). TOP's drawer aside is zero-width on desktop (#505).
        assertTrue(css.contains(".ff-panel > .ff-sidebar:empty"))
        assertTrue(css.contains(".ff-panel > .ff-sidebar:has(> .ff-sidebar-offcanvas)"))
        assertTrue(css.contains(".ff-panel .ff-nav-top .ff-nav-dropdown"))
        assertTrue(css.contains(".ff-panel .ff-nav-top .ff-nav-dropdown.ff-open > .ff-nav-list"))
        // Top-bar links and group triggers share one box (#507). SIDE cluster padding stays put.
        val topControls = css.substringAfter(".ff-panel .ff-nav-top .ff-nav-item > a,")
            .substringBefore(".ff-panel .ff-nav-top .ff-nav-dropdown {")
        assertTrue(topControls.contains(".ff-nav-dropdown-trigger"), topControls)
        assertTrue(topControls.contains("height: 2.15rem"), topControls)
        assertTrue(topControls.contains("min-height: 2.15rem"), topControls)
        assertTrue(topControls.contains("padding: 0.4rem 0.7rem"), topControls)
        assertTrue(topControls.contains("line-height: 1.2"), topControls)
        assertTrue(topControls.contains("align-items: center"), topControls)
        // Group <li>s carry .ff-nav-group, whose 0.75rem top margin is for SIDE/drawer
        // headings. Clear it on the top-bar row or the triggers sit low (#509).
        val topItems = css.substringAfter(".ff-panel .ff-nav-top > .ff-nav-list > .ff-nav-item {")
            .substringBefore("}")
        assertTrue(topItems.contains("margin-top: 0"), topItems)
        assertTrue(css.contains(".ff-panel .ff-nav-group {\n  margin-top: 0.75rem;\n}"))
        assertTrue(css.contains(".ff-panel .ff-nav-item.ff-cluster > a"))
        val verticalBox = css.substringAfter("/* Nav item, group heading, and dropdown trigger share one vertical box. */")
            .substringBefore("}")
        assertTrue(verticalBox.contains(".ff-panel .ff-nav-item > a,"), verticalBox)
        assertTrue(verticalBox.contains(".ff-panel .ff-nav-group-label,"), verticalBox)
        assertTrue(verticalBox.contains(".ff-panel .ff-nav-dropdown-trigger"), verticalBox)
        assertTrue(verticalBox.contains("display: flex"), verticalBox)
        assertTrue(verticalBox.contains("align-items: center"), verticalBox)
        assertTrue(verticalBox.contains("min-height: 2.15rem"), verticalBox)
        assertTrue(verticalBox.contains("padding: 0.4rem 0.7rem"), verticalBox)
        assertTrue(verticalBox.contains("line-height: 1.2"), verticalBox)
        val cluster = css.substringAfter(".ff-panel .ff-nav-item.ff-cluster > a {").substringBefore("}")
        assertFalse(cluster.contains("padding-top"), cluster)
        assertFalse(cluster.contains("padding-bottom"), cluster)
        assertFalse(cluster.contains("min-height"), cluster)
        val navIcon = css.substringAfter(".ff-panel .ff-nav-icon {").substringBefore("}")
        assertTrue(navIcon.contains("width: 16px"), navIcon)
        assertTrue(navIcon.contains("height: 16px"), navIcon)
        assertTrue(navIcon.contains("flex-shrink: 0"), navIcon)
        assertTrue(navIcon.contains("transform: none"), navIcon)
        assertFalse(navIcon.contains("rem"), navIcon)
        val buttonIcon = css.substringAfter(".ff-panel svg.ff-icon {").substringBefore("}")
        assertTrue(buttonIcon.contains("width: 16px"), buttonIcon)
        assertTrue(buttonIcon.contains("height: 16px"), buttonIcon)
        assertTrue(buttonIcon.contains("flex-shrink: 0"), buttonIcon)
        assertTrue(buttonIcon.contains("transform: none"), buttonIcon)
        assertFalse(buttonIcon.contains("rem"), buttonIcon)
        // APP sidebar and INVERSE top-bar group clusters are links: pointer and item hover.
        val groupLinks = css.substringAfter(".ff-panel .ff-nav-side .ff-nav-item.ff-cluster > a,")
            .substringBefore("}")
        assertTrue(groupLinks.contains(".ff-panel .ff-nav-top .ff-nav-item.ff-cluster > a"), groupLinks)
        assertTrue(groupLinks.contains("cursor: pointer"), groupLinks)
        val groupHover = css.substringAfter(".ff-panel .ff-nav-side .ff-nav-item.ff-cluster > a:hover,")
            .substringBefore("}")
        assertTrue(groupHover.contains(".ff-panel .ff-nav-top .ff-nav-item.ff-cluster > a:hover"), groupHover)
        assertTrue(groupHover.contains("background: var(--ff-hover)"), groupHover)
        assertTrue(groupHover.contains("color: var(--ff-heading)"), groupHover)
        assertFalse(groupHover.contains("var(--ff-active)"), groupHover)
        assertFalse(groupHover.contains("box-shadow"), groupHover)
        assertTrue(css.contains(".ff-panel .ff-nav-drawer"))
        assertTrue(css.contains(".ff-panel .ff-nav-more"))
        assertTrue(css.contains("flex: 0 0 0"))
        assertTrue(css.contains(".ff-panel .ff-nav-side"))
        // SIDE nav scroller: slim, hover/focus-within-only thumb (#497).
        assertTrue(css.contains("scrollbar-width: thin"))
        assertTrue(css.contains("scrollbar-color: transparent transparent"))
        assertTrue(css.contains(".ff-panel .ff-sidebar:hover .ff-nav-side"))
        assertTrue(css.contains(".ff-panel .ff-sidebar:focus-within .ff-nav-side"))
        assertTrue(css.contains(".ff-panel .ff-nav-side::-webkit-scrollbar"))
        assertTrue(css.contains(".ff-panel .ff-nav-side::-webkit-scrollbar-thumb"))
        // SIDE active parent group: subtle left border (#498). Accordion may still use it.
        assertTrue(css.contains(".ff-panel .ff-nav-side .ff-nav-group.ff-nav-group-active"))
        // SIDE static/accordion children: indented tree line, primary color on the active branch.
        assertTrue(css.contains(".ff-panel .ff-nav-side .ff-nav-tree"))
        assertTrue(css.contains(".ff-panel .ff-nav-side .ff-nav-tree.ff-nav-tree-active"))
        assertTrue(css.contains("padding-left: 0.75rem"))
        assertTrue(css.contains("border-left: 2px solid transparent"))
        assertTrue(css.contains(".ff-panel .ff-nav-item"))
        assertTrue(css.contains(":where(.ff-panel .ff-nav-item > a)"))
        assertTrue(css.contains(":where(.ff-panel .ff-nav-top .ff-nav-dropdown-trigger)"))
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
        assertTrue(css.contains(".ff-panel .ff-action-dialog.ff-closing"))
        assertTrue(css.contains(".ff-panel .ff-table-button-dialog.ff-closing"))
        assertTrue(css.contains(".ff-panel .ff-dialog-overlay"))
        assertTrue(css.contains("pointer-events: none !important"))
        assertTrue(css.contains("pointer-events: auto !important"))
        // Open dialog mounts stack above the drawer backdrop; window paints above its overlay.
        assertTrue(css.contains("z-index: 60"))
        assertTrue(css.contains(".ff-panel .ff-dialog-modal .ff-dialog-window"))
        assertTrue(css.contains(".ff-panel .ff-open .ff-dialog-modal .ff-dialog-window"))
        assertTrue(css.contains(".ff-panel .ff-open .ff-dialog-overlay"))
        assertTrue(css.contains("cubic-bezier(0.22, 1, 0.36, 1)"))
        assertTrue(css.contains("translate3d(0, 0.85rem, 0) scale(0.97)") || css.contains("scale(0.97)"))
        assertTrue(css.contains(".ff-panel .ff-dialog-sidebar .ff-dialog-window"))
        assertTrue(css.contains("translate3d(100%, 0, 0)") || css.contains("translate3d(100%"))
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
        assertTrue(css.contains("--ff-footer-height: 70px"))
        val bodyRule = Regex("""\.ff-panel \.ff-body\s*\{[^}]*\}""").find(css)
        assertNotNull(bodyRule)
        assertTrue(bodyRule!!.value.contains("min-height: calc(100% - 70px)"))
        val footerRules = Regex("""([^{]+)\{([^}]*)\}""").findAll(css).filter { rule ->
            rule.groupValues[1].split(",").any { selector ->
                val trimmed = selector.trim()
                trimmed.endsWith(".ff-footer") || trimmed.contains(".ff-footer ")
            }
        }
        assertTrue(footerRules.any())
        assertTrue(
            footerRules.none { rule ->
                Regex("""position:\s*(fixed|sticky)""").containsMatchIn(rule.groupValues[2])
            },
        )
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

        val themed = object : FireflyTheme() {
            override fun id() = "harbor"
            override fun tokens() = KonaTheme().tokens()
            override fun extraCss() = ".ff-btn { color: red; }"
        }
        assertTrue(Stylesheet.render(themed).contains("color: red"))

        val leak = object : FireflyTheme() {
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
        // Footer follows the body inside the scroll region, in normal flow.
        val scrollOpen = html.indexOf("""<div class="ff-scroll">""")
        val mainOpen = html.indexOf("""<main class="ff-body">""")
        val footerOpen = html.indexOf("""<footer class="ff-footer">""")
        assertTrue(scrollOpen >= 0 && mainOpen > scrollOpen && footerOpen > mainOpen)
        val afterFooter = html.substring(footerOpen)
        val footerEnd = afterFooter.indexOf("</footer>")
        val scrollClose = afterFooter.indexOf("</div>")
        assertTrue(footerEnd >= 0 && scrollClose > footerEnd)
        assertTrue(html.contains("ff-sidebar"))
        val noSidebar = Panel().includeSidebar(false).render()
        assertFalse(noSidebar.contains("""<aside class="ff-sidebar""""))
        assertTrue(noSidebar.contains("""class="ff-column""""))
        assertTrue(Panel().includeSidebar(true).render().contains("""<aside class="ff-sidebar""""))
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
        assertTrue(html.contains("fixedContainingOrigin"))
        assertTrue(html.contains("willChanges"))
        assertTrue(html.contains("-origin.x"))
        assertTrue(html.contains("-origin.y"))
        assertTrue(html.contains("releaseDialogClip"))
        assertTrue(html.contains("restoreDialogClip"))
        assertTrue(html.contains("data-ff-clip-released"))
        assertTrue(html.contains("closePickers();closeComboboxes();"))
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
        assertTrue(html.contains("openDialogFromQuery"))
        assertTrue(html.contains("params.get('dialog')"))
        assertTrue(html.contains("switchMount"))
        assertTrue(html.contains("data-ff-record-url"))
        assertTrue(html.contains("data-ff-record-action"))
        assertTrue(html.contains("recordTrigger"))
        assertTrue(html.contains("ff-open"))
        assertTrue(html.contains("ff-closing"))
        assertTrue(html.contains("finishCloseDialog"))
        assertTrue(html.contains("prefersReducedMotion"))
        assertTrue(html.contains("requestAnimationFrame"))
        assertTrue(html.contains("transitionend"))
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
        // SIDE exclusive accordion chrome (#494 / #496): live toggle closes siblings.
        assertTrue(html.contains("data-ff-nav-accordion"))
        assertTrue(html.contains("collapseSiblings"))
        assertTrue(html.contains("onSummaryClick"))
        assertTrue(html.contains("data-ff-nav-accordion-armed"))
        // Top-bar group dropdowns (#505). The script does not drive the sidebar accordion.
        assertTrue(html.contains("ff-nav-dropdown"))
        assertTrue(html.contains("ff-nav-dropdown-trigger"))
        assertTrue(html.contains("aria-expanded"))
        assertTrue(html.contains("aria-haspopup=\"menu\""))
        assertTrue(html.contains("data-ff-nav-more"))
        assertTrue(html.contains("ArrowDown"))
        assertTrue(html.contains("ArrowUp"))
        assertTrue(html.contains("ArrowRight"))
        assertTrue(html.contains("Escape"))
        assertTrue(html.contains("addEventListener(\"resize\""))
        assertTrue(html.contains("document.addEventListener(\"navigated\""))
        val dropdown = html.substringAfter("Top-bar group menus (#505).").substringBefore("</script>")
        assertTrue(dropdown.contains(".ff-nav.ff-nav-top"))
        assertFalse(dropdown.contains("data-ff-nav-accordion"), dropdown)
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
        assertTrue(escapedAuth.contains("ff-auth-brand"))
        assertTrue(escapedAuth.contains("ff-brand-mark"))
        assertTrue(escapedAuth.contains("""<span class="ff-brand">A & B <tag> "q"</span>"""))
        assertTrue(escapedAuth.contains("<title>A &amp; B  &quot;q&quot;</title>"))
        assertFalse(escapedAuth.contains("&lt;"))
        assertFalse(escapedAuth.contains("""name="csrf-token""""))
        assertTrue(escapedAuth.contains("<title>"))
        assertThrows(FireflyException::class.java) {
            AuthLayout().render(
                ChromeContext(KonaTheme(), RenderHooks(), emptyMap(), "", mode = ChromeMode.AUTH),
            )
        }
        val blankOptional = AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->ok<!-- ff-hook:body.end --></main>"""),
                "/* css */",
                csrfToken = "   ",
                documentTitle = "Sign in",
                mode = ChromeMode.AUTH,
                branding = FireflyBranding.DEFAULT.copy(
                    fontUrl = " ",
                    fontPreconnect = true,
                    copyrightText = " ",
                ),
            ),
        )
        assertFalse(blankOptional.contains("""name="csrf-token""""))
        assertFalse(blankOptional.contains("rel=\"preconnect\""))
        assertFalse(blankOptional.contains("ff-auth-copyright"))
        assertTrue(blankOptional.contains("<title>Sign in</title>"))

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

        val bad = object : FireflyTheme() {
            override fun id() = "bad"
            override fun tokens() = KonaTheme().tokens()
            override fun partial(slot: String) = if (slot == "sidebar") "<aside></aside>" else null
        }
        assertThrows(FireflyException::class.java) { Panel().theme(bad).render() }

        val customPartial = object : FireflyTheme() {
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

    @Test
    fun themeClassesAppendOnChromeAndDefaultAddsNothing() {
        val stock = Panel().render()
        assertEquals(stock, Panel().theme(FireflyTheme()).render())
        assertTrue(stock.contains("""<aside class="ff-sidebar">"""))
        assertTrue(stock.contains("""<header class="ff-topbar">"""))
        assertTrue(stock.contains("""<main class="ff-body">"""))
        assertTrue(stock.contains("""<footer class="ff-footer">"""))
        assertFalse(stock.contains("theme-sidebar"))
        assertFalse(stock.contains("""rel="stylesheet" href="""))

        val bare = FireflyTheme()
        assertEquals("custom", bare.id())
        assertEquals(KonaTheme().tokens().primary, bare.tokens().primary)
        assertEquals(null, bare.stylesheet())
        assertEquals("", bare.sidebar())
        assertEquals("", bare.navigationItemActive())
        assertEquals("", bare.navigationGroupActive())
        assertEquals("", sanitizeThemeClasses("   "))
        assertEquals("", sanitizeThemeClasses("<script>"))
        assertEquals("bg-slate-950 dark:text-white", sanitizeThemeClasses("bg-slate-950 \"nope\" dark:text-white"))
        assertEquals("bg-[#0f172a]", sanitizeThemeClasses("bg-[#0f172a]"))
        assertFalse(legalThemeClass(""))
        assertFalse(legalThemeClass("a<b"))
        assertTrue(legalThemeClass("[&_a]:underline"))
        assertEquals("", escapeThemeAttr(""))
        assertEquals("a&amp;b&quot;&lt;c", escapeThemeAttr("a&b\"<c"))

        val untouched = """<nav class="ff-nav ff-nav-side"><li class="ff-nav-item"></li></nav>"""
        val navOnly = appendThemeClasses(untouched, setOf("ff-nav"), "theme-nav")
        assertTrue(navOnly.contains("""class="ff-nav ff-nav-side theme-nav""""))
        assertTrue(navOnly.contains("""class="ff-nav-item""""))
        val bodyOnly = appendThemeClasses(
            """<body class="ff-body-root"><main class="ff-body "></main></body>""",
            setOf("ff-body"),
            "theme-content",
        )
        assertTrue(bodyOnly.contains("""class="ff-body-root""""))
        assertTrue(bodyOnly.contains("""class="ff-body theme-content""""))
        val already = """<aside class="ff-sidebar theme-sidebar"></aside>"""
        assertEquals(already, appendThemeClasses(already, setOf("ff-sidebar"), "theme-sidebar"))

        val theme = object : FireflyTheme() {
            override fun sidebar() = "theme-sidebar"
            override fun topbar() = "theme-topbar"
            override fun brand() = "theme-brand"
            override fun navigation() = "theme-nav"
            override fun navigationItem() = "theme-item"
            override fun navigationGroup() = "theme-group"
            override fun content() = "theme-content"
            override fun pageHeader() = "theme-header"
            override fun footer() = "theme-footer"
            override fun sidebarFoot() = "theme-foot"
            override fun userMenu() = "theme-user"
            override fun panel() = "theme-panel"
            override fun column() = "theme-column"
            override fun scroll() = "theme-scroll"
            override fun auth() = "theme-auth"
            override fun authCard() = "theme-auth-card <bad>"
            override fun stylesheet() = "/css/midnight.css"
        }
        val html = Panel().theme(theme)
            .hook(
                Hook.SIDEBAR_START,
                """<div class="ff-sidebar-brand"><span class="ff-brand">Acme</span></div>""" +
                    """<nav class="ff-nav ff-nav-side"><ul class="ff-nav-list">""" +
                    """<li class="ff-nav-item">Home</li>""" +
                    """<section class="ff-nav-group">Shop</section></ul></nav>""" +
                    """<div class="ff-sidebar-foot">foot</div>""",
            )
            .hook(Hook.TOPBAR_END, """<span class="ff-brand-mark ff-topbar-brand">T</span><div class="ff-user-menu">u</div>""")
            .hook(Hook.BODY_START, """<header class="ff-page-header"><h1>Desk</h1></header>""")
            .render()
        assertTrue(html.contains("""<aside class="ff-sidebar theme-sidebar">"""))
        assertTrue(html.contains("""<header class="ff-topbar theme-topbar">"""))
        assertTrue(html.contains("""class="ff-sidebar-brand theme-brand""""))
        assertTrue(html.contains("""class="ff-brand-mark ff-topbar-brand theme-brand""""))
        assertTrue(html.contains("""class="ff-nav ff-nav-side theme-nav""""))
        assertTrue(html.contains("""class="ff-nav-item theme-item""""))
        assertTrue(html.contains("""class="ff-nav-group theme-group""""))
        assertTrue(html.contains("""<main class="ff-body theme-content">"""))
        assertTrue(html.contains("""class="ff-page-header theme-header""""))
        assertTrue(html.contains("""<footer class="ff-footer theme-footer">"""))
        assertTrue(html.contains("""class="ff-sidebar-foot theme-foot""""))
        assertTrue(html.contains("""class="ff-user-menu theme-user""""))
        assertTrue(html.contains("""class="ff-panel theme-panel""""))
        assertTrue(html.contains("""class="ff-column theme-column""""))
        assertTrue(html.contains("""class="ff-scroll theme-scroll""""))
        assertTrue(html.contains("""href="/css/midnight.css""""))
        assertTrue(html.indexOf("""href="/css/midnight.css"""") < html.indexOf("</head>"))
        assertTrue(html.indexOf("</head>") < html.indexOf("<body"))
        assertTrue(html.indexOf("<body") < html.indexOf("""<aside class="ff-sidebar"""))
        assertFalse(html.contains("ff-theme-pending"))
        assertFalse(html.contains("html.ff-theme-pending{visibility:hidden}"))
        assertFalse(html.contains("data-ff-theme-reveal"))
        assertFalse(html.contains("ff-theme-hold"))
        assertTrue(html.contains(".ff-sidebar {") || html.contains(".ff-panel .ff-sidebar"))
        assertFalse(html.contains("theme-sidebar {"))

        val auth = Panel().theme(theme).mode(ChromeMode.AUTH).layout(AuthLayout())
            .overridePartial(
                "body",
                """<main class="ff-auth-main"><!-- ff-hook:body.start --><div class="ff-auth-panel">card</div><!-- ff-hook:body.end --></main>""",
            )
            .render()
        assertTrue(auth.contains("""class="ff-auth theme-auth""""))
        assertTrue(auth.contains("""class="ff-auth-panel theme-auth-card""""))
        assertFalse(auth.contains("<bad>"))
        assertFalse(auth.contains("""<aside class="ff-sidebar""""))
        assertTrue(auth.indexOf("""href="/css/midnight.css"""") < auth.indexOf("</head>"))
        assertTrue(auth.indexOf("</head>") < auth.indexOf("<body"))
        assertFalse(auth.contains("ff-theme-pending"))
        assertFalse(auth.contains("data-ff-theme-reveal"))

        val blankSheet = object : FireflyTheme() {
            override fun stylesheet() = "   "
        }
        val blankHtml = Panel().theme(blankSheet).render()
        assertFalse(blankHtml.contains("""rel="stylesheet" href="""))
        assertFalse(blankHtml.contains("ff-theme-pending"))
        assertFalse(blankHtml.contains("data-ff-theme-reveal"))
        assertFalse(blankHtml.contains("ff-theme-hold"))
        val escaped = object : FireflyTheme() {
            override fun stylesheet() = """/c?a&b"<"""
        }
        assertTrue(Panel().theme(escaped).render().contains("""href="/c?a&amp;b&quot;&lt;""""))
        assertEquals("no-head", withThemeStylesheet("no-head", theme))
    }

    @Test
    fun activeNavHooksMergeOntoTheOpenPageOnly() {
        val nav = """
            <nav class="ff-nav">
              <ul>
                <li class="ff-nav-item">Idle</li>
                <li class="ff-nav-item ff-active">Open</li>
                <li class="ff-nav-item ff-cluster ff-active">Group cluster</li>
                <li class="ff-nav-item ff-cluster">Shut cluster</li>
                <li class="ff-nav-item ff-cluster ff-nav-group ff-nav-dropdown ff-active">
                  <button type="button" class="ff-nav-dropdown-trigger">Top group</button>
                </li>
                <section class="ff-nav-group">Other</section>
                <section class="ff-nav-group ff-nav-group-active">Open group</section>
                <a class="ff-menu-link ff-active" href="/profile">Profile</a>
              </ul>
            </nav>
        """.trimIndent()
        assertEquals(nav, applyThemeClasses(nav, FireflyTheme()))
        val theme = object : FireflyTheme() {
            override fun navigationItem() = "theme-item"
            override fun navigationItemActive() = "theme-item-active <bad>"
            override fun navigationGroup() = "theme-group"
            override fun navigationGroupActive() = "theme-group-active"
        }
        val html = applyThemeClasses(nav, theme)
        assertTrue(html.contains("""class="ff-nav-item theme-item""""))
        assertTrue(html.contains("""class="ff-nav-item ff-active theme-item theme-item-active""""))
        assertTrue(html.contains("""class="ff-nav-item ff-cluster ff-active theme-item theme-group-active""""))
        assertTrue(html.contains("""class="ff-nav-item ff-cluster theme-item""""))
        assertTrue(
            html.contains(
                """class="ff-nav-item ff-cluster ff-nav-group ff-nav-dropdown ff-active theme-item theme-group theme-group-active""""
            ),
        )
        assertTrue(html.contains("""class="ff-nav-dropdown-trigger theme-group-active""""))
        assertTrue(html.contains("""class="ff-nav-group theme-group""""))
        assertTrue(html.contains("""class="ff-nav-group ff-nav-group-active theme-group theme-group-active""""))
        assertTrue(html.contains("""class="ff-menu-link ff-active""""))
        assertFalse(html.contains("ff-menu-link ff-active theme-"))
        assertFalse(html.contains("<bad>"))
        assertFalse(html.contains("theme-item-active theme-item-active"))
    }

    @Test
    fun hookClassListsLandOnThePaintedActiveItem() {
        val side = applyThemeClasses(
            """<aside class="ff-sidebar"></aside>""",
            object : FireflyTheme() {
                override fun sidebar() = "alpha rounded-tl-none"
            },
        )
        assertTrue(side.contains("""class="ff-sidebar alpha rounded-tl-none""""))

        val nav = """
            before
            <!DOCTYPE html>
            <!-- note -->
            <script>if (a < b) {}</script>
            <style>.a::before { content: "<"; }</style>
            <link rel="stylesheet" href="/css/app.css">
            <my-el></my-el>
            <foo:bar></foo:bar>
            <div class="keep  me"></div>
            <section class="ff-nav-group ff-nav-group-active">
              <h3 class="ff-nav-group-label">Sales</h3>
              <ul>
                <li class="ff-nav-item ff-active"><a href="/open">Open</a></li>
                <li class="ff-nav-item"><a href="/idle">Idle</a></li>
                <li class="ff-nav-item ff-active"><a class="alpha" href="/part">Part</a></li>
                <li class="ff-nav-item ff-active"><a class="alpha  rounded-tl-none" href="/same">Same</a></li>
                <li class="ff-nav-item ff-active">< a href="/spaced">Spaced</a></li>
                <li class="ff-nav-item ff-active"><a/></li>
              </ul>
            </section>
            <li class="ff-nav-item ff-cluster ff-active">
              <button type="button" class="ff-nav-dropdown-trigger">Group</button>
              <ul><li class="ff-nav-item"><a href="/child">Child</a></li></ul>
            </li>
            <details class="ff-nav-group ff-nav-group-active">
              <summary class="ff-nav-group-label">Drawer</summary>
            </details>
            <img src="mark.svg">
            tail
        """.trimIndent()
        val theme = object : FireflyTheme() {
            override fun navigationItem() = "theme-item"
            override fun navigationItemActive() = "alpha rounded-tl-none <bad>"
            override fun navigationGroup() = "theme-group"
            override fun navigationGroupActive() = "theme-group-active"
        }
        val html = applyThemeClasses(nav, theme)
        assertTrue(html.contains("""if (a < b)"""))
        assertTrue(html.contains("""content: "<";"""))
        assertTrue(html.contains("""<!DOCTYPE html>"""))
        assertTrue(html.contains("""<!-- note -->"""))
        assertTrue(html.contains("""<a href="/open" class="alpha rounded-tl-none">"""), html)
        assertFalse(html.contains("<bad>"))
        assertTrue(html.contains("""<a href="/idle">"""))
        assertTrue(html.contains("""<a class="alpha rounded-tl-none" href="/part">"""))
        assertTrue(html.contains("""<a class="alpha  rounded-tl-none" href="/same">"""))
        assertFalse(html.contains("rounded-tl-none rounded-tl-none"))
        assertTrue(html.contains("""<a class="alpha rounded-tl-none"/>"""))
        assertTrue(html.contains("""< a href="/spaced" class="alpha rounded-tl-none">"""), html)
        assertTrue(html.contains("""class="ff-nav-group-label theme-group-active""""))
        assertTrue(html.contains("""class="ff-nav-dropdown-trigger theme-group-active""""))
        assertTrue(html.contains("""<a href="/child">"""))
        assertFalse(html.contains("""href="/child" class=""""))
        assertFalse(html.contains("""href="/idle" class=""""))
        assertTrue(html.contains("""class="ff-nav-item ff-active theme-item alpha rounded-tl-none""""))
        assertTrue(html.contains("tail"))
        assertEquals(nav, copyPaintedThemeClasses(nav, setOf("ff-nav-item"), "", setOf("ff-active"), setOf("ff-cluster")))
        assertTrue(copyPaintedThemeClasses("<!-- bare", setOf("ff-nav-item"), "alpha", setOf("ff-active"), emptySet()).startsWith("<!--"))
        assertTrue(copyPaintedThemeClasses("<script>if (a < b)", setOf("ff-nav-item"), "alpha", setOf("ff-active"), emptySet()).contains("a < b"))
        assertTrue(copyPaintedThemeClasses("<style>.a { content: \"<\"", setOf("ff-nav-item"), "alpha", setOf("ff-active"), emptySet()).contains("content"))
        assertTrue(copyPaintedThemeClasses("<li class=\"ff-nav-item ff-active\"", setOf("ff-nav-item"), "alpha", setOf("ff-active"), emptySet()).contains("ff-active"))
        val closed = copyPaintedThemeClasses("</orphan>plain", setOf("ff-nav-item"), "alpha", setOf("ff-active"), emptySet())
        assertTrue(closed.contains("</orphan>plain"))
    }

    @Test
    fun themeTailwindAndGoogleFonts() {
        assertFalse(FireflyTheme().useTailwind())
        assertTrue(FireflyTheme().googleFonts().isEmpty())
        assertEquals(null, googleFontsHref(listOf(" ", "")))
        assertEquals(
            "https://fonts.googleapis.com/css2?family=Figtree:wght@400;500;600;700&display=swap",
            googleFontsHref(listOf(" ", "Figtree")),
        )
        assertEquals(
            "https://fonts.googleapis.com/css2?family=Figtree:wght@400;500;600;700&family=Source+Sans+3:wght@400;500;600;700&display=swap",
            googleFontsHref(listOf("Figtree", "Source Sans 3")),
        )
        assertTrue(googleFontsHref(listOf("""Figtree"><x"""))!!.contains("Figtree%22%3E%3Cx"))

        val stock = Panel().render()
        assertTrue(stock.indexOf("getItem('ff-theme')") < stock.indexOf("<body"))
        assertFalse(stock.contains("fonts.googleapis.com"))
        assertFalse(stock.contains("fonts.gstatic.com"))
        assertFalse(stock.contains("/_firefly/tailwind.js"))
        assertFalse(stock.contains("text/tailwindcss"))
        assertFalse(stock.contains("ff-theme-pending"))
        assertFalse(stock.contains("html.ff-theme-pending{visibility:hidden}"))
        assertFalse(stock.contains("data-ff-theme-reveal"))
        assertFalse(stock.contains("ff-theme-hold"))
        assertFalse(stock.contains("--ff-theme-compiled"))

        val fonts = object : FireflyTheme() {
            override fun googleFonts() = listOf("Figtree", "Source Sans 3")
        }
        val fontPanel = Panel().theme(fonts).render()
        val fontAuth = authPage(fonts)
        for (html in listOf(fontPanel, fontAuth)) {
            assertTrue(html.contains("fonts.googleapis.com"))
            assertTrue(html.contains("fonts.gstatic.com"))
            assertTrue(html.contains("family=Figtree"))
            assertTrue(html.contains("Source+Sans+3"))
            assertTrue(html.contains("display=swap"))
            assertTrue(html.contains("""rel="preconnect" href="https://fonts.googleapis.com""""))
            assertTrue(html.indexOf("family=Figtree") < html.indexOf("</head>"))
            assertTrue(html.indexOf("</head>") < html.indexOf("<body"))
            assertFalse(html.contains("/_firefly/tailwind.js"))
            assertFalse(html.contains("ff-theme-pending"))
            assertFalse(html.contains("html.ff-theme-pending{visibility:hidden}"))
            assertFalse(html.contains("data-ff-theme-reveal"))
        }

        val blankFonts = object : FireflyTheme() {
            override fun googleFonts() = listOf("  ", "")
        }
        assertFalse(Panel().theme(blankFonts).render().contains("fonts.googleapis.com"))

        val tailwind = object : FireflyTheme() {
            override fun useTailwind() = true
            override fun stylesheet() = "/css/midnight.css"
            override fun sidebar() = "bg-slate-950"
        }
        val twPanel = Panel().theme(tailwind).render()
        val twAuth = authPage(tailwind)
        assertTrue(twPanel.contains("bg-slate-950"))
        for (html in listOf(twPanel, twAuth)) {
            assertTrue(html.contains("""src="/_firefly/tailwind.js""""))
            assertTrue(html.contains("text/tailwindcss"))
            assertTrue(html.contains("""@import "tailwindcss/utilities";"""))
            assertTrue(html.contains("html { --ff-theme-compiled: 1; }"))
            assertFalse(html.contains("ff-theme-paint"))
            assertTrue(html.contains("""data-ff-tailwind-href="/css/midnight.css""""))
            assertTrue(html.contains("""xhr.open("GET",href,false)"""))
            assertFalse(html.contains("""rel="stylesheet" href="/css/midnight.css""""))
            assertFalse(html.contains("fonts.googleapis.com"))
            val headEnd = html.indexOf("</head>")
            val bodyAt = html.indexOf("<body")
            assertTrue(html.indexOf("getItem('ff-theme')") < bodyAt)
            assertTrue(html.indexOf("getItem('ff-theme')") < html.indexOf("ff-theme-pending"))
            assertTrue(html.indexOf("html.ff-theme-pending{visibility:hidden}") < headEnd)
            assertTrue(html.indexOf("ff-theme-pending") < html.indexOf("data-ff-tailwind-href"))
            assertTrue(html.indexOf("data-ff-tailwind-href") < html.indexOf("/_firefly/tailwind.js"))
            assertTrue(html.indexOf("/_firefly/tailwind.js") < headEnd)
            assertTrue(headEnd < bodyAt)
            val revealAt = html.indexOf("data-ff-theme-reveal")
            assertTrue(revealAt > bodyAt)
            assertTrue(revealAt < html.indexOf("</body>"))
            val reveal = html.substring(revealAt)
            assertTrue(reveal.contains("--ff-theme-compiled"))
            assertTrue(reveal.indexOf("--ff-theme-compiled") < reveal.indexOf("""style.visibility="visible""""))
            assertFalse(reveal.contains("classList.remove"))
            assertFalse(reveal.contains("classList.add"))
        }
        assertTrue(twPanel.indexOf("</head>") < twPanel.indexOf("""<aside class="ff-sidebar"""))
        assertTrue(twPanel.indexOf("</head>") < twPanel.indexOf("""<header class="ff-topbar""""))
        assertTrue(twPanel.indexOf("</head>") < twPanel.indexOf("""<main class="ff-body""""))
        assertTrue(twAuth.indexOf("</head>") < twAuth.indexOf("""class="ff-auth-panel""""))
        assertTrue(twPanel.contains("html.ff-theme-pending{visibility:hidden}"))
        assertTrue(twPanel.contains("html.ff-dark.ff-theme-pending{background:#1F2937}"))
        assertFalse(twPanel.contains("html.ff-theme-pending{background:"))
        assertTrue(twAuth.contains("html.ff-theme-pending{visibility:hidden}"))
        assertFalse(twAuth.contains("html.ff-dark.ff-theme-pending{background:"))
        assertEquals("no-head", withThemeStylesheet("no-head", tailwind))
        val headOnly = withThemeStylesheet("<head></head>", tailwind)
        assertTrue(headOnly.contains("""id="ff-theme-hold""""))
        assertTrue(headOnly.contains("#1F2937"))
        assertFalse(headOnly.contains("data-ff-theme-reveal"))
        val bodyOnly = withThemeStylesheet("<body class=\"ff-auth\"></body>", tailwind)
        assertTrue(bodyOnly.contains("data-ff-theme-reveal"))
        assertFalse(bodyOnly.contains("""id="ff-theme-hold""""))

        val painted = object : FireflyTheme() {
            override fun useTailwind() = true
            override fun stylesheet() = "/css/both.css"
            override fun googleFonts() = listOf("Figtree")
            override fun tokens() = ThemeTokens(
                primary = "#111111",
                accent = "#222222",
                surface = "#333333",
                heading = "#444444",
                font = "sans-serif",
                radius = "0",
                sidebarWidth = "1rem",
                topbarHeight = "1rem",
                bodyBackground = "#abc</style>",
                darkPrimary = "#111111",
                darkSurface = "#def<bad>",
                darkHeading = "#eeeeee",
            )
        }
        val paintedPanel = Panel().theme(painted).render()
        val paintedAuth = authPage(painted)
        assertTrue(paintedPanel.contains("html.ff-theme-pending{visibility:hidden}"))
        assertTrue(paintedPanel.contains("html.ff-dark.ff-theme-pending{background:#defbad}"))
        assertFalse(paintedPanel.contains("html.ff-theme-pending{background:#abc"))
        assertTrue(paintedAuth.contains("html.ff-theme-pending{visibility:hidden}"))
        assertFalse(paintedAuth.contains("html.ff-dark.ff-theme-pending{background:"))
        assertTrue(paintedPanel.indexOf("/_firefly/tailwind.js") < paintedPanel.indexOf("family=Figtree"))
        assertTrue(paintedPanel.indexOf("family=Figtree") < paintedPanel.indexOf("</head>"))
        assertTrue(paintedAuth.indexOf("/_firefly/tailwind.js") < paintedAuth.indexOf("family=Figtree"))

        val utilitiesOnly = object : FireflyTheme() {
            override fun useTailwind() = true
            override fun stylesheet() = "   "
        }
        val utilitiesHtml = Panel().theme(utilitiesOnly).render()
        assertTrue(utilitiesHtml.contains("""src="/_firefly/tailwind.js""""))
        assertTrue(utilitiesHtml.contains("ff-theme-pending"))
        assertTrue(utilitiesHtml.contains("data-ff-theme-reveal"))
        assertTrue(utilitiesHtml.indexOf("/_firefly/tailwind.js") < utilitiesHtml.indexOf("</head>"))
        assertTrue(utilitiesHtml.indexOf("</head>") < utilitiesHtml.indexOf("<body"))
        assertTrue(utilitiesHtml.indexOf("data-ff-theme-reveal") > utilitiesHtml.indexOf("<body"))
        assertFalse(utilitiesHtml.contains("data-ff-tailwind-href"))
        assertFalse(utilitiesHtml.contains("""rel="stylesheet" href=""""))

        val quoted = object : FireflyTheme() {
            override fun useTailwind() = true
            override fun stylesheet() = """/c?a&b"<"""
        }
        assertTrue(Panel().theme(quoted).render().contains("""data-ff-tailwind-href="/c?a&amp;b&quot;&lt;""""))

        val branded = object : FireflyTheme() {
            override fun useTailwind() = true
            override fun sidebar() = "bg-primary-800 text-primary-50 text-accent-700 border-accent-500 text-success-500 text-danger-700 bg-warning-200 border-info-900 bg-gray-500"
        }
        val brandedHtml = Panel().theme(branded).branding(
            FireflyBranding(primaryColor = "#FF2A00", accentColor = "#286291"),
        ).render()
        val brandedAuth = authPage(branded)
        assertTrue(brandedHtml.contains("bg-primary-800"))
        assertTrue(brandedHtml.contains("text-primary-50"))
        assertTrue(brandedHtml.contains("text-accent-700"))
        assertTrue(brandedHtml.contains("border-accent-500"))
        assertTrue(brandedHtml.contains("text-success-500"))
        assertTrue(brandedHtml.contains("text-danger-700"))
        assertTrue(brandedHtml.contains("bg-warning-200"))
        assertTrue(brandedHtml.contains("border-info-900"))
        assertTrue(brandedHtml.contains("bg-gray-500"))
        assertTrue(brandedHtml.contains("--ff-primary: #ff2a00"))
        assertTrue(brandedHtml.contains("--ff-accent: #286291"))
        for (html in listOf(brandedHtml, brandedAuth)) {
            assertTrue(html.contains("@theme inline {"))
            assertTrue(html.contains("--ff-primary-500: var(--ff-primary);"))
            assertTrue(html.contains("--ff-accent-500: var(--ff-accent);"))
            assertTrue(html.contains("--color-primary-500: var(--ff-primary-500);"))
            assertTrue(html.contains("--color-accent-500: var(--ff-accent-500);"))
            assertTrue(html.contains("--color-primary: var(--ff-primary);"))
            assertTrue(html.contains("--color-accent: var(--ff-accent);"))
            assertTrue(html.contains("--color-heading: var(--ff-heading);"))
            assertTrue(html.contains("--color-surface: var(--ff-surface);"))
            assertTrue(html.contains("--ff-success: #16a34a;"))
            assertTrue(html.contains("--ff-warning: #d97706;"))
            assertTrue(html.contains("--ff-danger: #dc2626;"))
            assertTrue(html.contains("--ff-info: #2563eb;"))
            assertTrue(html.contains("--ff-success-500: var(--ff-success);"))
            assertTrue(html.contains("--ff-warning-500: var(--ff-warning);"))
            assertTrue(html.contains("--ff-danger-500: var(--ff-danger);"))
            assertTrue(html.contains("--ff-info-500: var(--ff-info);"))
            assertTrue(html.contains("--ff-gray-500: var(--ff-body-subtle);"))
            assertTrue(html.contains("--color-success: var(--ff-success);"))
            assertTrue(html.contains("--color-warning: var(--ff-warning);"))
            assertTrue(html.contains("--color-danger: var(--ff-danger);"))
            assertTrue(html.contains("--color-info: var(--ff-info);"))
            assertTrue(html.contains("--color-gray: var(--ff-body-subtle);"))
            assertTrue(html.contains("color-mix(in srgb, var(--ff-primary) 8%, white)"))
            assertTrue(html.contains("color-mix(in srgb, var(--ff-primary) 74%, white)"))
            assertTrue(html.contains("color-mix(in srgb, var(--ff-accent) 84%, black)"))
            assertTrue(html.contains("color-mix(in srgb, var(--ff-accent) 32%, black)"))
            val themeStart = html.indexOf("@theme inline {")
            val themeEnd = html.indexOf("}", themeStart)
            assertTrue(themeStart >= 0 && themeEnd > themeStart)
            assertFalse(html.substring(themeStart, themeEnd).contains("#"))
            val scales = listOf(
                "primary" to "--ff-primary",
                "accent" to "--ff-accent",
                "success" to "--ff-success",
                "warning" to "--ff-warning",
                "danger" to "--ff-danger",
                "info" to "--ff-info",
                "gray" to "--ff-body-subtle",
            )
            for ((name, source) in scales) {
                for (step in listOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900)) {
                    assertTrue(html.contains("--color-$name-$step: var(--ff-$name-$step);"))
                    assertTrue(html.contains("--ff-$name-$step:"))
                }
                assertTrue(html.contains("--ff-$name-500: var($source);"))
            }
        }

        val off = Panel().render()
        for (name in listOf("primary", "accent", "success", "warning", "danger", "info", "gray")) {
            assertFalse(off.contains("--color-$name-500"))
            assertFalse(off.contains("--ff-$name-500"))
            assertFalse(off.contains("--color-$name:"))
        }
        assertFalse(off.contains("--ff-success:"))
        assertFalse(off.contains("--ff-warning:"))
        assertFalse(off.contains("--ff-danger:"))
        assertFalse(off.contains("--ff-info:"))
        assertFalse(off.contains("@theme inline"))
        assertFalse(off.contains("text/tailwindcss"))
        assertFalse(off.contains("/_firefly/tailwind.js"))

        val saved = FireflyTailwind.resourcePath
        try {
            val bytes = FireflyTailwind.bytes()
            assertTrue(bytes != null)
            assertTrue(bytes!!.isNotEmpty())
            assertTrue(String(bytes).contains("tailwindcss"))
            FireflyTailwind.resourcePath = "/org/konaframework/firefly/support/no-such-tailwind.js"
            assertEquals(null, FireflyTailwind.bytes())
        } finally {
            FireflyTailwind.resourcePath = saved
        }
    }

    @Test
    fun compiledThemeAssetResolvesAndExpands() {
        val bare = themeCompileSource(".a { color: var(--ff-primary); }", emptyList())
        assertTrue(bare.contains("""@import "tailwindcss/theme";"""))
        assertTrue(bare.contains("""@import "tailwindcss/utilities";"""))
        assertTrue(bare.contains("@theme inline {"))
        assertFalse(bare.contains("@source"))
        assertTrue(bare.endsWith("\n"))
        val kept = themeCompileSource(".a { color: var(--ff-primary); }\n", emptyList())
        assertTrue(kept.endsWith("}\n") || kept.endsWith(";\n"))
        val sourced = themeCompileSource(
            ".a { color: red; }",
            listOf("/app/src/main/kotlin/app/panels/admin/themes/**/*.kt"),
        )
        assertTrue(sourced.contains("""@source "/app/src/main/kotlin/app/panels/admin/themes/**/*.kt";"""))
        assertTrue(finishCompiledCss(".ok { color: var(--ff-accent); }").contains("color-mix(in srgb, var(--ff-primary)"))

        assertEquals("ff-asset:css/foo.css", ThemeAssets.asset("css/foo.css"))
        assertEquals(ThemeAssets.asset("css/foo.css"), ThemeAssets.asset("/css/foo.css"))
        assertEquals(ThemeAssets.asset("css/foo.css"), ThemeAssets.asset("src/main/resources/css/foo.css"))
        assertEquals(ThemeAssets.asset("css/foo.css"), ThemeAssets.asset("ff-asset:css/foo.css"))
        assertEquals("node", ThemeCssCompiler.nodeBinary)
        assertTrue(ThemeCssCompiler.cacheDir.toString().contains("tailwind-"))
        assertFalse(ThemeAssets.isCompiledAsset(null))
        assertFalse(ThemeAssets.isCompiledAsset("  "))
        assertFalse(ThemeAssets.isCompiledAsset("/css/foo.css"))
        assertTrue(ThemeAssets.isCompiledAsset("  ff-asset:css/foo.css"))
        for (bad in listOf("", "..", "css\\foo.css", "https://cdn.example/a.css")) {
            assertThrows(FireflyException::class.java) { ThemeAssets.asset(bad) }
        }

        val root = Files.createTempDirectory("theme-asset")
        ThemeAssets.root = root
        Files.createDirectories(root.resolve("src/main/resources/css"))
        Files.writeString(
            root.resolve("src/main/resources/css/missing.css"),
            ".ff-panel .dev-only { color: red; } /* </style> */\n",
        )
        val missing = object : FireflyTheme() {
            override fun stylesheet() = asset("css/missing.css")
        }
        val missingHtml = Panel().theme(missing).render()
        assertTrue(missingHtml.contains("""src="/_firefly/tailwind.js""""))
        assertTrue(missingHtml.contains("text/tailwindcss"))
        assertTrue(missingHtml.contains("""<style type="text/tailwindcss" data-ff-theme-sheet>"""))
        assertTrue(missingHtml.contains(".ff-panel .dev-only { color: red; }"))
        assertTrue(missingHtml.contains("/* <\\/style> */"))
        assertFalse(missingHtml.contains("/* </style> */"))
        assertFalse(missingHtml.contains("""rel="stylesheet" href="/css/missing.css""""))
        assertFalse(missingHtml.contains("data-ff-tailwind-href"))
        assertFalse(missingHtml.contains("ff-asset:"))
        assertTrue(missingHtml.contains("html.ff-theme-pending{visibility:hidden}"))
        assertTrue(missingHtml.contains("html.ff-dark.ff-theme-pending{background:#1F2937}"))
        assertFalse(missingHtml.contains("html.ff-theme-pending{background:"))
        assertTrue(missingHtml.contains("data-ff-theme-reveal"))
        assertTrue(missingHtml.indexOf(".dev-only") < missingHtml.indexOf("/_firefly/tailwind.js"))
        assertTrue(missingHtml.indexOf("/_firefly/tailwind.js") < missingHtml.indexOf("</head>"))
        val reveal = missingHtml.substring(missingHtml.indexOf("data-ff-theme-reveal"))
        assertTrue(reveal.contains("--ff-theme-compiled"))
        assertFalse(reveal.contains("classList.remove"))
        val missingAuth = authPage(missing)
        assertTrue(missingAuth.contains("""src="/_firefly/tailwind.js""""))
        assertTrue(missingAuth.contains(".ff-panel .dev-only { color: red; }"))
        assertTrue(missingAuth.contains("html.ff-theme-pending{visibility:hidden}"))
        assertFalse(missingAuth.contains("html.ff-dark.ff-theme-pending{background:"))
        assertFalse(missingAuth.contains("""href="/css/missing.css""""))
        assertTrue(missingAuth.contains("data-ff-theme-reveal"))

        val absent = object : FireflyTheme() {
            override fun stylesheet() = asset("css/absent.css")
        }
        val absentHtml = Panel().theme(absent).render()
        assertTrue(absentHtml.contains("""src="/_firefly/tailwind.js""""))
        assertFalse(absentHtml.contains("""<style type="text/tailwindcss" data-ff-theme-sheet>"""))
        assertFalse(absentHtml.contains(".dev-only"))
        assertFalse(absentHtml.contains("""href="/css/absent.css""""))
        assertEquals("", ThemeAssets.uncompiledCss(absent.stylesheet()!!))

        Files.createDirectories(root.resolve("public/css"))
        Files.writeString(root.resolve("public/css/foo.css"), "/* compiled */\n")
        Files.writeString(
            root.resolve("src/main/resources/css/foo.css"),
            ".ff-panel .compiled-source { color: blue; }\n",
        )
        val compiled = object : FireflyTheme() {
            override fun stylesheet() = asset("css/foo.css")
            override fun useTailwind() = true
            override fun googleFonts() = listOf("Figtree")
        }
        val html = Panel().theme(compiled).render()
        assertTrue(html.contains("""src="/_firefly/tailwind.js""""))
        assertTrue(html.contains("text/tailwindcss"))
        assertTrue(html.contains(".ff-panel .compiled-source { color: blue; }"))
        assertTrue(html.contains("family=Figtree"))
        assertFalse(html.contains("ff-asset:"))
        assertFalse(html.contains("""href="/css/foo.css""""))
        assertFalse(html.contains("data-ff-tailwind-href"))
        assertTrue(html.contains("ff-theme-pending"))
        assertTrue(html.contains("data-ff-theme-reveal"))
        assertTrue(html.contains("ff-theme-hold"))
        assertTrue(html.indexOf("/_firefly/tailwind.js") < html.indexOf("family=Figtree"))
        val auth = authPage(compiled)
        assertTrue(auth.contains("""src="/_firefly/tailwind.js""""))
        assertTrue(auth.contains(".compiled-source"))
        assertFalse(auth.contains("""href="/css/foo.css""""))
        assertTrue(auth.contains("data-ff-theme-reveal"))
        val quiet = object : FireflyTheme() {
            override fun stylesheet() = asset("css/foo.css")
        }
        val quietHtml = Panel().theme(quiet).render()
        assertTrue(quietHtml.contains("""href="/css/foo.css""""))
        assertFalse(quietHtml.contains("/_firefly/tailwind.js"))
        assertFalse(quietHtml.contains("text/tailwindcss"))
        assertFalse(quietHtml.contains("ff-theme-pending"))
        assertFalse(quietHtml.contains("data-ff-theme-reveal"))
        assertFalse(quietHtml.contains(".compiled-source"))
        assertTrue(ThemeAssets.hasCompiledFile(quiet.stylesheet()!!))
        assertEquals("/css/foo.css", ThemeAssets.publicUrl(quiet.stylesheet()!!))
        assertEquals("no-head", withThemeStylesheet("no-head", compiled))
        assertEquals("no-head", withThemeStylesheet("no-head", missing))

        assertEquals("harbor", ThemeCssCompiler.themeSlug("Harbor"))
        assertEquals("aqua", ThemeCssCompiler.themeSlug("Aqua.CSS"))
        assertEquals("midnighttheme", ThemeCssCompiler.themeSlug("midnighttheme.css"))
        assertThrows(FireflyException::class.java) { ThemeCssCompiler.themeSlug("  ") }
        assertThrows(FireflyException::class.java) { ThemeCssCompiler.themeSlug(".css") }
        assertThrows(FireflyException::class.java) { ThemeCssCompiler.themeSlug("Bad Name") }

        val app = Files.createTempDirectory("theme-app")
        assertThrows(FireflyException::class.java) { ThemeCssCompiler.themeSources(app, null) }
        assertThrows(FireflyException::class.java) { ThemeCssCompiler.themeSources(app, "swatch") }
        val cssDir = app.resolve("src/main/resources/css")
        Files.createDirectories(cssDir)
        Files.createDirectories(cssDir.resolve("skip.css"))
        Files.writeString(cssDir.resolve("notes.txt"), "/* firefly-theme */\n")
        Files.writeString(cssDir.resolve("app.css"), "body { margin: 0; }\n")
        assertThrows(FireflyException::class.java) { ThemeCssCompiler.themeSources(app, null) }
        Files.writeString(cssDir.resolve("b.css"), "/* firefly-theme */\n.b { color: var(--ff-primary); }\n")
        Files.writeString(
            cssDir.resolve("a.css"),
            "/* firefly-theme */\n.ff-panel .a { @apply bg-primary-50; color: var(--ff-accent); }\n",
        )
        Files.writeString(cssDir.resolve("plain.css"), ".plain { color: var(--ff-primary); }\n")
        val found = ThemeCssCompiler.themeSources(app, null)
        assertEquals(listOf("a.css", "b.css"), found.map { it.fileName.toString() })
        assertEquals("plain.css", ThemeCssCompiler.themeSources(app, "plain").single().fileName.toString())

        val cache = Files.createTempDirectory("tw-cache")
        assertEquals(
            cache.resolve("node_modules"),
            ThemeCssCompiler.locateTailwindModules(null, null, cache) {
                Files.createDirectories(it.resolve("node_modules/@tailwindcss/cli"))
            },
        )
        val installError = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.locateTailwindModules(null, null, Files.createTempDirectory("tw-fail")) {
                throw FireflyException("npm broke")
            }
        }
        assertTrue(installError.message!!.contains("Could not install"))
        assertTrue(installError.message!!.contains("npm broke"))
        assertTrue(installError.message!!.contains("firefly:theme-css"))
        val emptyInstall = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.locateTailwindModules(null, null, Files.createTempDirectory("tw-empty")) { }
        }
        assertTrue(emptyInstall.message!!.contains("Could not install"))
        val badOverride = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.locateTailwindModules(Files.createTempDirectory("tw-override"), null, cache) { }
        }
        assertTrue(badOverride.message!!.contains("@tailwindcss/cli"))
        val override = Files.createTempDirectory("tw-ok").resolve("node_modules")
        Files.createDirectories(override.resolve("@tailwindcss/cli"))
        assertEquals(override, ThemeCssCompiler.locateTailwindModules(override, null, cache) { })

        val repo = Files.createTempDirectory("tw-repo")
        val walked = repo.resolve("modules/firefly-support/tailwind/node_modules")
        Files.createDirectories(walked.resolve("@tailwindcss/cli"))
        assertEquals(
            walked,
            ThemeCssCompiler.locateTailwindModules(null, repo.resolve("modules/grind"), cache) { },
        )
        var deep = Files.createTempDirectory("tw-deep")
        repeat(10) { deep = deep.resolve("d") }
        Files.createDirectories(deep)
        assertEquals(
            cache.resolve("node_modules"),
            ThemeCssCompiler.locateTailwindModules(null, deep, cache) { },
        )
        assertEquals(
            cache.resolve("node_modules"),
            ThemeCssCompiler.locateTailwindModules(null, Path.of("/"), cache) { },
        )

        val npmHome = Files.createTempDirectory("tw-npm")
        val npmOk = npmHome.resolve("npm-ok")
        Files.writeString(
            npmOk,
            """
            #!/bin/sh
            prefix=""
            prev=""
            for a in "$@"; do
              if [ "${'$'}prev" = "--prefix" ]; then prefix="${'$'}a"; fi
              prev="${'$'}a"
            done
            mkdir -p "${'$'}prefix/node_modules/@tailwindcss/cli"
            printf '{}' > "${'$'}prefix/node_modules/@tailwindcss/cli/package.json"
            exit 0
            """.trimIndent() + "\n",
        )
        npmOk.toFile().setExecutable(true)
        ThemeCssCompiler.npmBinary = npmOk.toString()
        val npmCache = Files.createTempDirectory("tw-npm-cache")
        ThemeCssCompiler.npmInstall(npmCache)
        assertTrue(Files.isDirectory(npmCache.resolve("node_modules/@tailwindcss/cli")))
        val npmBad = npmHome.resolve("npm-bad")
        Files.writeString(npmBad, "#!/bin/sh\necho nope >&2\nexit 1\n")
        npmBad.toFile().setExecutable(true)
        ThemeCssCompiler.npmBinary = npmBad.toString()
        val npmFailed = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.npmInstall(Files.createTempDirectory("tw-npm-fail"))
        }
        assertTrue(npmFailed.message!!.contains("Tailwind install failed"))
        assertTrue(npmFailed.message!!.contains("nope"))
        ThemeCssCompiler.npmBinary = "kona-missing-npm-bin"
        assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.npmInstall(Files.createTempDirectory("tw-npm-missing"))
        }

        val work = Files.createTempDirectory("tw-run")
        ThemeCssCompiler.runChecked(listOf("sh", "-c", "exit 0"), work, 5, "Theme CSS compile failed")
        val failedRun = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.runChecked(listOf("sh", "-c", "echo bad; exit 3"), work, 5, "Theme CSS compile failed")
        }
        assertTrue(failedRun.message!!.contains("Theme CSS compile failed (3)"))
        assertTrue(failedRun.message!!.contains("bad"))
        val timed = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.runChecked(listOf("sh", "-c", "sleep 30"), work, 1, "Theme CSS compile failed")
        }
        assertTrue(timed.message!!.contains("timed out"))
        assertTrue(timed.message!!.contains("firefly:theme-css"))
        val missingNode = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.runChecked(listOf("kona-missing-node-bin"), work, 5, "Theme CSS compile failed")
        }
        assertTrue(missingNode.message!!.contains("Node.js"))

        val fakeModules = Files.createTempDirectory("tw-fake").resolve("node_modules")
        Files.createDirectories(fakeModules.resolve("@tailwindcss/cli/dist"))
        Files.writeString(fakeModules.resolve("@tailwindcss/cli/dist/index.mjs"), "")
        ThemeCssCompiler.moduleOverride = fakeModules
        val source = cssDir.resolve("a.css")
        val dropped = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.compileSource(source, app.resolve("public/css/a.css")) { command, _ ->
                Files.deleteIfExists(Path.of(command[command.indexOf("--output") + 1]))
            }
        }
        assertTrue(dropped.message!!.contains("wrote no output"))
        val noDir = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.compileSource(source, Path.of("/")) { _, _ -> }
        }
        assertTrue(noDir.message!!.contains("no directory"))
        Files.delete(fakeModules.resolve("@tailwindcss/cli/dist/index.mjs"))
        val noCli = assertThrows(FireflyException::class.java) {
            ThemeCssCompiler.compileSource(source, app.resolve("public/css/a.css")) { _, _ -> }
        }
        assertTrue(noCli.message!!.contains("Tailwind CLI"))
        ThemeCssCompiler.moduleOverride = null

        val outside = Files.createTempFile("theme-outside", ".css")
        Files.writeString(outside, ".a { color: var(--ff-primary); }\n")
        assertEquals(null, ThemeCssCompiler.appRootForStylesheet(outside))
        val outsideOut = Files.createTempDirectory("theme-outside-out").resolve("out.css")
        ThemeCssCompiler.compileSource(outside, outsideOut) { command, _ ->
            Files.writeString(Path.of(command[command.indexOf("--output") + 1]), "/* outside */\n")
        }
        val outsideCss = Files.readString(outsideOut)
        assertTrue(outsideCss.contains("/* outside */"))
        assertTrue(outsideCss.contains("var(--ff-primary)"))
        var deepRoot = Files.createTempDirectory("theme-deep")
        repeat(10) { deepRoot = deepRoot.resolve("d") }
        Files.createDirectories(deepRoot)
        assertEquals(null, ThemeCssCompiler.appRootForStylesheet(deepRoot.resolve("a.css")))
        assertEquals(
            app.toAbsolutePath().normalize(),
            ThemeCssCompiler.appRootForStylesheet(cssDir.resolve("a.css"))?.toAbsolutePath()?.normalize(),
        )

        val bareApp = Files.createTempDirectory("theme-bare")
        assertEquals(emptyList<String>(), ThemeCssCompiler.themeClassSources(bareApp))
        Files.createDirectories(bareApp.resolve("src/main/kotlin/app"))
        Files.writeString(bareApp.resolve("src/main/kotlin/app/Page.kt"), "class Page\n")
        assertEquals(emptyList<String>(), ThemeCssCompiler.themeClassSources(bareApp))
        val javaOnly = Files.createTempDirectory("theme-java")
        val javaThemes = javaOnly.resolve("src/main/java/app/panels/shop/themes")
        Files.createDirectories(javaThemes)
        Files.writeString(javaThemes.resolve("Slate.java"), "class Slate {}\n")
        val javaGlobs = ThemeCssCompiler.themeClassSources(javaOnly)
        assertEquals(1, javaGlobs.size)
        assertTrue(javaGlobs.single().endsWith("/themes/**/*.java"))

        val themeDir = app.resolve("src/main/kotlin/app/panels/admin/themes")
        Files.createDirectories(themeDir)
        Files.writeString(
            themeDir.resolve("Harbor.kt"),
            "class Harbor {\n    override fun navigationItemActive(): String = \"rounded-tl-none\"\n}\n",
        )
        val decoy = app.resolve("src/main/kotlin/app/themes")
        Files.createDirectories(decoy)
        Files.writeString(decoy.resolve("Nope.kt"), "class Nope { val swatch = \"bg-[#FF2A00]\" }\n")
        val javaDir = app.resolve("src/main/java/app/panels/admin/themes")
        Files.createDirectories(javaDir)
        Files.writeString(
            javaDir.resolve("Slate.java"),
            "class Slate { public String navigationItemActive() { return \"rounded-br-none\"; } }\n",
        )
        val globs = ThemeCssCompiler.themeClassSources(app)
        assertTrue(globs.any { it.endsWith("/panels/admin/themes/**/*.kt") })
        assertTrue(globs.any { it.endsWith("/panels/admin/themes/**/*.java") })
        assertFalse(globs.any { it.contains("/app/themes/") })

        val written = ThemeCssCompiler.compileThemes(app, "a")
        assertEquals(1, written.size)
        val expanded = Files.readString(written.single())
        assertTrue(expanded.contains("background-color: var(--ff-primary-50)"))
        assertTrue(expanded.contains("var(--ff-accent)"))
        assertTrue(expanded.contains("var(--ff-primary)"))
        assertTrue(expanded.contains(".rounded-tl-none"))
        assertTrue(expanded.contains("border-top-left-radius: 0"))
        assertTrue(expanded.contains(".rounded-br-none"))
        assertTrue(expanded.contains("border-bottom-right-radius: 0"))
        assertFalse(expanded.contains("@apply"))
        assertFalse(expanded.contains("#FF2A00"))
        val both = ThemeCssCompiler.compileThemes(app, null)
        assertEquals(listOf("a.css", "b.css"), both.map { it.fileName.toString() })
        assertFalse(Files.exists(app.resolve("public/css/app.css")))
    }

    @Test
    fun compoundFieldsFillTheControlWidth() {
        val css = Stylesheet.render(KonaTheme())
        val marker = "Compound inputs fill the field slot (#555)."
        val start = css.indexOf(marker)
        assertTrue(start >= 0, "missing compound width rule")
        val block = css.substring(start, css.indexOf('}', start) + 1)
        val selectors = listOf(
            ".ff-panel .ff-control > .ff-repeater",
            ".ff-panel .ff-control > .ff-builder",
            ".ff-panel .ff-control > .ff-key-value",
            ".ff-panel .ff-control > .ff-code-editor",
            ".ff-panel .ff-control > .ff-markdown",
            ".ff-panel .ff-control > .ff-rich",
            ".ff-panel .ff-control > .ff-checkbox-list",
            ".ff-panel .ff-control > .ff-color",
            ".ff-panel .ff-control > .ff-tags",
            ".ff-panel .ff-control > .ff-slider-wrap",
            """.ff-panel .ff-control > .ff-filepond:not([data-ff-avatar="true"])""",
        )
        for (selector in selectors) {
            assertTrue(block.contains(selector), selector)
        }
        assertTrue(block.contains("flex: 1 1 auto"))
        assertTrue(block.contains("min-width: 0"))
        assertTrue(block.contains("width: auto"))
        assertTrue(block.contains("max-width: 100%"))
        // Same grow contract as a text input in .ff-control. Old CSS omitted these selectors.
        val textInput = css.indexOf(".ff-panel .ff-control > .ff-input,")
        assertTrue(textInput >= 0)
        val textBlock = css.substring(textInput, css.indexOf('}', textInput) + 1)
        assertTrue(textBlock.contains("flex: 1 1 auto"))
        assertTrue(textBlock.contains("width: auto"))
        val selectorLines = block.lineSequence().map { it.trim().trimEnd(',') }.toList()
        for (leftAlone in listOf(
            ".ff-panel .ff-control > .ff-checkbox",
            ".ff-panel .ff-control > .ff-toggle-wrap",
            ".ff-panel .ff-control > .ff-radio",
            ".ff-panel .ff-control > .ff-toggle-buttons",
            ".ff-panel .ff-control > .ff-placeholder",
            """.ff-panel .ff-control > .ff-filepond""",
        )) {
            assertFalse(selectorLines.contains(leftAlone), leftAlone)
        }
        assertTrue(css.contains(".ff-panel .ff-filepond[data-ff-avatar=\"true\"] {\n  width: 8rem;"))
        assertTrue(css.contains(".ff-panel div.ff-toggle-buttons[data-ff-grouped=\"true\"]"))
        val grouped = css.indexOf(".ff-panel div.ff-toggle-buttons[data-ff-grouped=\"true\"]")
        val groupedBlock = css.substring(grouped, css.indexOf('}', grouped) + 1)
        assertTrue(groupedBlock.contains("width: fit-content"))
    }

    @Test
    fun maxWidthAcceptsClassesContainerAndBracketedLengths() {
        for (token in MaxWidth.TOKENS) {
            assertEquals(token, MaxWidth.requireKnown(token))
            assertEquals(null, MaxWidth.utilityClass(token))
            assertEquals(null, MaxWidth.arbitraryStyle(token))
        }
        assertEquals("7xl", MaxWidth.DEFAULT)
        val classes = linkedMapOf(
            MaxWidth.PROSE to "max-w-prose",
            MaxWidth.SCREEN_SMALL to "max-w-screen-sm",
            MaxWidth.SCREEN_MEDIUM to "max-w-screen-md",
            MaxWidth.SCREEN_LARGE to "max-w-screen-lg",
            MaxWidth.SCREEN_EXTRA_LARGE to "max-w-screen-xl",
            MaxWidth.SCREEN_TWO_EXTRA_LARGE to "max-w-screen-2xl",
            MaxWidth.CONTAINER to "container",
            "Container" to "container",
        )
        for ((token, cssClass) in classes) {
            assertEquals(token, MaxWidth.requireKnown(token))
            assertEquals(cssClass, MaxWidth.utilityClass(token))
            assertEquals(null, MaxWidth.arbitraryStyle(token))
            val html = Panel().maxWidth(token).render()
            val tag = html.substringAfter("<div class=\"").substringBefore("\"")
            assertTrue(tag == "ff-panel $cssClass", tag)
            assertFalse(html.contains("data-ff-max-width=\"$token\""))
        }
        val lengths = listOf("[900px]", "[48rem]", "[80ch]", "[100%]", "[0]", "[0.5rem]", "[.5em]", "[900PX]")
        for (length in lengths) {
            assertEquals(length, MaxWidth.requireKnown(length))
            assertEquals("max-w-$length", MaxWidth.utilityClass(length))
            val style = MaxWidth.arbitraryStyle(length)
            assertTrue(style!!.startsWith("--ff-arbitrary-max: "))
            val html = Panel().maxWidth(length).render()
            val open = html.substringAfter("<div class=\"").substringBefore(">")
            assertTrue(open.startsWith("ff-panel max-w-$length\""), open)
            assertTrue(open.contains("""style="$style""""), open)
            assertFalse(open.contains("data-ff-max-width"), open)
        }
        for (rejected in listOf("huge", "[900]", "[]", "[900px] extra", "max-w-[900px]", "[900px][48rem]", "CONTAINER")) {
            val error = assertThrows(FireflyException::class.java) { MaxWidth.requireKnown(rejected) }
            assertTrue(error.message!!.contains("Unknown max width [$rejected]"))
            assertThrows(FireflyException::class.java) { Panel().maxWidth(rejected) }
        }
        val css = Stylesheet.render(KonaTheme())
        assertTrue(css.contains(".ff-panel.max-w-prose"))
        assertTrue(css.contains(".ff-panel.max-w-screen-sm"))
        assertTrue(css.contains(".ff-panel.max-w-screen-md"))
        assertTrue(css.contains(".ff-panel.max-w-screen-lg"))
        assertTrue(css.contains(".ff-panel.max-w-screen-xl"))
        assertTrue(css.contains(".ff-panel.max-w-screen-2xl"))
        assertTrue(css.contains(".ff-panel.container .ff-page"))
        assertTrue(css.contains("max-width: 40rem"))
        assertTrue(css.contains("max-width: 48rem"))
        assertTrue(css.contains("max-width: 64rem"))
        assertTrue(css.contains("max-width: 80rem"))
        assertTrue(css.contains("max-width: 96rem"))
        assertTrue(css.contains("--ff-content-max-width: var(--ff-arbitrary-max)"))
        assertFalse(css.contains("max-w-container"))
        val box = Panel().maxWidth("container").render()
        val tag = box.substringAfter("<div class=\"").substringBefore("\"")
        assertEquals("ff-panel container", tag)
        assertFalse(tag.contains("max-w-"))
        assertTrue(Panel().maxWidth("xl").render().contains("""class="ff-panel" data-ff-max-width="xl""""))
        val proseHtml = Panel().maxWidth("prose").render()
        assertEquals("ff-panel", proseHtml.substringAfter("<div class=\"").substringBefore("\""))
        assertTrue(proseHtml.contains("""data-ff-max-width="prose""""))
    }

    private fun authPage(theme: FireflyTheme): String =
        Panel().theme(theme).mode(ChromeMode.AUTH).layout(AuthLayout())
            .overridePartial(
                "body",
                """<main class="ff-auth-main"><!-- ff-hook:body.start --><div class="ff-auth-panel">card</div><!-- ff-hook:body.end --></main>""",
            )
            .render()
}
