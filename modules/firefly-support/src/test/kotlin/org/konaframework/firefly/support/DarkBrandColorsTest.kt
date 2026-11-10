package org.konaframework.firefly.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DarkBrandColorsTest {
    @Test
    fun panelPrimaryDerivesDarkAndDoesNotUseKonaLatte() {
        val css = Stylesheet.render(KonaTheme(), FireflyBranding(primaryColor = "#FF2A00", accentColor = "#286291"))
        assertTrue(css.contains("--ff-primary: #ff2a00"))
        assertTrue(css.contains("--ff-primary: #ff6647"))
        assertFalse(css.contains("--ff-primary: #C9A57B"))
        assertFalse(css.contains("--ff-primary: #c9a57b"))
        assertTrue(css.contains("--ff-accent: #286291"))
        assertTrue(css.contains("--ff-accent: #648eb0"))
        assertEquals("#ff6647", deriveDarkColor("#ff2a00"))
        assertEquals("#648eb0", deriveDarkColor("#286291"))
    }

    @Test
    fun unsetPanelColorsUseKonaLightAndDark() {
        val bare = Stylesheet.render(KonaTheme())
        assertTrue(bare.contains("--ff-primary: #DF8723"))
        assertTrue(bare.contains("--ff-primary: #C9A57B"))
        assertTrue(bare.contains("--ff-surface: #FAF6F0"))
        assertEquals(listOf("#74453F"), accentDeclarations(bare))
        assertTrue(bare.contains("--ff-surface: #1F2937"))
        assertTrue(bare.contains("--ff-heading: #F3F4F6"))

        val defaults = Stylesheet.render(KonaTheme(), FireflyBranding.DEFAULT)
        assertTrue(defaults.contains("--ff-primary: #df8723"))
        assertTrue(defaults.contains("--ff-primary: #C9A57B"))
        assertTrue(accentDeclarations(defaults).all { it == "#74453f" })

        val resolved = resolveBranding(PanelBranding())
        assertNull(resolved.panelPrimary)
        assertNull(resolved.panelAccent)
        val resolvedCss = Stylesheet.render(KonaTheme(), resolved)
        assertTrue(resolvedCss.contains("--ff-primary: #C9A57B"))
        assertFalse(resolvedCss.contains("--ff-primary: #ff6647"))
    }

    @Test
    fun explicitDarkColorsWinOverTheMix() {
        val panel = resolveBranding(
            PanelBranding(
                primaryColor = "#FF2A00",
                accentColor = "#286291",
                darkPrimaryColor = "#112233",
                darkAccentColor = "#445566",
            ),
        )
        assertEquals("#ff2a00", panel.panelPrimary)
        assertEquals("#112233", panel.panelDarkPrimary)
        assertEquals("#445566", panel.panelDarkAccent)
        val css = Stylesheet.render(KonaTheme(), panel)
        assertTrue(css.contains("--ff-primary: #ff2a00"))
        assertTrue(css.contains("--ff-primary: #112233"))
        assertFalse(css.contains("--ff-primary: #ff6647"))
        assertTrue(css.contains("--ff-accent: #445566"))
        assertFalse(css.contains("--ff-accent: #648eb0"))

        val themeWins = object : FireflyTheme() {
            override fun darkPrimary(): String? = "#C9A57B"

            override fun darkAccent(): String? = "#010203"
        }
        val themed = Stylesheet.render(themeWins, FireflyBranding(primaryColor = "#FF2A00", accentColor = "#286291"))
        assertTrue(themed.contains("--ff-primary: #c9a57b"))
        assertTrue(themed.contains("--ff-accent: #010203"))
        assertFalse(themed.contains("--ff-primary: #ff6647"))

        val panelBeatsTheme = Stylesheet.render(
            themeWins,
            FireflyBranding(
                primaryColor = "#FF2A00",
                accentColor = "#286291",
                panelDarkPrimary = "#abcdef",
                panelDarkAccent = "#fedcba",
            ),
        )
        assertTrue(panelBeatsTheme.contains("--ff-primary: #abcdef"))
        assertTrue(panelBeatsTheme.contains("--ff-accent: #fedcba"))
        assertFalse(panelBeatsTheme.contains("--ff-accent: #010203"))
    }

    @Test
    fun themeTokenDarkPrimaryDoesNotBeatAPanelPrimary() {
        val css = Stylesheet.render(TealTokens(), FireflyBranding(primaryColor = "#FF2A00"))
        assertTrue(css.contains("--ff-primary: #ff2a00"))
        assertTrue(css.contains("--ff-primary: #ff6647"))
        assertFalse(css.contains("#8FCBD6"))

        val unset = Stylesheet.render(TealTokens(), FireflyBranding.DEFAULT)
        assertTrue(unset.contains("--ff-primary: #8FCBD6"))
        assertTrue(unset.contains("--ff-surface: #102422"))
        assertTrue(unset.contains("--ff-heading: #E8D4B8"))
    }

    @Test
    fun settingTheKonaPrimaryStillDerivesInsteadOfUsingLatte() {
        val resolved = resolveBranding(PanelBranding(primaryColor = "#DF8723", accentColor = "#74453F"))
        assertEquals("#df8723", resolved.panelPrimary)
        assertEquals("#74453f", resolved.panelAccent)
        val css = Stylesheet.render(KonaTheme(), resolved)
        assertTrue(css.contains("--ff-primary: #e8a961"))
        assertFalse(css.contains("--ff-primary: #C9A57B"))
        assertTrue(css.contains("--ff-accent: #9b7975"))
    }

    @Test
    fun themeHooksOverrideSurfaceAndHeading() {
        assertNull(FireflyTheme().darkPrimary())
        assertNull(FireflyTheme().darkAccent())
        assertNull(FireflyTheme().darkSurface())
        assertNull(FireflyTheme().darkHeading())

        val hooked = object : FireflyTheme() {
            override fun darkSurface(): String? = "#010203"

            override fun darkHeading(): String? = " #ABCDEF "
        }
        val css = Stylesheet.render(hooked)
        assertTrue(css.contains("--ff-surface: #010203"))
        assertTrue(css.contains("--ff-body: #010203"))
        assertTrue(css.contains("--ff-heading: #abcdef"))
        assertFalse(css.contains("--ff-surface: #1F2937"))
        assertFalse(css.contains("--ff-heading: #F3F4F6"))

        val ignored = object : FireflyTheme() {
            override fun darkSurface(): String? = "  "

            override fun darkHeading(): String? = "nope"
        }
        val fallback = Stylesheet.render(ignored)
        assertTrue(fallback.contains("--ff-surface: #1F2937"))
        assertTrue(fallback.contains("--ff-heading: #F3F4F6"))
        assertEquals("#1F2937", themeDarkOrFallback(null, "#1F2937"))
        assertEquals("#1F2937", themeDarkOrFallback("nope", "#1F2937"))
    }

    @Test
    fun colorHelpersCoverBlankInvalidAndLightMixes() {
        assertNull(hexOrNull(null))
        assertNull(hexOrNull("  "))
        assertNull(hexOrNull("nope"))
        assertEquals("#aabbcc", hexOrNull("#abc"))
        assertEquals("#ff2a00", hexOrNull(" #FF2A00 "))

        assertEquals("#df8723", panelSuppliedColor("#DF8723", "#000000", "#DF8723"))
        assertNull(panelSuppliedColor("nope", "#ff2a00", "#DF8723"))
        assertNull(panelSuppliedColor("  ", "#ff2a00", "#DF8723"))
        assertNull(panelSuppliedColor(null, "nope", "#DF8723"))
        assertEquals("#ff2a00", panelSuppliedColor(null, "#FF2A00", "nope"))
        assertNull(panelSuppliedColor(null, "#DF8723", "#df8723"))
        assertEquals("#ff2a00", panelSuppliedColor(null, "#FF2A00", "#DF8723"))

        assertEquals("#112233", explicitOrDerivedDark("#112233", "#445566", "#ff2a00"))
        assertEquals("#445566", explicitOrDerivedDark("nope", "#445566", "#ff2a00"))
        assertEquals("#ff6647", explicitOrDerivedDark("  ", null, "#ff2a00"))
        assertNull(explicitOrDerivedDark(null, "  ", null))
        assertEquals("#112233", resolveDarkBrandColor("#112233", null, null, "#C9A57B"))
        assertEquals("#C9A57B", resolveDarkBrandColor(null, "nope", null, "#C9A57B"))

        assertEquals("#b2b2b2", deriveDarkColor("#ffffff"))
        assertEquals("#e8a961", deriveDarkColor("#df8723"))
        assertEquals("#9b7975", deriveDarkColor("#74453f"))
    }

    @Test
    fun brandingMergeKeepsDarkColors() {
        val built = PanelBranding.builder()
            .darkPrimaryColor("#112233")
            .darkAccentColor("#445566")
            .build()
        assertEquals("#112233", built.darkPrimaryColor)
        assertEquals("#445566", built.darkAccentColor)

        val primaryFromOverride = mergePanelBranding(
            PanelBranding(darkPrimaryColor = "#111111", darkAccentColor = "#222222"),
            PanelBranding(darkPrimaryColor = "#333333", darkAccentColor = "  "),
        )!!
        assertEquals("#333333", primaryFromOverride.darkPrimaryColor)
        assertEquals("#222222", primaryFromOverride.darkAccentColor)
        val accentFromOverride = mergePanelBranding(
            PanelBranding(darkPrimaryColor = "#111111"),
            PanelBranding(darkAccentColor = "#444444"),
        )!!
        assertEquals("#111111", accentFromOverride.darkPrimaryColor)
        assertEquals("#444444", accentFromOverride.darkAccentColor)

        val base = FireflyBranding.DEFAULT.copy(
            panelPrimary = "#112233",
            panelAccent = "#445566",
            panelDarkPrimary = "#aaaaaa",
            panelDarkAccent = "#bbbbbb",
        )
        val merged = mergeBranding(
            base,
            FireflyBranding(
                panelPrimary = "#ff2a00",
                panelAccent = "#00ff00",
                panelDarkPrimary = "#00ff01",
                panelDarkAccent = "#010203",
            ),
        )
        assertEquals("#ff2a00", merged.panelPrimary)
        assertEquals("#00ff00", merged.panelAccent)
        assertEquals("#00ff01", merged.panelDarkPrimary)
        assertEquals("#010203", merged.panelDarkAccent)
        val kept = mergeBranding(base, FireflyBranding(panelPrimary = "#ff2a00"))
        assertEquals("#445566", kept.panelAccent)
        assertEquals("#aaaaaa", kept.panelDarkPrimary)
        assertEquals("#bbbbbb", kept.panelDarkAccent)

        val darkOnly = resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(darkPrimaryColor = "#abcdef"))
        assertEquals("#abcdef", darkOnly.panelDarkPrimary)
        assertNull(darkOnly.panelPrimary)
        val accentOnly = resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(darkAccentColor = "#123456"))
        assertEquals("#123456", accentOnly.panelDarkAccent)

        val replaced = resolveEffectiveBranding(
            PanelBranding(
                primaryColor = "#111111",
                accentColor = "#222222",
                darkPrimaryColor = "#333333",
                darkAccentColor = "#444444",
            ),
            PanelBranding(primaryColor = "#FF2A00", accentColor = "#286291", darkPrimaryColor = "#010203"),
        )
        assertEquals("#ff2a00", replaced.panelPrimary)
        assertEquals("#286291", replaced.panelAccent)
        assertEquals("#010203", replaced.panelDarkPrimary)
        assertEquals("#444444", replaced.panelDarkAccent)

        val invalid = resolveEffectiveBranding(
            PanelBranding(primaryColor = "#FF2A00", accentColor = "#286291", darkPrimaryColor = "#112233", darkAccentColor = "#445566"),
            PanelBranding(name = "N", primaryColor = "nope", accentColor = "nope", darkPrimaryColor = "nope", darkAccentColor = "zz"),
        )
        assertEquals("#ff2a00", invalid.panelPrimary)
        assertEquals("#286291", invalid.panelAccent)
        assertEquals("#112233", invalid.panelDarkPrimary)
        assertEquals("#445566", invalid.panelDarkAccent)

        val blankDark = resolveBranding(PanelBranding(primaryColor = "  ", darkPrimaryColor = "  ", darkAccentColor = "nope"))
        assertNull(blankDark.panelPrimary)
        assertNull(blankDark.panelDarkPrimary)
        assertNull(blankDark.panelDarkAccent)
    }

    private fun accentDeclarations(css: String): List<String> =
        Regex("""--ff-accent:\s*([^;]+)""").findAll(css).map { it.groupValues[1].trim() }.toList()
}

/** Tokens carry a non-Kona dark primary. The hook stays null, so a panel primary still wins. */
private class TealTokens : FireflyTheme() {
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
}
