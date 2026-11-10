package org.konaframework.firefly.support

/**
 * Palette for a theme.
 * [darkPrimary], [darkSurface], and [darkHeading] are the dark values used when the panel
 * did not set that light color. They do not replace a primary or accent the panel set.
 * Override [FireflyTheme.darkPrimary] (and the matching hooks) to force a dark color.
 */
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

    /**
     * Explicit dark primary, or null to follow the panel.
     * Null derives a shade of the panel primary when the panel set one.
     * When the panel set no primary, the dark block uses [tokens] `darkPrimary`
     * (Kona's `#C9A57B` on [KonaTheme]). That token is the fallback, not an override:
     * a theme that only fills `tokens().darkPrimary` does not replace a panel primary.
     */
    open fun darkPrimary(): String? = null

    /**
     * Explicit dark accent, or null to follow the panel.
     * Null derives a shade of the panel accent when the panel set one, and otherwise
     * leaves the light accent in place for dark mode too.
     */
    open fun darkAccent(): String? = null

    /**
     * Explicit dark surface, or null to use [tokens] `darkSurface`.
     * The panel cannot set a surface, so this hook is the theme override.
     */
    open fun darkSurface(): String? = null

    /**
     * Explicit dark heading, or null to use [tokens] `darkHeading`.
     * The panel cannot set a heading, so this hook is the theme override.
     */
    open fun darkHeading(): String? = null

    /** Replacement HTML for a chrome slot, or null to keep the built-in partial. */
    open fun partial(slot: String): String? = null

    /** Extra CSS. Must not style the host page outside `.ff-panel`. */
    open fun extraCss(): String = ""

    /**
     * Stylesheet URL (`/css/midnight.css` or an absolute URL), or null.
     * A non-blank value is linked from the document `<head>` before the chrome.
     * When [useTailwind] is true, that file is fetched in the head and compiled
     * before the chrome is shown, so `@apply` in it does not arrive late.
     */
    open fun stylesheet(): String? = null

    /**
     * When true, the panel and the sign-in page load the Tailwind runtime in
     * `<head>` before the chrome. Hook classes such as `bg-primary-800` and
     * `@apply` in [stylesheet] both apply. The runtime compiles on a promise,
     * so the document stays on the theme background until that compile has
     * seen the chrome. `primary-500` is `--ff-primary`, `accent-500` is
     * `--ff-accent`, and the same is true for success, warning, danger, info,
     * and gray. False leaves the stock panel CSS alone and adds no wait.
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

    /**
     * Extra classes for the active leaf `.ff-nav-item` (the open page).
     * A group cluster that contains the page already has `ff-active` and does not get this class.
     */
    open fun navigationItemActive(): String = ""

    /** Extra classes for `.ff-nav-group`. */
    open fun navigationGroup(): String = ""

    /**
     * Extra classes for a group that contains the open page.
     * That is a cluster with `ff-active`, or a group marked `ff-nav-group-active`.
     */
    open fun navigationGroupActive(): String = ""

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
         * darkPrimary is the fallback when the panel sets no primary. A panel primary
         * keeps its own hue in dark mode instead of this latte. */
        darkPrimary = "#C9A57B",
        darkSurface = "#1F2937",
        darkHeading = "#F3F4F6",
    )
}

/** One chrome hook: markers match any token, [required] must all be present, [excluded] must be absent. */
internal class ThemeClassRule(
    val markers: Set<String>,
    val classes: String,
    val required: Set<String>,
    val excluded: Set<String>,
)

private val NO_THEME_TOKENS: Set<String> = emptySet()

private fun rule(markers: Set<String>, classes: String): ThemeClassRule =
    ThemeClassRule(markers, classes, NO_THEME_TOKENS, NO_THEME_TOKENS)

private fun activeRule(
    markers: Set<String>,
    classes: String,
    required: Set<String>,
    excluded: Set<String>,
): ThemeClassRule = ThemeClassRule(markers, classes, required, excluded)

/** Stable class token → extra classes from the matching [FireflyTheme] hook. */
internal fun FireflyTheme.chromeClasses(): List<ThemeClassRule> = listOf(
    rule(setOf("ff-panel"), panel()),
    rule(setOf("ff-sidebar"), sidebar()),
    rule(setOf("ff-topbar"), topbar()),
    rule(setOf("ff-sidebar-brand", "ff-topbar-brand", "ff-auth-brand"), brand()),
    rule(setOf("ff-nav"), navigation()),
    rule(setOf("ff-nav-item"), navigationItem()),
    // Open page only. Group clusters also carry ff-active and stay off this hook.
    activeRule(setOf("ff-nav-item"), navigationItemActive(), setOf("ff-active"), setOf("ff-cluster")),
    rule(setOf("ff-nav-group"), navigationGroup()),
    // Sidebar and drawer groups. Top-bar and app/inverse groups are clusters with ff-active.
    rule(setOf("ff-nav-group-active"), navigationGroupActive()),
    activeRule(setOf("ff-cluster"), navigationGroupActive(), setOf("ff-active"), NO_THEME_TOKENS),
    rule(setOf("ff-body"), content()),
    rule(setOf("ff-page-header"), pageHeader()),
    rule(setOf("ff-footer"), footer()),
    rule(setOf("ff-sidebar-foot"), sidebarFoot()),
    rule(setOf("ff-user-menu"), userMenu()),
    rule(setOf("ff-column"), column()),
    rule(setOf("ff-scroll"), scroll()),
    rule(setOf("ff-auth"), auth()),
    rule(setOf("ff-auth-panel"), authCard()),
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
internal fun appendThemeClasses(html: String, markers: Set<String>, extra: String): String =
    appendThemeClasses(html, markers, extra, NO_THEME_TOKENS, NO_THEME_TOKENS)

/**
 * Appends [extra] when a class attribute has any [markers] token, every [required]
 * token, and none of [excluded]. Whole tokens only.
 */
internal fun appendThemeClasses(
    html: String,
    markers: Set<String>,
    extra: String,
    required: Set<String>,
    excluded: Set<String>,
): String {
    val adding = extra.split(' ')
    return CLASS_ATTRIBUTE.replace(html) { match ->
        val existing = match.groupValues[1]
        val tokens = existing.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (!themeClassHit(tokens, markers, required, excluded)) {
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

private fun themeClassHit(
    tokens: List<String>,
    markers: Set<String>,
    required: Set<String>,
    excluded: Set<String>,
): Boolean {
    if (markers.none { it in tokens }) return false
    if (required.any { it !in tokens }) return false
    if (excluded.any { it in tokens }) return false
    return true
}

/** Applies every hook. Blank hooks leave the HTML unchanged. */
internal fun applyThemeClasses(html: String, theme: FireflyTheme): String {
    var result = html
    for (rule in theme.chromeClasses()) {
        val extra = sanitizeThemeClasses(rule.classes)
        if (extra.isEmpty()) continue
        result = appendThemeClasses(result, rule.markers, extra, rule.required, rule.excluded)
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
 * Inserts theme head tags before `</head>`, and when Tailwind is on, a reveal
 * script before `</body>`. A theme that asks for none of those, and a document
 * with no head, is returned as-is.
 *
 * The Tailwind browser build compiles on a promise after the parser continues,
 * and it only reads `style type="text/tailwindcss"`. A plain link would paint
 * the stock chrome, then pop `@apply` in. The head therefore fetches that file
 * before the runtime script, and [themePaintScript] keeps the body hidden on
 * the theme background until the compile has seen the chrome.
 */
internal fun withThemeStylesheet(html: String, theme: FireflyTheme): String {
    val extra = themeHeadExtra(theme, html.contains("<body class=\"ff-auth\""))
    var result = html
    if (extra.isNotEmpty()) {
        val head = result.indexOf("</head>")
        if (head >= 0) {
            result = result.substring(0, head) + extra + "\n" + result.substring(head)
        }
    }
    if (theme.useTailwind()) {
        val body = result.indexOf("</body>")
        if (body >= 0) {
            result = result.substring(0, body) + themePaintScript() + "\n" + result.substring(body)
        }
    }
    return result
}

internal fun themeHeadExtra(theme: FireflyTheme, authDocument: Boolean): String {
    val parts = ArrayList<String>()
    val href = theme.stylesheet()?.trim().orEmpty()
    if (theme.useTailwind()) {
        parts.add(themeHold(theme, authDocument))
        parts.add(tailwindHead(href))
    }
    val fonts = googleFontsHref(theme.googleFonts())
    if (fonts != null) {
        parts.add("""<link rel="preconnect" href="https://fonts.googleapis.com" />""")
        parts.add("""<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />""")
        parts.add("""<link rel="stylesheet" href="${escapeThemeAttr(fonts)}" />""")
    }
    if (!theme.useTailwind() && href.isNotEmpty()) {
        parts.add("""<link rel="stylesheet" href="${escapeThemeAttr(href)}" />""")
    }
    return parts.joinToString("\n")
}

/**
 * Hides the chrome without a blank white wait. The early dark-mode script has
 * already set `ff-dark`. Auth keeps the light canvas in dark mode because the
 * guest body paints that color inline; the panel shell covers the viewport,
 * so its hold uses the dark surface.
 */
private fun themeHold(theme: FireflyTheme, authDocument: Boolean): String {
    val light = holdColor(theme.tokens().bodyBackground)
    val dark = holdColor(if (authDocument) light else theme.tokens().darkSurface)
    return """<script>(function(){document.documentElement.classList.add("ff-theme-pending");})();</script>
<style id="ff-theme-hold">html.ff-theme-pending{background:$light}html.ff-dark.ff-theme-pending{background:$dark}html.ff-theme-pending body{visibility:hidden}</style>"""
}

/** Drops characters that would close the hold style. Theme colors are otherwise copied through. */
private fun holdColor(value: String): String = value.replace("<", "").replace(">", "")

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
${brandColorThemeCss()}
@utility ff-theme-paint { opacity: 1; }</style>"""

/**
 * Blocking runtime in the head, after the theme sheet when [href] is set.
 * The runtime's first build reads every `style type="text/tailwindcss"` already
 * in the document, and that read happens in a microtask after the script tag.
 * A synchronous fetch in the previous script puts `@apply` in that read.
 * `ff-theme-paint` is emitted only once the class is on an element, which is
 * how the reveal script knows the compile has seen the chrome.
 */
internal fun tailwindHead(href: String): String {
    val source = tailwindSource()
    val script = """<script src="${FireflyTailwind.SCRIPT}"></script>"""
    if (href.isEmpty()) return source + "\n" + script
    val loader =
        """<script data-ff-tailwind-href="${escapeThemeAttr(href)}">(function(){var href=document.currentScript.getAttribute("data-ff-tailwind-href");if(!href)return;document.documentElement.setAttribute("data-ff-theme-href",href);function inject(css){var el=document.createElement("style");el.setAttribute("type","text/tailwindcss");el.setAttribute("data-ff-theme-sheet","");el.textContent=css;document.head.appendChild(el);}var synced=false;try{var xhr=new XMLHttpRequest();xhr.open("GET",href,false);xhr.send(null);if(xhr.status>=200&&xhr.status<300){inject(xhr.responseText);document.documentElement.setAttribute("data-ff-theme-sync","");synced=true;}}catch(e){}if(!synced){fetch(href,{credentials:"same-origin"}).then(function(r){if(!r.ok)throw new Error("theme");return r.text();}).then(inject).catch(function(){document.documentElement.setAttribute("data-ff-theme-failed","");document.head.appendChild(document.createComment("ff-theme-failed"));});}})();</script>"""
    return source + "\n" + loader + "\n" + script
}

/**
 * Runs after the chrome is parsed. Reveals the document once the runtime's
 * generated style contains `ff-theme-paint`, which means that build scanned
 * the chrome. A failed sheet or a missing runtime still reveals.
 */
private fun themePaintScript(): String =
    """<script data-ff-theme-reveal>(function(){var root=document.documentElement;if(!root.classList.contains("ff-theme-pending"))return;var revealed=false;function reveal(){if(revealed)return;revealed=true;root.classList.remove("ff-theme-pending");}function generated(){var nodes=document.head?document.head.querySelectorAll("style"):[];var last=null;for(var i=0;i<nodes.length;i++){var s=nodes[i];if(s.getAttribute("type")==="text/tailwindcss")continue;if(s.id==="ff-theme-hold")continue;last=s;}return last;}function sheetReady(){if(root.hasAttribute("data-ff-theme-failed"))return true;if(!root.hasAttribute("data-ff-theme-href"))return true;return !!document.head.querySelector("style[data-ff-theme-sheet]");}var kicked=false;var obs=new MutationObserver(function(){check();});function check(){if(revealed)return;if(!sheetReady())return;var out=generated();if(!out)return;if(!kicked){kicked=true;if(document.body)document.body.classList.add("ff-theme-paint");}if((out.textContent||"").indexOf("ff-theme-paint")!==-1){obs.disconnect();reveal();}}obs.observe(document.head,{childList:true,subtree:true,characterData:true});obs.observe(root,{attributes:true,attributeFilter:["data-ff-theme-failed"]});check();setTimeout(function(){obs.disconnect();reveal();},4000);})();</script>"""
