package org.konaframework.firefly.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandingTest {
    @Test
    fun coversBrandingSurface() {
        assertNull(nonBlank(null))
        assertNull(nonBlank(""))
        assertNull(nonBlank("  "))
        assertEquals("x", nonBlank(" x "))

        assertNull(mergePanelBranding(null, null))
        assertEquals("A", mergePanelBranding(PanelBranding(name = "A"), null)!!.name)
        assertEquals("B", mergePanelBranding(null, PanelBranding(name = "B"))!!.name)
        val merged = mergePanelBranding(
            PanelBranding(
                name = "A",
                logo = "/a.svg",
                logoDark = "/ad.svg",
                logoHeight = 32,
                brandDisplay = BrandDisplay.BOTH,
                copyright = "© A",
                googleFont = "Inter",
                fontFamily = "Inter, sans-serif",
                fontUrl = "https://fonts.googleapis.com/css2?family=Inter",
                primaryColor = "#111111",
                accentColor = "#222222",
            ),
            PanelBranding(name = "B", logo = "", primaryColor = "#333333"),
        )!!
        assertEquals("B", merged.name)
        assertEquals("/a.svg", merged.logo)
        assertEquals("#333333", merged.primaryColor)
        val fullOverride = mergePanelBranding(
            PanelBranding(name = "A"),
            PanelBranding(
                name = "B",
                logo = "/b.svg",
                logoDark = "/bd.svg",
                logoHeight = 40,
                brandDisplay = BrandDisplay.NAME,
                copyright = "© B",
                googleFont = "Roboto",
                fontFamily = "Roboto, sans-serif",
                fontUrl = "https://example.com/r.css",
                primaryColor = "#aaaaaa",
                accentColor = "#bbbbbb",
            ),
        )!!
        assertEquals("/b.svg", fullOverride.logo)
        assertEquals(BrandDisplay.NAME, fullOverride.brandDisplay)
        assertEquals("Roboto", fullOverride.googleFont)
        assertEquals("A", PanelBranding(name = "A").merge(null).name)
        assertEquals("/x.svg", PanelBranding(name = "X").merge(PanelBranding(logo = "/x.svg")).logo)

        val built =
            PanelBranding.builder()
                .name("Built")
                .logo("/b.svg")
                .logoDark("/bd.svg")
                .logoHeight("2.5rem")
                .logoWidth("4rem")
                .brandDisplay(BrandDisplay.LOGO)
                .copyright("© B")
                .googleFont(GoogleFontOptions("Poppins"))
                .fontFamily("Poppins, sans-serif")
                .fontUrl("https://example.com/f.css")
                .primaryColor("#abcdef")
                .accentColor("#fedcba")
                .build()
        assertEquals("Built", built.name)

        assertEquals("Firefly", resolveBranding(null).brandName)
        val base = resolveBranding(
            PanelBranding(
                name = "Desk",
                logo = "/logo.svg",
                logoDark = "/logo-dark.svg",
                logoHeight = 40,
                brandDisplay = BrandDisplay.BOTH,
                primaryColor = "#f1511b",
                accentColor = "#286291",
                copyright = "© Desk",
                googleFont = "DM Sans",
            ),
        )
        assertTrue(base.showLogo)
        assertTrue(base.fontPreconnect)
        assertTrue(buildBrandingCss(base).contains("#f1511b"))

        resolveBranding(PanelBranding(name = "F", fontFamily = "Custom", fontUrl = "https://fonts.gstatic.com/x.css"))
        resolveBranding(PanelBranding(name = "G", googleFont = "Inter", fontFamily = "Keep", fontUrl = "https://cdn/x.css"))
        resolveBranding(PanelBranding(name = "H", googleFont = "Inter"))
        resolveBranding(PanelBranding(name = "H2", googleFont = "Inter", fontFamily = "Keep"))
        resolveBranding(PanelBranding(name = "H3", googleFont = "Inter", fontUrl = "https://cdn/y.css"))
        resolveBranding(PanelBranding(name = "I", logoHeight = "  "))
        resolveBranding(PanelBranding(name = "Bare"))
        resolveBranding(PanelBranding())
        resolveBranding(PanelBranding(name = "  ", primaryColor = "  ", accentColor = "  "))

        assertFalse(resolveBranding(PanelBranding(name = "X", brandDisplay = BrandDisplay.LOGO)).showLogo)
        assertTrue(resolveBranding(PanelBranding(name = "X", logo = "/a.svg", brandDisplay = BrandDisplay.LOGO)).showLogo)
        assertFalse(resolveBranding(PanelBranding(name = "X", logo = "/a.svg", brandDisplay = BrandDisplay.NAME)).showLogo)

        assertEquals("P", resolveEffectiveBranding(PanelBranding(name = "P"), null).brandName)
        assertEquals("P", resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding()).brandName)
        assertEquals(
            "Override",
            resolveEffectiveBranding(
                PanelBranding(name = "Panel", logo = "/p.svg", copyright = "base"),
                PanelBranding(name = "Override", primaryColor = "#112233", brandDisplay = BrandDisplay.NAME, copyright = "over"),
            ).brandName,
        )
        resolveEffectiveBranding(PanelBranding(name = "H"), PanelBranding(logoHeight = "3rem", logo = "/h.svg"))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(logoDark = "/d.svg"))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(logoHeight = "1px"))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(name = "Kept", logoHeight = "  "))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(logoHeight = " "))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(brandDisplay = BrandDisplay.BOTH))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(copyright = "c"))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(accentColor = "#123456"))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(logo = "/o.svg"))
        resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(primaryColor = "#abcdef"))

        mergeBranding(FireflyBranding.DEFAULT, null)
        mergeBranding(
            FireflyBranding.DEFAULT.copy(fontPreconnect = true, logoUrl = "/b.svg"),
            FireflyBranding(brandName = "", logoUrl = "", fontPreconnect = false, primaryColor = "#010203"),
        )
        mergeBranding(
            FireflyBranding.DEFAULT,
            FireflyBranding(
                brandName = "R",
                logoUrl = "/r.svg",
                logoDarkUrl = "/rd.svg",
                logoHeight = "3rem",
                brandDisplay = BrandDisplay.LOGO,
                copyrightText = "cr",
                fontFamily = "F",
                fontUrl = "https://x",
                fontPreconnect = true,
                primaryColor = "#010203",
                accentColor = "#040506",
            ),
        )

        assertEquals("2rem", normalizeLogoHeight(null))
        assertEquals("2rem", normalizeLogoHeight(-1))
        assertEquals("2rem", normalizeLogoHeight(""))
        assertEquals("2rem", normalizeLogoHeight(Double.NaN))
        assertEquals("36px", normalizeLogoHeight(36))
        assertEquals("36.5px", normalizeLogoHeight(36.5))
        assertEquals("2.5rem", normalizeLogoHeight("2.5rem"))
        assertEquals("10px", normalizeLogoHeight("10"))
        assertEquals("#aabbcc", normalizeHexColor("#abc"))
        assertNull(normalizeHexColor("nope"))
        assertEquals("#112233", resolveThemeColor("#112233", "#000000"))
        assertEquals("#000000", resolveThemeColor("nope", "#000000"))
        assertEquals(BrandDisplay.BOTH, normalizeBrandDisplay(null))

        assertTrue(brandMarkHtml(base).contains("ff-brand-logo"))
        brandMarkHtml(FireflyBranding.DEFAULT.copy(showLogo = true, logoUrl = "  ", showBrandName = false))
        brandMarkHtml(FireflyBranding.DEFAULT.copy(showLogo = true, logoUrl = "/l.svg", logoDarkUrl = null, showBrandName = false))
        brandMarkHtml(FireflyBranding.DEFAULT.copy(showLogo = true, logoUrl = "/l.svg", logoDarkUrl = "/d.svg", showBrandName = true), "extra")
        val marked = brandMarkHtml(
            FireflyBranding.DEFAULT.copy(
                brandName = "<em>Hydro</em>",
                showBrandName = true,
                showLogo = true,
                logoUrl = """/q"&<.svg""",
            ),
        )
        assertTrue(marked.contains("""<span class="ff-brand"><em>Hydro</em></span>"""))
        assertTrue(marked.contains("""src="/q&quot;&amp;&lt;.svg""""))
        assertEquals("Hydro", plainTextFromHtml("<em>Hydro</em>"))
        assertEquals("Desk", plainTextFromHtml("Desk"))

        assertThrows<FireflyException> { resolveGoogleFont("") }
        assertThrows<FireflyException> { resolveGoogleFont(42) }
        resolveGoogleFont("DM Sans")
        resolveGoogleFont(GoogleFontOptions("Inter", italic = true, weights = listOf(400, 700, 400)))
        resolveGoogleFont(GoogleFontOptions("Roboto", weights = emptyList()))

        Stylesheet.render(KonaTheme(), null)
        Stylesheet.render(KonaTheme(), base)
        Stylesheet.render(KonaTheme(), FireflyBranding.DEFAULT.copy(primaryColor = "#112233", accentColor = "#445566", fontFamily = "X"))
        Stylesheet.render(KonaTheme(), FireflyBranding.DEFAULT.copy(primaryColor = "nope", accentColor = "xyz", fontFamily = ""))
        Panel().branding(base).render()
        normalizeLogoHeight(0)
        normalizeLogoHeight(Double.NEGATIVE_INFINITY)
        normalizeLogoHeight(Double.POSITIVE_INFINITY)

        AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->ok<!-- ff-hook:body.end --></main>"""),
                "css",
                brandName = "AuthCo",
                mode = ChromeMode.AUTH,
                branding = base.copy(fontUrl = "https://fonts.googleapis.com/css2?family=X", fontPreconnect = true, copyrightText = "© Auth"),
            ),
        )
        AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->ok<!-- ff-hook:body.end --></main>"""),
                "css",
                mode = ChromeMode.AUTH,
                branding = FireflyBranding.DEFAULT.copy(fontUrl = "https://cdn.example/font.css", fontPreconnect = false),
            ),
        )
        AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->ok<!-- ff-hook:body.end --></main>"""),
                "css",
                brandName = "",
                mode = ChromeMode.AUTH,
                branding = FireflyBranding.DEFAULT,
            ),
        )
        AuthLayout().render(
            ChromeContext(
                KonaTheme(),
                RenderHooks(),
                mapOf("body" to """<main><!-- ff-hook:body.start -->ok<!-- ff-hook:body.end --></main>"""),
                "css",
                brandName = "Firefly",
                mode = ChromeMode.AUTH,
                branding = FireflyBranding.DEFAULT,
            ),
        )
        buildBrandingCss(FireflyBranding.DEFAULT.copy(primaryColor = "zzz", accentColor = "zzz"))
        BrandingOverrideContext("a", "b", "c")

        assertEquals("2rem", resolveBranding(null).logoHeight)
        assertEquals("auto", resolveBranding(null).logoWidth)
        val heightOnly = resolveBranding(PanelBranding(logoHeight = "40px"))
        assertEquals("40px", heightOnly.logoHeight)
        assertEquals("auto", heightOnly.logoWidth)
        val widthOnly = resolveBranding(PanelBranding(logoWidth = "8rem"))
        assertEquals("auto", widthOnly.logoHeight)
        assertEquals("8rem", widthOnly.logoWidth)
        val both = resolveBranding(PanelBranding(logoHeight = 32, logoWidth = "4rem"))
        assertEquals("32px", both.logoHeight)
        assertEquals("4rem", both.logoWidth)
        assertEquals("2rem" to "auto", resolveLogoBox(null, null))
        assertEquals("36px" to "auto", resolveLogoBox(36, " "))
        assertEquals("auto" to "2rem", resolveLogoBox(0, "2rem"))
        assertEquals("1px" to "2px", resolveLogoBox("1px", "2px"))
        assertEquals("2rem" to "auto", resolveLogoBox(-1, Double.NaN))

        val widthOverride = resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(logoWidth = "4rem"))
        assertEquals("auto", widthOverride.logoHeight)
        assertEquals("4rem", widthOverride.logoWidth)
        val keptHeight = resolveEffectiveBranding(
            PanelBranding(name = "P", logoHeight = "40px"),
            PanelBranding(logoWidth = "3rem"),
        )
        assertEquals("40px", keptHeight.logoHeight)
        assertEquals("3rem", keptHeight.logoWidth)
        val keptWidth = resolveEffectiveBranding(
            PanelBranding(name = "P", logoWidth = "5rem"),
            PanelBranding(logoHeight = "28px"),
        )
        assertEquals("28px", keptWidth.logoHeight)
        assertEquals("5rem", keptWidth.logoWidth)
        val heightOnlyOverride = resolveEffectiveBranding(PanelBranding(name = "P"), PanelBranding(logoHeight = "28px"))
        assertEquals("28px", heightOnlyOverride.logoHeight)
        assertEquals("auto", heightOnlyOverride.logoWidth)
        val bothOverride = resolveEffectiveBranding(
            PanelBranding(name = "P", logoHeight = "10px", logoWidth = "10px"),
            PanelBranding(logoHeight = "22px", logoWidth = "2rem"),
        )
        assertEquals("22px", bothOverride.logoHeight)
        assertEquals("2rem", bothOverride.logoWidth)
        val untouched = resolveEffectiveBranding(
            PanelBranding(name = "P", logoHeight = "18px", logoWidth = "1rem"),
            PanelBranding(name = "Q"),
        )
        assertEquals("18px", untouched.logoHeight)
        assertEquals("1rem", untouched.logoWidth)
        assertEquals("40px", resolveEffectiveBranding(
            PanelBranding(name = "P", logoHeight = "40px"),
            PanelBranding(logoHeight = 0, logoWidth = -1),
        ).logoHeight)
        assertEquals("auto", resolveEffectiveBranding(null, PanelBranding(logoWidth = "6rem")).logoHeight)
        assertEquals("6rem", resolveEffectiveBranding(null, PanelBranding(logoWidth = "6rem")).logoWidth)
        assertEquals(
            "30px",
            resolveEffectiveBranding(PanelBranding(logoHeight = "30px"), PanelBranding(logoWidth = "  ")).logoHeight,
        )

        assertEquals("auto", normalizeLogoWidth(null))
        assertEquals("12px", normalizeLogoWidth(12))
        assertEquals("4rem", normalizeLogoWidth("4rem"))
        assertEquals("auto", normalizeLogoWidth(-2))
        assertEquals("auto", normalizeLogoWidth(""))
        assertEquals("8px", normalizeLogoWidth("8"))
        assertEquals("1.5px", normalizeLogoWidth(1.5))
        assertEquals("auto", normalizeLogoWidth(Double.NaN))
        assertEquals("auto", normalizeLogoWidth(Double.POSITIVE_INFINITY))
        assertEquals("auto", normalizeLogoWidth("  "))

        assertEquals(
            "5rem",
            mergeBranding(
                FireflyBranding.DEFAULT.copy(logoWidth = "5rem", logoHeight = "3rem"),
                FireflyBranding.DEFAULT.copy(logoWidth = " ", logoHeight = " "),
            ).logoWidth,
        )
        assertEquals(
            "6rem",
            mergeBranding(
                FireflyBranding.DEFAULT,
                FireflyBranding.DEFAULT.copy(logoWidth = "6rem", logoHeight = "4rem"),
            ).logoWidth,
        )
        assertEquals("9rem", PanelBranding.builder().logoWidth("9rem").logoHeight("2rem").build().logoWidth)
        assertEquals("2rem", mergePanelBranding(PanelBranding(logoWidth = "1rem"), PanelBranding(logoWidth = "2rem"))!!.logoWidth)
        assertEquals("1rem", mergePanelBranding(PanelBranding(logoWidth = "1rem"), PanelBranding(name = "N"))!!.logoWidth)
        assertTrue(buildBrandingCss(widthOnly).contains("--ff-logo-width: 8rem"))
        assertTrue(buildBrandingCss(widthOnly).contains("--ff-logo-height: auto"))
        assertTrue(Stylesheet.render(KonaTheme(), widthOnly).contains("--ff-logo-width: 8rem"))
        assertTrue(Stylesheet.render(KonaTheme(), null).contains("--ff-logo-width: auto"))
        assertTrue(brandMarkHtml(widthOnly.copy(showLogo = true, logoUrl = "/w.svg")).contains("ff-brand-logo"))

        assertNotNull(FireflyBranding.DEFAULT)
    }
}
