package org.konaframework.firefly.support

class ThemeTokens(
    val primary: String,
    val accent: String,
    val surface: String,
    val heading: String,
    val font: String,
    val radius: String,
    val sidebarWidth: String,
    val topbarHeight: String,
    val bodyBackground: String,
    val darkPrimary: String,
    val darkSurface: String,
    val darkHeading: String,
)

/**
 * Panel chrome theme. Subclass it and override a hook to append CSS classes on a
 * region the shell already draws. Empty hooks leave the stock panel unchanged.
 *
 * Register one theme on the panel with `theme(MidnightTheme())`. The render adds
 * those classes to the existing elements (`ff-sidebar`, `ff-topbar`, and the
 * rest). It does not replace the layout and it does not rename those classes.
 * `data-ff-theme` stays the light / dark / system toggle on the theme button.
 *
 * [id], [tokens], [partial], and [extraCss] still drive the palette, a replacement
 * slot, and extra rules under `.ff-panel`. [stylesheet] may point at another file.
 * Classes are the way to reskin a region.
 */
open class FireflyTheme {
    /** Short name used in theme error messages. [KonaTheme] returns `kona`. */
    open fun id(): String = "custom"

    /** Color, type, and size tokens. The default is the shipped Kona palette. */
    open fun tokens(): ThemeTokens = KonaTheme().tokens()

    /** Replacement HTML for a chrome slot, or null to keep the built-in partial. */
    open fun partial(slot: String): String? = null

    /** Extra CSS. Must not style the host page outside `.ff-panel`. */
    open fun extraCss(): String = ""

    /**
     * Stylesheet URL (`/css/midnight.css` or an absolute URL), or null.
     * A non-blank value is linked from the document `<head>`.
     */
    open fun stylesheet(): String? = null

    /** Extra classes for `.ff-panel`. */
    open fun panel(): String = ""

    /** Extra classes for `aside.ff-sidebar`. */
    open fun sidebar(): String = ""

    /** Extra classes for `header.ff-topbar`. */
    open fun topbar(): String = ""

    /** Extra classes for `.ff-sidebar-brand`, `.ff-topbar-brand`, and `.ff-auth-brand`. */
    open fun brand(): String = ""

    /** Extra classes for `nav.ff-nav` (side, top, and drawer). */
    open fun navigation(): String = ""

    /** Extra classes for `.ff-nav-item`. */
    open fun navigationItem(): String = ""

    /** Extra classes for `.ff-nav-group`. */
    open fun navigationGroup(): String = ""

    /** Extra classes for `main.ff-body`. */
    open fun content(): String = ""

    /** Extra classes for `.ff-page-header`. */
    open fun pageHeader(): String = ""

    /** Extra classes for `footer.ff-footer`. */
    open fun footer(): String = ""

    /** Extra classes for `.ff-sidebar-foot`. */
    open fun sidebarFoot(): String = ""

    /** Extra classes for `.ff-user-menu`. */
    open fun userMenu(): String = ""

    /** Extra classes for `.ff-column`. */
    open fun column(): String = ""

    /** Extra classes for `.ff-scroll`. */
    open fun scroll(): String = ""

    /** Extra classes for the guest document `body.ff-auth`. */
    open fun auth(): String = ""

    /** Extra classes for `.ff-auth-panel`. */
    open fun authCard(): String = ""
}

class KonaTheme : FireflyTheme() {
    override fun id(): String = "kona"

    override fun tokens(): ThemeTokens = ThemeTokens(
        primary = "#DF8723",
        accent = "#74453F",
        surface = "#FAF6F0",
        heading = "#2A1C10",
        // Inter / DM Sans self-hosted under /_firefly/fonts (see FireflyFonts); system stack fallback.
        font = "\"Inter\", \"DM Sans\", ui-sans-serif, system-ui, sans-serif",
        radius = "0.55rem",
        sidebarWidth = "16.5rem",
        topbarHeight = "3.75rem",
        bodyBackground = "#FAF6F0",
        /* Dark chrome matches Shamar calm dark (gray shell / near-white headings).
         * Primary stays latte so brand accents remain coffee-toned on gray surfaces. */
        darkPrimary = "#C9A57B",
        darkSurface = "#1F2937",
        darkHeading = "#F3F4F6",
    )
}

/** Stable class token → extra classes from the matching [FireflyTheme] hook. */
internal fun FireflyTheme.chromeClasses(): List<Pair<Set<String>, String>> = listOf(
    setOf("ff-panel") to panel(),
    setOf("ff-sidebar") to sidebar(),
    setOf("ff-topbar") to topbar(),
    setOf("ff-sidebar-brand", "ff-topbar-brand", "ff-auth-brand") to brand(),
    setOf("ff-nav") to navigation(),
    setOf("ff-nav-item") to navigationItem(),
    setOf("ff-nav-group") to navigationGroup(),
    setOf("ff-body") to content(),
    setOf("ff-page-header") to pageHeader(),
    setOf("ff-footer") to footer(),
    setOf("ff-sidebar-foot") to sidebarFoot(),
    setOf("ff-user-menu") to userMenu(),
    setOf("ff-column") to column(),
    setOf("ff-scroll") to scroll(),
    setOf("ff-auth") to auth(),
    setOf("ff-auth-panel") to authCard(),
)

private const val THEME_CLASS_EXTRA = "_-:./#%[](),&!"

/** True when [token] can be appended to a `class` attribute without breaking the markup. */
internal fun legalThemeClass(token: String): Boolean {
    if (token.isEmpty()) return false
    for (ch in token) {
        if (!(ch.isLetterOrDigit() || ch in THEME_CLASS_EXTRA)) return false
    }
    return true
}

/** Drops blank and illegal tokens. An empty result means the hook adds nothing. */
internal fun sanitizeThemeClasses(raw: String): String {
    if (raw.isBlank()) return ""
    val kept = ArrayList<String>()
    for (token in raw.trim().split(Regex("\\s+"))) {
        if (legalThemeClass(token)) kept.add(token)
    }
    return kept.joinToString(" ")
}

private val CLASS_ATTRIBUTE = Regex("""class="([^"]*)"""")

/**
 * Appends [extra] (already sanitized) to every `class` attribute that contains
 * one of [markers] as a whole token. `ff-nav` does not match `ff-nav-item`.
 */
internal fun appendThemeClasses(html: String, markers: Set<String>, extra: String): String {
    val adding = extra.split(' ')
    return CLASS_ATTRIBUTE.replace(html) { match ->
        val existing = match.groupValues[1]
        val tokens = existing.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (markers.none { it in tokens }) {
            match.value
        } else {
            val fresh = adding.filter { it !in tokens }
            if (fresh.isEmpty()) {
                match.value
            } else {
                """class="${existing.trimEnd()} ${fresh.joinToString(" ")}""""
            }
        }
    }
}

/** Applies every hook. Blank hooks leave the HTML unchanged. */
internal fun applyThemeClasses(html: String, theme: FireflyTheme): String {
    var result = html
    for ((markers, raw) in theme.chromeClasses()) {
        val extra = sanitizeThemeClasses(raw)
        if (extra.isEmpty()) continue
        result = appendThemeClasses(result, markers, extra)
    }
    return result
}

internal fun escapeThemeAttr(value: String): String = buildString {
    for (ch in value) {
        when (ch) {
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '<' -> append("&lt;")
            else -> append(ch)
        }
    }
}

/**
 * Links [FireflyTheme.stylesheet] before `</head>` when the theme names a file.
 * Documents without a head, and themes with no stylesheet, are returned as-is.
 */
internal fun withThemeStylesheet(html: String, theme: FireflyTheme): String {
    val href = theme.stylesheet()?.trim().orEmpty()
    if (href.isEmpty()) return html
    val head = html.indexOf("</head>")
    if (head < 0) return html
    val link = """<link rel="stylesheet" href="${escapeThemeAttr(href)}" />"""
    return html.substring(0, head) + link + "\n" + html.substring(head)
}
