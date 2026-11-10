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
 *
 * [useTailwind] loads Tailwind for hook classes and for `@apply` in [stylesheet].
 * That runtime registers scales for the panel colors: primary, accent, success,
 * warning, danger, info, and gray. `500` is the panel variable. Raw `heading`
 * and `surface` tokens are colors too.
 * [googleFonts] loads those families on the panel and the sign-in page.
 * Both stay off until a theme overrides them.
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
     * When [useTailwind] is true, that file is compiled in the browser instead
     * of linked as plain CSS, so `@apply` in it works.
     */
    open fun stylesheet(): String? = null

    /**
     * When true, the panel and the sign-in page load the Tailwind runtime.
     * Hook classes such as `bg-primary-800` and `@apply` in [stylesheet] both apply.
     * `primary-500` is `--ff-primary`, `accent-500` is `--ff-accent`, and the same
     * is true for success, warning, danger, info, and gray.
     * False leaves the stock panel CSS alone.
     */
    open fun useTailwind(): Boolean = false

    /**
     * Google font family names, for example `Figtree` or `Source Sans 3`.
     * Empty adds no font stylesheet. Names are loaded with `display=swap`.
     */
    open fun googleFonts(): List<String> = emptyList()

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
 * Inserts theme head tags before `</head>`: the Google font stylesheet, the
 * Tailwind runtime, and the plain stylesheet link. A theme that asks for none
 * of those, and a document with no head, is returned as-is.
 */
internal fun withThemeStylesheet(html: String, theme: FireflyTheme): String {
    val extra = themeHeadExtra(theme)
    if (extra.isEmpty()) return html
    val head = html.indexOf("</head>")
    if (head < 0) return html
    return html.substring(0, head) + extra + "\n" + html.substring(head)
}

internal fun themeHeadExtra(theme: FireflyTheme): String {
    val parts = ArrayList<String>()
    val fonts = googleFontsHref(theme.googleFonts())
    if (fonts != null) {
        parts.add("""<link rel="preconnect" href="https://fonts.googleapis.com" />""")
        parts.add("""<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />""")
        parts.add("""<link rel="stylesheet" href="${escapeThemeAttr(fonts)}" />""")
    }
    val href = theme.stylesheet()?.trim().orEmpty()
    if (theme.useTailwind()) {
        parts.add(tailwindHead(href))
    } else if (href.isNotEmpty()) {
        parts.add("""<link rel="stylesheet" href="${escapeThemeAttr(href)}" />""")
    }
    return parts.joinToString("\n")
}

/**
 * Google CSS API URL for [families], or null when every name is blank.
 * Spaces are encoded. Weights 400, 500, 600, and 700 and `display=swap` are fixed.
 */
internal fun googleFontsHref(families: List<String>): String? {
    val names = ArrayList<String>()
    for (family in families) {
        val trimmed = family.trim()
        if (trimmed.isEmpty()) continue
        names.add(trimmed)
    }
    if (names.isEmpty()) return null
    val query = StringBuilder()
    for (index in names.indices) {
        if (index > 0) query.append('&')
        query.append("family=")
        query.append(java.net.URLEncoder.encode(names[index], Charsets.UTF_8))
        query.append(":wght@400;500;600;700")
    }
    query.append("&display=swap")
    return "https://fonts.googleapis.com/css2?$query"
}

/**
 * Status colors the chrome already paints (stats, callouts, notifications).
 * They are not custom properties yet, so Tailwind defines them here and a later
 * rule can replace one. Gray is the existing `--ff-body-subtle`.
 */
private val SEMANTIC_COLOR_DEFAULTS = listOf(
    "--ff-success" to "#16a34a",
    "--ff-warning" to "#d97706",
    "--ff-danger" to "#dc2626",
    "--ff-info" to "#2563eb",
)

/** Each scale's 500 step is this variable. */
private val BRAND_COLOR_SCALES = listOf(
    "primary" to "--ff-primary",
    "accent" to "--ff-accent",
    "success" to "--ff-success",
    "warning" to "--ff-warning",
    "danger" to "--ff-danger",
    "info" to "--ff-info",
    "gray" to "--ff-body-subtle",
)

/** Unsuffixed utilities (`bg-primary`, `text-danger`) for variables that already exist. */
private val THEME_COLOR_TOKENS = listOf(
    "primary" to "--ff-primary",
    "accent" to "--ff-accent",
    "heading" to "--ff-heading",
    "surface" to "--ff-surface",
    "success" to "--ff-success",
    "warning" to "--ff-warning",
    "danger" to "--ff-danger",
    "info" to "--ff-info",
    "gray" to "--ff-body-subtle",
)

/**
 * Brand-color share in each step. 500 is the brand variable itself.
 * Lower steps mix toward white. Higher steps mix toward black.
 */
private val BRAND_SHADES = listOf(
    50 to 8,
    100 to 18,
    200 to 34,
    300 to 52,
    400 to 74,
    500 to 100,
    600 to 84,
    700 to 68,
    800 to 50,
    900 to 32,
)

private fun brandShadeValue(source: String, step: Int, percent: Int): String {
    if (step == 500) return "var($source)"
    val toward = if (step < 500) "white" else "black"
    return "color-mix(in srgb, var($source) $percent%, $toward)"
}

/** Shade custom properties. A later rule can replace one step. */
private fun brandColorScaleCss(): String = buildString {
    appendLine(".ff-panel,")
    appendLine(".ff-auth {")
    for ((variable, hex) in SEMANTIC_COLOR_DEFAULTS) {
        append("  ")
        append(variable)
        append(": ")
        append(hex)
        appendLine(";")
    }
    for ((name, source) in BRAND_COLOR_SCALES) {
        for ((step, percent) in BRAND_SHADES) {
            append("  --ff-")
            append(name)
            append("-")
            append(step)
            append(": ")
            append(brandShadeValue(source, step, percent))
            appendLine(";")
        }
    }
    append("}")
}

/**
 * `@theme inline` so utilities use `var(--ff-…)` on the element.
 * A `:root` copy would freeze the brand color before `.ff-panel` sets it.
 */
private fun brandColorThemeCss(): String = buildString {
    appendLine("@theme inline {")
    for ((name, source) in THEME_COLOR_TOKENS) {
        append("  --color-")
        append(name)
        append(": var(")
        append(source)
        appendLine(");")
    }
    for ((name, _) in BRAND_COLOR_SCALES) {
        for ((step, _) in BRAND_SHADES) {
            append("  --color-")
            append(name)
            append("-")
            append(step)
            append(": var(--ff-")
            append(name)
            append("-")
            append(step)
            appendLine(");")
        }
    }
    append("}")
}

private fun tailwindSource(): String =
    """<style>
${brandColorScaleCss()}
</style>
<style type="text/tailwindcss">@import "tailwindcss/theme";
@import "tailwindcss/utilities";
${brandColorThemeCss()}</style>"""

/**
 * Browser runtime plus a stylesheet source that imports Tailwind utilities
 * without the page reset. A non-blank [href] is fetched and compiled so
 * `@apply` in that file runs. Hook classes are already on the chrome elements,
 * and the runtime reads those class attributes. Each `*-500` utility is the
 * panel variable for that color (`--ff-primary`, `--ff-success`, and the rest).
 */
internal fun tailwindHead(href: String): String {
    val script = """<script src="${FireflyTailwind.SCRIPT}"></script>"""
    val source = tailwindSource()
    if (href.isEmpty()) return source + "\n" + script
    val loader =
        """<script data-ff-tailwind-href="${escapeThemeAttr(href)}">(function(){var href=document.currentScript.getAttribute("data-ff-tailwind-href");fetch(href,{credentials:"same-origin"}).then(function(r){return r.text();}).then(function(css){var el=document.createElement("style");el.setAttribute("type","text/tailwindcss");el.textContent=css;document.head.appendChild(el);}).catch(function(){});})();</script>"""
    return source + "\n" + script + "\n" + loader
}
