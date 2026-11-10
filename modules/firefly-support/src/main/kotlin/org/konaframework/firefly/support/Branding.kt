package org.konaframework.firefly.support

/**
 * What the shell / auth brand mark shows (Shamar `BrandDisplay`).
 * - [BOTH] — logo (when set) and brand name (default)
 * - [LOGO] — logo only (falls back to name if no logo URL)
 * - [NAME] — brand text only
 */
enum class BrandDisplay {
    BOTH,
    LOGO,
    NAME,
}

/**
 * Panel chrome branding config (Shamar `PanelBranding`).
 * Empty / null fields are ignored when merging.
 */
data class PanelBranding @JvmOverloads constructor(
    val name: String? = null,
    val logo: String? = null,
    val logoDark: String? = null,
    val logoHeight: Any? = null,
    val logoWidth: Any? = null,
    val brandDisplay: BrandDisplay? = null,
    val copyright: String? = null,
    val googleFont: Any? = null,
    val fontFamily: String? = null,
    val fontUrl: String? = null,
    val primaryColor: String? = null,
    val accentColor: String? = null,
    /** Dark primary. Null derives a shade of [primaryColor], or the theme fallback when that is unset. */
    val darkPrimaryColor: String? = null,
    /** Dark accent. Null derives a shade of [accentColor], or keeps the light accent when that is unset. */
    val darkAccentColor: String? = null,
) {
    fun merge(override: PanelBranding?): PanelBranding {
        if (override == null) return this
        return mergePanelBranding(this, override)!!
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var name: String? = null
        private var logo: String? = null
        private var logoDark: String? = null
        private var logoHeight: Any? = null
        private var logoWidth: Any? = null
        private var brandDisplay: BrandDisplay? = null
        private var copyright: String? = null
        private var googleFont: Any? = null
        private var fontFamily: String? = null
        private var fontUrl: String? = null
        private var primaryColor: String? = null
        private var accentColor: String? = null
        private var darkPrimaryColor: String? = null
        private var darkAccentColor: String? = null

        fun name(value: String?) = apply { name = value }
        fun logo(value: String?) = apply { logo = value }
        fun logoDark(value: String?) = apply { logoDark = value }
        fun logoHeight(value: Any?) = apply { logoHeight = value }
        fun logoWidth(value: Any?) = apply { logoWidth = value }
        fun brandDisplay(value: BrandDisplay?) = apply { brandDisplay = value }
        fun copyright(value: String?) = apply { copyright = value }
        fun googleFont(value: Any?) = apply { googleFont = value }
        fun fontFamily(value: String?) = apply { fontFamily = value }
        fun fontUrl(value: String?) = apply { fontUrl = value }
        fun primaryColor(value: String?) = apply { primaryColor = value }
        fun accentColor(value: String?) = apply { accentColor = value }
        fun darkPrimaryColor(value: String?) = apply { darkPrimaryColor = value }
        fun darkAccentColor(value: String?) = apply { darkAccentColor = value }

        fun build(): PanelBranding =
            PanelBranding(
                name,
                logo,
                logoDark,
                logoHeight,
                logoWidth,
                brandDisplay,
                copyright,
                googleFont,
                fontFamily,
                fontUrl,
                primaryColor,
                accentColor,
                darkPrimaryColor,
                darkAccentColor,
            )
    }
}

/** Google Font options (Shamar `GoogleFontOptions`). */
data class GoogleFontOptions(
    val family: String,
    val weights: List<Int> = listOf(400, 500, 600, 700),
    val italic: Boolean = false,
    val display: String = "swap",
)

/**
 * Runtime branding for chrome / auth (Shamar `ShamarBranding`).
 */
data class FireflyBranding(
    val brandName: String = DEFAULT.brandName,
    val logoUrl: String? = null,
    val logoDarkUrl: String? = null,
    val logoHeight: String = DEFAULT.logoHeight,
    val logoWidth: String = "auto",
    val brandDisplay: BrandDisplay = BrandDisplay.BOTH,
    val showLogo: Boolean = false,
    val showBrandName: Boolean = true,
    val copyrightText: String? = null,
    val fontFamily: String = DEFAULT.fontFamily,
    val fontUrl: String? = null,
    val fontPreconnect: Boolean = false,
    val primaryColor: String = DEFAULT.primaryColor,
    val accentColor: String = DEFAULT.accentColor,
    /**
     * Primary the panel set, normalized, or null when the panel left primary unset.
     * A null value is what selects the theme's dark fallback (Kona's `#C9A57B` on [KonaTheme]).
     */
    val panelPrimary: String? = null,
    /** Accent the panel set, normalized, or null when the panel left accent unset. */
    val panelAccent: String? = null,
    /** Explicit dark primary from the panel. Null means derive, or use the theme fallback. */
    val panelDarkPrimary: String? = null,
    /** Explicit dark accent from the panel. Null means derive, or keep the light accent. */
    val panelDarkAccent: String? = null,
) {
    companion object {
        @JvmField
        val DEFAULT = FireflyBranding(
            brandName = "Firefly",
            logoHeight = "2rem",
            logoWidth = "auto",
            brandDisplay = BrandDisplay.BOTH,
            showLogo = false,
            showBrandName = true,
            fontFamily = "ui-sans-serif, system-ui, sans-serif, \"Apple Color Emoji\", \"Segoe UI Emoji\"",
            primaryColor = "#DF8723",
            accentColor = "#74453F",
        )
    }
}

/** Context passed to [org.konaframework.firefly.Panel.resolveBrandingOverrides]. */
data class BrandingOverrideContext(
    val panelId: String? = null,
    val companyId: String? = null,
    val userId: String? = null,
)

internal fun nonBlank(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    return if (trimmed.isEmpty()) null else trimmed
}

fun mergePanelBranding(base: PanelBranding?, override: PanelBranding?): PanelBranding? {
    if (base == null && override == null) return null
    if (override == null) return base
    if (base == null) return override
    return PanelBranding(
        name = nonBlank(override.name) ?: nonBlank(base.name),
        logo = nonBlank(override.logo) ?: nonBlank(base.logo),
        logoDark = nonBlank(override.logoDark) ?: nonBlank(base.logoDark),
        logoHeight = override.logoHeight ?: base.logoHeight,
        logoWidth = override.logoWidth ?: base.logoWidth,
        brandDisplay = override.brandDisplay ?: base.brandDisplay,
        copyright = nonBlank(override.copyright) ?: nonBlank(base.copyright),
        googleFont = override.googleFont ?: base.googleFont,
        fontFamily = nonBlank(override.fontFamily) ?: nonBlank(base.fontFamily),
        fontUrl = nonBlank(override.fontUrl) ?: nonBlank(base.fontUrl),
        primaryColor = nonBlank(override.primaryColor) ?: nonBlank(base.primaryColor),
        accentColor = nonBlank(override.accentColor) ?: nonBlank(base.accentColor),
        darkPrimaryColor = nonBlank(override.darkPrimaryColor) ?: nonBlank(base.darkPrimaryColor),
        darkAccentColor = nonBlank(override.darkAccentColor) ?: nonBlank(base.darkAccentColor),
    )
}

fun mergeBranding(base: FireflyBranding, override: FireflyBranding?): FireflyBranding {
    if (override == null) return withBrandVisibility(base)
    fun choose(preferred: String?, fallback: String): String {
        val value = nonBlank(preferred)
        return value ?: fallback
    }
    fun chooseNullable(preferred: String?, fallback: String?): String? {
        val value = nonBlank(preferred)
        return value ?: fallback
    }
    return withBrandVisibility(
        FireflyBranding(
            brandName = choose(override.brandName, base.brandName),
            logoUrl = chooseNullable(override.logoUrl, base.logoUrl),
            logoDarkUrl = chooseNullable(override.logoDarkUrl, base.logoDarkUrl),
            logoHeight = choose(override.logoHeight, base.logoHeight),
            logoWidth = choose(override.logoWidth, base.logoWidth),
            brandDisplay = override.brandDisplay,
            copyrightText = chooseNullable(override.copyrightText, base.copyrightText),
            fontFamily = choose(override.fontFamily, base.fontFamily),
            fontUrl = chooseNullable(override.fontUrl, base.fontUrl),
            fontPreconnect = override.fontPreconnect || base.fontPreconnect,
            primaryColor = choose(override.primaryColor, base.primaryColor),
            accentColor = choose(override.accentColor, base.accentColor),
            panelPrimary = override.panelPrimary ?: base.panelPrimary,
            panelAccent = override.panelAccent ?: base.panelAccent,
            panelDarkPrimary = override.panelDarkPrimary ?: base.panelDarkPrimary,
            panelDarkAccent = override.panelDarkAccent ?: base.panelDarkAccent,
        ),
    )
}

fun resolveBranding(config: PanelBranding?): FireflyBranding {
    if (config == null) return withBrandVisibility(FireflyBranding.DEFAULT)
    var fontFamily = nonBlank(config.fontFamily)
    var fontUrl = nonBlank(config.fontUrl)
    if (fontFamily == null || fontUrl == null) {
        val google = config.googleFont
        if (google != null) {
            val resolved = resolveGoogleFont(google)
            if (fontFamily == null) fontFamily = resolved.first
            if (fontUrl == null) fontUrl = resolved.second
        }
    }
    val (height, width) = resolveLogoBox(config.logoHeight, config.logoWidth)
    val preconnect =
        fontUrl != null &&
            (fontUrl.contains("fonts.googleapis.com") || fontUrl.contains("fonts.gstatic.com"))
    val name = nonBlank(config.name)
    val primary = nonBlank(config.primaryColor)
    val accent = nonBlank(config.accentColor)
    return withBrandVisibility(
        FireflyBranding(
            brandName = if (name == null) FireflyBranding.DEFAULT.brandName else name,
            logoUrl = nonBlank(config.logo),
            logoDarkUrl = nonBlank(config.logoDark),
            logoHeight = height,
            logoWidth = width,
            brandDisplay = normalizeBrandDisplay(config.brandDisplay),
            copyrightText = nonBlank(config.copyright),
            fontFamily = if (fontFamily == null) FireflyBranding.DEFAULT.fontFamily else fontFamily,
            fontUrl = fontUrl,
            fontPreconnect = preconnect,
            primaryColor = if (primary == null) FireflyBranding.DEFAULT.primaryColor else primary,
            accentColor = if (accent == null) FireflyBranding.DEFAULT.accentColor else accent,
            panelPrimary = primary?.let { hexOrNull(it) },
            panelAccent = accent?.let { hexOrNull(it) },
            panelDarkPrimary = nonBlank(config.darkPrimaryColor)?.let { hexOrNull(it) },
            panelDarkAccent = nonBlank(config.darkAccentColor)?.let { hexOrNull(it) },
        ),
    )
}

fun resolveEffectiveBranding(panelBranding: PanelBranding?, override: PanelBranding?): FireflyBranding {
    val base = resolveBranding(panelBranding)
    if (override == null) return base
    if (!hasOverrideContent(override)) return base
    val (height, width) = effectiveLogoBox(panelBranding, override, base)
    return withBrandVisibility(
        FireflyBranding(
            brandName = nonBlank(override.name) ?: base.brandName,
            logoUrl = nonBlank(override.logo) ?: base.logoUrl,
            logoDarkUrl = nonBlank(override.logoDark) ?: base.logoDarkUrl,
            logoHeight = height,
            logoWidth = width,
            brandDisplay = override.brandDisplay ?: base.brandDisplay,
            copyrightText = nonBlank(override.copyright) ?: base.copyrightText,
            fontFamily = base.fontFamily,
            fontUrl = base.fontUrl,
            fontPreconnect = base.fontPreconnect,
            primaryColor = nonBlank(override.primaryColor) ?: base.primaryColor,
            accentColor = nonBlank(override.accentColor) ?: base.accentColor,
            panelPrimary = nonBlank(override.primaryColor)?.let { hexOrNull(it) } ?: base.panelPrimary,
            panelAccent = nonBlank(override.accentColor)?.let { hexOrNull(it) } ?: base.panelAccent,
            panelDarkPrimary = nonBlank(override.darkPrimaryColor)?.let { hexOrNull(it) } ?: base.panelDarkPrimary,
            panelDarkAccent = nonBlank(override.darkAccentColor)?.let { hexOrNull(it) } ?: base.panelDarkAccent,
        ),
    )
}

private fun hasLogoSize(value: Any?): Boolean {
    if (value == null) return false
    return value.toString().isNotBlank()
}

private fun hasOverrideContent(override: PanelBranding): Boolean {
    if (nonBlank(override.name) != null) return true
    if (nonBlank(override.logo) != null) return true
    if (nonBlank(override.logoDark) != null) return true
    if (hasLogoSize(override.logoHeight)) return true
    if (hasLogoSize(override.logoWidth)) return true
    if (override.brandDisplay != null) return true
    if (nonBlank(override.copyright) != null) return true
    if (nonBlank(override.primaryColor) != null) return true
    if (nonBlank(override.accentColor) != null) return true
    if (nonBlank(override.darkPrimaryColor) != null) return true
    if (nonBlank(override.darkAccentColor) != null) return true
    return false
}

fun normalizeBrandDisplay(value: BrandDisplay?): BrandDisplay = value ?: BrandDisplay.BOTH

/**
 * CSS length for a logo dimension, or null when unset / not a usable length.
 * Bare numbers are pixels. `px` and `rem` (and any other CSS length) pass through.
 */
internal fun parseCssLength(value: Any?): String? {
    if (value == null) return null
    if (value is Number) {
        val n = value.toDouble()
        if (n <= 0) return null
        if (!n.isFinite()) return null
        if (n == kotlin.math.floor(n)) return "${n.toInt()}px"
        return "${n}px"
    }
    val raw = value.toString().trim()
    if (raw.isEmpty()) return null
    if (raw.matches(Regex("""^\d+(\.\d+)?$"""))) return "${raw}px"
    return raw
}

fun normalizeLogoHeight(value: Any?): String =
    parseCssLength(value) ?: FireflyBranding.DEFAULT.logoHeight

fun normalizeLogoWidth(value: Any?): String = parseCssLength(value) ?: "auto"

/**
 * One configured dimension stays `auto` on the other axis so the SVG keeps its aspect ratio.
 * Both apply when both are set. Neither falls back to a modest height.
 */
internal fun resolveLogoBox(heightRaw: Any?, widthRaw: Any?): Pair<String, String> {
    val height = parseCssLength(heightRaw)
    val width = parseCssLength(widthRaw)
    if (height != null && width != null) return height to width
    if (height != null) return height to "auto"
    if (width != null) return "auto" to width
    return FireflyBranding.DEFAULT.logoHeight to "auto"
}

/**
 * An override that sets only one dimension does not keep a default height (or width) from the
 * panel, which would squash the mark. A dimension the panel actually configured is kept.
 */
private fun effectiveLogoBox(
    panel: PanelBranding?,
    override: PanelBranding,
    base: FireflyBranding,
): Pair<String, String> {
    val overrideHeight = parseCssLength(override.logoHeight)
    val overrideWidth = parseCssLength(override.logoWidth)
    val panelHeight = parseCssLength(panel?.logoHeight)
    val panelWidth = parseCssLength(panel?.logoWidth)
    val height =
        when {
            overrideHeight != null -> overrideHeight
            overrideWidth != null && panelHeight == null -> "auto"
            else -> base.logoHeight
        }
    val width =
        when {
            overrideWidth != null -> overrideWidth
            overrideHeight != null && panelWidth == null -> "auto"
            else -> base.logoWidth
        }
    return height to width
}

fun resolveGoogleFont(input: Any): Pair<String, String> {
    val opts =
        when (input) {
            is GoogleFontOptions -> input
            is String -> GoogleFontOptions(family = input.trim())
            else -> throw FireflyException("googleFont must be a String or GoogleFontOptions")
        }
    val family = opts.family.trim()
    if (family.isEmpty()) throw FireflyException("googleFont.family must be a non-empty string")
    val weights = opts.weights.distinct().sorted().ifEmpty { listOf(400, 500, 600, 700) }
    val familyParam = java.net.URLEncoder.encode(family, Charsets.UTF_8).replace("+", "%20").replace("%20", "+")
    val axis =
        if (opts.italic) {
            val pairs = weights.map { "0,$it" } + weights.map { "1,$it" }
            "ital,wght@${pairs.joinToString(";")}"
        } else {
            "wght@${weights.joinToString(";")}"
        }
    val fontUrl = "https://fonts.googleapis.com/css2?family=$familyParam:$axis&display=${opts.display}"
    val fontFamily = "\"${family.replace("\"", "")}\", ${FireflyBranding.DEFAULT.fontFamily}"
    return fontFamily to fontUrl
}

fun withBrandVisibility(branding: FireflyBranding): FireflyBranding {
    val mode = normalizeBrandDisplay(branding.brandDisplay)
    val hasLogo = nonBlank(branding.logoUrl) != null
    return when (mode) {
        BrandDisplay.NAME -> branding.copy(brandDisplay = mode, showLogo = false, showBrandName = true)
        BrandDisplay.LOGO -> branding.copy(brandDisplay = mode, showLogo = hasLogo, showBrandName = !hasLogo)
        BrandDisplay.BOTH -> branding.copy(brandDisplay = mode, showLogo = hasLogo, showBrandName = true)
    }
}

fun buildBrandingCss(branding: FireflyBranding): String {
    val primary = normalizeHexColor(branding.primaryColor) ?: FireflyBranding.DEFAULT.primaryColor
    val accent = normalizeHexColor(branding.accentColor) ?: FireflyBranding.DEFAULT.accentColor
    return """
/* Firefly panel branding — generated at runtime */
.ff-panel,
.ff-auth {
  --ff-primary: $primary;
  --ff-accent: $accent;
  --ff-logo-height: ${branding.logoHeight};
  --ff-logo-width: ${branding.logoWidth};
  --ff-font: ${branding.fontFamily};
  --ff-brand-soft: color-mix(in srgb, $primary 28%, white);
}
.ff-panel.ff-dark,
.ff-auth.ff-dark {
  --ff-brand-soft: color-mix(in srgb, $primary 42%, black);
}
""".trimIndent()
}

/** HTML for sidebar / topbar / auth brand mark. */
fun brandMarkHtml(branding: FireflyBranding, extraClass: String = ""): String {
    val classes = listOf("ff-brand-mark", extraClass.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    val parts = StringBuilder("""<span class="$classes">""")
    val logo = nonBlank(branding.logoUrl)
    if (branding.showLogo && logo != null) {
        val dark = nonBlank(branding.logoDarkUrl) ?: logo
        parts.append(
            """<img class="ff-brand-logo ff-brand-logo--light" src="${escapeAttr(logo)}" alt="" />""",
        )
        parts.append(
            """<img class="ff-brand-logo ff-brand-logo--dark" src="${escapeAttr(dark)}" alt="" />""",
        )
    }
    if (branding.showBrandName) {
        parts.append("""<span class="ff-brand">${escapeAttr(branding.brandName)}</span>""")
    }
    parts.append("</span>")
    return parts.toString()
}

fun normalizeHexColor(value: String): String? {
    val trimmed = value.trim()
    val match = Regex("""^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$""").matchEntire(trimmed) ?: return null
    val hex = match.groupValues[1]
    return if (hex.length == 3) {
        "#" + hex.map { "$it$it" }.joinToString("").lowercase()
    } else {
        "#${hex.lowercase()}"
    }
}

/** Prefer a valid hex color; otherwise [fallback]. */
fun resolveThemeColor(raw: String, fallback: String): String {
    val parsed = normalizeHexColor(raw)
    if (parsed == null) return fallback
    return parsed
}

/** A trimmed hex color, or null when [raw] is blank or not a hex color. */
internal fun hexOrNull(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return normalizeHexColor(raw)
}

/**
 * Panel light color that should drive the dark shade.
 * [explicit] is the normalized color the panel set, including a color that matches the default.
 * When [explicit] is null, a [resolved] color that differs from [defaultColor] still counts as set
 * (a [FireflyBranding] built with a custom `primaryColor`). The stock default does not.
 */
internal fun panelSuppliedColor(explicit: String?, resolved: String, defaultColor: String): String? {
    if (explicit != null) return hexOrNull(explicit)
    val hex = hexOrNull(resolved) ?: return null
    val fallback = hexOrNull(defaultColor) ?: return hex
    if (hex == fallback) return null
    return hex
}

/**
 * Dark brand color.
 * Panel explicit dark, then the theme hook, then a shade of [panelLight], then [fallback].
 */
internal fun resolveDarkBrandColor(
    panelDark: String?,
    themeDark: String?,
    panelLight: String?,
    fallback: String,
): String {
    val chosen = explicitOrDerivedDark(panelDark, themeDark, panelLight)
    if (chosen != null) return chosen
    return fallback
}

/**
 * Panel explicit dark, then the theme hook, then a shade of [panelLight].
 * Null means the light color should stay as it is.
 */
internal fun explicitOrDerivedDark(panelDark: String?, themeDark: String?, panelLight: String?): String? {
    val panel = hexOrNull(panelDark)
    if (panel != null) return panel
    val theme = hexOrNull(themeDark)
    if (theme != null) return theme
    if (panelLight != null) return deriveDarkColor(panelLight)
    return null
}

/** Theme hook color, or [fallback] when the hook is null, blank, or not hex. */
internal fun themeDarkOrFallback(raw: String?, fallback: String): String {
    val hex = hexOrNull(raw)
    if (hex == null) return fallback
    return hex
}

/**
 * Dark-mode shade of a normalized `#rrggbb` color. The hue stays.
 * A light color (relative luminance at least 0.5) is `color-mix` of 70% color and 30% black.
 * Any other color is `color-mix` of 72% color and 28% white, so it stays readable on a dark surface.
 * `#ff2a00` mixes toward white and becomes `#ff6647`.
 */
internal fun deriveDarkColor(hex: String): String {
    val red = hex.substring(1, 3).toInt(16)
    val green = hex.substring(3, 5).toInt(16)
    val blue = hex.substring(5, 7).toInt(16)
    val light = relativeLuminance(red, green, blue) >= 0.5
    val keep = if (light) 0.70 else 0.72
    val other = if (light) 0 else 255
    fun channel(value: Int): Int = kotlin.math.round(value * keep + other * (1.0 - keep)).toInt()
    return "#%02x%02x%02x".format(channel(red), channel(green), channel(blue))
}

private fun relativeLuminance(red: Int, green: Int, blue: Int): Double {
    fun linear(channel: Int): Double {
        val encoded = channel / 255.0
        if (encoded <= 0.04045) return encoded / 12.92
        return Math.pow((encoded + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
}

private fun escapeAttr(value: String): String = buildString {
    for (ch in value) {
        when (ch) {
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '<' -> append("&lt;")
            else -> append(ch)
        }
    }
}
