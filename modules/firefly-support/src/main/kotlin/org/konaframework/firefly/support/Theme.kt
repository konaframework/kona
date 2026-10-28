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

interface FireflyTheme {
    fun id(): String

    fun tokens(): ThemeTokens

    /** Replacement HTML for a chrome slot, or null to keep the built-in partial. */
    fun partial(slot: String): String? = null

    /** Extra CSS. Must not style the host page outside `.ff-panel`. */
    fun extraCss(): String = ""
}

class KonaTheme : FireflyTheme {
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
