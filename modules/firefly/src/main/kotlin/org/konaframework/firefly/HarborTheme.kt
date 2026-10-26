package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyTheme
import org.konaframework.firefly.support.ThemeTokens

/**
 * Sample theme a panel can select with `theme(HarborTheme())`.
 * Tokens change the caramel defaults. The sidebar partial keeps the hook markers.
 */
class HarborTheme : FireflyTheme {
    override fun id(): String = "harbor"

    override fun tokens(): ThemeTokens = ThemeTokens(
        primary = "#0F6E6E",
        accent = "#74453F",
        surface = "#F3F7F7",
        heading = "#102422",
        font = "ui-sans-serif, system-ui, sans-serif",
        radius = "0.75rem",
        sidebarWidth = "18rem",
        topbarHeight = "4rem",
        bodyBackground = "#F3F7F7",
        darkPrimary = "#8FCBD6",
        darkSurface = "#102422",
        darkHeading = "#E8D4B8",
    )

    override fun partial(slot: String): String? {
        if (slot == "sidebar") {
            return """<aside class="ff-sidebar ff-harbor"><!-- ff-hook:sidebar.start --><!-- ff-hook:sidebar.end --></aside>"""
        }
        return null
    }

    override fun extraCss(): String = ".ff-harbor-mark{color:#0F6E6E}"
}
