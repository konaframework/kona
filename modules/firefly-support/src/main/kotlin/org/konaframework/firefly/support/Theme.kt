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
 * A hook may return several classes separated by spaces. Each token is checked
 * on its own, so one illegal token does not drop the rest.
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
 * [asset] names an uncompiled file under `src/main/resources`. [stylesheet]
 * returns that token. The render links the compiled file in `public/` when
 * that file exists. `./grind firefly:theme-css` compiles it. A generated theme
 * uses that path and leaves [useTailwind] off. A missing compiled file loads
 * the browser runtime with the resources source, so a refresh shows edits.
 *
 * [useTailwind] loads Tailwind in the browser for hook classes and for `@apply`
 * in a stylesheet URL. That runtime registers scales for the panel colors:
 * primary, accent, success, warning, danger, info, and gray. `500` is the panel
 * variable. Raw `heading` and `surface` tokens are colors too. The compile
 * happens after the HTML is parsed, so the document stays hidden until the
 * themed sheet is in the CSSOM. That is the no-build path, and it still
 * flickers unless that hide stays on.
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
     * Stylesheet URL (`/css/midnight.css` or an absolute URL), an [asset]
     * token, or null.
     * A non-blank URL is linked from the document `<head>` before the chrome.
     * An [asset] token names the uncompiled resources file. When the compiled
     * file is in `public/`, the browser receives that public URL and does not
     * load the runtime. When it is missing, the page does not fail: the runtime
     * compiles `src/main/resources/css/{slug}.css` (the path inside the token)
     * and the document stays hidden until that compile is in the CSSOM.
     * When [useTailwind] is true and this is a plain URL, that file is fetched
     * in the head and compiled before the chrome is shown, so `@apply` in it
     * does not arrive late. [useTailwind] true stays on the runtime even when
     * an [asset] file has already been compiled, and feeds it that same source.
     */
    open fun stylesheet(): String? = null

    /**
     * Names the uncompiled stylesheet under `src/main/resources`.
     * `asset("css/midnight.css")` is `src/main/resources/css/midnight.css`.
     * The render resolves it to `/css/midnight.css`, the compiled file
     * `./grind firefly:theme-css` writes under `public/`.
     */
    fun asset(path: String): String = ThemeAssets.asset(path)

    /**
     * When true, the panel and the sign-in page load the Tailwind runtime in
     * `<head>` before the chrome. Hook classes such as `bg-primary-800` and
     * `@apply` in a plain [stylesheet] URL both apply. The runtime compiles on
     * a promise, so the document stays `visibility: hidden` until that compile
     * is in the CSSOM, then it is shown once. The reveal does not change a
     * class or a background, so the first painted frame is the themed chrome.
     * `primary-500` is `--ff-primary`, `accent-500` is `--ff-accent`, and the
     * same is true for success, warning, danger, info, and gray. False leaves
     * the stock panel CSS alone and adds no hide.
     * An [asset] stylesheet whose compiled file exists does not load this
     * runtime and does not hide the page, unless this returns true. True keeps
     * the runtime even when that compiled file exists. A missing compiled file
     * loads the runtime either way, so the resources source can be edited and
     * refreshed. The compiled file is the flicker-free path.
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
     * The same classes are copied onto the painted anchor or dropdown trigger, which is the box
     * that shows the corner radius.
     */
    open fun navigationItemActive(): String = ""

    /** Extra classes for `.ff-nav-group`. */
    open fun navigationGroup(): String = ""

    /**
     * Extra classes for a group that contains the open page.
     * That is a cluster with `ff-active`, or a group marked `ff-nav-group-active`.
     * The same classes are copied onto the painted label or trigger.
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

/**
 * One chrome hook: markers match any token, [required] must all be present, [excluded] must be absent.
 * When [paintChild] is true, the classes are also copied onto the painted child (the anchor, trigger,
 * or group label). The stock radius lives on that child, not on the wrapper the marker matches.
 */
internal class ThemeClassRule(
    val markers: Set<String>,
    val classes: String,
    val required: Set<String>,
    val excluded: Set<String>,
    val paintChild: Boolean,
)

private val NO_THEME_TOKENS: Set<String> = emptySet()

private fun rule(markers: Set<String>, classes: String): ThemeClassRule =
    ThemeClassRule(markers, classes, NO_THEME_TOKENS, NO_THEME_TOKENS, false)

private fun paintedRule(markers: Set<String>, classes: String): ThemeClassRule =
    ThemeClassRule(markers, classes, NO_THEME_TOKENS, NO_THEME_TOKENS, true)

private fun activeRule(
    markers: Set<String>,
    classes: String,
    required: Set<String>,
    excluded: Set<String>,
): ThemeClassRule = ThemeClassRule(markers, classes, required, excluded, true)

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
    paintedRule(setOf("ff-nav-group-active"), navigationGroupActive()),
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
        if (rule.paintChild) {
            result = copyPaintedThemeClasses(result, rule.markers, extra, rule.required, rule.excluded)
        }
    }
    return result
}

private val VOID_TAGS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
)

/**
 * Copies [extra] onto the painted child of each element the hook already matched.
 * The child is a direct anchor, `.ff-nav-dropdown-trigger`, or `.ff-nav-group-label`.
 * Nested links inside a group keep their own hooks. Script and style bodies are left alone.
 */
internal fun copyPaintedThemeClasses(
    html: String,
    markers: Set<String>,
    extra: String,
    required: Set<String>,
    excluded: Set<String>,
): String {
    val adding = extra.split(' ').filter { it.isNotEmpty() }
    if (adding.isEmpty()) return html
    val out = StringBuilder(html.length)
    val stack = ArrayDeque<Boolean>()
    var index = 0
    val length = html.length
    while (index < length) {
        val start = html.indexOf('<', index)
        if (start < 0) {
            out.append(html, index, length)
            break
        }
        out.append(html, index, start)
        val skipped = rawRegionEnd(html, start)
        if (skipped >= 0) {
            out.append(html, start, skipped)
            index = skipped
            continue
        }
        val end = html.indexOf('>', start + 1)
        if (end < 0) {
            out.append(html, start, length)
            break
        }
        val tag = html.substring(start, end + 1)
        if (tag.startsWith("</")) {
            out.append(tag)
            if (stack.isNotEmpty()) stack.removeLast()
            index = end + 1
            continue
        }
        if (tag.startsWith("<!")) {
            out.append(tag)
            index = end + 1
            continue
        }
        val name = openTagName(tag)
        val tokens = classTokens(tag)
        val parentPaints = stack.lastOrNull() == true
        val rewritten = if (parentPaints && isPaintTarget(name, tokens)) withClasses(tag, adding) else tag
        out.append(rewritten)
        val selfClosing = tag.endsWith("/>") || name in VOID_TAGS
        if (!selfClosing) {
            stack.addLast(themeClassHit(tokens, markers, required, excluded))
        }
        index = end + 1
    }
    return out.toString()
}

/** End offset of a comment, script, or style that starts at [start], or -1 when [start] is a normal tag. */
private fun rawRegionEnd(html: String, start: Int): Int {
    if (html.startsWith("<!--", start)) {
        val end = html.indexOf("-->", start + 4)
        return if (end < 0) html.length else end + 3
    }
    val script = html.regionMatches(start, "<script", 0, 7, ignoreCase = true)
    val style = !script && html.regionMatches(start, "<style", 0, 6, ignoreCase = true)
    if (!script && !style) return -1
    val endTag = if (script) "</script>" else "</style>"
    val end = html.indexOf(endTag, start + endTag.length, ignoreCase = true)
    return if (end < 0) html.length else end + endTag.length
}

private fun openTagName(tag: String): String =
    tag.removePrefix("<").trimStart().takeWhile { it.isLetterOrDigit() || it == '-' || it == ':' }.lowercase()

private fun classTokens(tag: String): List<String> {
    val match = CLASS_ATTRIBUTE.find(tag) ?: return emptyList()
    return match.groupValues[1].split(' ').filter { it.isNotEmpty() }
}

private fun isPaintTarget(tag: String, tokens: List<String>): Boolean =
    tag == "a" || "ff-nav-dropdown-trigger" in tokens || "ff-nav-group-label" in tokens

private fun withClasses(tag: String, adding: List<String>): String {
    val match = CLASS_ATTRIBUTE.find(tag)
    if (match == null) {
        val at = if (tag.endsWith("/>")) tag.length - 2 else tag.length - 1
        return tag.substring(0, at) + " class=\"" + adding.joinToString(" ") + "\"" + tag.substring(at)
    }
    val existing = match.groupValues[1]
    val tokens = existing.split(' ').filter { it.isNotEmpty() }
    val fresh = adding.filter { it !in tokens }
    if (fresh.isEmpty()) return tag
    val updated = existing.trimEnd() + " " + fresh.joinToString(" ")
    return tag.replaceRange(match.range, "class=\"$updated\"")
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
 * Inserts theme head tags before `</head>`, and when the Tailwind runtime is
 * on, a reveal script before `</body>`. The runtime is on when
 * [FireflyTheme.useTailwind] is true, and when an asset stylesheet has no
 * compiled file in `public/`. A theme that asks for none of those, and a
 * document with no head, is returned as-is.
 *
 * The Tailwind browser build compiles on a promise after the parser continues,
 * and it only reads `style type="text/tailwindcss"`. A plain link would paint
 * the stock chrome, then pop `@apply` in. The head therefore fetches that file
 * before the runtime script. [themeHold] keeps the root `visibility: hidden`
 * so stock chrome cannot paint, and [themePaintScript] shows it once the
 * compile that includes the theme is in the CSSOM. Showing sets an inline
 * style. It does not remove the pending class: that class change makes the
 * runtime replace the compiled sheet, which flashes stock colors.
 */
internal fun withThemeStylesheet(html: String, theme: FireflyTheme): String {
    val runtime = themeUsesRuntime(theme)
    val extra = themeHeadExtra(theme, html.contains("<body class=\"ff-auth\""))
    var result = html
    if (extra.isNotEmpty()) {
        val head = result.indexOf("</head>")
        if (head >= 0) {
            result = result.substring(0, head) + extra + "\n" + result.substring(head)
        }
    }
    if (runtime) {
        val body = result.indexOf("</body>")
        if (body >= 0) {
            result = result.substring(0, body) + themePaintScript() + "\n" + result.substring(body)
        }
    }
    return result
}

/**
 * True when the page should load `/_firefly/tailwind.js` and stay hidden until
 * that compile is in the CSSOM. [FireflyTheme.useTailwind] always does.
 * An asset token does too when its compiled public file is missing, so the
 * uncompiled resources file can be edited and refreshed.
 */
internal fun themeUsesRuntime(theme: FireflyTheme): Boolean {
    if (theme.useTailwind()) return true
    val raw = theme.stylesheet()?.trim().orEmpty()
    return ThemeAssets.isCompiledAsset(raw) && !ThemeAssets.hasCompiledFile(raw)
}

internal fun themeHeadExtra(theme: FireflyTheme, authDocument: Boolean): String {
    val parts = ArrayList<String>()
    val raw = theme.stylesheet()?.trim().orEmpty()
    val asset = ThemeAssets.isCompiledAsset(raw)
    val runtime = themeUsesRuntime(theme)
    if (runtime) {
        parts.add(themeHold(theme, authDocument))
        val userCss = if (asset) ThemeAssets.uncompiledCss(raw) else ""
        val href = if (asset) "" else raw
        parts.add(tailwindHead(href, userCss))
    }
    val fonts = googleFontsHref(theme.googleFonts())
    if (fonts != null) {
        parts.add("""<link rel="preconnect" href="https://fonts.googleapis.com" />""")
        parts.add("""<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />""")
        parts.add("""<link rel="stylesheet" href="${escapeThemeAttr(fonts)}" />""")
    }
    if (raw.isNotEmpty() && !runtime) {
        val href = if (asset) ThemeAssets.publicUrl(raw) else raw
        parts.add("""<link rel="stylesheet" href="${escapeThemeAttr(href)}" />""")
    }
    return parts.joinToString("\n")
}

/**
 * Hides the root until [themePaintScript] shows it. The pending class is not
 * removed later. A panel in dark mode also paints [ThemeTokens.darkSurface] on
 * the canvas so a blocked parser cannot flash the light body color; the shell
 * uses that same surface, and the rule stays after the reveal. Auth keeps the
 * light canvas, so it does not set that background.
 */
private fun themeHold(theme: FireflyTheme, authDocument: Boolean): String {
    val rules = StringBuilder("html.ff-theme-pending{visibility:hidden}")
    if (!authDocument) {
        rules.append("html.ff-dark.ff-theme-pending{background:")
        rules.append(holdColor(theme.tokens().darkSurface))
        rules.append('}')
    }
    return """<script>(function(){document.documentElement.classList.add("ff-theme-pending");})();</script>
<style id="ff-theme-hold">$rules</style>"""
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
 * `@import` plus `@theme inline` so `@apply` of panel scales becomes `var(--ff-*)`.
 * [sources] are Tailwind `@source` globs for panel theme classes. Utilities named only
 * in those files are emitted. The import does not inline a panel color.
 */
internal fun themeCompileSource(userCss: String, sources: List<String>): String = buildString {
    appendLine("""@import "tailwindcss/theme";""")
    appendLine("""@import "tailwindcss/utilities";""")
    for (source in sources) {
        append("@source \"")
        append(source)
        appendLine("\";")
    }
    append(brandColorThemeCss())
    append(userCss)
    if (!userCss.endsWith("\n")) append('\n')
}

/** Scale custom properties in front of the expanded sheet, so the public file stands alone. */
internal fun finishCompiledCss(expanded: String): String =
    brandColorScaleCss().trimEnd() + "\n" + expanded.trimStart()

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
html { --ff-theme-compiled: 1; }</style>"""

/**
 * Blocking runtime in the head, after the theme sheet when [href] is set.
 * The runtime's first build reads every `style type="text/tailwindcss"` already
 * in the document, and that read happens in a microtask after the script tag.
 * A synchronous fetch in the previous script puts `@apply` in that read.
 * `--ff-theme-compiled` is copied into the generated sheet, which is how the
 * reveal script knows that build has landed. It does not depend on a class,
 * so waiting for it does not schedule another build.
 */
internal fun tailwindHead(href: String, userCss: String): String {
    val source = tailwindSource()
    val script = """<script src="${FireflyTailwind.SCRIPT}"></script>"""
    val inline = inlineTailwindSheet(userCss)
    if (href.isEmpty()) {
        if (inline.isEmpty()) return source + "\n" + script
        return source + "\n" + inline + "\n" + script
    }
    val loader =
        """<script data-ff-tailwind-href="${escapeThemeAttr(href)}">(function(){var href=document.currentScript.getAttribute("data-ff-tailwind-href");if(!href)return;document.documentElement.setAttribute("data-ff-theme-href",href);function inject(css){var el=document.createElement("style");el.setAttribute("type","text/tailwindcss");el.setAttribute("data-ff-theme-sheet","");el.textContent=css;document.head.appendChild(el);}var synced=false;try{var xhr=new XMLHttpRequest();xhr.open("GET",href,false);xhr.send(null);if(xhr.status>=200&&xhr.status<300){inject(xhr.responseText);document.documentElement.setAttribute("data-ff-theme-sync","");synced=true;}}catch(e){}if(!synced){fetch(href,{credentials:"same-origin"}).then(function(r){if(!r.ok)throw new Error("theme");return r.text();}).then(inject).catch(function(){document.documentElement.setAttribute("data-ff-theme-failed","");document.head.appendChild(document.createComment("ff-theme-failed"));});}})();</script>"""
    return source + "\n" + loader + "\n" + script
}

/**
 * User CSS already in the document, the same sheet the runtime fetch injects.
 * The resources file is not a public URL, so a missing compiled asset is inlined
 * and a refresh reads it again. `</` is escaped so the style cannot close early.
 */
private fun inlineTailwindSheet(userCss: String): String {
    if (userCss.isEmpty()) return ""
    val css = userCss.replace("</", "<\\/")
    return """<style type="text/tailwindcss" data-ff-theme-sheet>$css</style>"""
}

/**
 * Runs after the chrome is parsed. The head build has already compiled by then,
 * because the runtime script's microtasks finish before the body is parsed.
 * Shows the document when the generated sheet contains `--ff-theme-compiled`.
 * A stylesheet URL that arrived through the async fallback is not treated as
 * compiled until the generated sheet is rewritten after that node exists.
 * Showing sets inline visibility and does not touch `class`, so the runtime
 * does not rebuild. A failed sheet or a missing runtime still shows.
 */
private fun themePaintScript(): String =
    """<script data-ff-theme-reveal>(function(){var root=document.documentElement;if(!root.classList.contains("ff-theme-pending"))return;var shown=false;function generated(){var nodes=document.head?document.head.querySelectorAll("style"):[];var last=null;for(var i=0;i<nodes.length;i++){var s=nodes[i];if(s.getAttribute("type")==="text/tailwindcss")continue;if(s.id==="ff-theme-hold")continue;last=s;}return last;}function hasSheet(){return !!document.head.querySelector("style[data-ff-theme-sheet]");}var sheetCompiled=root.hasAttribute("data-ff-theme-sync")||!root.hasAttribute("data-ff-theme-href");function compiled(){if(root.hasAttribute("data-ff-theme-failed"))return true;if(root.hasAttribute("data-ff-theme-href")&&!hasSheet())return false;if(!sheetCompiled)return false;var out=generated();if(!out)return false;return (out.textContent||"").indexOf("--ff-theme-compiled")!==-1;}function show(force){if(shown)return;if(!force&&!compiled())return;shown=true;root.style.visibility="visible";}if(compiled()){show();return;}var obs=new MutationObserver(function(records){var text=false;for(var i=0;i<records.length;i++){var rec=records[i];if(rec.type!=="characterData")continue;var node=rec.target;var el=node&&node.nodeType===3?node.parentNode:node;if(!el||el.id==="ff-theme-hold")continue;if(el.getAttribute&&el.getAttribute("type")==="text/tailwindcss")continue;if(el.tagName==="STYLE")text=true;}if(root.hasAttribute("data-ff-theme-failed"))sheetCompiled=true;if(hasSheet()&&text)sheetCompiled=true;if(compiled()){obs.disconnect();show();}});obs.observe(document.head,{childList:true,subtree:true,characterData:true});obs.observe(root,{attributes:true,attributeFilter:["data-ff-theme-failed"]});setTimeout(function(){obs.disconnect();show(true);},4000);})();</script>"""
