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
        font = "\"Inter\", \"DM Sans\", ui-sans-serif, system-ui, sans-serif",
        radius = "0.55rem",
        sidebarWidth = "16.5rem",
        topbarHeight = "3.75rem",
        bodyBackground = "#FAF6F0",
        darkPrimary = "#C9A57B",
        darkSurface = "#1A120C",
        darkHeading = "#E8D4B8",
    )
}
