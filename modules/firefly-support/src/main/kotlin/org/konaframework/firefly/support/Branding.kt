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
data class PanelBranding(
    val name: String? = null,
    val logo: String? = null,
    val logoDark: String? = null,
    val logoHeight: Any? = null,
    val brandDisplay: BrandDisplay? = null,
    val copyright: String? = null,
    val googleFont: Any? = null,
    val fontFamily: String? = null,
    val fontUrl: String? = null,
    val primaryColor: String? = null,
    val accentColor: String? = null,
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
        private var brandDisplay: BrandDisplay? = null
        private var copyright: String? = null
        private var googleFont: Any? = null
        private var fontFamily: String? = null
        private var fontUrl: String? = null
        private var primaryColor: String? = null
        private var accentColor: String? = null

        fun name(value: String?) = apply { name = value }
        fun logo(value: String?) = apply { logo = value }
        fun logoDark(value: String?) = apply { logoDark = value }
        fun logoHeight(value: Any?) = apply { logoHeight = value }
        fun brandDisplay(value: BrandDisplay?) = apply { brandDisplay = value }
        fun copyright(value: String?) = apply { copyright = value }
        fun googleFont(value: Any?) = apply { googleFont = value }
        fun fontFamily(value: String?) = apply { fontFamily = value }
        fun fontUrl(value: String?) = apply { fontUrl = value }
        fun primaryColor(value: String?) = apply { primaryColor = value }
        fun accentColor(value: String?) = apply { accentColor = value }

        fun build(): PanelBranding =
            PanelBranding(
                name,
                logo,
                logoDark,
                logoHeight,
                brandDisplay,
                copyright,
                googleFont,
                fontFamily,
                fontUrl,
                primaryColor,
                accentColor,
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
    val brandDisplay: BrandDisplay = BrandDisplay.BOTH,
    val showLogo: Boolean = false,
    val showBrandName: Boolean = true,
    val copyrightText: String? = null,
    val fontFamily: String = DEFAULT.fontFamily,
    val fontUrl: String? = null,
    val fontPreconnect: Boolean = false,
    val primaryColor: String = DEFAULT.primaryColor,
    val accentColor: String = DEFAULT.accentColor,
) {
    companion object {
        @JvmField
        val DEFAULT = FireflyBranding(
            brandName = "Firefly",
            logoHeight = "2rem",
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
        brandDisplay = override.brandDisplay ?: base.brandDisplay,
        copyright = nonBlank(override.copyright) ?: nonBlank(base.copyright),
        googleFont = override.googleFont ?: base.googleFont,
        fontFamily = nonBlank(override.fontFamily) ?: nonBlank(base.fontFamily),
        fontUrl = nonBlank(override.fontUrl) ?: nonBlank(base.fontUrl),
        primaryColor = nonBlank(override.primaryColor) ?: nonBlank(base.primaryColor),
        accentColor = nonBlank(override.accentColor) ?: nonBlank(base.accentColor),
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
            brandDisplay = override.brandDisplay,
            copyrightText = chooseNullable(override.copyrightText, base.copyrightText),
            fontFamily = choose(override.fontFamily, base.fontFamily),
            fontUrl = chooseNullable(override.fontUrl, base.fontUrl),
            fontPreconnect = override.fontPreconnect || base.fontPreconnect,
            primaryColor = choose(override.primaryColor, base.primaryColor),
            accentColor = choose(override.accentColor, base.accentColor),
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
    val height =
        if (hasLogoHeight(config.logoHeight)) {
            normalizeLogoHeight(config.logoHeight)
        } else {
            FireflyBranding.DEFAULT.logoHeight
        }
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
            brandDisplay = normalizeBrandDisplay(config.brandDisplay),
            copyrightText = nonBlank(config.copyright),
            fontFamily = if (fontFamily == null) FireflyBranding.DEFAULT.fontFamily else fontFamily,
            fontUrl = fontUrl,
            fontPreconnect = preconnect,
            primaryColor = if (primary == null) FireflyBranding.DEFAULT.primaryColor else primary,
            accentColor = if (accent == null) FireflyBranding.DEFAULT.accentColor else accent,
        ),
    )
}

fun resolveEffectiveBranding(panelBranding: PanelBranding?, override: PanelBranding?): FireflyBranding {
    val base = resolveBranding(panelBranding)
    if (override == null) return base
    if (!hasOverrideContent(override)) return base
    val height =
        if (hasLogoHeight(override.logoHeight)) {
            normalizeLogoHeight(override.logoHeight)
        } else {
            base.logoHeight
        }
    return withBrandVisibility(
        FireflyBranding(
            brandName = nonBlank(override.name) ?: base.brandName,
            logoUrl = nonBlank(override.logo) ?: base.logoUrl,
            logoDarkUrl = nonBlank(override.logoDark) ?: base.logoDarkUrl,
            logoHeight = height,
            brandDisplay = override.brandDisplay ?: base.brandDisplay,
            copyrightText = nonBlank(override.copyright) ?: base.copyrightText,
            fontFamily = base.fontFamily,
            fontUrl = base.fontUrl,
            fontPreconnect = base.fontPreconnect,
            primaryColor = nonBlank(override.primaryColor) ?: base.primaryColor,
            accentColor = nonBlank(override.accentColor) ?: base.accentColor,
        ),
    )
}

private fun hasLogoHeight(value: Any?): Boolean {
    if (value == null) return false
    return value.toString().isNotBlank()
}

private fun hasOverrideContent(override: PanelBranding): Boolean {
    if (nonBlank(override.name) != null) return true
    if (nonBlank(override.logo) != null) return true
    if (nonBlank(override.logoDark) != null) return true
    if (hasLogoHeight(override.logoHeight)) return true
    if (override.brandDisplay != null) return true
    if (nonBlank(override.copyright) != null) return true
    if (nonBlank(override.primaryColor) != null) return true
    if (nonBlank(override.accentColor) != null) return true
    return false
}

fun normalizeBrandDisplay(value: BrandDisplay?): BrandDisplay = value ?: BrandDisplay.BOTH

fun normalizeLogoHeight(value: Any?): String {
    if (value == null) return FireflyBranding.DEFAULT.logoHeight
    if (value is Number) {
        val n = value.toDouble()
        if (n <= 0) return FireflyBranding.DEFAULT.logoHeight
        if (!n.isFinite()) return FireflyBranding.DEFAULT.logoHeight
        if (n == kotlin.math.floor(n)) return "${n.toInt()}px"
        return "${n}px"
    }
    val raw = value.toString().trim()
    if (raw.isEmpty()) return FireflyBranding.DEFAULT.logoHeight
    if (raw.matches(Regex("""^\d+(\.\d+)?$"""))) return "${raw}px"
    return raw
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
            """<img class="ff-brand-logo ff-brand-logo--light" src="${escapeAttr(logo)}" alt="" height="${escapeAttr(branding.logoHeight)}" />""",
        )
        parts.append(
            """<img class="ff-brand-logo ff-brand-logo--dark" src="${escapeAttr(dark)}" alt="" height="${escapeAttr(branding.logoHeight)}" />""",
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
